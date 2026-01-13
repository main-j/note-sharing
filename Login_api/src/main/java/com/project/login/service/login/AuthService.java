package com.project.login.service.login;

import com.project.login.model.request.login.LoginRequest;
import com.project.login.model.entity.UserEntity;
import com.project.login.repository.UserRepository;
import com.project.login.service.adminservice.OnlineUserService;
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
    private final OnlineUserService onlineUserService; 

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

        // 将 token 添加到活跃队列，用于统计在线人数
        onlineUserService.addActiveToken(token);

        Map<String, String> result = new HashMap<>();
        result.put("token", token);

        return result;
    }

    public Map<String, String> adminLogin(LoginRequest req) {

        UserEntity user;

        if (req.getEmail() == null || req.getEmail().trim().isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }
    
        if (req.getPassword() == null || req.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
    
        user = userRepository.findByEmail(req.getEmail().trim())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword_hash())) {
            throw new RuntimeException("密码错误");
        }
    
        String userRole = user.getRole() != null ? user.getRole() : "User";
        if (!"Admin".equals(userRole)) {
            throw new RuntimeException("您暂无权限");
        }
    
        String token = jwtService.generateToken(user);
    
        // 将 token 添加到活跃队列，用于统计在线人数
        onlineUserService.addActiveToken(token);
    
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
    
        return result;
    }
    


    @Deprecated
    public UserEntity getUserByToken(String token) {
        return jwtService.getUserByToken(token);
    }
}
