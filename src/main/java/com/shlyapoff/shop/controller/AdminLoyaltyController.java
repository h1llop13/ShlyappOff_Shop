package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.LoyaltyTier;
import com.shlyapoff.shop.service.CustomerService;
import com.shlyapoff.shop.service.LoyaltyTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLoyaltyController {

    private final LoyaltyTierService loyaltyTierService;
    private final CustomerService customerService;

    // === Пороги программы лояльности ===

    @GetMapping("/loyalty")
    public String loyaltyPage(Model model) {
        model.addAttribute("tiers", loyaltyTierService.findAll());
        return "admin/loyalty";
    }

    @GetMapping("/loyalty/create")
    public String createTierForm(Model model) {
        model.addAttribute("tier", new LoyaltyTier());
        return "admin/loyalty-form";
    }

    @PostMapping("/loyalty/create")
    public String createTier(@ModelAttribute("tier") LoyaltyTier tier, RedirectAttributes redirectAttributes) {
        loyaltyTierService.save(tier);
        redirectAttributes.addFlashAttribute("successMessage", "Порог лояльности добавлен!");
        return "redirect:/admin/loyalty";
    }

    @GetMapping("/loyalty/edit/{id}")
    public String editTierForm(@PathVariable Long id, Model model) {
        Optional<LoyaltyTier> tier = loyaltyTierService.findById(id);
        if (tier.isEmpty()) return "redirect:/admin/loyalty";
        model.addAttribute("tier", tier.get());
        return "admin/loyalty-form";
    }

    @PostMapping("/loyalty/edit/{id}")
    public String editTier(@PathVariable Long id, @ModelAttribute("tier") LoyaltyTier tier,
                            RedirectAttributes redirectAttributes) {
        Optional<LoyaltyTier> existing = loyaltyTierService.findById(id);
        if (existing.isEmpty()) return "redirect:/admin/loyalty";

        LoyaltyTier toUpdate = existing.get();
        toUpdate.setMinAmount(tier.getMinAmount());
        toUpdate.setDiscountPercent(tier.getDiscountPercent());
        loyaltyTierService.save(toUpdate);

        redirectAttributes.addFlashAttribute("successMessage", "Порог лояльности обновлён!");
        return "redirect:/admin/loyalty";
    }

    @PostMapping("/loyalty/delete/{id}")
    public String deleteTier(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        loyaltyTierService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Порог лояльности удалён!");
        return "redirect:/admin/loyalty";
    }

    // === Клиенты (профили из Mini App) ===

    @GetMapping("/customers")
    public String customersPage(Model model) {
        model.addAttribute("customers", customerService.findAllOrderBySpentDesc());
        return "admin/customers";
    }
}
