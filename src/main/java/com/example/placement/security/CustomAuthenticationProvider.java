package com.example.placement.security;

import com.example.placement.entity.UsersEntries;
import com.example.placement.service.AuthenticationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private static final String NO_PASSWORD = "{noop}N/A";

    private final AuthenticationService authenticationService;

    public CustomAuthenticationProvider(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String enrollmentNumber = authentication.getName();
        String mobileNumber = String.valueOf(authentication.getCredentials());

        UsersEntries user = authenticationService.validateCredentials(enrollmentNumber, mobileNumber);

        if (user == null) {
            throw new BadCredentialsException("Invalid enrollment number or mobile number");
        }

        String role;
        if (user.getRole() != null && user.getRole().equalsIgnoreCase("admin")) {
            role = "ROLE_ADMIN";
        } else if (user.getRole() != null && user.getRole().equalsIgnoreCase("coordinator")) {
            role = "ROLE_COORDINATOR";
        } else {
            role = "ROLE_STUDENT";
        }
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        UserDetails principal = User.withUsername(user.getEnrollmentno())
                .password(user.getEmail())
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, mobileNumber, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
