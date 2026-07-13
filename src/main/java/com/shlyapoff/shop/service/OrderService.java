package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final TelegramNotificationService telegramNotificationService;
    private final CustomerService customerService;

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId,
                                     String telegramUsername) {
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
        order.setTelegramUsername(telegramUsername);

        // Рассчитываем сумму товаров (без скидки) и добавляем товары
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductName(cartItem.getProduct().getName());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtMoment(cartItem.getProduct().getPrice());

            order.addItem(orderItem);

            // Сумма = цена * количество
            subtotal = subtotal.add(cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        // Если заказ оформлен из Telegram Mini App — находим/заводим профиль клиента
        // и применяем скидку, накопленную по программе лояльности с ПРЕДЫДУЩИХ заказов.
        Customer customer = null;
        int discountPercent = 0;
        if (telegramUserId != null) {
            customer = customerService.findOrCreateByTelegram(telegramUserId, telegramUsername, null, null);
            discountPercent = customer.getDiscountPercent() == null ? 0 : customer.getDiscountPercent();
        }

        BigDecimal total = applyDiscount(subtotal, discountPercent);

        order.setSubtotalAmount(subtotal);
        order.setDiscountPercent(discountPercent);
        order.setTotalAmount(total);
        order.setCustomer(customer);

        // Сохраняем заказ
        Order savedOrder = orderRepository.save(order);

        // Очищаем корзину
        cartService.clearCart(sessionId);

        // Обновляем накопленную сумму клиента и пересчитываем скидку на СЛЕДУЮЩИЙ заказ
        if (customer != null) {
            customerService.registerOrderAndRecalculateDiscount(customer, subtotal);
        }

        telegramNotificationService.notifyAdminAboutNewOrder(savedOrder);

        return savedOrder;
    }

    private BigDecimal applyDiscount(BigDecimal subtotal, int discountPercent) {
        if (discountPercent <= 0) {
            return subtotal;
        }
        BigDecimal multiplier = BigDecimal.valueOf(100 - discountPercent).divide(BigDecimal.valueOf(100));
        return subtotal.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public List<Order> findAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Order> findByCustomerId(Long customerId) {
        return orderRepository.findByCustomerIdWithItems(customerId);
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

    public Optional<Cart> getCartForCheckout(String sessionId) {
        Optional<Cart> cartOpt = cartService.getCartBySessionId(sessionId);
        if (cartOpt.isPresent() && !cartOpt.get().getItems().isEmpty()) {
            return cartOpt;
        }
        return Optional.empty();
    }
}