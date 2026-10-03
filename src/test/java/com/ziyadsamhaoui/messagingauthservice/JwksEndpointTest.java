package com.ziyadsamhaoui.messagingauthservice;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ziyadsamhaoui.messagingauthservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwksEndpointTest extends IntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void jwksEndpointPublishesTheRsaPublicKey() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/oauth2/jwks"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> key = (Map<String, Object>) ((List<?>) response.getBody().get("keys")).get(0);
        assertThat(key).containsEntry("kty", "RSA")
                .containsEntry("alg", "RS256")
                .containsEntry("use", "sig")
                .containsKeys("kid", "n", "e");
    }

    @Test
    void accessTokenHeaderAndClaimsMatchThePublishedKey() throws Exception {
        register("jwks@example.com");
        ResponseEntity<Map> login = restTemplate.postForEntity(
                url("/auth/login"), jsonEntity(Map.of(
                        "email", "jwks@example.com",
                        "password", "SecurePassword123!")), Map.class);
        String accessToken = (String) login.getBody().get("accessToken");

        SignedJWT signedJWT = SignedJWT.parse(accessToken);
        assertThat(signedJWT.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(signedJWT.getHeader().getKeyID()).isNotBlank();

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("messaging-api");
        assertThat(claims.getSubject()).isNotBlank();
    }
}
