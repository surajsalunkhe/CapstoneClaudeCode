package com.docsync.service;

import com.docsync.model.entity.User;
import com.docsync.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserDetailsServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserDetailsServiceImpl userDetailsService;

    /**
     * Initialises the service under test before each test.
     */
    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailsServiceImpl(userRepository);
    }

    /**
     * Verifies that a valid email returns the corresponding User entity.
     */
    @Test
    void loadUserByUsername_returnsUserForValidEmail() {
        User user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("user@example.com");

        assertEquals("user@example.com", result.getUsername());
    }

    /**
     * Verifies that an unknown email throws {@link UsernameNotFoundException}.
     */
    @Test
    void loadUserByUsername_throwsUsernameNotFoundExceptionForUnknownEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("unknown@example.com"));
    }
}
