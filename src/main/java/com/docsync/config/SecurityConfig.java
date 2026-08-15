package com.docsync.config;

import com.docsync.service.UserDetailsServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the Login API.
 * Configures the filter chain, authentication provider, password encoder,
 * and registers the JWT and rate-limiter filters.
 *
 * <p>Public endpoints (no token required):
 * <ul>
 *   <li>{@code POST /api/v1/auth/register}</li>
 *   <li>{@code POST /api/v1/auth/login}</li>
 *   <li>{@code POST /api/v1/auth/refresh}</li>
 *   <li>{@code GET /v3/api-docs/**}</li>
 *   <li>{@code GET /swagger-ui/**}</li>
 *   <li>{@code GET /actuator/health}</li>
 * </ul>
 *
 * <p>All other endpoints require a valid, non-blacklisted access token.
 * CSRF is disabled for the stateless JWT flow (NFR-001.3).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final IpRateLimiterFilter ipRateLimiterFilter;

    /**
     * Constructs a {@code SecurityConfig} with the required dependencies.
     *
     * @param userDetailsService  Spring Security user details service (RC-002)
     * @param jwtAuthFilter       JWT token validation filter
     * @param ipRateLimiterFilter per-IP rate limiting filter (RC-001)
     */
    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
            JwtAuthenticationFilter jwtAuthFilter,
            IpRateLimiterFilter ipRateLimiterFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.ipRateLimiterFilter = ipRateLimiterFilter;
    }

    /**
     * Configures the Spring Security filter chain.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the built {@link SecurityFilterChain}
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(ipRateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    /**
     * Creates the {@link DaoAuthenticationProvider} that wires together
     * {@link UserDetailsServiceImpl} and the BCrypt password encoder.
     *
     * @return configured authentication provider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Creates the {@link BCryptPasswordEncoder} bean with cost factor 10 (NFR-001.1).
     *
     * @return BCrypt encoder with cost 10
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean.
     *
     * @param config Spring authentication configuration
     * @return the application's authentication manager
     * @throws Exception if the authentication manager cannot be obtained
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
