package com.docsync.service;

import com.docsync.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security {@link UserDetailsService} implementation that loads users by email address.
 * Since {@link com.docsync.model.entity.User} implements {@link UserDetails} directly (RC-002),
 * no adapter wrapping is needed.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Constructs a {@code UserDetailsServiceImpl} with the given user repository.
     *
     * @param userRepository repository for looking up user records by email
     */
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a user by their email address for Spring Security authentication.
     *
     * @param username the email address (used as the Spring Security username)
     * @return the {@link UserDetails} (a {@link com.docsync.model.entity.User} entity)
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
