package com.ziyadsamhaoui.messagingauthservice.service;

import com.ziyadsamhaoui.messagingauthservice.model.Credential;
import com.ziyadsamhaoui.messagingauthservice.outbox.CredentialEvents;
import com.ziyadsamhaoui.messagingauthservice.outbox.OutboxPublisher;
import com.ziyadsamhaoui.messagingauthservice.outbox.TransactionalOutboxPublisher;
import com.ziyadsamhaoui.messagingauthservice.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialLockService {

    private final CredentialRepository credentialRepository;
    private final OutboxPublisher outboxPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFailedAttempt(Credential credential, Instant now, Duration lockoutDuration) {
        credential.registerFailedAttempt(now, now.plus(lockoutDuration));
        credentialRepository.saveAndFlush(credential);
        if (credential.isLocked()) {
            outboxPublisher.publish(TransactionalOutboxPublisher.AGGREGATE_TYPE, credential.getId().toString(),
                    CredentialEvents.CREDENTIAL_LOCKED, new CredentialEvents.CredentialLocked(
                            credential.getId(), credential.getLockoutEnd()));
            log.warn("account locked for user {} until {}", credential.getId(), credential.getLockoutEnd());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistUnlock(Credential credential) {
        credential.unlock();
        credentialRepository.saveAndFlush(credential);
        outboxPublisher.publish(TransactionalOutboxPublisher.AGGREGATE_TYPE, credential.getId().toString(),
                CredentialEvents.CREDENTIAL_UNLOCKED, new CredentialEvents.CredentialUnlocked(credential.getId()));
    }
}
