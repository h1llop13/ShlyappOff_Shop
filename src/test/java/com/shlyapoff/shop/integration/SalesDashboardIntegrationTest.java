package com.shlyapoff.shop.integration;

import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderItem;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.Product;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.ProductRepository;
import com.shlyapoff.shop.service.SalesDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard-aggregates;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "telegram.enabled=false",
        "telegram.bot-token=test-token",
        "telegram.admin-chat-id=1",
        "app.notifications.fixed-delay-ms=3600000",
        "app.orders.reservation-cleanup-ms=3600000"
})
class SalesDashboardIntegrationTest {
    @Autowired private SalesDashboardService dashboardService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    @Transactional
    void aggregatesCompletedOrdersWithoutLoadingEveryOrder() {
        Product product = new Product();
        product.setName("Тестовый продукт дашборда");
        product.setPrice(new BigDecimal("10.00"));
        product = productRepository.saveAndFlush(product);

        LocalDate today = LocalDate.now();
        createOrder(product, OrderStatus.COMPLETED, today, "30.00", 3);
        createOrder(product, OrderStatus.COMPLETED, today.minusDays(1), "20.00", 2);
        createOrder(product, OrderStatus.CANCELLED, today, "100.00", 10);
        createOrder(product, OrderStatus.COMPLETED, today.minusDays(31), "100.00", 10);

        var dashboard = dashboardService.getDashboard();

        assertThat(dashboard.todayRevenue()).isEqualByComparingTo("30.00");
        assertThat(dashboard.periodRevenue()).isEqualByComparingTo("50.00");
        assertThat(dashboard.periodOrders()).isEqualTo(2);
        assertThat(dashboard.averageOrder()).isEqualByComparingTo("25.00");
        assertThat(dashboard.dailySales()).hasSize(14);
        assertThat(dashboard.dailySales().getLast().orders()).isEqualTo(1);
        assertThat(dashboard.topProducts()).hasSize(1);
        assertThat(dashboard.topProducts().getFirst().quantity()).isEqualTo(5);
        assertThat(dashboard.topProducts().getFirst().revenue()).isEqualByComparingTo("50.00");
    }

    private void createOrder(Product product, OrderStatus status, LocalDate date,
                             String total, int quantity) {
        Order order = new Order();
        order.setCustomerName("Покупатель");
        order.setDeliveryType("Самовывоз");
        order.setStatus(status);
        order.setCompletedAt(date.atTime(12, 0));
        order.setTotalAmount(new BigDecimal(total));

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setPriceAtMoment(new BigDecimal("10.00"));
        item.setQuantity(quantity);
        order.addItem(item);
        orderRepository.saveAndFlush(order);
    }
}
