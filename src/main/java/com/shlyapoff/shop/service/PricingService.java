package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.DiscountType;
import com.shlyapoff.shop.money.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class PricingService {

    private final Money configuredDeliveryFee;

    public PricingService(@Value("${app.pricing.delivery-fee:0.00}") BigDecimal deliveryFee) {
        this.configuredDeliveryFee = Money.of(deliveryFee);
    }

    public PricingBreakdown calculate(Cart cart, BigDecimal promoDiscountAmount,
                                      BigDecimal bonusBalance, boolean useBonuses,
                                      String deliveryType) {
        Money subtotal = Money.of(cartSubtotal(cart));
        Money promoDiscount = Money.of(promoDiscountAmount).min(subtotal);
        Money itemsAfterDiscount = subtotal.subtract(promoDiscount);
        Money bonusesSpent = useBonuses
                ? Money.of(bonusBalance).min(itemsAfterDiscount)
                : Money.zero();
        Money deliveryAmount = Money.of(deliveryFeeFor(deliveryType));
        Money total = itemsAfterDiscount.subtract(bonusesSpent).add(deliveryAmount);

        return new PricingBreakdown(
                subtotal.amount(),
                promoDiscount.amount(),
                bonusesSpent.amount(),
                deliveryAmount.amount(),
                total.amount()
        );
    }

    public BigDecimal cartSubtotal(Cart cart) {
        if (cart == null || cart.getItems() == null) {
            return Money.zero().amount();
        }
        Money total = Money.zero();
        for (CartItem item : cart.getItems()) {
            total = total.add(Money.of(lineTotal(item)));
        }
        return total.amount();
    }

    public BigDecimal lineTotal(CartItem item) {
        if (item == null || item.getProduct() == null) {
            return Money.zero().amount();
        }
        return Money.of(item.getProduct().getPrice()).multiply(item.getQuantity()).amount();
    }

    public BigDecimal promoDiscount(BigDecimal subtotal, DiscountType discountType,
                                    BigDecimal discountValue, BigDecimal maximumDiscount) {
        Money subtotalMoney = Money.of(subtotal);
        Money discount = discountType == DiscountType.PERCENTAGE
                ? subtotalMoney.percentage(discountValue)
                : Money.of(discountValue);
        if (maximumDiscount != null) {
            discount = discount.min(Money.of(maximumDiscount));
        }
        return discount.min(subtotalMoney).amount();
    }

    public BigDecimal percentageOf(BigDecimal amount, BigDecimal percent) {
        return Money.of(amount).percentage(percent).amount();
    }

    public BigDecimal bonusAccrualBase(BigDecimal totalAmount, BigDecimal deliveryAmount) {
        Money total = Money.of(totalAmount);
        Money delivery = Money.of(deliveryAmount).min(total);
        return total.subtract(delivery).amount();
    }

    public BigDecimal normalize(BigDecimal amount) {
        return Money.of(amount).amount();
    }

    public BigDecimal deliveryFeeFor(String deliveryType) {
        if (deliveryType == null || deliveryType.isBlank()) {
            return Money.zero().amount();
        }
        return switch (deliveryType.trim().toLowerCase(Locale.ROOT)) {
            case "доставка", "delivery" -> configuredDeliveryFee.amount();
            case "самовывоз", "pickup" -> Money.zero().amount();
            default -> throw new IllegalArgumentException("Неизвестный способ получения заказа");
        };
    }

    public record PricingBreakdown(
            BigDecimal subtotalAmount,
            BigDecimal promoDiscountAmount,
            BigDecimal bonusesSpent,
            BigDecimal deliveryAmount,
            BigDecimal totalAmount
    ) {
    }
}
