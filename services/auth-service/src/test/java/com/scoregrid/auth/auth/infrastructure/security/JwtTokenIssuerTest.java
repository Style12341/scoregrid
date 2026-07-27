package com.scoregrid.auth.auth.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.scoregrid.auth.auth.domain.model.IssuedToken;
import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The claim set here is a cross-service contract
 * (docs/contracts.md#authentication-contract). Four other services decode these
 * tokens with the same shared secret, so renaming a claim breaks authorisation
 * everywhere at once and nothing in this service would notice.
 *
 * <p>These tests decode with a {@link NimbusJwtDecoder} built exactly the way
 * every service's {@code SecurityConfig} builds one — so a green run means the
 * other four can genuinely read what we issue.
 */
class JwtTokenIssuerTest {

    private static final String SECRET = "test-only-secret-at-least-32-bytes-long-for-hs256";
    private static final String ISSUER = "scoregrid-auth";

    private JwtTokenIssuer issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        issuer = new JwtTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)), ISSUER, Duration.ofHours(24));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static User maxi() {
        return new User(42L, "maxi", "maxi@example.com", "hashed", Set.of(Role.PLAYER));
    }

    @Test
    @DisplayName("the issued token carries exactly the contract claim set")
    void claimSetMatchesTheContract() {
        IssuedToken token = issuer.issue(maxi());
        Jwt decoded = decoder.decode(token.value());

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("username")).isEqualTo("maxi");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("maxi@example.com");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("PLAYER");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isNotNull();

        // Read as a string, not via Jwt.getIssuer(). The contract's issuer is
        // "scoregrid-auth", a bare StringOrURI — legal JWT, but getIssuer()
        // coerces to URL and throws on it. Anything that validates the issuer
        // downstream has to compare the claim as a string for the same reason.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(ISSUER);
    }

    @Test
    @DisplayName("sub is the user id, never the username")
    void subjectIsTheUserId() {
        Jwt decoded = decoder.decode(issuer.issue(maxi()).value());

        // Every service takes the acting user from 'sub'. Putting the username
        // here would make CurrentUser.requireId() return a name.
        assertThat(decoded.getSubject()).isEqualTo("42").isNotEqualTo("maxi");
    }

    @Test
    void signsWithHs256() {
        Jwt decoded = decoder.decode(issuer.issue(maxi()).value());

        assertThat(decoded.getHeaders()).containsEntry("alg", MacAlgorithm.HS256.getName());
    }

    @Test
    @DisplayName("roles serialise as a JSON array of strings, which is what the converter expects")
    void rolesAreAListOfStrings() {
        User admin = new User(7L, "boss", "boss@example.com", "hashed", Set.of(Role.PLAYER, Role.ADMIN));

        List<String> roles = decoder.decode(issuer.issue(admin).value()).getClaimAsStringList("roles");

        assertThat(roles).containsExactlyInAnyOrder("PLAYER", "ADMIN");
    }

    @Test
    @DisplayName("lifetime comes from the configured ttl, not a hardcoded constant")
    void expiryHonoursTheConfiguredTtl() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtTokenIssuer shortLived = new JwtTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)), ISSUER, Duration.ofMinutes(15));

        Jwt decoded = decoder.decode(shortLived.issue(maxi()).value());

        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("the reported expiresAt is the expiry the token actually carries")
    void reportedExpiryMatchesTheTokenExpiry() {
        IssuedToken token = issuer.issue(maxi());
        Jwt decoded = decoder.decode(token.value());

        // 'exp' is a numeric date claim, so sub-second precision is dropped in
        // the token. Reporting an unrounded value to the client would promise
        // an expiry a fraction later than the token really has.
        assertThat(token.expiresAt()).isEqualTo(decoded.getExpiresAt());
        assertThat(token.expiresAt()).isEqualTo(token.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void anotherSecretDoesNotVerify() {
        SecretKey otherKey = new SecretKeySpec(
                "a-completely-different-secret-also-32-bytes-plus".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        JwtTokenIssuer impostor = new JwtTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), ISSUER, Duration.ofHours(24));

        String forged = impostor.issue(maxi()).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("the password hash never reaches the token")
    void tokenDoesNotLeakTheHash() {
        IssuedToken token = issuer.issue(maxi());

        assertThat(token.value()).doesNotContain("hashed");
        assertThat(decoder.decode(token.value()).getClaims()).doesNotContainKey("passwordHash");
    }
}
