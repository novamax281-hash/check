package com.example.placement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Enrollment number is required")
    @Size(min = 1, max = 50, message = "Enrollment number must be between 1 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Enrollment number must contain only alphanumeric characters")
    private String enrollmentno;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Enter a valid 10-digit mobile number")
    private String mobile;

    @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain only digits")
    private String otp;
}
