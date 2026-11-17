package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.dto.ProductCard;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CatalogQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Slice<ProductCard> findCards(String name, Long categoryId, Long brandId,
                                        BigDecimal minPrice, BigDecimal maxPrice,
                                        boolean onlyInStock, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductCard> query = builder.createQuery(ProductCard.class);
        Root<Product> product = query.from(Product.class);
        var category = product.join("category", JoinType.LEFT);

        query.select(builder.construct(ProductCard.class,
                product.get("id"), product.get("name"), product.get("description"),
                product.get("price"), product.get("stockQuantity"), product.get("imageUrl"),
                product.get("imageThumbnailUrl"), category.get("variantType")));

        List<Predicate> filters = new ArrayList<>();
        filters.add(builder.isTrue(product.get("active")));
        if (name != null && !name.isBlank()) {
            String escapedName = name.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filters.add(builder.like(builder.lower(product.get("name")), "%" + escapedName + "%", '\\'));
        }
        if (categoryId != null) filters.add(builder.equal(category.get("id"), categoryId));
        if (brandId != null) filters.add(builder.equal(product.get("brand").get("id"), brandId));
        if (minPrice != null) filters.add(builder.greaterThanOrEqualTo(product.get("price"), minPrice));
        if (maxPrice != null) filters.add(builder.lessThanOrEqualTo(product.get("price"), maxPrice));
        if (onlyInStock) {
            var variantQuery = query.subquery(Long.class);
            Root<ProductVariant> variant = variantQuery.from(ProductVariant.class);
            variantQuery.select(variant.get("id"))
                    .where(builder.equal(variant.get("product"), product),
                            builder.greaterThan(variant.get("stockQuantity"), 0));
            filters.add(builder.or(builder.greaterThan(product.get("stockQuantity"), 0),
                    builder.exists(variantQuery)));
        }
        query.where(filters.toArray(Predicate[]::new));

        boolean descending = pageable.getSort().getOrderFor("price") == null
                || pageable.getSort().getOrderFor("price").isDescending();
        String sortField = pageable.getSort().getOrderFor("price") == null ? "createdAt" : "price";
        query.orderBy(descending ? builder.desc(product.get(sortField)) : builder.asc(product.get(sortField)),
                descending ? builder.desc(product.get("id")) : builder.asc(product.get("id")));

        List<ProductCard> rows = entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize() + 1)
                .getResultList();
        boolean hasNext = rows.size() > pageable.getPageSize();
        return new SliceImpl<>(hasNext ? rows.subList(0, pageable.getPageSize()) : rows,
                pageable, hasNext);
    }
}
