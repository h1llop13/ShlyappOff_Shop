package com.shlyapoff.shop.service;

import com.shlyapoff.shop.dto.ProductCard;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    @Cacheable("latestProducts")
    public List<ProductCard> findLatestActive() {
        return productRepository.findLatestActiveCards(PageRequest.of(0, 12));
    }

    @Transactional(readOnly = true)
    public Page<Product> findAdminProducts(int page) {
        return productRepository.findByActiveTrue(PageRequest.of(Math.max(page, 0), 30, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public List<Product> findByCategory(long categoryId) {
        return productRepository.findByCategory_Id(categoryId);
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(cacheNames = "latestProducts", allEntries = true)
    public boolean deleteById(Long id) {
        return productRepository.findById(id)
                .map(product -> {
                    // Keep completed orders intact: they reference the product for order history.
                    product.setActive(false);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Page<ProductCard> findWithFilters(String name, Long categoryId, Long brandId,
                                             java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                             boolean onlyInStock, String sort, int page, int size) {
        Sort order = switch (sort == null ? "newest" : sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        // PostgreSQL cannot infer the type of a null parameter inside lower(...).
        // An empty string preserves the "no search filter" behavior without sending null.
        String searchTerm = name == null || name.isBlank() ? "" : name.trim();
        return productRepository.findCardsWithFilters(
                searchTerm, categoryId, brandId,
                minPrice, maxPrice, onlyInStock, PageRequest.of(Math.max(page, 0), size, order));
    }

    /** Совместимость для внутренних вызовов без расширенных фильтров. */
    public Page<ProductCard> findWithFilters(String name, Long categoryId, Long brandId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), size);
        boolean hasName = name != null && !name.isBlank();
        if (hasName && categoryId != null && brandId != null) return productRepository.findCardsByNameAndCategoryAndBrand(name, categoryId, brandId, pageable);
        if (hasName && categoryId != null) return productRepository.findCardsByNameAndCategory(name, categoryId, pageable);
        if (hasName && brandId != null) return productRepository.findCardsByNameAndBrand(name, brandId, pageable);
        if (categoryId != null && brandId != null) return productRepository.findCardsByCategoryAndBrand(categoryId, brandId, pageable);
        if (hasName) return productRepository.findCardsByName(name, pageable);
        if (categoryId != null) return productRepository.findCardsByCategory(categoryId, pageable);
        if (brandId != null) return productRepository.findCardsByBrand(brandId, pageable);
        return productRepository.findAllActiveCards(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByIdWithVariants(Long id) {
        return productRepository.findByIdWithVariants(id);
    }
}
