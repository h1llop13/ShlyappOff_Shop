package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.DiscountType;
import com.shlyapoff.shop.model.OrderStatus;
import com.shlyapoff.shop.model.PromoCode;
import com.shlyapoff.shop.repository.OrderRepository;
import com.shlyapoff.shop.repository.PromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;
    private final OrderRepository orderRepository;

    public record AppliedPromoCode(PromoCode promoCode, BigDecimal discountAmount) {
        public static AppliedPromoCode none() {
            return new AppliedPromoCode(null, BigDecimal.ZERO);
        }
    }

    public List<PromoCode> findAll() {
        return promoCodeRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<PromoCode> findById(Long id) {
        return promoCodeRepository.findById(id);
    }

    @Transactional
    public AppliedPromoCode apply(String rawCode, BigDecimal subtotal, Customer customer) {
        if (!StringUtils.hasText(rawCode)) return AppliedPromoCode.none();

        String code = normalizeCode(rawCode);
        PromoCode promoCode = promoCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("Промокод не найден"));
        LocalDateTime now = LocalDateTime.now();

        if (!Boolean.TRUE.equals(promoCode.getActive())) throw new IllegalStateException("Промокод отключён");
        if (promoCode.getStartsAt() != null && now.isBefore(promoCode.getStartsAt())) {
            throw new IllegalStateException("Промокод ещё не действует");
        }
        if (promoCode.getEndsAt() != null && !now.isBefore(promoCode.getEndsAt())) {
            throw new IllegalStateException("Срок действия промокода закончился");
        }
        if (subtotal.compareTo(promoCode.getMinOrderAmount()) < 0) {
            throw new IllegalStateException("Промокод действует от суммы " + promoCode.getMinOrderAmount() + " ₽");
        }

        long uses = orderRepository.countByPromoCodeEntityIdAndStatusNot(promoCode.getId(), OrderStatus.CANCELLED);
        if (promoCode.getUsageLimit() != null && uses >= promoCode.getUsageLimit()) {
            throw new IllegalStateException("Лимит использований промокода исчерпан");
        }
        if (promoCode.getPerCustomerLimit() != null) {
            if (customer == null) {
                throw new IllegalStateException("Этот промокод доступен только покупателям, вошедшим через Telegram");
            }
            long customerUses = orderRepository.countByPromoCodeEntityIdAndCustomerIdAndStatusNot(
                    promoCode.getId(), customer.getId(), OrderStatus.CANCELLED);
            if (customerUses >= promoCode.getPerCustomerLimit()) {
                throw new IllegalStateException("Вы уже использовали этот промокод максимальное число раз");
            }
        }

        BigDecimal discount = promoCode.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(promoCode.getDiscountValue()).movePointLeft(2)
                : promoCode.getDiscountValue();
        if (promoCode.getMaxDiscountAmount() != null) discount = discount.min(promoCode.getMaxDiscountAmount());
        discount = discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        return new AppliedPromoCode(promoCode, discount);
    }

    public PromoCode save(PromoCode promoCode) {
        validate(promoCode);
        promoCode.setCode(normalizeCode(promoCode.getCode()));
        promoCode.setDescription(StringUtils.hasText(promoCode.getDescription()) ? promoCode.getDescription().trim() : null);
        promoCode.setMinOrderAmount(promoCode.getMinOrderAmount() == null ? BigDecimal.ZERO : promoCode.getMinOrderAmount());
        promoCode.setActive(Boolean.TRUE.equals(promoCode.getActive()));
        return promoCodeRepository.save(promoCode);
    }

    public void deleteById(Long id) {
        promoCodeRepository.deleteById(id);
    }

    public void validate(PromoCode promoCode) {
        if (!StringUtils.hasText(promoCode.getCode())) throw new IllegalArgumentException("Укажите промокод");
        String normalized = normalizeCode(promoCode.getCode());
        if (!normalized.matches("[A-Z0-9_-]{3,40}")) {
            throw new IllegalArgumentException("Промокод: 3–40 символов, латинские буквы, цифры, _ или -");
        }
        if (promoCode.getDiscountType() == null) throw new IllegalArgumentException("Выберите тип скидки");
        if (promoCode.getDiscountValue() == null || promoCode.getDiscountValue().signum() <= 0) {
            throw new IllegalArgumentException("Значение скидки должно быть больше нуля");
        }
        if (promoCode.getDiscountType() == DiscountType.PERCENTAGE
                && promoCode.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Процент скидки не может быть больше 100");
        }
        if (promoCode.getMinOrderAmount() != null && promoCode.getMinOrderAmount().signum() < 0) {
            throw new IllegalArgumentException("Минимальная сумма не может быть отрицательной");
        }
        if (promoCode.getStartsAt() != null && promoCode.getEndsAt() != null
                && !promoCode.getEndsAt().isAfter(promoCode.getStartsAt())) {
            throw new IllegalArgumentException("Дата окончания должна быть позже даты начала");
        }
    }

    public boolean codeBelongsToAnotherPromo(String code, Long currentId) {
        return promoCodeRepository.findByCodeIgnoreCase(normalizeCode(code))
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .isPresent();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
