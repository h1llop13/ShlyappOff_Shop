package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.Promotion;
import com.shlyapoff.shop.model.PromotionKind;
import com.shlyapoff.shop.model.PublicationStatus;
import com.shlyapoff.shop.service.PromotionService;
import com.shlyapoff.shop.service.PromoCodeService;
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
    private final PromoCodeService promoCodeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("promotions", promotionService.findAll());
        return "admin/promotions";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        Promotion promotion = new Promotion();
        promotion.setPublicationStatus(PublicationStatus.DRAFT);
        promotion.setActive(false);
        addFormData(model, promotion);
        return "admin/promotion-form";
    }

    @PostMapping("/create")
    public String create(@ModelAttribute Promotion promotion,
                         @RequestParam(required = false) Long promoCodeId,
                         RedirectAttributes attributes) {
        String error = validate(promotion);
        if (error != null) {
            attributes.addFlashAttribute("errorMessage", error);
            return "redirect:/admin/promotions/create";
        }
        promotion.setPromoCode(resolvePromoCode(promoCodeId));
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
    public String edit(@PathVariable Long id, @ModelAttribute Promotion form,
                       @RequestParam(required = false) Long promoCodeId,
                       RedirectAttributes attributes) {
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
        promotion.setStartsAt(form.getStartsAt());
        promotion.setEndsAt(form.getEndsAt());
        promotion.setPublicationStatus(form.getPublicationStatus());
        promotion.setPublishAt(form.getPublishAt());
        promotion.setTerms(form.getTerms());
        promotion.setPromoCode(resolvePromoCode(promoCodeId));
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
        model.addAttribute("publicationStatuses", PublicationStatus.values());
        model.addAttribute("promoCodes", promoCodeService.findAll());
    }

    private String validate(Promotion promotion) {
        if (promotion.getTitle() == null || promotion.getTitle().isBlank()) return "Укажите название акции";
        if (promotion.getKind() == null) return "Выберите тип акции";
        if (promotion.getBonusMultiplier() == null || promotion.getBonusMultiplier().compareTo(BigDecimal.ONE) < 0) {
            return "Множитель бонусов не может быть меньше 1";
        }
        if (promotion.getPublicationStatus() == PublicationStatus.SCHEDULED && promotion.getPublishAt() == null) {
            return "Для отложенной публикации укажите дату и время";
        }
        return promotionService.hasValidSchedule(promotion) ? null : "Дата окончания должна быть позже даты начала";
    }

    private Promotion normalize(Promotion promotion) {
        promotion.setTitle(promotion.getTitle().trim());
        if (promotion.getDescription() != null && promotion.getDescription().isBlank()) promotion.setDescription(null);
        if (promotion.getDisplayPriority() == null) promotion.setDisplayPriority(0);
        return promotion;
    }

    private com.shlyapoff.shop.model.PromoCode resolvePromoCode(Long promoCodeId) {
        if (promoCodeId == null) return null;
        return promoCodeService.findById(promoCodeId)
                .orElseThrow(() -> new IllegalArgumentException("Промокод не найден"));
    }
}
