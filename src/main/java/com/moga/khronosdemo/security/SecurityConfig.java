package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.enums.Role;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Paths only owners and admins ever use. Here a worker is turned away before the body or the parameters
    // are read, so they get 403, never a 400 that describes a request they could not make anyway. Which
    // owner may reach which establishment is still up to @PreAuthorize and AccessGuard on each endpoint.
    // /api/reports/worker/** is left out on purpose: a worker reads their own daily report there. The
    // inventory is all owner-only, and its report and exports are under /api/reports/establishment/**.
    private static final String[] OWNERS_AND_ADMINS_ONLY = {
            "/api/cash-closings/**",
            "/api/expenses/**",
            "/api/reports/establishment/**",
            "/api/workers/*/rates/**",
            "/api/time-entries/establishment/**",
            "/api/suppliers/**",
            "/api/products/**",
            "/api/stock-movements/**",
            "/api/stock-counts/**",
            "/api/purchase-orders/**"
    };

    private final HandlerExceptionResolver exceptionResolver;

    public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    // Only the login endpoint checks a password; every other request carries a short-lived bearer token
    @Bean
    public AuthenticationManager authenticationManager(DatabaseUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // The account status is checked only once the password matches, so a wrong password gets the
        // same answer whether the account is active, disabled or missing, and reveals nothing about it
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtUserAuthenticationConverter jwtConverter,
                                                   @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins)
            throws Exception {
        AuthenticationEntryPoint entryPoint =
                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex);
        AccessDeniedHandler accessDeniedHandler =
                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex);

        // Browsers on other origins are only let in when app.cors.allowed-origins lists them
        List<String> origins = allowedOrigins.stream().filter(StringUtils::hasText).map(String::trim).toList();
        if (!origins.isEmpty()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource(origins)));
        }

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(OWNERS_AND_ADMINS_ONLY).hasAnyRole(Role.OWNER.name(), Role.PLATFORM_ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    // Tokens travel in the Authorization header, not in cookies, so credentials are not allowed
    private static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Lets the front end read how long to wait after a 429 from the login endpoint
        configuration.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
