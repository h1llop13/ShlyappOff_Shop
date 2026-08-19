package com.shlyapoff.shop.integration;

import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.model.PromoCode;
import com.shlyapoff.shop.model.DiscountType;
import com.shlyapoff.shop.repository.CustomerRepository;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.repository.PromoCodeRepository;
import com.shlyapoff.shop.service.CartService;
import com.shlyapoff.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "telegram.enabled=false",
        "telegram.bot-token=test-token",
        "telegram.admin-chat-id=1",
        "app.notifications.fixed-delay-ms=3600000",
        "app.orders.reservation-cleanup-ms=3600000",
        "app.pricing.delivery-fee=300.00"
})
class OrderCheckoutIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shop_test")
            .withUsername("shop_test")
            .withPassword("shop_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PromoCodeRepository promoCodeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseAppliesTheCompleteSchemaOnPostgresql() {
        Integer changeSetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE filename = ?",
                Integer.class,
                "db/changelog/changes/023-add-pricing-and-admin-audit.sql"
        );
        String deliveryColumnType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = 'orders' AND column_name = 'delivery_amount'",
                String.class
        );
        Integer auditTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = 'admin_audit_log'",
                Integer.class
        );

        assertThat(changeSetCount).isEqualTo(1);
        assertThat(deliveryColumnType).isEqualTo("numeric");
        assertThat(auditTableCount).isEqualTo(1);
    }

    @Test
    void cartToCompletedOrderReservesStockAndUpdatesBonuses() {
        Product product = new Product();
        product.setName("Интеграционный товар");
        product.setPrice(new BigDecimal("100.00"));
        product.setStockQuantity(10);
        product.setActive(true);
        product = productRepository.saveAndFlush(product);

        Customer customer = new Customer();
        customer.setTelegramUserId(88005553535L);
        customer.setTelegramUsername("integration_customer");
        customer.setTotalSpent(BigDecimal.ZERO);
        customer.setDiscountPercent(0);
        customer.setBonusBalance(new BigDecimal("20.00"));
        customer = customerRepository.saveAndFlush(customer);

        String sessionId = "integration-session";
        cartService.addToCart(sessionId, customer.getTelegramUserId(), product.getId(), null, 2);

        Order order = orderService.createOrderFromCart(
                sessionId, "Покупатель", "+79990000000", "Самовывоз", null,
                customer.getTelegramUserId(), customer.getTelegramUsername(), true, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getInventoryReserved()).isTrue();
        assertThat(order.getReservationExpiresAt()).isNotNull();
        assertThat(order.getSubtotalAmount()).isEqualByComparingTo("200.00");
        assertThat(order.getBonusesSpent()).isEqualByComparingTo("20.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("180.00");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getBonusBalance())
                .isEqualByComparingTo("0.00");

        orderService.updateStatus(order.getId(), "PROCESSING");
        orderService.updateStatus(order.getId(), "COMPLETED");

        Order completed = orderRepository.findById(order.getId()).orElseThrow();
        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getInventoryReserved()).isFalse();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(updatedCustomer.getTotalSpent()).isEqualByComparingTo("200.00");
        assertThat(updatedCustomer.getBonusBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void promoCodeAppliesDiscountAndEnforcesGlobalUsageLimit() {
        Product product = product("Товар для промокода", "100.00", 10);
        PromoCode promoCode = new PromoCode();
        promoCode.setCode("TEST50");
        promoCode.setDiscountType(DiscountType.FIXED_AMOUNT);
        promoCode.setDiscountValue(new BigDecimal("50.00"));
        promoCode.setMinOrderAmount(new BigDecimal("100.00"));
        promoCode.setUsageLimit(1);
        promoCode.setActive(true);
        promoCode = promoCodeRepository.saveAndFlush(promoCode);
        String code = promoCode.getCode();

        cartService.addToCart("promo-session-1", product.getId(), 2);
        Order first = orderService.createOrderFromCart(
                "promo-session-1", "Покупатель", "+79990000001", "Самовывоз", null,
                null, null, false, code);

        assertThat(first.getPromoCode()).isEqualTo("TEST50");
        assertThat(first.getPromoDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(first.getTotalAmount()).isEqualByComparingTo("150.00");

        cartService.addToCart("promo-session-2", product.getId(), 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.createOrderFromCart(
                        "promo-session-2", "Другой покупатель", "+79990000002", "Самовывоз", null,
                        null, null, false, "TEST50"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Лимит использований");
    }

    @Test
    void expiredReservationReleasesStockWithoutCancellingOrder() {
        Product product = product("Товар для резерва", "75.00", 5);
        cartService.addToCart("expired-reservation-session", product.getId(), 2);
        Order order = orderService.createOrderFromCart(
                "expired-reservation-session", "Покупатель", "+79990000003", "Самовывоз", null,
                null, null, false, null);
        order.setReservationExpiresAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.saveAndFlush(order);

        orderService.releaseExpiredReservations();

        Order expired = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(expired.getInventoryReserved()).isFalse();
        assertThat(expired.getReservationExpiresAt()).isNull();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);

        orderService.updateStatus(order.getId(), "PROCESSING");

        Order processing = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(processing.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(processing.getInventoryReserved()).isTrue();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test
    void deliveryPromoAndBonusesUseOneBigDecimalCheckoutCalculation() {
        Product product = product("Единый расчёт", "99.99", 10);
        Customer customer = new Customer();
        customer.setTelegramUserId(88005550001L);
        customer.setTelegramUsername("pricing_customer");
        customer.setTotalSpent(BigDecimal.ZERO);
        customer.setDiscountPercent(0);
        customer.setBonusBalance(new BigDecimal("25.00"));
        customer = customerRepository.saveAndFlush(customer);

        PromoCode promoCode = new PromoCode();
        promoCode.setCode("TENPERCENT");
        promoCode.setDiscountType(DiscountType.PERCENTAGE);
        promoCode.setDiscountValue(new BigDecimal("10.00"));
        promoCode.setMinOrderAmount(BigDecimal.ZERO);
        promoCode.setActive(true);
        promoCode = promoCodeRepository.saveAndFlush(promoCode);

        cartService.addToCart("pricing-session", customer.getTelegramUserId(), product.getId(), null, 3);
        Order order = orderService.createOrderFromCart(
                "pricing-session", "Покупатель", "+79990000004", "Доставка", null,
                customer.getTelegramUserId(), customer.getTelegramUsername(), true, promoCode.getCode());

        assertThat(order.getSubtotalAmount()).isEqualByComparingTo("299.97");
        assertThat(order.getPromoDiscountAmount()).isEqualByComparingTo("30.00");
        assertThat(order.getBonusesSpent()).isEqualByComparingTo("25.00");
        assertThat(order.getDeliveryAmount()).isEqualByComparingTo("300.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("544.97");
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getTotalAmount())
                .isEqualByComparingTo("544.97");
    }

    private Product product(String name, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setActive(true);
        return productRepository.saveAndFlush(product);
    }
}
