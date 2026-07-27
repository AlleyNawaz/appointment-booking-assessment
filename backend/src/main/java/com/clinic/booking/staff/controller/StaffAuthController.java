package com.clinic.booking.staff.controller;

import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.staff.dto.LoginRequest;
import com.clinic.booking.staff.dto.StaffSessionResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.service.StaffAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** POST/GET /api/v1/staff/auth/{login,logout,session} (PRD §8.20) — never gated (§6). */
@RestController
@RequestMapping("/api/v1/staff/auth")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/login")
    public StaffSessionResponse login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new MissingRequiredFieldException("username");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new MissingRequiredFieldException("password");
        }
        return staffAuthService.login(request.username(), request.password(), httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        staffAuthService.logout(httpRequest, httpResponse);
    }

    @GetMapping("/session")
    public StaffSessionResponse session(
            @AuthenticationPrincipal StaffUserPrincipal principal, HttpServletRequest httpRequest) {
        return staffAuthService.currentSession(principal, httpRequest);
    }
}
