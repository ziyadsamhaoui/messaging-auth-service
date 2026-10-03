package com.ziyadsamhaoui.messagingauthservice.security;

import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import com.ziyadsamhaoui.messagingauthservice.model.Role;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String ISSUER = "http://messaging-auth-service:8081";
    private static final String AUDIENCE = "messaging-api";

    private final AuthProperties properties = new AuthProperties(
            new AuthProperties.Jwt(ISSUER, AUDIENCE, null, Duration.ofMinutes(15), Duration.ofDays(7)),
            null, null, null);

    private final RsaKeyProvider keyProvider = new RsaKeyProvider(properties);

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(properties, keyProvider);

    @Test
    void issuesRs256TokenWithKeyIdAndAudience() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.issueAccessToken(userId, Role.USER);

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(signedJWT.getHeader().getKeyID()).isEqualTo(keyProvider.keyId());

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(claims.getStringClaim(JwtTokenProvider.ROLE_CLAIM)).isEqualTo(Role.USER.name());
    }

    @Test
    void tokenVerifiesAgainstThePublishedPublicKey() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.issueAccessToken(userId, Role.ADMIN);

        JWTClaimsSet verified = tokenProvider.parseAndVerify(token);

        assertThat(verified.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicJwkSetExposesOnlyPublicRsaMaterial() {
        Map<String, Object> json = keyProvider.publicJwkSet().toJSONObject();
        Map<String, Object> key = (Map<String, Object>) ((java.util.List<?>) json.get("keys")).get(0);

        assertThat(key).containsEntry("kty", "RSA")
                .containsEntry("alg", "RS256")
                .containsEntry("use", "sig")
                .containsKeys("kid", "n", "e");
        assertThat(key).doesNotContainKeys("d", "p", "q");
    }

    @Test
    void configuredKeyIdOverridesTheThumbprint() {
        AuthProperties withKeyId = new AuthProperties(
                new AuthProperties.Jwt(ISSUER, AUDIENCE, "badrlink-key-1", Duration.ofMinutes(15), Duration.ofDays(7)),
                null, null, null);
        RsaKeyProvider provider = new RsaKeyProvider(withKeyId);
        RSAKey publicKey = provider.publicJwkSet().getKeys().get(0).toRSAKey();

        assertThat(publicKey.getKeyID()).isEqualTo("badrlink-key-1");
    }
}
