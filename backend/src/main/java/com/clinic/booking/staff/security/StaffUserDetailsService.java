package com.clinic.booking.staff.security;

import com.clinic.booking.staff.repository.StaffUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Standard Spring Security {@link UserDetailsService} for {@code staff_users}
 * (§7.12). {@link com.clinic.booking.staff.service.StaffAuthService} does
 * <em>not</em> use this for its own login flow — it needs the
 * username-not-found case to run the same constant-time path as a wrong
 * password (§15.9 anti-enumeration), which this interface's
 * exception-throwing contract doesn't fit — but this remains the single,
 * conventional principal-loading entry point for the rest of Spring Security.
 */
@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffUserRepository staffUserRepository;

    public StaffUserDetailsService(StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return staffUserRepository.findByUsername(username)
                .map(StaffUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No staff user found for username: " + username));
    }
}
