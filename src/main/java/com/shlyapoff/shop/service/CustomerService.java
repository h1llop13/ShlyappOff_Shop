package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final LoyaltyTierService loyaltyTierService;

    public Optional<Customer> findByTelegramUserId(Long telegramUserId) {
        return customerRepository.findByTelegramUserId(telegramUserId);
    }

    public List<Customer> findAllOrderBySpentDesc() {
        return customerRepository.findAllByOrderByTotalSpentDesc();
    }

    @Transactional(readOnly = true)
    public Page<Customer> findAllOrderBySpentDesc(int page) {
        return customerRepository.findAllByOrderByTotalSpentDesc(
                PageRequest.of(Math.max(page, 0), 30, Sort.by(Sort.Direction.DESC, "totalSpent"))
        );
    }

    /**
     * Находит клиента по telegramUserId либо создаёт новый профиль.
     * Заодно обновляет username/имя, если они изменились в Telegram.
     */
    @Transactional
    public Customer findOrCreateByTelegram(Long telegramUserId, String telegramUsername,
                                            String firstName, String lastName) {
        Customer customer = customerRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setTelegramUserId(telegramUserId);
                    c.setTotalSpent(BigDecimal.ZERO);
                    c.setDiscountPercent(0); // historical field; new orders use bonuses
                    c.setBonusBalance(BigDecimal.ZERO);
                    return c;
                });

        boolean changed = false;
        if (telegramUsername != null && !telegramUsername.isBlank() && !telegramUsername.equals(customer.getTelegramUsername())) {
            customer.setTelegramUsername(telegramUsername);
            changed = true;
        }
        if (firstName != null && !firstName.isBlank() && !firstName.equals(customer.getFirstName())) {
            customer.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !lastName.isBlank() && !lastName.equals(customer.getLastName())) {
            customer.setLastName(lastName);
            changed = true;
        }

        if (customer.getId() == null || changed) {
            customer = customerRepository.save(customer);
        }
        return customer;
    }

    /**
     * Прибавляет сумму нового заказа (ДО скидки) к totalSpent клиента
     * и пересчитывает его скидку на будущие заказы согласно программе лояльности.
     */
    @Transactional
    public Customer registerOrderAndAccrueBonuses(Customer customer, BigDecimal orderSubtotal, BigDecimal orderTotal) {
        BigDecimal newTotal = customer.getTotalSpent().add(orderSubtotal);
        customer.setTotalSpent(newTotal);
        int bonusPercent = loyaltyTierService.resolveBonusPercent(newTotal);
        BigDecimal earned = orderTotal.multiply(BigDecimal.valueOf(bonusPercent))
                .movePointLeft(2).setScale(2, java.math.RoundingMode.HALF_UP);
        customer.setBonusBalance(customer.getBonusBalance().add(earned));
        return customerRepository.save(customer);
    }

    /** Устаревшее имя для совместимости; новые заказы начисляют бонусы. */
    @Transactional
    public Customer registerOrderAndRecalculateDiscount(Customer customer, BigDecimal orderSubtotal) {
        return registerOrderAndAccrueBonuses(customer, orderSubtotal, orderSubtotal);
    }

    @Transactional
    public void spendBonuses(Customer customer, BigDecimal amount) {
        if (amount.signum() <= 0) return;
        BigDecimal balance = customer.getBonusBalance() == null ? BigDecimal.ZERO : customer.getBonusBalance();
        if (balance.compareTo(amount) < 0) throw new IllegalStateException("Недостаточно бонусов");
        customer.setBonusBalance(balance.subtract(amount));
        customerRepository.save(customer);
    }

    @Transactional
    public void restoreBonuses(Customer customer, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        customer.setBonusBalance(customer.getBonusBalance().add(amount));
        customerRepository.save(customer);
    }
}
