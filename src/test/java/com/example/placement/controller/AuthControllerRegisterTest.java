package com.example.placement.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerRegisterTest {

    @Test
    void acceptsLietDomainOnly() {
        assertThat(AuthController.isLietEmail("student@liet.in")).isTrue();
        assertThat(AuthController.isLietEmail("STUDENT@LIET.IN")).isTrue();
        assertThat(AuthController.isLietEmail("  student@liet.in ")).isTrue();
    }

    @Test
    void rejectsOtherDomains() {
        assertThat(AuthController.isLietEmail("student@gmail.com")).isFalse();
        assertThat(AuthController.isLietEmail("student@notliet.in")).isFalse();
        assertThat(AuthController.isLietEmail("student@liet.in.evil.com")).isFalse();
    }

    @Test
    void rejectsNullAndBlank() {
        assertThat(AuthController.isLietEmail(null)).isFalse();
        assertThat(AuthController.isLietEmail("")).isFalse();
        assertThat(AuthController.isLietEmail("   ")).isFalse();
    }
}