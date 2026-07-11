package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    boolean existsByTelegramChatId(Long chatId);
    List<Admin> findAll();
}