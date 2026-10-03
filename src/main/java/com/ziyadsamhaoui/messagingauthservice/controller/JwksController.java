package com.ziyadsamhaoui.messagingauthservice.controller;

import com.ziyadsamhaoui.messagingauthservice.security.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final RsaKeyProvider keyProvider;

    public JwksController(RsaKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return keyProvider.publicJwkSet().toJSONObject();
    }
}
