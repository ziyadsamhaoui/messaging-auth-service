package com.ziyadsamhaoui.messagingauthservice.security;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.exception.InvalidTokenException;
import com.ziyadsamhaoui.messagingauthservice.model.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Component
public class JwtTokenProvider {

    public static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public JwtTokenProvider(AuthProperties properties) {
        AuthProperties.Jwt jwt = properties.jwt();
        this.key = new SecretKeySpec(
                jwt.hmacSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        this.issuer = jwt.issuer();
        this.accessTokenTtlSeconds = jwt.accessTokenTtl().toSeconds();
    }

    public String issueAccessToken(UUID userId, Role role) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .jwtID(UUID.randomUUID().toString())
                .claim(ROLE_CLAIM, role.name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims);
        try {
            signedJWT.sign(new MACSigner(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to sign access token", ex);
        }
        return signedJWT.serialize();
    }

    public JWTClaimsSet parseAndVerify(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(key))) {
                throw new InvalidTokenException("invalid access token signature");
            }
            return signedJWT.getJWTClaimsSet();
        } catch (ParseException | JOSEException ex) {
            throw new InvalidTokenException("malformed access token");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
