package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.TelegramLoginToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TelegramLoginTokenRepository extends JpaRepository<TelegramLoginToken, Long> {
    Optional<TelegramLoginToken> findByTokenAndUsedFalse(String token);
}