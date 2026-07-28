package com.shlyapoff.shop.repository;

import com.shlyapoff.shop.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByTelegramUserId(Long telegramUserId);

    List<Customer> findAllByOrderByTotalSpentDesc();
    Page<Customer> findAllByOrderByTotalSpentDesc(Pageable pageable);
}
