package com.project.login.service.login;

import com.project.login.model.dto.request.login.LoginRequest;
import com.project.login.model.entity.UserEntity;
import com.project.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public Map<String, String> login(LoginRequest req) {

        UserEntity user;

        if (req.getEmail() != null) {
            user = userRepository.findByEmail(req.getEmail())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
        } else {
            user = userRepository.findByStudentNumber(req.getStudentNumber())
                    .orElseThrow(() -> new RuntimeException("学号不存在"));
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword_hash())) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtService.generateToken(user);

        Map<String, String> result = new HashMap<>();
        result.put("token", token);

        return result;
    }
}
