package com.example.placement.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The catch-all {@code Exception} handler in {@link GlobalExceptionHandler} used to
 * outrank Spring's own resolver, so malformed input answered 500 instead of 4xx.
 */
class GlobalExceptionHandlerStatusTest {

    public static class Payload {
        public String name;
        public Integer count;
    }

    @RestController
    static class ProbeController {
        @PostMapping("/probe/json")
        public String json(@RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/probe/number")
        public String number(@RequestParam int page) {
            return "ok";
        }

        @PutMapping("/probe/put-only")
        public String putOnly() {
            return "ok";
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void malformedJsonIsABadRequestNotAServerError() throws Exception {
        var response = mvc().perform(MockMvcRequestBuilders.post("/probe/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("message");
    }

    @Test
    void unparsableRequestParamIsABadRequestNotAServerError() throws Exception {
        var response = mvc().perform(MockMvcRequestBuilders.get("/probe/number")
                .param("page", "abc")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void wrongJsonFieldTypeIsABadRequestNotAServerError() throws Exception {
        var response = mvc().perform(MockMvcRequestBuilders.post("/probe/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"count\":\"not-a-number\"}")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void unsupportedHttpMethodKeepsMethodNotAllowed() throws Exception {
        var response = mvc().perform(MockMvcRequestBuilders.get("/probe/put-only"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(405);
    }

    @Test
    void validRequestIsUnaffected() throws Exception {
        var response = mvc().perform(MockMvcRequestBuilders.post("/probe/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ok\"}")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
