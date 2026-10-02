package com.example.placement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OtpVerifyRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits")
    private String otp;

    @JsonProperty("g-recaptcha-response")
    private String captchaResponse;
}