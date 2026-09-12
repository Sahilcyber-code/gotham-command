package com.gotham.command.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.Comment;
import com.gotham.command.entity.Issue;
import com.gotham.command.entity.OrganizationMember;
import com.gotham.command.entity.OrganizationRole;
import com.gotham.command.entity.Project;
import com.gotham.command.entity.ProjectMember;
import com.gotham.command.entity.ProjectRole;
import com.gotham.command.repository.CommentRepository;
import com.gotham.command.repository.IssueRepository;
import com.gotham.command.repository.OrganizationMemberRepository;
import com.gotham.command.repository.OrganizationRepository;
import com.gotham.command.repository.ProjectMemberRepository;
import com.gotham.command.repository.ProjectRepository;
import com.gotham.command.repository.UserRepository;

@Service("authorizationService")
@Transactional(readOnly = true)
public class RbacAuthorizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IssueRepository issueRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public RbacAuthorizationService(
        OrganizationRepository organizationRepository,
        OrganizationMemberRepository organizationMemberRepository,
        ProjectRepository projectRepository,
        ProjectMemberRepository projectMemberRepository,
        IssueRepository issueRepository,
        CommentRepository commentRepository,
        UserRepository userRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.issueRepository = issueRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    public UUID getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof SecurityUserPrincipal principal) {
            return principal.getId();
        }
        if (authentication.getPrincipal() instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isSuperAdmin(UUID userId) {
        if (userId == null) return false;
        return organizationMemberRepository.findByUserId(userId).stream()
            .anyMatch(m -> m.getRole() == OrganizationRole.SUPER_ADMIN);
    }

    public Optional<OrganizationRole> getOrganizationRole(UUID userId, UUID organizationId) {
        if (userId == null || organizationId == null) return Optional.empty();
        return organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
            .map(OrganizationMember::getRole);
    }

    public boolean isOrganizationMember(UUID userId, UUID organizationId) {
        if (userId == null || organizationId == null) return false;
        return organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
    }

    public boolean isOrganizationAdmin(UUID userId, UUID organizationId) {
        if (userId == null || organizationId == null) return false;
        if (isSuperAdmin(userId)) return true;
        return getOrganizationRole(userId, organizationId)
            .filter(r -> r == OrganizationRole.ORG_ADMIN)
            .isPresent();
    }

    public boolean canViewOrganization(Authentication auth, UUID organizationId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || organizationId == null) return false;
        if (isSuperAdmin(userId)) return true;
        return isOrganizationMember(userId, organizationId);
    }

    public boolean canManageOrganization(Authentication auth, UUID organizationId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || organizationId == null) return false;
        return isOrganizationAdmin(userId, organizationId);
    }

    public boolean canManageOrganizationMembers(Authentication auth, UUID organizationId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || organizationId == null) return false;
        return isOrganizationAdmin(userId, organizationId);
    }

    public boolean canCreateProject(Authentication auth, UUID organizationId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || organizationId == null) return false;
        if (isSuperAdmin(userId)) return true;
        return getOrganizationRole(userId, organizationId)
            .filter(r -> r == OrganizationRole.ORG_ADMIN || r == OrganizationRole.PROJECT_MANAGER)
            .isPresent();
    }

    public Optional<ProjectRole> getProjectRole(UUID userId, UUID projectId) {
        if (userId == null || projectId == null) return Optional.empty();
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .map(ProjectMember::getRole);
    }

    public boolean canViewProject(Authentication auth, UUID projectId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || projectId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) return false;
        Project project = projectOpt.get();

        // If user is ORG_ADMIN of owning organization
        if (isOrganizationAdmin(userId, project.getOrganization().getId())) {
            return true;
        }

        // If user is project member
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            return true;
        }

        // If user is organization member
        return isOrganizationMember(userId, project.getOrganization().getId());
    }

    public boolean canManageProject(Authentication auth, UUID projectId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || projectId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) return false;
        Project project = projectOpt.get();

        if (isOrganizationAdmin(userId, project.getOrganization().getId())) {
            return true;
        }

        return getProjectRole(userId, projectId)
            .filter(r -> r == ProjectRole.PROJECT_MANAGER)
            .isPresent();
    }

    public boolean canDeleteProject(Authentication auth, UUID projectId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || projectId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) return false;
        Project project = projectOpt.get();

        return isOrganizationAdmin(userId, project.getOrganization().getId());
    }

    public boolean canManageProjectMembers(Authentication auth, UUID projectId) {
        return canManageProject(auth, projectId);
    }

    public boolean canViewIssue(Authentication auth, UUID issueId) {
        if (issueId == null) return false;
        Optional<Issue> issueOpt = issueRepository.findById(issueId);
        if (issueOpt.isEmpty()) return false;
        return canViewProject(auth, issueOpt.get().getProject().getId());
    }

    public boolean canCreateIssue(Authentication auth, UUID projectId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || projectId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) return false;
        Project project = projectOpt.get();

        if (isOrganizationAdmin(userId, project.getOrganization().getId())) {
            return true;
        }

        Optional<ProjectRole> projectRole = getProjectRole(userId, projectId);
        if (projectRole.isPresent()) {
            return projectRole.get() != ProjectRole.VIEWER;
        }

        return isOrganizationMember(userId, project.getOrganization().getId());
    }

    public boolean canEditIssue(Authentication auth, UUID issueId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || issueId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Issue> issueOpt = issueRepository.findById(issueId);
        if (issueOpt.isEmpty()) return false;
        Issue issue = issueOpt.get();

        if (isOrganizationAdmin(userId, issue.getProject().getOrganization().getId())) {
            return true;
        }

        Optional<ProjectRole> projectRole = getProjectRole(userId, issue.getProject().getId());
        if (projectRole.isPresent()) {
            return projectRole.get() != ProjectRole.VIEWER;
        }

        return isOrganizationMember(userId, issue.getProject().getOrganization().getId());
    }

    public boolean canDeleteIssue(Authentication auth, UUID issueId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || issueId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Issue> issueOpt = issueRepository.findById(issueId);
        if (issueOpt.isEmpty()) return false;
        Issue issue = issueOpt.get();

        if (isOrganizationAdmin(userId, issue.getProject().getOrganization().getId())) {
            return true;
        }

        if (getProjectRole(userId, issue.getProject().getId()).filter(r -> r == ProjectRole.PROJECT_MANAGER).isPresent()) {
            return true;
        }

        // Reporter can delete own issue
        return issue.getReporter() != null && issue.getReporter().getId().equals(userId);
    }

    public boolean canCreateSubtask(Authentication auth, UUID parentIssueId) {
        return canEditIssue(auth, parentIssueId);
    }

    public boolean canViewComments(Authentication auth, UUID issueId) {
        return canViewIssue(auth, issueId);
    }

    public boolean canCreateComment(Authentication auth, UUID issueId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || issueId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Issue> issueOpt = issueRepository.findById(issueId);
        if (issueOpt.isEmpty()) return false;
        Issue issue = issueOpt.get();

        Optional<ProjectRole> projectRole = getProjectRole(userId, issue.getProject().getId());
        if (projectRole.isPresent() && projectRole.get() == ProjectRole.VIEWER) {
            return false;
        }

        return canViewProject(auth, issue.getProject().getId());
    }

    public boolean canDeleteComment(Authentication auth, UUID commentId) {
        UUID userId = getAuthenticatedUserId(auth);
        if (userId == null || commentId == null) return false;
        if (isSuperAdmin(userId)) return true;

        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) return false;
        Comment comment = commentOpt.get();

        UUID orgId = comment.getIssue().getProject().getOrganization().getId();
        if (isOrganizationAdmin(userId, orgId)) {
            return true;
        }

        UUID projectId = comment.getIssue().getProject().getId();
        if (getProjectRole(userId, projectId).filter(r -> r == ProjectRole.PROJECT_MANAGER).isPresent()) {
            return true;
        }

        return comment.getAuthor() != null && comment.getAuthor().getId().equals(userId);
    }
}
