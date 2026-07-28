package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Promotion;
import com.shlyapoff.shop.model.PromotionKind;
import com.shlyapoff.shop.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {
    private final PromotionService promotionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("promotions", promotionService.findAll());
        return "admin/promotions";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        addFormData(model, new Promotion());
        return "admin/promotion-form";
    }

    @PostMapping("/create")
    public String create(@ModelAttribute Promotion promotion, @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes attributes) {
        promotion.setActive(active);
        String error = validate(promotion);
        if (error != null) {
            attributes.addFlashAttribute("errorMessage", error);
            return "redirect:/admin/promotions/create";
        }
        promotionService.save(normalize(promotion));
        attributes.addFlashAttribute("successMessage", "Акция создана");
        return "redirect:/admin/promotions";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        return promotionService.findById(id)
                .map(promotion -> {
                    addFormData(model, promotion);
                    return "admin/promotion-form";
                })
                .orElse("redirect:/admin/promotions");
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @ModelAttribute Promotion form, @RequestParam(defaultValue = "false") boolean active,
                       RedirectAttributes attributes) {
        form.setActive(active);
        var existing = promotionService.findById(id);
        if (existing.isEmpty()) return "redirect:/admin/promotions";
        String error = validate(form);
        if (error != null) {
            attributes.addFlashAttribute("errorMessage", error);
            return "redirect:/admin/promotions/edit/" + id;
        }
        Promotion promotion = existing.get();
        promotion.setTitle(form.getTitle().trim());
        promotion.setDescription(form.getDescription());
        promotion.setKind(form.getKind());
        promotion.setBonusMultiplier(form.getBonusMultiplier());
        promotion.setDisplayPriority(form.getDisplayPriority());
        promotion.setActive(Boolean.TRUE.equals(form.getActive()));
        promotion.setStartsAt(form.getStartsAt());
        promotion.setEndsAt(form.getEndsAt());
        promotionService.save(normalize(promotion));
        attributes.addFlashAttribute("successMessage", "Акция обновлена");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes attributes) {
        promotionService.deleteById(id);
        attributes.addFlashAttribute("successMessage", "Акция удалена");
        return "redirect:/admin/promotions";
    }

    private void addFormData(Model model, Promotion promotion) {
        model.addAttribute("promotion", promotion);
        model.addAttribute("kinds", PromotionKind.values());
    }

    private String validate(Promotion promotion) {
        if (promotion.getTitle() == null || promotion.getTitle().isBlank()) return "Укажите название акции";
        if (promotion.getKind() == null) return "Выберите тип акции";
        if (promotion.getBonusMultiplier() == null || promotion.getBonusMultiplier().compareTo(BigDecimal.ONE) < 0) {
            return "Множитель бонусов не может быть меньше 1";
        }
        return promotionService.hasValidSchedule(promotion) ? null : "Дата окончания должна быть позже даты начала";
    }

    private Promotion normalize(Promotion promotion) {
        promotion.setTitle(promotion.getTitle().trim());
        if (promotion.getDescription() != null && promotion.getDescription().isBlank()) promotion.setDescription(null);
        if (promotion.getDisplayPriority() == null) promotion.setDisplayPriority(0);
        if (promotion.getActive() == null) promotion.setActive(false);
        return promotion;
    }
}
