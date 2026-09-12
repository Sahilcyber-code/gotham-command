package com.gotham.command.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gotham.command.entity.Project;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByOrganizationId(UUID organizationId);

    Optional<Project> findByOrganizationIdAndProjectKey(UUID organizationId, String projectKey);

    Optional<Project> findByOrganizationIdAndProjectKeyIgnoreCase(UUID organizationId, String projectKey);

    boolean existsByOrganizationIdAndProjectKey(UUID organizationId, String projectKey);

    boolean existsByOrganizationIdAndProjectKeyIgnoreCase(UUID organizationId, String projectKey);

    @Query("SELECT pm.project FROM ProjectMember pm WHERE pm.user.id = :userId")
    List<Project> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT pm.project FROM ProjectMember pm WHERE pm.project.organization.id = :organizationId AND pm.user.id = :userId")
    List<Project> findByOrganizationIdAndUserId(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);
}
