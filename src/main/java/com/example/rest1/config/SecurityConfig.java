
package com.example.rest1.config;

import com.example.rest1.repo.UserRepo;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepo users) {

        return email -> {

            var user = users.findByEmailIgnoreCase(email)
                    .orElseThrow(() ->
                            new UsernameNotFoundException("User not found"));

            if (user.passwordHash == null) {
                throw new UsernameNotFoundException("User not enabled");
            }

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.email)
                    .password(user.passwordHash)
                    .roles(user.role.name())
                    .disabled(!user.enabled)
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse()
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers(
                                "/api/users",
                                "/api/users/**"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                "/api/money-accounts",
                                "/api/money-accounts/**",
                                "/api/budget-categories",
                                "/api/budget-categories/**",
                                "/api/savings-goals",
                                "/api/savings-goals/**",
                                "/api/financial-transactions",
                                "/api/financial-transactions/**"
                        ).hasAnyRole("USER", "ADMIN")
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
