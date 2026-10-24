package com.warsha.erp.config;

import com.warsha.erp.entities.User;
import com.warsha.erp.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads ERP staff accounts from the Users table. Every account here is ERP staff and
 * gets ROLE_ADMIN; the stored Role column is added as an extra authority when present.
 */
@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UserRepository userRepo;

    public CustomUserDetailsService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
        String role = user.getRole();
        if (role != null && !role.isBlank()) {
            String normalized = role.trim().toUpperCase(Locale.ROOT);
            String authority = normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
            if (!authority.equals(ROLE_ADMIN)) {
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
    }
}
