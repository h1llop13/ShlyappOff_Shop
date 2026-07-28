package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Optional<Favorite> findByCustomerIdAndProductId(Long customerId, Long productId);
    List<Favorite> findAllByCustomerId(Long customerId);
}
