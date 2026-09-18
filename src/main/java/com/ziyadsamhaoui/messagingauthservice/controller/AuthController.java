package com.ziyadsamhaoui.messagingauthservice.controller;

import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.ForgotPasswordRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.LoginRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.LogoutRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.RefreshRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.RegisterRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.ResetPasswordRequest;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthResponses.RefreshedTokenResponse;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthResponses.RegisterResponse;
import com.ziyadsamhaoui.messagingauthservice.dto.AuthResponses.TokenResponse;
import com.ziyadsamhaoui.messagingauthservice.security.JwtTokenProvider;
import com.ziyadsamhaoui.messagingauthservice.service.AuthService;
import com.ziyadsamhaoui.messagingauthservice.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(authService.register(request), request.email()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair pair = authService.login(request);
        return ResponseEntity.ok(TokenResponse.of(
                pair.accessToken(), pair.refreshToken(), jwtTokenProvider.getAccessTokenTtlSeconds()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshedTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.TokenPair pair = authService.refresh(request);
        return ResponseEntity.ok(RefreshedTokenResponse.of(
                pair.accessToken(), pair.refreshToken(), jwtTokenProvider.getAccessTokenTtlSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(request, extractBearerToken(authorization));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
