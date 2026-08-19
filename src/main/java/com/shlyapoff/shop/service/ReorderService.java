package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReorderService {
    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ShoppingEventService shoppingEventService;

    @Transactional
    public int repeat(Long orderId, Long customerId, String sessionId, Long telegramUserId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Нельзя повторить чужой заказ");
        }
        if (order.getItems().isEmpty()) {
            throw new IllegalStateException("В заказе нет товаров");
        }

        int added = 0;
        for (var item : order.getItems()) {
            Long variantId = item.getProductVariant() == null ? null : item.getProductVariant().getId();
            cartService.addToCart(sessionId, telegramUserId, item.getProduct().getId(), variantId, item.getQuantity());
            shoppingEventService.recordCartAdd(sessionId, telegramUserId, item.getProduct());
            added += item.getQuantity();
        }
        return added;
    }
}
