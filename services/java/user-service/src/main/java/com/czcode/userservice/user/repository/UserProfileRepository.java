package com.czcode.userservice.user.repository;

import com.czcode.userservice.user.entity.UserProfileEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

  Optional<UserProfileEntity> findByIdAndDeletedAtIsNull(UUID id);

  List<UserProfileEntity> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  List<UserProfileEntity> findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(short status);
}
