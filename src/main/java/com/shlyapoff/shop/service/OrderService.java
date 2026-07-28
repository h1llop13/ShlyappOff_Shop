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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId,
                                     String telegramUsername) {
        return createOrderFromCart(sessionId, customerName, phone, deliveryType, comment,
                telegramUserId, telegramUsername, false);
    }

    @Transactional
    public Order createOrderFromCart(String sessionId, String customerName, String phone,
                                     String deliveryType, String comment, Long telegramUserId,
                                     String telegramUsername, boolean useBonuses) {
        // Получаем корзину
        Optional<Cart> cartOpt = cartService.getCartBySessionIdForCheckout(sessionId);
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

            // Сумма = цена * количество
            subtotal = subtotal.add(cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        // Если заказ оформлен из Telegram Mini App — находим/заводим профиль клиента
        // и применяем скидку, накопленную по программе лояльности с ПРЕДЫДУЩИХ заказов.
        Customer customer = null;
        BigDecimal bonusesSpent = BigDecimal.ZERO;
        if (telegramUserId != null) {
            customer = customerService.findOrCreateByTelegram(telegramUserId, telegramUsername, null, null);
            if (useBonuses) {
                BigDecimal balance = customer.getBonusBalance() == null ? BigDecimal.ZERO : customer.getBonusBalance();
                bonusesSpent = balance.min(subtotal);
                customerService.spendBonuses(customer, bonusesSpent);
            }
        }

        BigDecimal total = subtotal.subtract(bonusesSpent);

        order.setSubtotalAmount(subtotal);
        order.setDiscountPercent(0);
        order.setBonusesSpent(bonusesSpent);
        order.setTotalAmount(total);
        order.setCustomer(customer);

        // Сохраняем заказ
        Order savedOrder = orderRepository.save(order);

        // Очищаем корзину
        if (telegramUserId == null) cartService.clearCart(sessionId);
        else cartService.clearCart(sessionId, telegramUserId);

        // ВАЖНО: сумма заказа НЕ прибавляется к totalSpent клиента здесь!
        // Заказ ещё не подтверждён администратором, поэтому он не должен
        // ни попадать в историю профиля, ни влиять на скидку по программе лояльности.
        // Начисление происходит в updateStatus(), когда админ подтверждает статус COMPLETED.

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

        if (nextStatus == OrderStatus.COMPLETED) {
            deductInventory(order);
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

    private void deductInventory(Order order) {
        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing((OrderItem item) -> item.getProduct().getId())
                        .thenComparing(item -> item.getProductVariant() == null ? 0L : item.getProductVariant().getId()))
                .toList();

        for (OrderItem item : items) {
            if (item.getProductVariant() != null) {
                ProductVariant variant = productVariantRepository.findByIdForUpdate(item.getProductVariant().getId())
                        .orElseThrow(() -> new IllegalStateException("Вариант товара для списания не найден"));
                deductVariantQuantity(variant, item.getQuantity());
            } else if (item.getVariantValue() != null) {
                throw new IllegalStateException("Нельзя списать остаток: у позиции заказа не указан вариант товара");
            } else {
                Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                        .orElseThrow(() -> new IllegalStateException("Товар для списания не найден"));
                deductProductQuantity(product, item.getQuantity());
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
