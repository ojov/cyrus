package com.ojo.cyrus.controllers;

import com.ojo.cyrus.exception.InvalidTokenException;
import com.ojo.cyrus.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final AuthService authService;

    @GetMapping("/verify-email")
    public String verify(@RequestParam String token, Model model) {
        try {
            authService.verifyEmail(token);
            return "auth/verified";
        } catch (InvalidTokenException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/verification-error";
        }
    }
}
