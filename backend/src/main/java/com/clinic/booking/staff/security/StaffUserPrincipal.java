package com.clinic.booking.staff.security;

import com.clinic.booking.staff.domain.StaffUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal backed by {@code staff_users} (§7.12). Exposes
 * {@code providerId} directly (not just via role name) since §10's
 * authorization pattern for provider-scoped endpoints reads
 * {@code authentication.principal.providerId} in {@code @PreAuthorize}
 * expressions (Milestone 9+).
 */
public class StaffUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final StaffUser.Role role;
    private final Long providerId;
    private final boolean active;

    public StaffUserPrincipal(StaffUser staffUser) {
        this.id = staffUser.getId();
        this.username = staffUser.getUsername();
        this.passwordHash = staffUser.getPasswordHash();
        this.role = staffUser.getRole();
        this.providerId = staffUser.getProviderId();
        this.active = staffUser.isActive();
    }

    public Long getId() {
        return id;
    }

    public StaffUser.Role getRole() {
        return role;
    }

    public Long getProviderId() {
        return providerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Lockout (§15.9) is enforced explicitly in StaffAuthService before authentication
        // is ever established, not delegated to Spring Security's own locked-account check.
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
