package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrderIdInOrderByChangedAtAscIdAsc(Collection<Long> orderIds);
}
