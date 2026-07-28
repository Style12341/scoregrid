package com.scoregrid.prediction.shared.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Component
public class ServiceTokenProvider {

    private final byte[] secret;
    private final String serviceName;

    public ServiceTokenProvider(@Value("${scoregrid.jwt.secret}") String secret,
                                 @Value("${spring.application.name}") String serviceName) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.serviceName = serviceName;
    }

    public String generate() {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(serviceName)
                    .claim("roles", List.of("ADMIN"))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(24, ChronoUnit.HOURS)))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).build(),
                    claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate service token", e);
        }
    }
}
