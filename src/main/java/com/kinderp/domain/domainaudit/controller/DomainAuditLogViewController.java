package com.kinderp.domain.domainaudit.controller;

import com.kinderp.domain.domainaudit.entity.DomainAuditAction;
import com.kinderp.domain.domainaudit.entity.DomainAuditTargetType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DomainAuditLogViewController {

    @GetMapping("/domain-audit-logs")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public String auditLogPage(Model model) {
        model.addAttribute("actions", DomainAuditAction.values());
        model.addAttribute("targetTypes", DomainAuditTargetType.values());
        return "domainaudit/audit-logs";
    }
}
