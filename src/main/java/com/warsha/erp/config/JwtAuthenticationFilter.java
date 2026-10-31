package com.warsha.erp.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying "Authorization: Bearer <jwt>".
 * The token's role claim decides which table the subject is loaded from, so a
 * customer token can never resolve to an ERP account (or the other way round).
 * Invalid or expired tokens leave the request anonymous; protected routes then
 * answer 401 UNAUTHENTICATED through {@link RestAuthErrorHandlers}.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService adminService;
    private final CustomerDetailsService customerService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   CustomUserDetailsService adminService,
                                   CustomerDetailsService customerService) {
        this.jwtUtil = jwtUtil;
        this.adminService = adminService;
        this.customerService = customerService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(7), request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtUtil.parse(token);
            String role = claims.get(JwtUtil.ROLE_CLAIM, String.class);
            UserDetailsService source = JwtUtil.ROLE_CUSTOMER.equals(role) ? customerService : adminService;
            UserDetails user = source.loadUserByUsername(claims.getSubject());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT on {}: {}", request.getRequestURI(), e.getMessage());
        } catch (UsernameNotFoundException e) {
            log.warn("JWT subject no longer exists on {}", request.getRequestURI());
        }
    }
}
