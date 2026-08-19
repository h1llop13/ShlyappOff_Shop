package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.SalesDashboardService;
import com.shlyapoff.shop.service.ShoppingEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminDashboardController {
    private final SalesDashboardService salesDashboardService;
    private final ShoppingEventService shoppingEventService;

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("dashboard", salesDashboardService.getDashboard());
        model.addAttribute("funnel", shoppingEventService.getFunnel(30));
        return "admin/dashboard";
    }
}
