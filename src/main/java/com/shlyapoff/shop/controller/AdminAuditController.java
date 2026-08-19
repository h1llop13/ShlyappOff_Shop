package com.shlyapoff.shop.controller;

import com.shlyapoff.shop.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditLogService auditLogService;

    @GetMapping
    public String auditPage(@RequestParam(defaultValue = "0") int page, Model model) {
        var auditPage = auditLogService.findRecent(page);
        model.addAttribute("entries", auditPage.getContent());
        model.addAttribute("currentPage", auditPage.getNumber());
        model.addAttribute("totalPages", auditPage.getTotalPages());
        return "admin/audit";
    }
}
