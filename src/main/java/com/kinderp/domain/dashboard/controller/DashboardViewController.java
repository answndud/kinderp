package com.kinderp.domain.dashboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardViewController {

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PRINCIPAL')")
    public String dashboardPage() {
        return "dashboard/dashboard";
    }
}
