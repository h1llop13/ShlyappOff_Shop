package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final NotificationOutboxService notificationOutboxService;
    private final CustomerService customerService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PromoCodeService promoCodeService;

    @Value("${app.orders.reservation-minutes:15}")
    private long reservationMinutes;

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId,
                                     String telegramUsername) {
        return createOrderFromCart(sessionId, customerName, phone, deliveryType, comment,
                telegramUserId, telegramUsername, false, null);
    }

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId,
                                     String telegramUsername, boolean useBonuses) {
        return createOrderFromCart(sessionId, customerName, phone, deliveryType, comment,
                telegramUserId, telegramUsername, useBonuses, null);
    }

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId,
                                     String telegramUsername, boolean useBonuses, String promoCode) {
        Optional<Cart> cartOpt = cartService.getCartForCheckout(sessionId, telegramUserId);
        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }

        Cart cart = cartOpt.get();

        Order order = new Order();
        order.setCustomerName(customerName);
        order.setPhone(phone);
        order.setDeliveryType(deliveryType);
        order.setComment(comment);
        order.setTelegramUserId(telegramUserId);
        order.setTelegramUsername(telegramUsername);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (var cartItem : cart.getItems()) {
            cartService.validateCartItemAvailability(cartItem);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setProductName(cartItem.getProduct().getName());
            if (cartItem.getProductVariant() != null) {
                orderItem.setProductVariant(cartItem.getProductVariant());
                orderItem.setVariantValue(cartItem.getProductVariant().getValue());
            }
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtMoment(cartItem.getProduct().getPrice());

            order.addItem(orderItem);

            subtotal = subtotal.add(cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        Customer customer = null;
        if (telegramUserId != null) {
            customer = customerService.findOrCreateByTelegram(telegramUserId, telegramUsername, null, null);
        }

        PromoCodeService.AppliedPromoCode appliedPromoCode = promoCodeService.apply(promoCode, subtotal, customer);
        BigDecimal afterPromo = subtotal.subtract(appliedPromoCode.discountAmount());
        BigDecimal bonusesSpent = BigDecimal.ZERO;
        if (customer != null && useBonuses) {
            BigDecimal balance = customer.getBonusBalance() == null ? BigDecimal.ZERO : customer.getBonusBalance();
            bonusesSpent = balance.min(afterPromo);
            customerService.spendBonuses(customer, bonusesSpent);
        }
        BigDecimal total = afterPromo.subtract(bonusesSpent).setScale(2, RoundingMode.HALF_UP);

        order.setSubtotalAmount(subtotal);
        order.setDiscountPercent(0);
        order.setPromoCodeEntity(appliedPromoCode.promoCode());
        order.setPromoCode(appliedPromoCode.promoCode() == null ? null : appliedPromoCode.promoCode().getCode());
        order.setPromoDiscountAmount(appliedPromoCode.discountAmount());
        order.setBonusesSpent(bonusesSpent);
        order.setTotalAmount(total);
        order.setCustomer(customer);
        reserveInventory(order);
        order.setInventoryReserved(true);
        order.setReservationExpiresAt(LocalDateTime.now().plusMinutes(Math.max(1, reservationMinutes)));

        Order savedOrder = orderRepository.save(order);

        if (telegramUserId == null) cartService.clearCart(sessionId);
        else cartService.clearCart(sessionId, telegramUserId);

        notificationOutboxService.enqueueNewOrderNotification(savedOrder);

        return savedOrder;
    }

    public void bindTelegramCart(String sessionId, Long telegramUserId) {
        cartService.bindTelegramCart(sessionId, telegramUserId);
    }

    public List<Order> findAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrdersPage(int page) {
        return orderRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), 30, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public List<Order> findByCustomerId(Long customerId) {
        return orderRepository.findByCustomerIdWithItems(customerId);
    }

    /**
     * История заказов для профиля клиента: показываем только заказы,
     * подтверждённые администратором (статус COMPLETED). Пока заказ не подтверждён,
     * он не должен быть виден клиенту в истории и не должен влиять на скидку.
     */
    public List<Order> findConfirmedByCustomerId(Long customerId) {
        return orderRepository.findByCustomerIdAndStatusWithItems(customerId, OrderStatus.COMPLETED);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public void updateStatus(Long orderId, String status) {
        OrderStatus nextStatus = OrderStatus.from(status);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        OrderStatus previousStatus = order.getStatus();
        if (previousStatus == nextStatus) {
            return;
        }
        if (!previousStatus.canTransitionTo(nextStatus)) {
            throw new IllegalStateException(
                    "Недопустимый переход статуса: " + previousStatus + " -> " + nextStatus
            );
        }

        if ((nextStatus == OrderStatus.PROCESSING || nextStatus == OrderStatus.COMPLETED)
                && !Boolean.TRUE.equals(order.getInventoryReserved())) {
            reserveInventory(order);
            order.setInventoryReserved(true);
        }
        if (nextStatus == OrderStatus.PROCESSING) {
            order.setReservationExpiresAt(null);
        }
        if (nextStatus == OrderStatus.COMPLETED) {
            order.setInventoryReserved(false);
            order.setReservationExpiresAt(null);
            order.setCompletedAt(LocalDateTime.now());
        }
        if (nextStatus == OrderStatus.CANCELLED && Boolean.TRUE.equals(order.getInventoryReserved())) {
            restoreInventory(order);
            order.setInventoryReserved(false);
            order.setReservationExpiresAt(null);
        }

        order.setStatus(nextStatus);
        orderRepository.save(order);

        // Начисляем сумму заказа и бонусы только при первом завершении заказа.
        // ТОЛЬКО в момент, когда админ впервые подтверждает заказ статусом COMPLETED.
        // Проверка previousStatus защищает от повторного начисления,
        // если админ случайно ещё раз сохранит тот же статус.
        if (nextStatus == OrderStatus.COMPLETED && order.getCustomer() != null) {
            BigDecimal balanceBefore = order.getCustomer().getBonusBalance() == null
                    ? BigDecimal.ZERO : order.getCustomer().getBonusBalance();
            Customer customer = customerService.registerOrderAndAccrueBonuses(
                    order.getCustomer(), order.getSubtotalAmount(), order.getTotalAmount());
            if (customer != null && customer.getBonusBalance() != null) {
                order.setBonusesEarned(customer.getBonusBalance().subtract(balanceBefore));
            }
        }
        if (nextStatus == OrderStatus.CANCELLED && order.getCustomer() != null
                && order.getBonusesSpent() != null && order.getBonusesSpent().signum() > 0) {
            customerService.restoreBonuses(order.getCustomer(), order.getBonusesSpent());
        }
    }

    @Scheduled(fixedDelayString = "${app.orders.reservation-cleanup-ms:60000}")
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        for (Order order : orderRepository.findExpiredReservations(now)) {
            restoreInventory(order);
            order.setInventoryReserved(false);
            order.setReservationExpiresAt(null);
            orderRepository.save(order);
        }
    }

    private void reserveInventory(Order order) {
        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing((OrderItem item) -> item.getProduct().getId())
                        .thenComparing(item -> item.getProductVariant() == null ? 0L : item.getProductVariant().getId()))
                .toList();

        for (OrderItem item : items) {
            if (item.getProductVariant() != null) {
                ProductVariant variant = productVariantRepository.findByIdForUpdate(item.getProductVariant().getId())
                        .orElseThrow(() -> new IllegalStateException("Вариант товара для резервирования не найден"));
                deductVariantQuantity(variant, item.getQuantity());
            } else if (item.getVariantValue() != null) {
                throw new IllegalStateException("Нельзя зарезервировать остаток: у позиции заказа не указан вариант товара");
            } else {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new IllegalStateException("Товар для резервирования не найден"));
                deductProductQuantity(product, item.getQuantity());
            }
        }
    }

    private void restoreInventory(Order order) {
        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing((OrderItem item) -> item.getProduct().getId())
                        .thenComparing(item -> item.getProductVariant() == null ? 0L : item.getProductVariant().getId()))
                .toList();
        for (OrderItem item : items) {
            if (item.getProductVariant() != null) {
                ProductVariant variant = productVariantRepository.findByIdForUpdate(item.getProductVariant().getId())
                        .orElseThrow(() -> new IllegalStateException("Вариант товара для возврата резерва не найден"));
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            } else {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new IllegalStateException("Товар для возврата резерва не найден"));
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
        }
    }

    private void deductProductQuantity(Product product, int quantity) {
        int currentQuantity = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        if (currentQuantity < quantity) {
            throw new IllegalStateException("Недостаточно остатка для товара: " + product.getName());
        }
        product.setStockQuantity(currentQuantity - quantity);
    }

    private void deductVariantQuantity(ProductVariant variant, int quantity) {
        int currentQuantity = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
        if (currentQuantity < quantity) {
            throw new IllegalStateException("Недостаточно остатка для варианта: " + variant.getValue());
        }
        variant.setStockQuantity(currentQuantity - quantity);
    }

    public Optional<Cart> getCartForCheckout(String sessionId, Long telegramUserId) {
        Optional<Cart> cartOpt = cartService.getCart(sessionId, telegramUserId);
        if (cartOpt.isPresent() && !cartOpt.get().getItems().isEmpty()) {
            return cartOpt;
        }
        return Optional.empty();
    }

    public Optional<Cart> getCartForCheckout(String sessionId) {
        Optional<Cart> cartOpt = cartService.getCartBySessionId(sessionId);
        return cartOpt.filter(cart -> !cart.getItems().isEmpty());
    }

    public BigDecimal findBonusBalance(Long telegramUserId) {
        return customerService.findByTelegramUserId(telegramUserId)
                .map(Customer::getBonusBalance).orElse(BigDecimal.ZERO);
    }
}
