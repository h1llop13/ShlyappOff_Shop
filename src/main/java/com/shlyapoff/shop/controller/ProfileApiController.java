package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.dto.ProfileDto;
import com.shlyapoff.shop.model.Customer;
import com.shlyapoff.shop.model.LoyaltyTier;
import com.shlyapoff.shop.model.Order;
import com.shlyapoff.shop.service.CustomerService;
import com.shlyapoff.shop.service.LoyaltyTierService;
import com.shlyapoff.shop.service.OrderService;
import com.shlyapoff.shop.service.TelegramWebAppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class ProfileApiController {

    private final TelegramWebAppAuthService telegramWebAppAuthService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final LoyaltyTierService loyaltyTierService;

    public record InitDataRequest(String initData) {}

    /**
     * Отдаёт профиль и историю заказов ТОЛЬКО того пользователя, чья initData прислана.
     * initData подписана Telegram и проверяется на сервере (TelegramWebAppAuthService),
     * поэтому подделать чужой telegramUserId через запрос нельзя.
     */
    @PostMapping("/api/profile/me")
    public ResponseEntity<ProfileDto.ProfileResponse> getProfile(@RequestBody InitDataRequest request) {
        Optional<TelegramWebAppAuthService.TelegramWebAppUser> tgUserOpt =
                telegramWebAppAuthService.validate(request.initData());

        if (tgUserOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        var tgUser = tgUserOpt.get();
        Customer customer = customerService.findOrCreateByTelegram(
                tgUser.id(), tgUser.username(), tgUser.firstName(), tgUser.lastName());

        List<Order> orders = orderService.findByCustomerId(customer.getId());

        List<ProfileDto.OrderView> orderViews = orders.stream()
                .map(o -> new ProfileDto.OrderView(
                        o.getId(),
                        o.getCreatedAt(),
                        o.getStatus(),
                        o.getDeliveryType(),
                        o.getSubtotalAmount() != null ? o.getSubtotalAmount() : o.getTotalAmount(),
                        o.getDiscountPercent(),
                        o.getTotalAmount(),
                        o.getItems().stream()
                                .map(i -> new ProfileDto.OrderItemView(i.getProductName(), i.getQuantity(), i.getPriceAtMoment()))
                                .toList()
                ))
                .toList();

        BigDecimal totalSpent = customer.getTotalSpent() != null ? customer.getTotalSpent() : BigDecimal.ZERO;
        Optional<LoyaltyTier> nextTier = loyaltyTierService.findNextTier(totalSpent);

        ProfileDto.LoyaltyProgress loyalty = new ProfileDto.LoyaltyProgress(
                totalSpent,
                customer.getDiscountPercent(),
                nextTier.map(LoyaltyTier::getDiscountPercent).orElse(null),
                nextTier.map(t -> t.getMinAmount().subtract(totalSpent)).orElse(null)
        );

        ProfileDto.ProfileResponse response = new ProfileDto.ProfileResponse(
                customer.getTelegramUserId(),
                customer.getTelegramUsername(),
                customer.getFirstName(),
                customer.getLastName(),
                loyalty,
                orderViews
        );

        return ResponseEntity.ok(response);
    }
}
