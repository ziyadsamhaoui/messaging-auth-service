package com.ziyadsamhaoui.messagingauthservice.controller;

import com.ziyadsamhaoui.messagingauthservice.dto.AuthRequests.UpdateRoleRequest;
import com.ziyadsamhaoui.messagingauthservice.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCredentialController {

    private final CredentialService credentialService;

    @PatchMapping("/credentials/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        credentialService.updateRole(id, request.role());
        return ResponseEntity.ok().build();
    }
}
