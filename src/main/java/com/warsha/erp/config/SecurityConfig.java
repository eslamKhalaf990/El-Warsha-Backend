package com.warsha.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService adminService;
    private final CustomerDetailsService customerService;
    private final JwtAuthenticationFilter jwtFilter;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public SecurityConfig(CustomUserDetailsService adminService,
                          CustomerDetailsService customerService,
                          JwtAuthenticationFilter jwtFilter) {
        this.adminService = adminService;
        this.customerService = customerService;
        this.jwtFilter = jwtFilter;
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider adminAuthenticationProvider() {
        System.out.println("[" + getTimestamp() + "] CONFIG: Initializing Admin Authentication Provider");
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(adminService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationProvider customerAuthenticationProvider() {
        System.out.println("[" + getTimestamp() + "] CONFIG: Initializing Customer Authentication Provider");
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customerService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        System.out.println("[" + getTimestamp() + "] CONFIG: Building Authentication Manager with dual providers");
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);

        // Register both providers
        authenticationManagerBuilder.authenticationProvider(adminAuthenticationProvider());
        authenticationManagerBuilder.authenticationProvider(customerAuthenticationProvider());

        AuthenticationManager manager = authenticationManagerBuilder.build();
        System.out.println("[" + getTimestamp() + "] SUCCESS: Authentication Manager ready");
        return manager;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        System.out.println("[" + getTimestamp() + "] CONFIG: Configuring Security Filter Chain...");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/api/files/**", "/invoice/pdf/**", "/status", "/products/**", "/category/**", "/customers/**", "/governorates/**", "/images/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/shipping/**").permitAll()
                        .requestMatchers("/feedbacks/customer").authenticated()
                        .requestMatchers("/feedbacks/public/**", "/feedbacks/product/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/feedbacks", "/feedbacks/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/feedbacks/*").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        System.out.println("[" + getTimestamp() + "] SUCCESS: Security Filter Chain applied.");
        return http.build();
    }
}
