package com.shlyapoff.shop.service;

import com.shlyapoff.shop.dto.ProductCard;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.OrderItemRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ShoppingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShoppingEventRepository shoppingEventRepository;

    @Transactional(readOnly = true)
    public List<ProductCard> similar(Product product, int limit) {
        Long categoryId = product.getCategory() == null ? null : product.getCategory().getId();
        Long brandId = product.getBrand() == null ? null : product.getBrand().getId();
        return productRepository.findSimilar(product.getId(), categoryId, brandId, PageRequest.of(0, limit))
                .stream().map(this::toCard).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductCard> boughtTogether(Long productId, int limit) {
        List<Long> ids = orderItemRepository.findBoughtTogetherProductIds(productId, PageRequest.of(0, limit));
        return orderedCards(ids);
    }

    @Transactional(readOnly = true)
    public List<ProductCard> recentlyViewed(String sessionId, Long telegramUserId, Long excludedProductId, int limit) {
        List<Long> ids = shoppingEventRepository.findRecentProductIds(sessionId, telegramUserId, limit + 1).stream()
                .filter(id -> !id.equals(excludedProductId))
                .limit(limit)
                .toList();
        return orderedCards(ids);
    }

    private List<ProductCard> orderedCards(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
        return productRepository.findActiveByIds(ids).stream()
                .sorted(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), Integer.MAX_VALUE)))
                .map(this::toCard)
                .toList();
    }

    private ProductCard toCard(Product product) {
        return new ProductCard(
                product.getId(), product.getName(), product.getDescription(), product.getPrice(),
                product.getStockQuantity(), product.getImageUrl(), product.getImageThumbnailUrl(),
                product.getCategory() == null ? null : product.getCategory().getVariantType()
        );
    }
}
