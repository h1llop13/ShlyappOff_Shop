package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.AdminAuditAction;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.OrderStatusHistory;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.ProductVariant;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.OrderStatusHistoryRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для OrderService.
 * CartService и TelegramNotificationService замоканы — проверяем только логику OrderService.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private CartService cartService;

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Mock
    private CustomerService customerService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private PromoCodeService promoCodeService;

    @Spy
    private PricingService pricingService = new PricingService(BigDecimal.ZERO);

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderService orderService;

    private static final String SESSION_ID = "session-abc";

    private Cart cartWithItems;

    @BeforeEach
    void setUp() {
        Product product1 = new Product();
        product1.setId(1L);
        product1.setName("Товар 1");
        product1.setPrice(new BigDecimal("100.00"));
        product1.setStockQuantity(10);

        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("Товар 2");
        product2.setPrice(new BigDecimal("250.50"));
        product2.setStockQuantity(10);

        CartItem item1 = new CartItem();
        item1.setProduct(product1);
        item1.setQuantity(2); // 200.00

        CartItem item2 = new CartItem();
        item2.setProduct(product2);
        item2.setQuantity(1); // 250.50

        cartWithItems = new Cart();
        cartWithItems.setId(5L);
        cartWithItems.setSessionId(SESSION_ID);
        cartWithItems.getItems().add(item1);
        cartWithItems.getItems().add(item2);

        lenient().when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product1));
        lenient().when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(product2));
        lenient().when(promoCodeService.apply(isNull(), any(BigDecimal.class), nullable(Customer.class)))
                .thenReturn(PromoCodeService.AppliedPromoCode.none());
    }

    @Test
    @DisplayName("создаёт заказ из корзины, верно считает сумму, очищает корзину и шлёт уведомление")
    void createsOrderFromCartSuccessfully() {
        Customer customer = new Customer();
        customer.setId(7L);
        customer.setTelegramUserId(12345L);
        customer.setDiscountPercent(0);

        when(cartService.getCartForCheckout(SESSION_ID, 12345L)).thenReturn(Optional.of(cartWithItems));
        when(customerService.findOrCreateByTelegram(12345L, "ivan_the_customer", null, null))
                .thenReturn(customer);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(99L);
            return order;
        });

        Order result = orderService.createOrderFromCart(
                SESSION_ID, "Иван Иванов", "+79990001122", "DELIVERY", "Позвонить заранее",
                12345L, "ivan_the_customer");

        // Сумма = 100.00*2 + 250.50*1 = 450.50
        assertThat(result.getSubtotalAmount()).isEqualByComparingTo("450.50");
        assertThat(result.getDiscountPercent()).isEqualTo(0);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("450.50");
        assertThat(result.getCustomerName()).isEqualTo("Иван Иванов");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getTelegramUsername()).isEqualTo("ivan_the_customer");
        assertThat(result.getTelegramUserId()).isEqualTo(12345L);
        assertThat(result.getCustomer()).isEqualTo(customer);

        verify(cartService).clearCart(SESSION_ID, 12345L);
        verify(notificationOutboxService).enqueueNewOrderNotification(result);
        verify(notificationOutboxService).enqueueCustomerStatusNotification(
                result, null, OrderStatus.NEW, null);
        // Заказ ещё не подтверждён администратором — сумма НЕ должна начисляться клиенту сразу.
        verify(customerService, never()).registerOrderAndRecalculateDiscount(any(), any());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("+79990001122");
    }

    @Test
    @DisplayName("списывает бонусы клиента по его выбору")
    void spendsCustomerBonusesWhenRequested() {
        Customer customer = new Customer();
        customer.setId(8L);
        customer.setTelegramUserId(999L);
        customer.setBonusBalance(new BigDecimal("50.00"));

        when(cartService.getCartForCheckout(SESSION_ID, 999L)).thenReturn(Optional.of(cartWithItems));
        when(customerService.findOrCreateByTelegram(999L, "vip_client", null, null)).thenReturn(customer);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrderFromCart(
                SESSION_ID, "Пётр", "+7000", "PICKUP", null, 999L, "vip_client", true);

        // 450.50 - 50 бонусов = 400.50
        assertThat(result.getSubtotalAmount()).isEqualByComparingTo("450.50");
        assertThat(result.getBonusesSpent()).isEqualByComparingTo("50.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("400.50");

        // Заказ ещё не подтверждён администратором — сумма НЕ должна начисляться клиенту сразу.
        verify(customerService, never()).registerOrderAndRecalculateDiscount(any(), any());
    }

    @Test
    @DisplayName("не создаёт клиента и не применяет скидку, если заказ оформлен без Telegram")
    void doesNotCreateCustomerWithoutTelegramUserId() {
        when(cartService.getCartForCheckout(SESSION_ID, null)).thenReturn(Optional.of(cartWithItems));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrderFromCart(
                SESSION_ID, "Гость", "+7000", "PICKUP", null, null, null);

        assertThat(result.getCustomer()).isNull();
        assertThat(result.getDiscountPercent()).isEqualTo(0);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("450.50");

        verify(customerService, never()).findOrCreateByTelegram(any(), any(), any(), any());
        verify(customerService, never()).registerOrderAndRecalculateDiscount(any(), any());
    }

    @Test
    @DisplayName("выбрасывает исключение и не сохраняет заказ, если корзина отсутствует")
    void throwsWhenCartMissing() {
        when(cartService.getCartForCheckout(SESSION_ID, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                SESSION_ID, "Иван", "+7000", "PICKUP", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Корзина пуста");

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
        verify(notificationOutboxService, never()).enqueueNewOrderNotification(any());
    }

    @Test
    @DisplayName("выбрасывает исключение, если корзина есть, но пустая (без товаров)")
    void throwsWhenCartHasNoItems() {
        Cart emptyCart = new Cart();
        emptyCart.setSessionId(SESSION_ID);

        when(cartService.getCartForCheckout(SESSION_ID, null)).thenReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                SESSION_ID, "Иван", "+7000", "PICKUP", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Корзина пуста");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("подтверждает новый заказ")
    void updatesOrderStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.NEW);

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        orderService.updateStatus(1L, "CONFIRMED", "order-admin");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
        verify(adminAuditLogService).recordChange(
                "order-admin", AdminAuditAction.ORDER_STATUS_CHANGED,
                "ORDER", 1L, "status", OrderStatus.NEW, OrderStatus.CONFIRMED);
        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("order-admin");
        assertThat(historyCaptor.getValue().getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("повторно резервирует остаток обычного товара при подтверждении заказа")
    void deductsProductStockWhenOrderIsCompleted() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Товар");
        product.setStockQuantity(5);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.NEW);
        order.addItem(item);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        orderService.updateStatus(1L, "CONFIRMED");

        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(productRepository).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("повторно резервирует остаток конкретного варианта при подтверждении заказа")
    void deductsVariantStockWhenOrderIsCompleted() {
        Product product = new Product();
        product.setId(1L);
        ProductVariant variant = new ProductVariant();
        variant.setId(7L);
        variant.setProduct(product);
        variant.setValue("Манго");
        variant.setStockQuantity(4);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setQuantity(3);
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.NEW);
        order.addItem(item);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(variant));

        orderService.updateStatus(1L, "CONFIRMED");

        assertThat(variant.getStockQuantity()).isEqualTo(1);
        assertThat(variant.getInStock()).isTrue();
        verify(productVariantRepository).findByIdForUpdate(7L);
    }

    @Test
    @DisplayName("не подтверждает заказ, если для повторного резерва недостаточно остатка")
    void doesNotCompleteOrderWhenStockIsInsufficient() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Товар");
        product.setStockQuantity(1);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.NEW);
        order.addItem(item);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.updateStatus(1L, "CONFIRMED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Недостаточно остатка");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        verify(orderRepository, never()).save(order);
    }

    @Test
    @DisplayName("отклоняет неизвестный статус до обращения к базе")
    void rejectsUnknownOrderStatus() {
        assertThatThrownBy(() -> orderService.updateStatus(1L, "HACKED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Недопустимый статус заказа");

        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("не позволяет изменить завершённый заказ")
    void rejectsTransitionFromCompletedStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(1L, "CANCELLED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED -> CANCELLED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository, never()).save(any());
        verify(customerService, never()).registerOrderAndRecalculateDiscount(any(), any());
    }

    @Test
    @DisplayName("повторный статус COMPLETED не начисляет сумму ещё раз")
    void completesOrderOnlyOnce() {
        Customer customer = new Customer();
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.READY);
        order.setDeliveryType("Самовывоз");
        order.setInventoryReserved(true);
        order.setCustomer(customer);
        order.setSubtotalAmount(new BigDecimal("450.50"));
        order.setTotalAmount(new BigDecimal("450.50"));
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(customerService.registerOrderAndAccrueBonuses(customer, new BigDecimal("450.50"), new BigDecimal("450.50")))
                .thenReturn(customer);

        orderService.updateStatus(1L, "COMPLETED");
        orderService.updateStatus(1L, "COMPLETED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository, times(1)).save(order);
        verify(customerService, times(1))
                .registerOrderAndAccrueBonuses(customer, new BigDecimal("450.50"), new BigDecimal("450.50"));
    }

    @Test
    @DisplayName("проводит заказ по полному жизненному циклу самовывоза")
    void followsCompletePickupLifecycle() {
        Order order = new Order();
        order.setId(15L);
        order.setStatus(OrderStatus.NEW);
        order.setDeliveryType("Самовывоз");
        order.setInventoryReserved(true);
        when(orderRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(order));

        orderService.updateStatus(15L, "CONFIRMED", "admin");
        orderService.updateStatus(15L, "PAYMENT_PENDING", "admin");
        orderService.updateStatus(15L, "PAID", "admin");
        orderService.updateStatus(15L, "ASSEMBLING", "admin");
        orderService.updateStatus(15L, "READY", "admin");
        orderService.updateStatus(15L, "COMPLETED", "admin");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(order.getInventoryReserved()).isFalse();
        verify(orderRepository, times(6)).save(order);
        verify(notificationOutboxService, times(6)).enqueueCustomerStatusNotification(
                eq(order), any(), any(), isNull());
    }

    @Test
    @DisplayName("отмена требует причину и возвращает зарезервированный остаток")
    void cancellationRequiresReasonAndRestoresInventory() {
        Product product = new Product();
        product.setId(1L);
        product.setStockQuantity(3);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        Order order = new Order();
        order.setId(20L);
        order.setStatus(OrderStatus.PAID);
        order.setInventoryReserved(true);
        order.addItem(item);
        when(orderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.updateStatus(20L, "CANCELLED", " ", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("причину отмены");

        orderService.updateStatus(20L, "CANCELLED", "Покупатель отказался", "admin");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Покупатель отказался");
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(notificationOutboxService).enqueueCustomerStatusNotification(
                order, OrderStatus.PAID, OrderStatus.CANCELLED, "Покупатель отказался");
    }

    @Test
    @DisplayName("не позволяет отправить заказ с самовывозом в доставку")
    void rejectsShippedForPickup() {
        Order order = new Order();
        order.setId(21L);
        order.setStatus(OrderStatus.ASSEMBLING);
        order.setDeliveryType("Самовывоз");
        when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(21L, "SHIPPED", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("только для доставки");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("выбрасывает исключение при обновлении статуса несуществующего заказа")
    void throwsWhenUpdatingStatusOfMissingOrder() {
        when(orderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(404L, "COMPLETED"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Заказ не найден");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("findAllOrders возвращает заказы, отсортированные по дате создания")
    void findAllOrdersDelegatesToRepository() {
        List<Order> orders = List.of(new Order(), new Order());
        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(orders);

        List<Order> result = orderService.findAllOrders();

        assertThat(result).isEqualTo(orders);
    }

    @Test
    @DisplayName("getCartForCheckout возвращает пусто, если корзина пустая")
    void getCartForCheckoutReturnsEmptyForEmptyCart() {
        Cart emptyCart = new Cart();
        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(emptyCart));

        Optional<Cart> result = orderService.getCartForCheckout(SESSION_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCartForCheckout возвращает корзину, если в ней есть товары")
    void getCartForCheckoutReturnsCartWithItems() {
        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(cartWithItems));

        Optional<Cart> result = orderService.getCartForCheckout(SESSION_ID);

        assertThat(result).isPresent().contains(cartWithItems);
    }
}
