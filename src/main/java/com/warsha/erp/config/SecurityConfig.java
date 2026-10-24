package com.warsha.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Access rules, in three tiers:
 * <ul>
 *   <li><b>Public</b>: login/sign-up flows, the read-only storefront catalog, and reviews
 *       (guests may leave feedback).</li>
 *   <li><b>CUSTOMER</b>: the e-commerce customer's own orders and reviews.</li>
 *   <li><b>ADMIN</b>: everything else, i.e. the whole ERP.</li>
 * </ul>
 * Anything not listed explicitly falls through to ADMIN, so new endpoints are private by default.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String CUSTOMER = "CUSTOMER";

    private final CustomUserDetailsService adminService;
    private final JwtAuthenticationFilter jwtFilter;
    private final RestAuthErrorHandlers authErrorHandlers;

    public SecurityConfig(CustomUserDetailsService adminService,
                          JwtAuthenticationFilter jwtFilter,
                          RestAuthErrorHandlers authErrorHandlers) {
        this.adminService = adminService;
        this.jwtFilter = jwtFilter;
        this.authErrorHandlers = authErrorHandlers;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Used only by the ERP login. Customers log in through /auth/customerLogin, which checks
     * their password directly, so a customer account can never obtain an ADMIN token here.
     */
    @Bean
    public AuthenticationManager authManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authErrorHandlers.entryPoint())
                        .accessDeniedHandler(authErrorHandlers.accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        // --- Infrastructure ---
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/status").permitAll()

                        // --- Public: authentication flows ---
                        .requestMatchers(HttpMethod.POST,
                                "/auth/login", "/auth/customerLogin", "/auth/verifyCode",
                                "/auth/forgotPassword", "/auth/resetPassword", "/auth/changePassword",
                                "/customers/customerSignUp", "/customers/resend-otp").permitAll()

                        // --- Public: storefront catalog (read-only) ---
                        .requestMatchers(HttpMethod.GET,
                                "/products/**", "/category/**", "/governorates/**", "/shipping/**",
                                "/images/**", "/api/files/**").permitAll()

                        // --- Public: reviews (guests may submit feedback and comments) ---
                        .requestMatchers(HttpMethod.GET, "/feedbacks/public/**", "/feedbacks/product/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/feedbacks/customer").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.GET, "/feedbacks/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/feedbacks", "/feedbacks/*/comments").permitAll()

                        // --- CUSTOMER: own orders ---
                        .requestMatchers(HttpMethod.POST, "/orders/placeOrder").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.GET, "/orders/customer").hasRole(CUSTOMER)
                        .requestMatchers(HttpMethod.POST, "/vouchers/validate").hasAnyRole(CUSTOMER, ADMIN)

                        // --- ADMIN: the whole ERP, including /auth/register and invoice PDFs ---
                        .anyRequest().hasRole(ADMIN)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
