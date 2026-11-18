
package com.example.rest1.config;

import com.example.rest1.repo.UserRepo;
import com.example.rest1.servic.JwtServ;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

                throw new UsernameNotFoundException(
                        "User not enabled"
                );
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
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtServ jwt,
            UserDetailsService users
    ) throws Exception {

        JwtFilter filter = new JwtFilter(jwt, users);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        response.setStatus(
                                                HttpServletResponse.SC_UNAUTHORIZED
                                        )
                        )
                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        response.setStatus(
                                                HttpServletResponse.SC_FORBIDDEN
                                        )
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        .requestMatchers(
                                "/auth/login",
                                "/auth/register",
                                "/auth/refresh",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh"
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

                .addFilterBefore(
                        filter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
