package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.AbandonmentReason;
import com.shlyapoff.shop.model.ShoppingEventType;
import com.shlyapoff.shop.repository.ShoppingEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingEventServiceTest {
    @Mock ShoppingEventRepository shoppingEventRepository;
    @InjectMocks ShoppingEventService service;

    @Test
    void calculatesFunnelConversionsAndReasons() {
        when(shoppingEventRepository.countDistinctSessionIdByEventTypeAndCreatedAtAfter(eq(ShoppingEventType.PRODUCT_VIEW), any(LocalDateTime.class))).thenReturn(100L);
        when(shoppingEventRepository.countDistinctSessionIdByEventTypeAndCreatedAtAfter(eq(ShoppingEventType.CART_ADD), any(LocalDateTime.class))).thenReturn(40L);
        when(shoppingEventRepository.countDistinctSessionIdByEventTypeAndCreatedAtAfter(eq(ShoppingEventType.CHECKOUT_STARTED), any(LocalDateTime.class))).thenReturn(20L);
        when(shoppingEventRepository.countDistinctSessionIdByEventTypeAndCreatedAtAfter(eq(ShoppingEventType.ORDER_CREATED), any(LocalDateTime.class))).thenReturn(10L);
        when(shoppingEventRepository.countDistinctSessionIdByEventTypeAndCreatedAtAfter(eq(ShoppingEventType.CART_ABANDONED), any(LocalDateTime.class))).thenReturn(7L);
        ShoppingEventRepository.AbandonmentCount row = org.mockito.Mockito.mock(ShoppingEventRepository.AbandonmentCount.class);
        when(row.getReason()).thenReturn(AbandonmentReason.TOTAL_PRICE);
        when(row.getTotal()).thenReturn(4L);
        when(shoppingEventRepository.countAbandonmentReasons(any())).thenReturn(List.of(row));

        var funnel = service.getFunnel(30);

        assertThat(funnel.viewToCartPercent()).isEqualTo(40.0);
        assertThat(funnel.cartToCheckoutPercent()).isEqualTo(50.0);
        assertThat(funnel.checkoutToOrderPercent()).isEqualTo(50.0);
        assertThat(funnel.abandonmentReasons()).containsExactly(
                new ShoppingEventService.AbandonmentData(AbandonmentReason.TOTAL_PRICE, 4));
    }
}
