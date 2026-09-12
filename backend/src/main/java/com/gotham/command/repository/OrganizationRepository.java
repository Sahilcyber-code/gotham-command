package com.gotham.command.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gotham.command.entity.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    Optional<Organization> findBySlugIgnoreCase(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    @Query("SELECT om.organization FROM OrganizationMember om WHERE om.user.id = :userId")
    List<Organization> findByUserId(@Param("userId") UUID userId);
}
