package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SalesDashboardService {
    private final OrderRepository orderRepository;

    public record DailySales(LocalDate date, BigDecimal revenue, long orders, int widthPercent) {}
    public record TopProduct(String name, long quantity, BigDecimal revenue) {}
    public record DashboardData(BigDecimal todayRevenue, BigDecimal periodRevenue, long periodOrders,
                                BigDecimal averageOrder, long newOrders, long processingOrders,
                                List<DailySales> dailySales, List<TopProduct> topProducts) {}

    @Transactional(readOnly = true)
    public DashboardData getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate sinceDate = today.minusDays(29);
        List<Order> completed = orderRepository.findCompletedSinceWithItems(sinceDate.atStartOfDay());

        BigDecimal periodRevenue = completed.stream().map(Order::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todayRevenue = completed.stream()
                .filter(order -> order.getCompletedAt() != null && order.getCompletedAt().toLocalDate().equals(today))
                .map(Order::getTotalAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = completed.isEmpty() ? BigDecimal.ZERO
                : periodRevenue.divide(BigDecimal.valueOf(completed.size()), 2, RoundingMode.HALF_UP);

        Map<LocalDate, List<Order>> byDay = new HashMap<>();
        completed.stream().filter(order -> order.getCompletedAt() != null)
                .forEach(order -> byDay.computeIfAbsent(order.getCompletedAt().toLocalDate(), key -> new ArrayList<>()).add(order));
        BigDecimal maxRevenue = BigDecimal.ONE;
        for (List<Order> orders : byDay.values()) {
            BigDecimal revenue = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (revenue.compareTo(maxRevenue) > 0) maxRevenue = revenue;
        }
        List<DailySales> daily = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<Order> orders = byDay.getOrDefault(date, List.of());
            BigDecimal revenue = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            int width = revenue.signum() == 0 ? 2 : revenue.multiply(BigDecimal.valueOf(100))
                    .divide(maxRevenue, 0, RoundingMode.HALF_UP).intValue();
            daily.add(new DailySales(date, revenue, orders.size(), Math.max(2, width)));
        }

        Map<String, ProductAccumulator> products = new HashMap<>();
        completed.forEach(order -> order.getItems().forEach(item -> {
            ProductAccumulator accumulator = products.computeIfAbsent(item.getProductName(), key -> new ProductAccumulator());
            accumulator.quantity += item.getQuantity();
            accumulator.revenue = accumulator.revenue.add(item.getPriceAtMoment().multiply(BigDecimal.valueOf(item.getQuantity())));
        }));
        List<TopProduct> topProducts = products.entrySet().stream()
                .map(entry -> new TopProduct(entry.getKey(), entry.getValue().quantity, entry.getValue().revenue))
                .sorted(Comparator.comparingLong(TopProduct::quantity).reversed()).limit(5).toList();

        return new DashboardData(todayRevenue, periodRevenue, completed.size(), average,
                orderRepository.countByStatus(OrderStatus.NEW), orderRepository.countByStatus(OrderStatus.PROCESSING),
                daily, topProducts);
    }

    private static class ProductAccumulator {
        long quantity;
        BigDecimal revenue = BigDecimal.ZERO;
    }
}
