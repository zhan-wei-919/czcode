package com.czcode.authservice.auth.repository;

import com.czcode.authservice.auth.entity.AuthRefreshTokenEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, UUID> {

  Optional<AuthRefreshTokenEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);

  Optional<AuthRefreshTokenEntity> findByAccountIdAndTokenHashAndRevokedAtIsNull(UUID accountId, String tokenHash);

  List<AuthRefreshTokenEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      UPDATE AuthRefreshTokenEntity token
      SET token.revokedAt = :revokedAt
      WHERE token.accountId = :accountId
        AND token.revokedAt IS NULL
      """)
  int revokeAllActiveByAccountId(@Param("accountId") UUID accountId, @Param("revokedAt") Instant revokedAt);

  long countByAccountIdAndRevokedAtIsNull(UUID accountId);
}
