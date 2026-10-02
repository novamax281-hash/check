package com.example.placement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Pattern(regexp = "^[A-Za-z ]+$", message = "Name must contain only letters and spaces")
    private String name;

    @NotBlank(message = "Enrollment number is required")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Enrollment number must contain only alphanumeric characters")
    private String enrollmentno;

    @NotBlank(message = "College email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Enter a valid 10-digit mobile number")
    private String mobile;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    @NotBlank(message = "Batch selection is required")
    @Pattern(regexp = "^[0-9]{4}-[0-9]{4}$", message = "Select a valid batch")
    private String batch;

    @NotNull(message = "10th percentage is required")
    @DecimalMin(value = "0", message = "10th percentage must be at least 0")
    @DecimalMax(value = "100", message = "10th percentage must be 100 or less")
    private Double tenthPercentage;

    @NotNull(message = "12th percentage is required")
    @DecimalMin(value = "0", message = "12th percentage must be at least 0")
    @DecimalMax(value = "100", message = "12th percentage must be 100 or less")
    private Double twelfthPercentage;

    @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain only digits")
    private String otp;

    @JsonProperty("g-recaptcha-response")
    private String captchaResponse;
}