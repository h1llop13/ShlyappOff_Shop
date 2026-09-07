package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public String ordersPage(@RequestParam(defaultValue = "0") int page, Model model) {
        var ordersPage = orderService.findOrdersPage(page);
        var orders = ordersPage.getContent();
        model.addAttribute("orders", orders);
        model.addAttribute("historyByOrderId", orderService.findStatusHistory(orders));
        model.addAttribute("nextStatusesByOrderId", orders.stream().collect(
                java.util.stream.Collectors.toMap(
                        com.shlyapoff.shop.model.Order::getId,
                        orderService::allowedNextStatuses)));
        model.addAttribute("currentPage", ordersPage.getNumber());
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        return "admin/orders";
    }

    // Подтверждение/изменение статуса заказа.
    // Только когда админ проставляет статус COMPLETED, сумма заказа
    // засчитывается клиенту в программу лояльности (см. OrderService.updateStatus).
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam String status,
                                @RequestParam(required = false) String cancellationReason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            orderService.updateStatus(id, status, cancellationReason, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Статус заказа обновлён!");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/orders";
    }
}
