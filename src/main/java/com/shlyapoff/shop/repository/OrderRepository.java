package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByOrderByCreatedAtDesc();
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Order> findByStatus(OrderStatus status);
    long countByStatus(OrderStatus status);
    long countByPromoCodeEntityIdAndStatusNot(Long promoCodeId, OrderStatus status);
    long countByPromoCodeEntityIdAndCustomerIdAndStatusNot(Long promoCodeId, Long customerId, OrderStatus status);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = 'NEW' " +
            "AND o.inventoryReserved = true AND o.reservationExpiresAt <= :now")
    List<Order> findExpiredReservations(@Param("now") LocalDateTime now);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) AS revenue, COUNT(o) AS orders
            FROM Order o WHERE o.status = :status AND o.completedAt >= :since
            """)
    SalesTotals sumCompletedSince(@Param("status") OrderStatus status,
                                  @Param("since") LocalDateTime since);

    interface SalesTotals {
        BigDecimal getRevenue();
        long getOrders();
    }

    @Query("""
            SELECT year(o.completedAt), month(o.completedAt), day(o.completedAt),
                   SUM(o.totalAmount), COUNT(o)
            FROM Order o WHERE o.status = :status AND o.completedAt >= :since
            GROUP BY year(o.completedAt), month(o.completedAt), day(o.completedAt)
            """)
    List<Object[]> sumCompletedByDay(@Param("status") OrderStatus status,
                                     @Param("since") LocalDateTime since);

    @Query("""
            SELECT item.productName, SUM(item.quantity),
                   SUM(item.priceAtMoment * item.quantity)
            FROM OrderItem item JOIN item.order o
            WHERE o.status = :status AND o.completedAt >= :since
            GROUP BY item.productName ORDER BY SUM(item.quantity) DESC, item.productName ASC
            """)
    List<Object[]> findTopProductsSince(@Param("status") OrderStatus status,
                                        @Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    java.util.Optional<Order> findByIdWithItems(@Param("id") Long id);

    // open-in-view выключен (application.yml), поэтому для профиля Mini App
    // подтягиваем items сразу через JOIN FETCH, а не лениво.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomerIdWithItems(@Param("customerId") Long customerId);

    // Для истории заказов в профиле: только заказы, подтверждённые администратором.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId AND o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByCustomerIdAndStatusWithItems(@Param("customerId") Long customerId, @Param("status") OrderStatus status);
}
