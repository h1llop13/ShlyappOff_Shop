package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    @EntityGraph(attributePaths = {"product", "productVariant"})
    Page<InventoryMovement> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
