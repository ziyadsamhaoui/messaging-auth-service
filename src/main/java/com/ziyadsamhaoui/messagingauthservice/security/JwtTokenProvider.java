package com.ziyadsamhaoui.messagingauthservice.security;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.exception.InvalidTokenException;
import com.ziyadsamhaoui.messagingauthservice.model.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String ROLE_CLAIM = "role";

    private final RSASSASigner signer;
    private final RSASSAVerifier verifier;
    private final String keyId;
    private final String issuer;
    private final String audience;
    private final long accessTokenTtlSeconds;

    public JwtTokenProvider(AuthProperties properties, RsaKeyProvider keyProvider) {
        AuthProperties.Jwt jwt = properties.jwt();
        this.signer = createSigner(keyProvider.signingKey());
        this.verifier = createVerifier(keyProvider.signingKey());
        this.keyId = keyProvider.keyId();
        this.issuer = jwt.issuer();
        this.audience = jwt.audience();
        this.accessTokenTtlSeconds = jwt.accessTokenTtl().toSeconds();
    }

    public String issueAccessToken(UUID userId, Role role) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience(audience)
                .jwtID(UUID.randomUUID().toString())
                .claim(ROLE_CLAIM, role.name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(keyId)
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to sign access token", ex);
        }
        return signedJWT.serialize();
    }

    public JWTClaimsSet parseAndVerify(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
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

    private static RSASSASigner createSigner(RSAKey key) {
        try {
            return new RSASSASigner(key);
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to initialize the RS256 signer", ex);
        }
    }

    private static RSASSAVerifier createVerifier(RSAKey key) {
        try {
            return new RSASSAVerifier(key.toRSAPublicKey());
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to initialize the RS256 verifier", ex);
        }
    }
}
