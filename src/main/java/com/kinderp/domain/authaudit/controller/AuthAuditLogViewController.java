package com.kinderp.domain.authaudit.controller;

import com.kinderp.domain.authaudit.entity.AuthAuditEventType;
import com.kinderp.domain.authaudit.entity.AuthAuditResult;
import com.kinderp.domain.member.entity.MemberAuthProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthAuditLogViewController {

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public String auditLogPage(Model model) {
        model.addAttribute("eventTypes", AuthAuditEventType.values());
        model.addAttribute("results", AuthAuditResult.values());
        model.addAttribute("providers", MemberAuthProvider.values());
        return "authaudit/audit-logs";
    }
}
