package com.example.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String message;

    private String role;

    private boolean otpRequired;

    private String email;

    private Integer otpAttemptsRemaining;

    private Long retryAfterSeconds;

    public LoginResponse(String message, String role) {
        this.message = message;
        this.role = role;
        this.otpRequired = false;
    }

    public LoginResponse(String message, String role, boolean otpRequired, String email) {
        this.message = message;
        this.role = role;
        this.otpRequired = otpRequired;
        this.email = email;
    }
}
