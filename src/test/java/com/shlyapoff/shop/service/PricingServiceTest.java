package com.shlyapoff.shop.service;

import com.shlyapoff.shop.model.Cart;
import com.shlyapoff.shop.model.CartItem;
import com.shlyapoff.shop.model.DiscountType;
import com.shlyapoff.shop.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService(new BigDecimal("300.00"));

    @Test
    void calculatesCartDiscountBonusesDeliveryAndTotalWithBigDecimal() {
        Cart cart = cartWithItem("99.99", 3);

        BigDecimal discount = pricingService.promoDiscount(
                pricingService.cartSubtotal(cart),
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                null
        );
        PricingService.PricingBreakdown pricing = pricingService.calculate(
                cart, discount, new BigDecimal("25.00"), true, "Доставка");

        assertThat(pricing.subtotalAmount()).isEqualByComparingTo("299.97");
        assertThat(pricing.promoDiscountAmount()).isEqualByComparingTo("30.00");
        assertThat(pricing.bonusesSpent()).isEqualByComparingTo("25.00");
        assertThat(pricing.deliveryAmount()).isEqualByComparingTo("300.00");
        assertThat(pricing.totalAmount()).isEqualByComparingTo("544.97");
    }

    @Test
    void capsDiscountAndBonusesAtTheItemsAmount() {
        Cart cart = cartWithItem("10.00", 1);

        PricingService.PricingBreakdown pricing = pricingService.calculate(
                cart, new BigDecimal("100.00"), new BigDecimal("100.00"), true, "Самовывоз");

        assertThat(pricing.promoDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(pricing.bonusesSpent()).isZero();
        assertThat(pricing.totalAmount()).isZero();
    }

    @Test
    void rejectsUnknownDeliveryType() {
        assertThatThrownBy(() -> pricingService.calculate(
                cartWithItem("10.00", 1), BigDecimal.ZERO, BigDecimal.ZERO, false, "drone"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void excludesDeliveryFromTheBonusAccrualBase() {
        assertThat(pricingService.bonusAccrualBase(
                new BigDecimal("544.97"), new BigDecimal("300.00")))
                .isEqualByComparingTo("244.97");
    }

    private Cart cartWithItem(String price, int quantity) {
        Product product = new Product();
        product.setPrice(new BigDecimal(price));
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        Cart cart = new Cart();
        cart.getItems().add(item);
        return cart;
    }
}
