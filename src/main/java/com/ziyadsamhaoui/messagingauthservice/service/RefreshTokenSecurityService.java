package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenSecurityService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeChain(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExpired(Long tokenId) {
        refreshTokenRepository.markExpiredById(tokenId);
    }
}
