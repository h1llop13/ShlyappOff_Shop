package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    c.setDiscountPercent(0);
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
    public Customer registerOrderAndRecalculateDiscount(Customer customer, BigDecimal orderSubtotal) {
        BigDecimal newTotal = customer.getTotalSpent().add(orderSubtotal);
        customer.setTotalSpent(newTotal);
        customer.setDiscountPercent(loyaltyTierService.resolveDiscountPercent(newTotal));
        return customerRepository.save(customer);
    }
}
