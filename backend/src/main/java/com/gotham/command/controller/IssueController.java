package com.gotham.command.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gotham.command.config.ResourceNotFoundException;
import com.gotham.command.dto.comment.CommentDto;
import com.gotham.command.dto.comment.CreateCommentRequest;
import com.gotham.command.dto.issue.CreateIssueRequest;
import com.gotham.command.dto.issue.IssueDto;
import com.gotham.command.dto.issue.UpdateAssigneeRequest;
import com.gotham.command.dto.issue.UpdateIssueRequest;
import com.gotham.command.dto.issue.UpdateSortOrderRequest;
import com.gotham.command.dto.issue.UpdateStatusRequest;
import com.gotham.command.entity.Comment;
import com.gotham.command.entity.Issue;
import com.gotham.command.security.RbacAuthorizationService;
import com.gotham.command.service.CommentService;
import com.gotham.command.service.IssueService;
import com.gotham.command.service.UserService;

import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class IssueController {

    private final IssueService issueService;
    private final CommentService commentService;
    private final UserService userService;
    private final RbacAuthorizationService authorizationService;

    public IssueController(
        IssueService issueService,
        CommentService commentService,
        UserService userService,
        RbacAuthorizationService authorizationService
    ) {
        this.issueService = issueService;
        this.commentService = commentService;
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/projects/{projectId}/issues")
    @PreAuthorize("@authorizationService.canViewProject(authentication, #projectId)")
    @Transactional(readOnly = true)
    public ResponseEntity<List<IssueDto>> getIssuesByProject(@PathVariable UUID projectId) {
        List<Issue> issues = issueService.findByProjectId(projectId);
        return ResponseEntity.ok(issues.stream().map(IssueDto::fromEntity).toList());
    }

    @PostMapping("/api/projects/{projectId}/issues")
    @PreAuthorize("@authorizationService.canCreateIssue(authentication, #projectId)")
    public ResponseEntity<IssueDto> createIssue(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateIssueRequest request,
        Authentication authentication
    ) {
        UUID reporterId = authorizationService.getAuthenticatedUserId(authentication);

        Issue issue = new Issue();
        issue.setTitle(request.title());
        issue.setDescription(request.description());
        issue.setIssueType(request.issueType());
        if (request.status() != null) {
            issue.setStatus(request.status());
        }
        if (request.priority() != null) {
            issue.setPriority(request.priority());
        }
        if (request.sortOrder() != null) {
            issue.setSortOrder(request.sortOrder());
        }
        if (request.dueDate() != null) {
            issue.setDueDate(request.dueDate());
        }
        if (request.assigneeId() != null) {
            userService.findById(request.assigneeId()).ifPresent(issue::setAssignee);
        }
        if (request.labels() != null) {
            issue.setLabels(new LinkedHashSet<>(request.labels()));
        }

        Issue created = issueService.createIssue(projectId, issue, reporterId);
        return ResponseEntity.created(URI.create("/api/issues/" + created.getId()))
            .body(IssueDto.fromEntity(created));
    }

    @GetMapping("/api/issues/{id}")
    @PreAuthorize("@authorizationService.canViewIssue(authentication, #id)")
    public ResponseEntity<IssueDto> getIssue(@PathVariable UUID id) {
        Issue issue = issueService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + id));
        return ResponseEntity.ok(IssueDto.fromEntity(issue));
    }

    @PutMapping("/api/issues/{id}")
    @PreAuthorize("@authorizationService.canEditIssue(authentication, #id)")
    public ResponseEntity<IssueDto> updateIssue(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateIssueRequest request
    ) {
        Issue updated = issueService.updateIssue(id, request);
        return ResponseEntity.ok(IssueDto.fromEntity(updated));
    }

    @DeleteMapping("/api/issues/{id}")
    @PreAuthorize("@authorizationService.canDeleteIssue(authentication, #id)")
    public ResponseEntity<Void> deleteIssue(@PathVariable UUID id) {
        issueService.deleteIssue(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/issues/{id}/subtasks")
    @PreAuthorize("@authorizationService.canCreateSubtask(authentication, #id)")
    public ResponseEntity<IssueDto> createSubtask(
        @PathVariable UUID id,
        @Valid @RequestBody CreateIssueRequest request,
        Authentication authentication
    ) {
        UUID reporterId = authorizationService.getAuthenticatedUserId(authentication);

        Issue subtask = new Issue();
        subtask.setTitle(request.title());
        subtask.setDescription(request.description());
        if (request.status() != null) {
            subtask.setStatus(request.status());
        }
        if (request.priority() != null) {
            subtask.setPriority(request.priority());
        }
        if (request.sortOrder() != null) {
            subtask.setSortOrder(request.sortOrder());
        }
        if (request.dueDate() != null) {
            subtask.setDueDate(request.dueDate());
        }
        if (request.assigneeId() != null) {
            userService.findById(request.assigneeId()).ifPresent(subtask::setAssignee);
        }

        Issue created = issueService.createSubtask(id, subtask, reporterId);
        return ResponseEntity.created(URI.create("/api/issues/" + created.getId()))
            .body(IssueDto.fromEntity(created));
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/api/issues/{id}/status", method = {org.springframework.web.bind.annotation.RequestMethod.PATCH, org.springframework.web.bind.annotation.RequestMethod.PUT})
    @PreAuthorize("@authorizationService.canEditIssue(authentication, #id)")
    public ResponseEntity<IssueDto> updateStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateStatusRequest request
    ) {
        Issue updated = issueService.updateStatus(id, request.status());
        return ResponseEntity.ok(IssueDto.fromEntity(updated));
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/api/issues/{id}/assignee", method = {org.springframework.web.bind.annotation.RequestMethod.PATCH, org.springframework.web.bind.annotation.RequestMethod.PUT})
    @PreAuthorize("@authorizationService.canEditIssue(authentication, #id)")
    public ResponseEntity<IssueDto> updateAssignee(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAssigneeRequest request
    ) {
        Issue updated = issueService.assignIssue(id, request.assigneeId());
        return ResponseEntity.ok(IssueDto.fromEntity(updated));
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/api/issues/{id}/sort-order", method = {org.springframework.web.bind.annotation.RequestMethod.PATCH, org.springframework.web.bind.annotation.RequestMethod.PUT})
    @PreAuthorize("@authorizationService.canEditIssue(authentication, #id)")
    public ResponseEntity<IssueDto> updateSortOrder(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateSortOrderRequest request
    ) {
        Issue updated = issueService.updateSortOrder(id, request.sortOrder());
        return ResponseEntity.ok(IssueDto.fromEntity(updated));
    }

    @GetMapping("/api/issues/{id}/comments")
    @PreAuthorize("@authorizationService.canViewComments(authentication, #id)")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable UUID id) {
        List<Comment> comments = commentService.getCommentsByIssue(id);
        return ResponseEntity.ok(comments.stream().map(CommentDto::fromEntity).toList());
    }

    @PostMapping("/api/issues/{id}/comments")
    @PreAuthorize("@authorizationService.canCreateComment(authentication, #id)")
    public ResponseEntity<CommentDto> addComment(
        @PathVariable UUID id,
        @Valid @RequestBody CreateCommentRequest request,
        Authentication authentication
    ) {
        UUID authorId = authorizationService.getAuthenticatedUserId(authentication);
        Comment created = commentService.addComment(id, authorId, request.body());
        return ResponseEntity.created(URI.create("/api/issues/" + id + "/comments/" + created.getId()))
            .body(CommentDto.fromEntity(created));
    }

    @DeleteMapping("/api/comments/{id}")
    @PreAuthorize("@authorizationService.canDeleteComment(authentication, #id)")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id) {
        commentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }
}
