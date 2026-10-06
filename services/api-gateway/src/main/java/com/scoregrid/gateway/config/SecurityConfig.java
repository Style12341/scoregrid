package com.scoregrid.gateway.config;

import com.scoregrid.gateway.error.ErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Edge security for the gateway.
 *
 * <p>Two responsibilities that live here and nowhere else:
 * <ul>
 *   <li>CORS — configured once, at the single entry point. Do not add CORS
 *       config to the downstream services.</li>
 *   <li>Rejecting requests with no valid JWT before they reach a service.
 *       "Valid" includes the issuer: only auth-service's user tokens carry
 *       {@code iss}. Internal service tokens have none and must never be
 *       accepted from outside (docs/contracts.md, internal service JWTs).</li>
 * </ul>
 *
 * <p>Rejections are written in the contract error envelope, not left as an
 * empty 401/403 body.
 *
 * <p>This is a filter, not the security boundary. Every downstream service
 * validates the token again and enforces roles at the method level.
 */
@Configuration
public class SecurityConfig {

    private final String jwtSecret;
    private final String jwtIssuer;
    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${scoregrid.jwt.secret}") String jwtSecret,
                          @Value("${scoregrid.jwt.issuer}") String jwtIssuer,
                          @Value("${scoregrid.cors.allowed-origins}") List<String> allowedOrigins) {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "scoregrid.jwt.secret must be at least 32 bytes. Generate one with: openssl rand -base64 48");
        }
        if (jwtIssuer == null || jwtIssuer.isBlank()) {
            throw new IllegalStateException("scoregrid.jwt.issuer must match the issuer auth-service signs with.");
        }
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ErrorResponseWriter errorResponseWriter) throws Exception {
        AuthenticationEntryPoint entryPoint = authenticationEntryPoint(errorResponseWriter);
        AccessDeniedHandler accessDeniedHandler = accessDeniedHandler(errorResponseWriter);
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // A circuit breaker fallback is a server-side forward of a
                        // request that was already authorised (or public, like
                        // login). Re-checking it would turn "auth-service is down"
                        // into a misleading 401 on POST /api/auth/login.
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Timestamps plus iss. Without the issuer check, an internal service
        // token (same secret, no iss) would pass the edge.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtIssuer));
        return decoder;
    }

    /**
     * 401 in the contract envelope. The bearer entry point still runs first so
     * the standard {@code WWW-Authenticate} header is kept.
     */
    static AuthenticationEntryPoint authenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, authException) -> {
            bearer.commence(request, response, authException);
            String message = authException instanceof OAuth2AuthenticationException
                    ? "Invalid or expired bearer token."
                    : "Authentication is required.";
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
        };
    }

    /** 403 in the contract envelope, keeping the bearer {@code WWW-Authenticate} header. */
    static AccessDeniedHandler accessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, accessDeniedException) -> {
            bearer.handle(request, response, accessDeniedException);
            errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
