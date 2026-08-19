package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.OrderStatusHistory;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.model.InventoryMovementType;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.OrderStatusHistoryRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.Arrays;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartService cartService;
    private final NotificationOutboxService notificationOutboxService;
    private final CustomerService customerService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PromoCodeService promoCodeService;
    private final PricingService pricingService;
    private final AdminAuditLogService auditLogService;
    private final InventoryService inventoryService;

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
            orderItem.setPriceAtMoment(pricingService.normalize(cartItem.getProduct().getPrice()));

            order.addItem(orderItem);
        }

        Customer customer = null;
        if (telegramUserId != null) {
            customer = customerService.findOrCreateByTelegram(telegramUserId, telegramUsername, null, null);
        }

        BigDecimal subtotal = pricingService.cartSubtotal(cart);
        PromoCodeService.AppliedPromoCode appliedPromoCode = promoCodeService.apply(promoCode, subtotal, customer);
        BigDecimal bonusBalance = customer == null ? BigDecimal.ZERO : customer.getBonusBalance();
        PricingService.PricingBreakdown pricing = pricingService.calculate(
                cart,
                appliedPromoCode.discountAmount(),
                bonusBalance,
                customer != null && useBonuses,
                deliveryType
        );
        if (customer != null) {
            customerService.spendBonuses(customer, pricing.bonusesSpent());
        }

        order.setSubtotalAmount(pricing.subtotalAmount());
        order.setDiscountPercent(0);
        order.setPromoCodeEntity(appliedPromoCode.promoCode());
        order.setPromoCode(appliedPromoCode.promoCode() == null ? null : appliedPromoCode.promoCode().getCode());
        order.setPromoDiscountAmount(pricing.promoDiscountAmount());
        order.setBonusesSpent(pricing.bonusesSpent());
        order.setDeliveryAmount(pricing.deliveryAmount());
        order.setTotalAmount(pricing.totalAmount());
        order.setCustomer(customer);
        Order savedOrder = orderRepository.save(order);
        reserveInventory(savedOrder);
        order.setInventoryReserved(true);
        order.setReservationExpiresAt(LocalDateTime.now().plusMinutes(Math.max(1, reservationMinutes)));

        recordStatusChange(savedOrder, null, OrderStatus.NEW, creationActor(savedOrder), null);

        if (telegramUserId == null) cartService.clearCart(sessionId);
        else cartService.clearCart(sessionId, telegramUserId);

        notificationOutboxService.enqueueNewOrderNotification(savedOrder);
        notificationOutboxService.enqueueCustomerStatusNotification(
                savedOrder, null, OrderStatus.NEW, null);

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

    @Transactional(readOnly = true)
    public List<Order> findByCustomerId(Long customerId) {
        return orderRepository.findByCustomerIdWithItems(customerId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<OrderStatusHistory>> findStatusHistory(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OrderStatusHistory>> result = new LinkedHashMap<>();
        orderStatusHistoryRepository.findByOrderIdInOrderByChangedAtAscIdAsc(
                        orders.stream().map(Order::getId).toList())
                .forEach(entry -> result.computeIfAbsent(entry.getOrder().getId(), ignored -> new java.util.ArrayList<>())
                        .add(entry));
        return result;
    }

    public List<OrderStatus> allowedNextStatuses(Order order) {
        return Arrays.stream(OrderStatus.values())
                .filter(status -> status != OrderStatus.CANCELLED)
                .filter(order.getStatus()::canTransitionTo)
                .filter(status -> isFulfilmentStatusAllowed(order, status))
                .toList();
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public void updateStatus(Long orderId, String status) {
        updateStatus(orderId, status, null, "system");
    }

    @Transactional
    public void updateStatus(Long orderId, String status, String actorUsername) {
        updateStatus(orderId, status, null, actorUsername);
    }

    @Transactional
    public void updateStatus(Long orderId, String status, String cancellationReason, String actorUsername) {
        OrderStatus nextStatus = OrderStatus.from(status);
        Order order = orderRepository.findByIdForUpdate(orderId)
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

        if (!isFulfilmentStatusAllowed(order, nextStatus)) {
            throw new IllegalStateException(nextStatus == OrderStatus.READY
                    ? "Статус READY доступен только для самовывоза"
                    : "Статус SHIPPED доступен только для доставки");
        }

        String normalizedCancellationReason = normalizeCancellationReason(nextStatus, cancellationReason);

        if (!nextStatus.isTerminal() && nextStatus != OrderStatus.NEW
                && !Boolean.TRUE.equals(order.getInventoryReserved())) {
            reserveInventory(order);
            order.setInventoryReserved(true);
        }
        if (nextStatus == OrderStatus.CONFIRMED) {
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

        if (nextStatus == OrderStatus.CANCELLED) {
            order.setCancelledAt(LocalDateTime.now());
            order.setCancellationReason(normalizedCancellationReason);
        }

        order.setStatus(nextStatus);
        orderRepository.save(order);
        recordStatusChange(order, previousStatus, nextStatus, actorUsername, normalizedCancellationReason);
        auditLogService.recordChange(actorUsername, AdminAuditAction.ORDER_STATUS_CHANGED,
                "ORDER", order.getId(), "status", previousStatus, nextStatus);
        if (nextStatus == OrderStatus.CANCELLED) {
            auditLogService.recordChange(actorUsername, AdminAuditAction.ORDER_STATUS_CHANGED,
                    "ORDER", order.getId(), "cancellation_reason", null, normalizedCancellationReason);
        }

        // Начисляем сумму заказа и бонусы только при первом завершении заказа.
        // ТОЛЬКО в момент, когда админ впервые завершает заказ статусом COMPLETED.
        // Проверка previousStatus защищает от повторного начисления,
        // если админ случайно ещё раз сохранит тот же статус.
        if (nextStatus == OrderStatus.COMPLETED && order.getCustomer() != null) {
            BigDecimal balanceBefore = order.getCustomer().getBonusBalance() == null
                    ? BigDecimal.ZERO : order.getCustomer().getBonusBalance();
            BigDecimal bonusAccrualBase = pricingService.bonusAccrualBase(
                    order.getTotalAmount(), order.getDeliveryAmount());
            Customer customer = customerService.registerOrderAndAccrueBonuses(
                    order.getCustomer(), order.getSubtotalAmount(), bonusAccrualBase);
            if (customer != null && customer.getBonusBalance() != null) {
                order.setBonusesEarned(customer.getBonusBalance().subtract(balanceBefore));
            }
        }
        if (nextStatus == OrderStatus.CANCELLED && order.getCustomer() != null
                && order.getBonusesSpent() != null && order.getBonusesSpent().signum() > 0) {
            customerService.restoreBonuses(order.getCustomer(), order.getBonusesSpent());
        }
        notificationOutboxService.enqueueCustomerStatusNotification(
                order, previousStatus, nextStatus, normalizedCancellationReason);
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
                deductVariantQuantity(variant, item.getQuantity(), order.getId());
            } else if (item.getVariantValue() != null) {
                throw new IllegalStateException("Нельзя зарезервировать остаток: у позиции заказа не указан вариант товара");
            } else {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new IllegalStateException("Товар для резервирования не найден"));
                deductProductQuantity(product, item.getQuantity(), order.getId());
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
                int before = variant.getStockQuantity();
                variant.setStockQuantity(before + item.getQuantity());
                inventoryService.record(variant.getProduct(), variant, InventoryMovementType.RESERVATION_RELEASE,
                        before, variant.getStockQuantity(), "Возврат резерва заказа", "system",
                        "ORDER", order.getId());
            } else {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new IllegalStateException("Товар для возврата резерва не найден"));
                int before = product.getStockQuantity();
                product.setStockQuantity(before + item.getQuantity());
                inventoryService.record(product, null, InventoryMovementType.RESERVATION_RELEASE,
                        before, product.getStockQuantity(), "Возврат резерва заказа", "system",
                        "ORDER", order.getId());
            }
        }
    }

    private void deductProductQuantity(Product product, int quantity, Long orderId) {
        int currentQuantity = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        if (currentQuantity < quantity) {
            throw new IllegalStateException("Недостаточно остатка для товара: " + product.getName());
        }
        product.setStockQuantity(currentQuantity - quantity);
        inventoryService.record(product, null, InventoryMovementType.ORDER_RESERVATION,
                currentQuantity, product.getStockQuantity(), "Резервирование заказа", "customer",
                "ORDER", orderId);
    }

    private void deductVariantQuantity(ProductVariant variant, int quantity, Long orderId) {
        int currentQuantity = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
        if (currentQuantity < quantity) {
            throw new IllegalStateException("Недостаточно остатка для варианта: " + variant.getValue());
        }
        variant.setStockQuantity(currentQuantity - quantity);
        inventoryService.record(variant.getProduct(), variant, InventoryMovementType.ORDER_RESERVATION,
                currentQuantity, variant.getStockQuantity(), "Резервирование заказа", "customer",
                "ORDER", orderId);
    }

    private void recordStatusChange(Order order, OrderStatus previousStatus, OrderStatus newStatus,
                                    String actor, String cancellationReason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        history.setChangedBy(normalizeActor(actor));
        history.setCancellationReason(cancellationReason);
        orderStatusHistoryRepository.save(history);
    }

    private String creationActor(Order order) {
        if (order.getTelegramUsername() != null && !order.getTelegramUsername().isBlank()) {
            return "customer:@" + order.getTelegramUsername();
        }
        if (order.getTelegramUserId() != null) {
            return "customer:" + order.getTelegramUserId();
        }
        return "customer:web";
    }

    private String normalizeActor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor.trim();
    }

    private String normalizeCancellationReason(OrderStatus nextStatus, String cancellationReason) {
        if (nextStatus != OrderStatus.CANCELLED) {
            return null;
        }
        if (cancellationReason == null || cancellationReason.isBlank()) {
            throw new IllegalArgumentException("Укажите причину отмены заказа");
        }
        String normalized = cancellationReason.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Причина отмены не должна превышать 500 символов");
        }
        return normalized;
    }

    private boolean isFulfilmentStatusAllowed(Order order, OrderStatus status) {
        boolean delivery = order.getDeliveryType() != null
                && (order.getDeliveryType().equalsIgnoreCase("Доставка")
                || order.getDeliveryType().equalsIgnoreCase("DELIVERY"));
        if (status == OrderStatus.READY) {
            return !delivery;
        }
        if (status == OrderStatus.SHIPPED) {
            return delivery;
        }
        return true;
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
