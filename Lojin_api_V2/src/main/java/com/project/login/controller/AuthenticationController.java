package com.project.login.controller;

import com.project.login.model.dto.request.login.EmailRequest;
import com.project.login.model.dto.request.login.LoginRequest;
import com.project.login.model.dto.request.login.RegisterRequest;
import com.project.login.model.dto.request.login.ResetPasswordRequest;
import com.project.login.service.login.AuthService;
import com.project.login.service.login.EmailService;
import com.project.login.service.login.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "User Authentication", description = "Login, register, reset password")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthService authService;
    private final UserService userService;
    private final EmailService emailService;

    // --- 1) 登录 ---
    @Operation(summary = "User login")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Map<String, String> token = authService.login(request);
        return ResponseEntity.ok(token);
    }

    // --- 2) 注册：发送邮箱验证码 ---
    @Operation(summary = "Send email code for registration")
    @PostMapping("/register/send-code")
    public ResponseEntity<?> sendRegisterCode(@RequestBody EmailRequest request) {
        emailService.sendRegisterCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "验证码已发送"));
    }

    // --- 3) 注册 ---
    @Operation(summary = "Register new user")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok(Map.of("message", "注册成功"));
    }

    // --- 4) 忘记密码：发送验证码 ---
    @Operation(summary = "Send email code for password reset")
    @PostMapping("/password/send-code")
    public ResponseEntity<?> sendResetPasswordCode(@RequestBody EmailRequest request) {
        emailService.sendResetPasswordCode(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "验证码已发送"));
    }

    // --- 5) 忘记密码：重设密码 ---
    @Operation(summary = "Reset password using email code")
    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }
}
