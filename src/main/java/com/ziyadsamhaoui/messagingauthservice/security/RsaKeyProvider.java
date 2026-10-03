package com.ziyadsamhaoui.messagingauthservice.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.ziyadsamhaoui.messagingauthservice.config.AuthProperties;
import org.springframework.stereotype.Component;

@Component
public class RsaKeyProvider {

    private static final int KEY_SIZE = 2048;

    private final RSAKey signingKey;

    public RsaKeyProvider(AuthProperties properties) {
        this.signingKey = generateSigningKey(properties.jwt().keyId());
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public String keyId() {
        return signingKey.getKeyID();
    }

    public JWKSet publicJwkSet() {
        return new JWKSet(signingKey.toPublicJWK());
    }

    private static RSAKey generateSigningKey(String configuredKeyId) {
        try {
            RSAKey generated = new RSAKeyGenerator(KEY_SIZE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
            String keyId = (configuredKeyId != null && !configuredKeyId.isBlank())
                    ? configuredKeyId
                    : generated.computeThumbprint().toString();
            return new RSAKey.Builder(generated).keyID(keyId).build();
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to generate the RS256 signing key", ex);
        }
    }
}
