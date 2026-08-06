package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.DiscountType;
import com.shlyapoff.shop.model.PromoCode;
import com.shlyapoff.shop.service.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/promo-codes")
@RequiredArgsConstructor
public class AdminPromoCodeController {
    private final PromoCodeService promoCodeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("promoCodes", promoCodeService.findAll());
        return "admin/promo-codes";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        addFormData(model, new PromoCode());
        return "admin/promo-code-form";
    }

    @PostMapping("/create")
    public String create(@ModelAttribute PromoCode promoCode,
                         @RequestParam(defaultValue = "false") boolean active,
                         RedirectAttributes attributes) {
        promoCode.setActive(active);
        try {
            if (promoCodeService.codeBelongsToAnotherPromo(promoCode.getCode(), null)) {
                throw new IllegalArgumentException("Такой промокод уже существует");
            }
            promoCodeService.save(promoCode);
            attributes.addFlashAttribute("successMessage", "Промокод создан");
            return "redirect:/admin/promo-codes";
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/admin/promo-codes/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        return promoCodeService.findById(id).map(promoCode -> {
            addFormData(model, promoCode);
            return "admin/promo-code-form";
        }).orElse("redirect:/admin/promo-codes");
    }

    @PostMapping("/edit/{id}")
    public String edit(@PathVariable Long id, @ModelAttribute PromoCode form,
                       @RequestParam(defaultValue = "false") boolean active,
                       RedirectAttributes attributes) {
        form.setActive(active);
        try {
            PromoCode promoCode = promoCodeService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Промокод не найден"));
            if (promoCodeService.codeBelongsToAnotherPromo(form.getCode(), id)) {
                throw new IllegalArgumentException("Такой промокод уже существует");
            }
            promoCode.setCode(form.getCode());
            promoCode.setDescription(form.getDescription());
            promoCode.setDiscountType(form.getDiscountType());
            promoCode.setDiscountValue(form.getDiscountValue());
            promoCode.setMinOrderAmount(form.getMinOrderAmount());
            promoCode.setMaxDiscountAmount(form.getMaxDiscountAmount());
            promoCode.setUsageLimit(form.getUsageLimit());
            promoCode.setPerCustomerLimit(form.getPerCustomerLimit());
            promoCode.setStartsAt(form.getStartsAt());
            promoCode.setEndsAt(form.getEndsAt());
            promoCode.setActive(active);
            promoCodeService.save(promoCode);
            attributes.addFlashAttribute("successMessage", "Промокод обновлён");
            return "redirect:/admin/promo-codes";
        } catch (IllegalArgumentException exception) {
            attributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/admin/promo-codes/edit/" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes attributes) {
        promoCodeService.deleteById(id);
        attributes.addFlashAttribute("successMessage", "Промокод удалён");
        return "redirect:/admin/promo-codes";
    }

    private void addFormData(Model model, PromoCode promoCode) {
        model.addAttribute("promoCode", promoCode);
        model.addAttribute("discountTypes", DiscountType.values());
    }
}
