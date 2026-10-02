package com.example.placement.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class RecaptchaService {

    private static final String SITEVERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String secretKey;

    public RecaptchaService(RestClient.Builder restClientBuilder,
                            @Value("${google.recaptcha.secret-key:}") String secretKey) {
        this.restClient = restClientBuilder.build();
        this.secretKey = secretKey;
    }

    public boolean verify(String responseToken) {
        if (responseToken == null || responseToken.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", responseToken.trim());

        try {
            Map<String, Object> result = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RestClientException exception) {
            return false;
        }
    }
}