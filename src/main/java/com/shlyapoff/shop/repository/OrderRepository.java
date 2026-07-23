package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByStatus(OrderStatus status);

    // open-in-view выключен (application.yml), поэтому для профиля Mini App
    // подтягиваем items сразу через JOIN FETCH, а не лениво.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomerIdWithItems(@Param("customerId") Long customerId);

    // Для истории заказов в профиле: только заказы, подтверждённые администратором.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId AND o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByCustomerIdAndStatusWithItems(@Param("customerId") Long customerId, @Param("status") OrderStatus status);
}
