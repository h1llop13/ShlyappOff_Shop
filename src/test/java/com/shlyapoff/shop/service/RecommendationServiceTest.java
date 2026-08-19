package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Category;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.OrderItemRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ShoppingEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock ProductRepository productRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ShoppingEventRepository shoppingEventRepository;
    @InjectMocks RecommendationService service;

    @Test
    void boughtTogetherKeepsRankingReturnedByAnalyticsQuery() {
        when(orderItemRepository.findBoughtTogetherProductIds(any(), any())).thenReturn(List.of(3L, 2L));
        when(productRepository.findActiveByIds(List.of(3L, 2L)))
                .thenReturn(List.of(product(2L, "Второй"), product(3L, "Первый")));

        var result = service.boughtTogether(1L, 6);

        assertThat(result).extracting("id").containsExactly(3L, 2L);
    }

    @Test
    void recentlyViewedExcludesCurrentProduct() {
        when(shoppingEventRepository.findRecentProductIds("session", 10L, 4))
                .thenReturn(List.of(7L, 6L, 5L));
        when(productRepository.findActiveByIds(List.of(6L, 5L)))
                .thenReturn(List.of(product(5L, "Пятый"), product(6L, "Шестой")));

        var result = service.recentlyViewed("session", 10L, 7L, 3);

        assertThat(result).extracting("id").containsExactly(6L, 5L);
    }

    private Product product(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(BigDecimal.TEN);
        product.setStockQuantity(1);
        product.setCategory(new Category());
        return product;
    }
}
