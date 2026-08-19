package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.model.InventoryMovementType;
import com.shlyapoff.shop.service.InventoryCsvService;
import com.shlyapoff.shop.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {
    private final InventoryService inventoryService;
    private final InventoryCsvService csvService;

    @GetMapping
    public String inventory(@RequestParam(defaultValue = "0") int page, Model model) {
        var products = inventoryService.findInventory();
        var movements = inventoryService.findMovements(page);
        model.addAttribute("products", products);
        model.addAttribute("lowStockCount", inventoryService.lowStockCount(products));
        model.addAttribute("outOfStockCount", inventoryService.outOfStockCount(products));
        model.addAttribute("movements", movements.getContent());
        model.addAttribute("currentPage", movements.getNumber());
        model.addAttribute("totalPages", movements.getTotalPages());
        return "admin/inventory";
    }

    @PostMapping("/product/{id}")
    public String quickEditProduct(@PathVariable Long id,
                                   @RequestParam BigDecimal price,
                                   @RequestParam int stockQuantity,
                                   @RequestParam int lowStockThreshold,
                                   @RequestParam(required = false) String reason,
                                   Authentication authentication,
                                   RedirectAttributes attributes) {
        try {
            inventoryService.quickEditProduct(id, price, stockQuantity, lowStockThreshold,
                    reason, authentication.getName());
            attributes.addFlashAttribute("successMessage", "Цена и остаток товара обновлены");
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/inventory";
    }

    @PostMapping("/variant/{id}")
    public String quickEditVariant(@PathVariable Long id,
                                   @RequestParam int stockQuantity,
                                   @RequestParam(required = false) String reason,
                                   Authentication authentication,
                                   RedirectAttributes attributes) {
        try {
            inventoryService.adjustVariant(id, stockQuantity, InventoryMovementType.MANUAL_ADJUSTMENT,
                    reason, authentication.getName());
            attributes.addFlashAttribute("successMessage", "Остаток варианта обновлён");
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/inventory";
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvService.exportCsv());
    }

    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            Authentication authentication,
                            RedirectAttributes attributes) {
        try {
            var result = csvService.importCsv(file, authentication.getName());
            attributes.addFlashAttribute("successMessage", "CSV импортирован: создано товаров — "
                    + result.createdProducts() + ", обновлено — " + result.updatedProducts()
                    + ", вариантов — " + result.processedVariants());
        } catch (Exception ex) {
            attributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/inventory";
    }
}
