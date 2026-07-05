package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final TelegramNotificationService telegramNotificationService;

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId) {
        // Получаем корзину
        Optional<Cart> cartOpt = cartService.getCartBySessionId(sessionId);
        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }

        Cart cart = cartOpt.get();

        // Создаем заказ
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setPhone(phone);
        order.setDeliveryType(deliveryType);
        order.setComment(comment);
        order.setTelegramUserId(telegramUserId);

        // Рассчитываем общую сумму и добавляем товары
        BigDecimal total = BigDecimal.ZERO;
        for (var cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtMoment(cartItem.getProduct().getPrice());

            order.addItem(orderItem);

            // Сумма = цена * количество
            total = total.add(cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setTotalAmount(total);

        // Сохраняем заказ
        Order savedOrder = orderRepository.save(order);

        // Очищаем корзину
        cartService.clearCart(sessionId);

        telegramNotificationService.notifyAdminAboutNewOrder(savedOrder);

        return savedOrder;
    }

    public List<Order> findAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public void updateStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
        order.setStatus(status);
        orderRepository.save(order);
    }

    public Optional<com.shlyapoff.shop.model.Cart> getCartForCheckout(String sessionId) {
        Optional<com.shlyapoff.shop.model.Cart> cartOpt = cartService.getCartBySessionId(sessionId);
        if (cartOpt.isPresent() && !cartOpt.get().getItems().isEmpty()) {
            return cartOpt;
        }
        return Optional.empty();
    }
}