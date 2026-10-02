package com.example.placement.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RecaptchaServiceTest {

    private static final String SITEVERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Test
    void acceptsSuccessfulGoogleVerification() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SITEVERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("secret=test-secret"),
                        org.hamcrest.Matchers.containsString("response=valid-token"))))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        RecaptchaService service = new RecaptchaService(builder, "test-secret");

        assertTrue(service.verify("valid-token"));
        server.verify();
    }

    @Test
    void rejectsInvalidOrExpiredGoogleVerification() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SITEVERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}",
                        MediaType.APPLICATION_JSON));
        RecaptchaService service = new RecaptchaService(builder, "test-secret");

        assertFalse(service.verify("expired-token"));
        server.verify();
    }

    @Test
    void rejectsMissingTokenWithoutCallingGoogle() {
        RecaptchaService service = new RecaptchaService(RestClient.builder(), "test-secret");

        assertFalse(service.verify(" "));
    }

    @Test
    void rejectsMissingSecretWithoutCallingGoogle() {
        RecaptchaService service = new RecaptchaService(RestClient.builder(), "");

        assertFalse(service.verify("valid-token"));
    }
}