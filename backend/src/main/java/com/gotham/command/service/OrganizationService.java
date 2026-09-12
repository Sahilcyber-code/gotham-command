package com.gotham.command.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.config.ResourceNotFoundException;
import com.gotham.command.entity.Organization;
import com.gotham.command.entity.OrganizationMember;
import com.gotham.command.entity.OrganizationRole;
import com.gotham.command.entity.Project;
import com.gotham.command.entity.User;
import com.gotham.command.repository.OrganizationMemberRepository;
import com.gotham.command.repository.OrganizationRepository;
import com.gotham.command.repository.ProjectMemberRepository;
import com.gotham.command.repository.ProjectRepository;
import com.gotham.command.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public OrganizationService(
        OrganizationRepository organizationRepository,
        OrganizationMemberRepository memberRepository,
        UserRepository userRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional
    public Organization createOrganization(Organization organization, User creator) {
        if (organizationRepository.existsBySlug(organization.getSlug())) {
            throw new IllegalArgumentException("Organization slug already exists: " + organization.getSlug());
        }
        Organization savedOrg = organizationRepository.save(organization);

        OrganizationMember member = new OrganizationMember();
        member.setOrganization(savedOrg);
        member.setUser(creator);
        member.setRole(OrganizationRole.ORG_ADMIN);
        memberRepository.save(member);

        return savedOrg;
    }

    public List<Organization> findAll() {
        return organizationRepository.findAll();
    }

    public Optional<Organization> findById(UUID id) {
        return organizationRepository.findById(id);
    }

    public Optional<Organization> findBySlug(String slug) {
        return organizationRepository.findBySlug(slug);
    }

    public List<Organization> findByUserId(UUID userId) {
        return organizationRepository.findByUserId(userId);
    }

    @Transactional
    public Organization updateOrganization(UUID id, String name, String description) {
        Organization organization = organizationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + id));

        if (name != null && !name.isBlank()) {
            organization.setName(name);
        }
        if (description != null) {
            organization.setDescription(description);
        }
        return organizationRepository.save(organization);
    }

    @Transactional
    public void deleteOrganization(UUID id) {
        if (!organizationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Organization not found: " + id);
        }
        organizationRepository.deleteById(id);
    }

    @Transactional
    public OrganizationMember addMember(UUID organizationId, UUID userId, OrganizationRole role, boolean isOperatorSuperAdmin) {
        Organization organization = organizationRepository.findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (memberRepository.existsByOrganizationIdAndUserId(organizationId, userId)) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }

        if (role == OrganizationRole.SUPER_ADMIN && !isOperatorSuperAdmin) {
            throw new IllegalArgumentException("Only SUPER_ADMIN can grant system-wide SUPER_ADMIN privileges");
        }

        OrganizationMember member = new OrganizationMember();
        member.setOrganization(organization);
        member.setUser(user);
        member.setRole(role != null ? role : OrganizationRole.MEMBER);

        return memberRepository.save(member);
    }

    @Transactional
    public OrganizationMember updateMemberRole(
        UUID organizationId,
        UUID targetUserId,
        OrganizationRole newRole,
        UUID operatorUserId,
        boolean isOperatorSuperAdmin
    ) {
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization member not found"));

        if (operatorUserId != null && operatorUserId.equals(targetUserId) && !isOperatorSuperAdmin) {
            throw new IllegalStateException("Users cannot modify their own organization role");
        }

        if (newRole == OrganizationRole.SUPER_ADMIN && !isOperatorSuperAdmin) {
            throw new IllegalArgumentException("Only SUPER_ADMIN can grant system-wide SUPER_ADMIN privileges");
        }

        // Final ORG_ADMIN protection
        if (member.getRole() == OrganizationRole.ORG_ADMIN && newRole != OrganizationRole.ORG_ADMIN && !isOperatorSuperAdmin) {
            long adminCount = memberRepository.findByOrganizationId(organizationId).stream()
                .filter(m -> m.getRole() == OrganizationRole.ORG_ADMIN)
                .count();
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot demote the final ORG_ADMIN of an organization");
            }
        }

        member.setRole(newRole);
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(
        UUID organizationId,
        UUID targetUserId,
        UUID operatorUserId,
        boolean isOperatorSuperAdmin
    ) {
        OrganizationMember member = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization member not found"));

        // Final ORG_ADMIN protection
        if (member.getRole() == OrganizationRole.ORG_ADMIN && !isOperatorSuperAdmin) {
            long adminCount = memberRepository.findByOrganizationId(organizationId).stream()
                .filter(m -> m.getRole() == OrganizationRole.ORG_ADMIN)
                .count();
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot remove the final ORG_ADMIN of an organization");
            }
        }

        memberRepository.deleteByOrganizationIdAndUserId(organizationId, targetUserId);

        // Cascade cleanup: remove user from any project in this organization
        List<Project> orgProjects = projectRepository.findByOrganizationId(organizationId);
        for (Project project : orgProjects) {
            projectMemberRepository.deleteByProjectIdAndUserId(project.getId(), targetUserId);
        }
    }

    public List<OrganizationMember> getMembers(UUID organizationId) {
        return memberRepository.findByOrganizationId(organizationId);
    }
}
