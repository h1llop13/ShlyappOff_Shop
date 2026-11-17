package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        LocalDateTime since = sinceDate.atStartOfDay();
        OrderRepository.SalesTotals totals = orderRepository.sumCompletedSince(OrderStatus.COMPLETED, since);
        BigDecimal periodRevenue = totals.getRevenue();
        long periodOrders = totals.getOrders();
        BigDecimal average = periodOrders == 0 ? BigDecimal.ZERO
                : periodRevenue.divide(BigDecimal.valueOf(periodOrders), 2, RoundingMode.HALF_UP);

        Map<LocalDate, DailyTotal> byDay = new HashMap<>();
        for (Object[] row : orderRepository.sumCompletedByDay(OrderStatus.COMPLETED, since)) {
            LocalDate date = LocalDate.of(((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(), ((Number) row[2]).intValue());
            byDay.put(date, new DailyTotal((BigDecimal) row[3], ((Number) row[4]).longValue()));
        }
        BigDecimal todayRevenue = byDay.getOrDefault(today, DailyTotal.EMPTY).revenue();
        BigDecimal maxRevenue = BigDecimal.ONE;
        for (DailyTotal total : byDay.values()) {
            if (total.revenue().compareTo(maxRevenue) > 0) maxRevenue = total.revenue();
        }
        List<DailySales> daily = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DailyTotal total = byDay.getOrDefault(date, DailyTotal.EMPTY);
            BigDecimal revenue = total.revenue();
            int width = revenue.signum() == 0 ? 2 : revenue.multiply(BigDecimal.valueOf(100))
                    .divide(maxRevenue, 0, RoundingMode.HALF_UP).intValue();
            daily.add(new DailySales(date, revenue, total.orders(), Math.max(2, width)));
        }

        List<TopProduct> topProducts = orderRepository.findTopProductsSince(OrderStatus.COMPLETED, since,
                        PageRequest.of(0, 5)).stream()
                .map(row -> new TopProduct((String) row[0], ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();

        return new DashboardData(todayRevenue, periodRevenue, periodOrders, average,
                orderRepository.countByStatus(OrderStatus.NEW), orderRepository.countByStatus(OrderStatus.PROCESSING),
                daily, topProducts);
    }

    private record DailyTotal(BigDecimal revenue, long orders) {
        private static final DailyTotal EMPTY = new DailyTotal(BigDecimal.ZERO, 0);
    }
}
