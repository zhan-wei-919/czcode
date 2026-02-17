package com.czcode.authservice.auth.repository;

import com.czcode.authservice.auth.entity.AuthEmailCodeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEmailCodeRepository extends JpaRepository<AuthEmailCodeEntity, UUID> {

  Optional<AuthEmailCodeEntity> findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
      String email,
      String purpose);
}
