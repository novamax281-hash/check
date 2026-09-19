package com.example.placement.security;

import com.example.placement.entity.UsersEntries;
import com.example.placement.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomAuthenticationProviderTest {

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final CustomAuthenticationProvider provider =
            new CustomAuthenticationProvider(authenticationService);

    private UsersEntries user(String enrollment, String role) {
        UsersEntries user = new UsersEntries(enrollment, "9000000000");
        user.setRole(role);
        user.setEmail(enrollment + "@mail.com");
        return user;
    }

    private Authentication authenticate(String enrollment, String mobile, String role) {
        when(authenticationService.validateCredentials(enrollment, mobile)).thenReturn(user(enrollment, role));
        return provider.authenticate(new UsernamePasswordAuthenticationToken(enrollment, mobile));
    }

    @Test
    void studentGetsStudentRole() {
        Authentication authentication = authenticate("2023CS101", "9000000000", "Student");
        assertThat(authority(authentication)).isEqualTo("ROLE_STUDENT");
    }

    @Test
    void coordinatorGetsAdminRole() {
        Authentication authentication = authenticate("COORD1", "9000000001", "Coordinator");
        assertThat(authority(authentication)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void adminGetsAdminRole() {
        Authentication authentication = authenticate("ADMIN1", "9000000002", "admin");
        assertThat(authority(authentication)).isEqualTo("ROLE_ADMIN");
    }

    private String authority(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
    }
}