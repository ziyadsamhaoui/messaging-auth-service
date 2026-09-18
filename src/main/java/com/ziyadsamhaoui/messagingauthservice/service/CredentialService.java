package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.exception.CredentialNotFoundException;
import com.ziyadsamhaoui.messagingauthservice.model.Credential;
import com.ziyadsamhaoui.messagingauthservice.model.Role;
import com.ziyadsamhaoui.messagingauthservice.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;

    @Transactional
    public void updateRole(UUID id, Role role) {
        Credential credential = credentialRepository.findById(id)
                .orElseThrow(() -> new CredentialNotFoundException(id));
        credential.changeRole(role);
    }
}
