package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    // Обычный метод (оставляем для других случаев)
    Optional<Cart> findBySessionId(String sessionId);

    // НОВЫЙ метод: сразу загружает корзину с товарами и продуктами
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.sessionId = :sessionId")
    Optional<Cart> findBySessionIdWithItems(@Param("sessionId") String sessionId);
}