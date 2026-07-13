package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByTelegramUserId(Long telegramUserId);

    List<Customer> findAllByOrderByTotalSpentDesc();
}
