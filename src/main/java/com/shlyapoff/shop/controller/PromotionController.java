package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PromotionController {
    private final PromotionService promotionService;

    @GetMapping("/promotions")
    public String promotions(Model model) {
        model.addAttribute("promotions", promotionService.findActive());
        return "promotions";
    }

    @GetMapping("/promotions/{id}")
    public String promotion(@PathVariable Long id, Model model) {
        return promotionService.findActiveById(id)
                .map(promotion -> {
                    model.addAttribute("promotion", promotion);
                    return "promotion";
                })
                .orElse("redirect:/promotions");
    }
}
