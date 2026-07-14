package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public String ordersPage(Model model) {
        model.addAttribute("orders", orderService.findAllOrders());
        return "admin/orders";
    }

    // Подтверждение/изменение статуса заказа.
    // Только когда админ проставляет статус COMPLETED, сумма заказа
    // засчитывается клиенту в программу лояльности (см. OrderService.updateStatus).
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                                @RequestParam String status,
                                RedirectAttributes redirectAttributes) {
        orderService.updateStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Статус заказа обновлён!");
        return "redirect:/admin/orders";
    }
}
