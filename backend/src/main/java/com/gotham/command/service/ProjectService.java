package com.gotham.command.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.config.ResourceNotFoundException;
import com.gotham.command.entity.Organization;
import com.gotham.command.entity.Project;
import com.gotham.command.entity.ProjectMember;
import com.gotham.command.entity.ProjectRole;
import com.gotham.command.entity.User;
import com.gotham.command.repository.OrganizationMemberRepository;
import com.gotham.command.repository.OrganizationRepository;
import com.gotham.command.repository.ProjectMemberRepository;
import com.gotham.command.repository.ProjectRepository;
import com.gotham.command.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;

    public ProjectService(
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        OrganizationRepository organizationRepository,
        OrganizationMemberRepository organizationMemberRepository,
        UserRepository userRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Project createProject(UUID organizationId, Project project, User creator) {
        Organization organization = organizationRepository.findById(organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));

        if (projectRepository.existsByOrganizationIdAndProjectKey(organizationId, project.getProjectKey())) {
            throw new IllegalArgumentException("Project key already exists in organization: " + project.getProjectKey());
        }

        project.setOrganization(organization);
        Project savedProject = projectRepository.save(project);

        ProjectMember member = new ProjectMember();
        member.setProject(savedProject);
        member.setUser(creator);
        member.setRole(ProjectRole.PROJECT_MANAGER);
        projectMemberRepository.save(member);

        return savedProject;
    }

    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    public Optional<Project> findById(UUID id) {
        return projectRepository.findById(id);
    }

    public List<Project> findByOrganizationId(UUID organizationId) {
        return projectRepository.findByOrganizationId(organizationId);
    }

    public Optional<Project> findByOrganizationAndKey(UUID organizationId, String projectKey) {
        return projectRepository.findByOrganizationIdAndProjectKey(organizationId, projectKey);
    }

    public List<Project> findByUserId(UUID userId) {
        return projectRepository.findByUserId(userId);
    }

    @Transactional
    public Project updateProject(UUID id, String name, String description) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        if (name != null && !name.isBlank()) {
            project.setName(name);
        }
        if (description != null) {
            project.setDescription(description);
        }

        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }

    @Transactional
    public ProjectMember addMember(UUID projectId, UUID userId, ProjectRole role) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Integrity rule: project member must belong to owning organization
        UUID orgId = project.getOrganization().getId();
        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(orgId, userId)) {
            throw new IllegalArgumentException("Project member must belong to the owning organization first");
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new IllegalArgumentException("User is already a member of this project");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(role != null ? role : ProjectRole.MEMBER);

        return projectMemberRepository.save(member);
    }

    @Transactional
    public ProjectMember updateMemberRole(
        UUID projectId,
        UUID targetUserId,
        ProjectRole newRole,
        UUID operatorUserId,
        boolean isSuperAdmin
    ) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Project member not found"));

        if (operatorUserId != null && operatorUserId.equals(targetUserId) && !isSuperAdmin) {
            throw new IllegalStateException("Users cannot modify their own project role");
        }

        member.setRole(newRole);
        return projectMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    public List<ProjectMember> getMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }
}
