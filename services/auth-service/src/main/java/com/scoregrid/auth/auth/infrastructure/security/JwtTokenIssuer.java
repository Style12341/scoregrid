package com.scoregrid.auth.auth.infrastructure.security;

import com.scoregrid.auth.auth.domain.model.IssuedToken;
import com.scoregrid.auth.auth.domain.model.Role;
import com.scoregrid.auth.auth.domain.model.User;
import com.scoregrid.auth.auth.domain.port.out.TokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issues the claim set in docs/contracts.md#authentication-contract.
 *
 * <p>Every other service validates these tokens with the same shared secret, so
 * the claim names here are a public contract: renaming {@code roles} breaks
 * authorisation in four services at once, silently.
 */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration ttl;

    JwtTokenIssuer(JwtEncoder jwtEncoder,
                   @Value("${scoregrid.jwt.issuer}") String issuer,
                   @Value("${scoregrid.jwt.ttl}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    @Override
    public IssuedToken issue(User user) {
        // Truncated to seconds: 'exp' and 'iat' are numeric date claims, so the
        // sub-second part is dropped in the token anyway. Keeping it on the
        // response would report an expiry the token does not actually carry.
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);

        List<String> roles = user.roles().stream().map(Role::name).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.idAsString())
                .claim("username", user.username())
                .claim("email", user.email())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedToken(value, expiresAt);
    }
}
