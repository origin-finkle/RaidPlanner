package com.origin.controller;

import com.origin.auth.DiscordAuthService;
import com.origin.auth.DiscordUserSession;
import com.origin.dto.AuthStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final DiscordAuthService discordAuthService;

    @GetMapping("/me")
    @ResponseBody
    public ResponseEntity<AuthStatusDTO> me(HttpSession session) {
        DiscordUserSession user = discordAuthService.getCurrentUser(session);
        if (user == null) {
            return ResponseEntity.ok(new AuthStatusDTO(
                    discordAuthService.isConfigured(),
                    false,
                    false,
                    null,
                    null,
                    null
            ));
        }

        return ResponseEntity.ok(new AuthStatusDTO(
                discordAuthService.isConfigured(),
                true,
                user.isOfficer(),
                user.getDiscordId(),
                user.getUsername(),
                user.getDisplayName()
        ));
    }

    @GetMapping("/discord/login")
    public String login(HttpSession session) {
        try {
            return "redirect:" + discordAuthService.buildAuthorizationUrl(session);
        } catch (Exception exception) {
            return "redirect:" + discordAuthService.getFrontendLoginUrlWithFlag("config");
        }
    }

    @GetMapping("/discord/callback")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String state,
                           @RequestParam(required = false) String error,
                           HttpSession session) {
        if (error != null || code == null || state == null) {
            return "redirect:" + discordAuthService.getFrontendLoginUrlWithFlag("oauth");
        }

        try {
            return "redirect:" + discordAuthService.handleCallback(code, state, session);
        } catch (IllegalStateException exception) {
            return "redirect:" + discordAuthService.getFrontendLoginUrlWithFlag("oauth");
        } catch (Exception exception) {
            return "redirect:" + discordAuthService.getFrontendLoginUrlWithFlag("oauth");
        }
    }

    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            discordAuthService.logout(session);
        }
        return ResponseEntity.ok().build();
    }
}
