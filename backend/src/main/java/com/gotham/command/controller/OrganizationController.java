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
import com.gotham.command.dto.organization.AddOrganizationMemberRequest;
import com.gotham.command.dto.organization.CreateOrganizationRequest;
import com.gotham.command.dto.organization.OrganizationDto;
import com.gotham.command.dto.organization.OrganizationMemberDto;
import com.gotham.command.dto.organization.UpdateOrganizationMemberRequest;
import com.gotham.command.dto.organization.UpdateOrganizationRequest;
import com.gotham.command.entity.Organization;
import com.gotham.command.entity.OrganizationMember;
import com.gotham.command.entity.User;
import com.gotham.command.security.RbacAuthorizationService;
import com.gotham.command.service.OrganizationService;
import com.gotham.command.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;
    private final RbacAuthorizationService authorizationService;

    public OrganizationController(
        OrganizationService organizationService,
        UserService userService,
        RbacAuthorizationService authorizationService
    ) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationDto>> listOrganizations(Authentication authentication) {
        UUID userId = authorizationService.getAuthenticatedUserId(authentication);
        List<Organization> orgs;
        if (authorizationService.isSuperAdmin(userId)) {
            orgs = organizationService.findAll();
        } else {
            orgs = organizationService.findByUserId(userId);
        }
        return ResponseEntity.ok(orgs.stream().map(OrganizationDto::fromEntity).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.canViewOrganization(authentication, #id)")
    public ResponseEntity<OrganizationDto> getOrganization(@PathVariable UUID id) {
        Organization org = organizationService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + id));
        return ResponseEntity.ok(OrganizationDto.fromEntity(org));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationDto> createOrganization(
        @Valid @RequestBody CreateOrganizationRequest request,
        Authentication authentication
    ) {
        UUID userId = authorizationService.getAuthenticatedUserId(authentication);
        User creator = userService.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Organization org = new Organization();
        org.setName(request.name());
        org.setSlug(request.slug());
        org.setDescription(request.description());

        Organization created = organizationService.createOrganization(org, creator);
        return ResponseEntity.created(URI.create("/api/organizations/" + created.getId()))
            .body(OrganizationDto.fromEntity(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageOrganization(authentication, #id)")
    public ResponseEntity<OrganizationDto> updateOrganization(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateOrganizationRequest request
    ) {
        Organization updated = organizationService.updateOrganization(id, request.name(), request.description());
        return ResponseEntity.ok(OrganizationDto.fromEntity(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageOrganization(authentication, #id)")
    public ResponseEntity<Void> deleteOrganization(@PathVariable UUID id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@authorizationService.canViewOrganization(authentication, #id)")
    public ResponseEntity<List<OrganizationMemberDto>> getMembers(@PathVariable UUID id) {
        List<OrganizationMember> members = organizationService.getMembers(id);
        return ResponseEntity.ok(members.stream().map(OrganizationMemberDto::fromEntity).toList());
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@authorizationService.canManageOrganizationMembers(authentication, #id)")
    public ResponseEntity<OrganizationMemberDto> addMember(
        @PathVariable UUID id,
        @Valid @RequestBody AddOrganizationMemberRequest request,
        Authentication authentication
    ) {
        UUID operatorUserId = authorizationService.getAuthenticatedUserId(authentication);
        boolean isSuperAdmin = authorizationService.isSuperAdmin(operatorUserId);

        OrganizationMember member = organizationService.addMember(id, request.userId(), request.role(), isSuperAdmin);
        return ResponseEntity.created(URI.create("/api/organizations/" + id + "/members/" + member.getUser().getId()))
            .body(OrganizationMemberDto.fromEntity(member));
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("@authorizationService.canManageOrganizationMembers(authentication, #id)")
    public ResponseEntity<OrganizationMemberDto> updateMemberRole(
        @PathVariable UUID id,
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateOrganizationMemberRequest request,
        Authentication authentication
    ) {
        UUID operatorUserId = authorizationService.getAuthenticatedUserId(authentication);
        boolean isSuperAdmin = authorizationService.isSuperAdmin(operatorUserId);

        OrganizationMember member = organizationService.updateMemberRole(id, userId, request.role(), operatorUserId, isSuperAdmin);
        return ResponseEntity.ok(OrganizationMemberDto.fromEntity(member));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@authorizationService.canManageOrganizationMembers(authentication, #id)")
    public ResponseEntity<Void> removeMember(
        @PathVariable UUID id,
        @PathVariable UUID userId,
        Authentication authentication
    ) {
        UUID operatorUserId = authorizationService.getAuthenticatedUserId(authentication);
        boolean isSuperAdmin = authorizationService.isSuperAdmin(operatorUserId);

        organizationService.removeMember(id, userId, operatorUserId, isSuperAdmin);
        return ResponseEntity.noContent().build();
    }
}
