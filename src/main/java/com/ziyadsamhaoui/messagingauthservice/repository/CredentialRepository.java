package com.ziyadsamhaoui.messagingauthservice.repository;

import com.ziyadsamhaoui.messagingauthservice.model.Credential;
import com.ziyadsamhaoui.messagingauthservice.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Modifying
    @Query("update Credential c set c.role = :role where c.id = :id")
    int updateRole(@Param("id") UUID id, @Param("role") Role role);
}
