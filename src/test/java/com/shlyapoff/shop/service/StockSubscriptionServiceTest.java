package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.StockSubscription;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.StockSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockSubscriptionServiceTest {
    @Mock StockSubscriptionRepository stockSubscriptionRepository;
    @Mock ProductRepository productRepository;
    @Mock NotificationOutboxService notificationOutboxService;
    @InjectMocks StockSubscriptionService service;

    @Test
    void createsOneSubscriptionForOutOfStockProduct() {
        Product product = new Product();
        product.setId(5L);
        product.setStockQuantity(0);
        when(productRepository.findByIdWithVariants(5L)).thenReturn(Optional.of(product));
        when(stockSubscriptionRepository.findByProductIdAndTelegramUserId(5L, 42L))
                .thenReturn(Optional.empty());

        assertThat(service.subscribe(5L, 42L)).isTrue();

        ArgumentCaptor<StockSubscription> captor = ArgumentCaptor.forClass(StockSubscription.class);
        verify(stockSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getProduct()).isSameAs(product);
        assertThat(captor.getValue().getTelegramUserId()).isEqualTo(42L);
    }
}
