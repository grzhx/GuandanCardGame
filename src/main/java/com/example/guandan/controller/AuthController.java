package com.example.guandan.controller;

import com.example.guandan.entity.User;
import com.example.guandan.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        String username = request.get("username");
        String password = request.get("password");
        String confirmation = request.get("confirmation");
        
        if (username == null || username.isEmpty()) {
            response.put("error", "Must provide username");
            return response;
        }
        
        if (password == null || password.isEmpty()) {
            response.put("error", "Must provide password");
            return response;
        }
        
        if (confirmation == null || confirmation.isEmpty()) {
            response.put("error", "Must provide confirmation of password");
            return response;
        }
        
        if (!password.equals(confirmation)) {
            response.put("error", "Passwords do not match");
            return response;
        }
        
        if (userService.findByUsername(username) != null) {
            response.put("error", "Username already exists");
            return response;
        }
        
        User user = userService.register(username, password);
        response.put("username", user.getUsername());
        return response;
    }
    
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        String username = request.get("username");
        String password = request.get("password");
        
        if (username == null || username.isEmpty()) {
            response.put("error", "Must provide username");
            return response;
        }
        
        if (password == null || password.isEmpty()) {
            response.put("error", "Must provide password");
            return response;
        }
        
        User user = userService.login(username, password);
        if (user == null) {
            response.put("error", "invalid username and/or password");
            return response;
        }
        
        response.put("username", user.getUsername());
        return response;
    }
}
