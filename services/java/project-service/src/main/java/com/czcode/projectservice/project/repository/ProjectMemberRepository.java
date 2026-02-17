package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectMemberEntity;
import com.czcode.projectservice.project.entity.ProjectMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {

  List<ProjectMemberEntity> findByIdProjectIdOrderByJoinedAtAsc(UUID projectId);

  List<ProjectMemberEntity> findByIdProjectIdAndStatusOrderByJoinedAtAsc(UUID projectId, short status);

  Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserId(UUID projectId, UUID userId);

  Optional<ProjectMemberEntity> findByIdProjectIdAndIdUserIdAndStatus(UUID projectId, UUID userId, short status);

  List<ProjectMemberEntity> findByIdUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, short status);
}
