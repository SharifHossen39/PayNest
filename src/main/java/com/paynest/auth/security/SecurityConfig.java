package com.paynest.auth.security;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String PBKDF2_ID = "pbkdf2@SpringSecurity_v5_8";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                PBKDF2_ID,
                Map.of(PBKDF2_ID, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        RequestMatcher currentProfileRequest = request ->
                "GET".equals(request.getMethod())
                        && (request.getContextPath() + "/api/users/me").equals(request.getRequestURI());
        RequestMatcher currentWalletRequest = request ->
                "GET".equals(request.getMethod())
                        && (request.getContextPath() + "/api/wallet").equals(request.getRequestURI());
        RequestMatcher transferRequest = request ->
                "POST".equals(request.getMethod())
                        && (request.getContextPath() + "/api/wallet/transfers")
                        .equals(request.getRequestURI());
        RequestMatcher transactionHistoryRequest = request ->
                "GET".equals(request.getMethod())
                        && (request.getContextPath() + "/api/transactions")
                        .equals(request.getRequestURI());
        RequestMatcher transactionDetailRequest = request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return "GET".equals(request.getMethod())
                    && path.matches("/api/transactions/[^/]+");
        };
        HttpStatusEntryPoint forbiddenEntryPoint = new HttpStatusEntryPoint(HttpStatus.FORBIDDEN);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .requestCache(requestCache -> requestCache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (currentProfileRequest.matches(request)
                                    || currentWalletRequest.matches(request)
                                    || transferRequest.matches(request)
                                    || transactionHistoryRequest.matches(request)
                                    || transactionDetailRequest.matches(request)) {
                                authenticationEntryPoint.commence(request, response, exception);
                            } else {
                                forbiddenEntryPoint.commence(request, response, exception);
                            }
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/signin").permitAll()
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(currentWalletRequest).authenticated()
                        .requestMatchers(transferRequest).authenticated()
                        .requestMatchers(transactionHistoryRequest).authenticated()
                        .requestMatchers(transactionDetailRequest).authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
