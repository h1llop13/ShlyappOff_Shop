package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import com.shlyapoff.shop.model.PublicationStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByOrderByDisplayPriorityDescCreatedAtDesc();

    @Modifying
    @Query("UPDATE Promotion p SET p.publicationStatus = :published, p.active = true " +
            "WHERE p.publicationStatus = :scheduled AND p.publishAt <= :now")
    int publishScheduled(@Param("now") LocalDateTime now,
                         @Param("scheduled") PublicationStatus scheduled,
                         @Param("published") PublicationStatus published);
}
