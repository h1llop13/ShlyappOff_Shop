package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private CartService cartService;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @Mock
    private CustomerService customerService;

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

        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("Товар 2");
        product2.setPrice(new BigDecimal("250.50"));

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
    }

    @Test
    @DisplayName("создаёт заказ из корзины, верно считает сумму, очищает корзину и шлёт уведомление")
    void createsOrderFromCartSuccessfully() {
        Customer customer = new Customer();
        customer.setId(7L);
        customer.setTelegramUserId(12345L);
        customer.setDiscountPercent(0);

        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(cartWithItems));
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

        verify(cartService).clearCart(SESSION_ID);
        verify(telegramNotificationService).notifyAdminAboutNewOrder(result);
        // Заказ ещё не подтверждён администратором — сумма НЕ должна начисляться клиенту сразу.
        verify(customerService, never()).registerOrderAndRecalculateDiscount(any(), any());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("+79990001122");
    }

    @Test
    @DisplayName("применяет накопленную скидку клиента к сумме заказа")
    void appliesCustomerDiscountToOrderTotal() {
        Customer customer = new Customer();
        customer.setId(8L);
        customer.setTelegramUserId(999L);
        customer.setDiscountPercent(10);

        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(cartWithItems));
        when(customerService.findOrCreateByTelegram(999L, "vip_client", null, null)).thenReturn(customer);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrderFromCart(
                SESSION_ID, "Пётр", "+7000", "PICKUP", null, 999L, "vip_client");

        // 450.50 - 10% = 405.45
        assertThat(result.getSubtotalAmount()).isEqualByComparingTo("450.50");
        assertThat(result.getDiscountPercent()).isEqualTo(10);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("405.45");

        // Заказ ещё не подтверждён администратором — сумма НЕ должна начисляться клиенту сразу.
        verify(customerService, never()).registerOrderAndRecalculateDiscount(any(), any());
    }

    @Test
    @DisplayName("не создаёт клиента и не применяет скидку, если заказ оформлен без Telegram")
    void doesNotCreateCustomerWithoutTelegramUserId() {
        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(cartWithItems));
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
        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                SESSION_ID, "Иван", "+7000", "PICKUP", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Корзина пуста");

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
        verify(telegramNotificationService, never()).notifyAdminAboutNewOrder(any());
    }

    @Test
    @DisplayName("выбрасывает исключение, если корзина есть, но пустая (без товаров)")
    void throwsWhenCartHasNoItems() {
        Cart emptyCart = new Cart();
        emptyCart.setSessionId(SESSION_ID);

        when(cartService.getCartBySessionId(SESSION_ID)).thenReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> orderService.createOrderFromCart(
                SESSION_ID, "Иван", "+7000", "PICKUP", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Корзина пуста");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("обновляет статус существующего заказа")
    void updatesOrderStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.NEW);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.updateStatus(1L, "COMPLETED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("отклоняет неизвестный статус до обращения к базе")
    void rejectsUnknownOrderStatus() {
        assertThatThrownBy(() -> orderService.updateStatus(1L, "HACKED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Недопустимый статус заказа");

        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("не позволяет изменить завершённый заказ")
    void rejectsTransitionFromCompletedStatus() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

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
        order.setStatus(OrderStatus.NEW);
        order.setCustomer(customer);
        order.setSubtotalAmount(new BigDecimal("450.50"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.updateStatus(1L, "COMPLETED");
        orderService.updateStatus(1L, "COMPLETED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepository, times(1)).save(order);
        verify(customerService, times(1))
                .registerOrderAndRecalculateDiscount(customer, new BigDecimal("450.50"));
    }

    @Test
    @DisplayName("выбрасывает исключение при обновлении статуса несуществующего заказа")
    void throwsWhenUpdatingStatusOfMissingOrder() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

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
