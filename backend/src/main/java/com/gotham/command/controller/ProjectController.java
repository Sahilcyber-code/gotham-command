package com.gotham.command.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gotham.command.config.ResourceNotFoundException;
import com.gotham.command.dto.project.AddProjectMemberRequest;
import com.gotham.command.dto.project.CreateProjectRequest;
import com.gotham.command.dto.project.ProjectDto;
import com.gotham.command.dto.project.ProjectMemberDto;
import com.gotham.command.dto.project.UpdateProjectMemberRequest;
import com.gotham.command.dto.project.UpdateProjectRequest;
import com.gotham.command.entity.Project;
import com.gotham.command.entity.ProjectMember;
import com.gotham.command.entity.User;
import com.gotham.command.security.RbacAuthorizationService;
import com.gotham.command.service.ProjectService;
import com.gotham.command.service.UserService;

import jakarta.validation.Valid;

@RestController
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;
    private final RbacAuthorizationService authorizationService;

    public ProjectController(
        ProjectService projectService,
        UserService userService,
        RbacAuthorizationService authorizationService
    ) {
        this.projectService = projectService;
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/organizations/{organizationId}/projects")
    @PreAuthorize("@authorizationService.canViewOrganization(authentication, #organizationId)")
    public ResponseEntity<List<ProjectDto>> getProjectsByOrganization(@PathVariable UUID organizationId) {
        List<Project> projects = projectService.findByOrganizationId(organizationId);
        return ResponseEntity.ok(projects.stream().map(ProjectDto::fromEntity).toList());
    }

    @PostMapping("/api/organizations/{organizationId}/projects")
    @PreAuthorize("@authorizationService.canCreateProject(authentication, #organizationId)")
    public ResponseEntity<ProjectDto> createProject(
        @PathVariable UUID organizationId,
        @Valid @RequestBody CreateProjectRequest request,
        Authentication authentication
    ) {
        UUID userId = authorizationService.getAuthenticatedUserId(authentication);
        User creator = userService.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Project project = new Project();
        project.setName(request.name());
        project.setProjectKey(request.projectKey().toUpperCase().trim());
        project.setDescription(request.description());

        Project created = projectService.createProject(organizationId, project, creator);
        return ResponseEntity.created(URI.create("/api/projects/" + created.getId()))
            .body(ProjectDto.fromEntity(created));
    }

    @GetMapping("/api/projects/{id}")
    @PreAuthorize("@authorizationService.canViewProject(authentication, #id)")
    public ResponseEntity<ProjectDto> getProject(@PathVariable UUID id) {
        Project project = projectService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        return ResponseEntity.ok(ProjectDto.fromEntity(project));
    }

    @PutMapping("/api/projects/{id}")
    @PreAuthorize("@authorizationService.canManageProject(authentication, #id)")
    public ResponseEntity<ProjectDto> updateProject(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateProjectRequest request
    ) {
        Project updated = projectService.updateProject(id, request.name(), request.description());
        return ResponseEntity.ok(ProjectDto.fromEntity(updated));
    }

    @DeleteMapping("/api/projects/{id}")
    @PreAuthorize("@authorizationService.canDeleteProject(authentication, #id)")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/projects/{id}/members")
    @PreAuthorize("@authorizationService.canViewProject(authentication, #id)")
    public ResponseEntity<List<ProjectMemberDto>> getProjectMembers(@PathVariable UUID id) {
        List<ProjectMember> members = projectService.getMembers(id);
        return ResponseEntity.ok(members.stream().map(ProjectMemberDto::fromEntity).toList());
    }

    @PostMapping("/api/projects/{id}/members")
    @PreAuthorize("@authorizationService.canManageProjectMembers(authentication, #id)")
    public ResponseEntity<ProjectMemberDto> addProjectMember(
        @PathVariable UUID id,
        @Valid @RequestBody AddProjectMemberRequest request
    ) {
        ProjectMember member = projectService.addMember(id, request.userId(), request.role());
        return ResponseEntity.created(URI.create("/api/projects/" + id + "/members/" + member.getUser().getId()))
            .body(ProjectMemberDto.fromEntity(member));
    }

    @PutMapping("/api/projects/{id}/members/{userId}")
    @PreAuthorize("@authorizationService.canManageProjectMembers(authentication, #id)")
    public ResponseEntity<ProjectMemberDto> updateProjectMemberRole(
        @PathVariable UUID id,
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateProjectMemberRequest request,
        Authentication authentication
    ) {
        UUID operatorUserId = authorizationService.getAuthenticatedUserId(authentication);
        boolean isSuperAdmin = authorizationService.isSuperAdmin(operatorUserId);

        ProjectMember member = projectService.updateMemberRole(id, userId, request.role(), operatorUserId, isSuperAdmin);
        return ResponseEntity.ok(ProjectMemberDto.fromEntity(member));
    }

    @DeleteMapping("/api/projects/{id}/members/{userId}")
    @PreAuthorize("@authorizationService.canManageProjectMembers(authentication, #id)")
    public ResponseEntity<Void> removeProjectMember(
        @PathVariable UUID id,
        @PathVariable UUID userId
    ) {
        projectService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
