package com.gotham.command.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.dto.issue.UpdateIssueRequest;
import com.gotham.command.entity.Issue;
import com.gotham.command.entity.IssuePriority;
import com.gotham.command.entity.IssueStatus;
import com.gotham.command.entity.IssueType;
import com.gotham.command.entity.Project;
import com.gotham.command.entity.User;
import com.gotham.command.config.ResourceNotFoundException;
import com.gotham.command.repository.IssueRepository;
import com.gotham.command.repository.ProjectRepository;
import com.gotham.command.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public IssueService(
        IssueRepository issueRepository,
        ProjectRepository projectRepository,
        UserRepository userRepository
    ) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Issue createIssue(UUID projectId, Issue issue, UUID reporterId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        issue.setProject(project);

        if (reporterId != null) {
            User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found: " + reporterId));
            issue.setReporter(reporter);
        }

        if (issue.getIssueKey() == null || issue.getIssueKey().isBlank()) {
            long count = issueRepository.countByProjectId(projectId) + 1;
            issue.setIssueKey(project.getProjectKey() + "-" + count);
        }

        if (issue.getStatus() == null) {
            issue.setStatus(IssueStatus.TODO);
        }
        if (issue.getPriority() == null) {
            issue.setPriority(IssuePriority.MEDIUM);
        }
        if (issue.getSortOrder() == null) {
            issue.setSortOrder(0.0);
        }

        return issueRepository.save(issue);
    }

    @Transactional
    public Issue createSubtask(UUID parentIssueId, Issue subtask, UUID reporterId) {
        Issue parent = issueRepository.findById(parentIssueId)
            .orElseThrow(() -> new ResourceNotFoundException("Parent issue not found: " + parentIssueId));

        subtask.setProject(parent.getProject());
        subtask.setParentIssue(parent);
        subtask.setIssueType(IssueType.SUBTASK);

        if (reporterId != null) {
            User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found: " + reporterId));
            subtask.setReporter(reporter);
        }

        if (subtask.getIssueKey() == null || subtask.getIssueKey().isBlank()) {
            long count = issueRepository.countByProjectId(parent.getProject().getId()) + 1;
            subtask.setIssueKey(parent.getProject().getProjectKey() + "-" + count);
        }

        if (subtask.getStatus() == null) {
            subtask.setStatus(IssueStatus.TODO);
        }
        if (subtask.getPriority() == null) {
            subtask.setPriority(IssuePriority.MEDIUM);
        }
        if (subtask.getSortOrder() == null) {
            subtask.setSortOrder(0.0);
        }

        Issue savedSubtask = issueRepository.save(subtask);
        parent.getSubtasks().add(savedSubtask);
        return savedSubtask;
    }

    public Optional<Issue> findById(UUID id) {
        return issueRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Issue> findByProjectId(UUID projectId) {
        List<Issue> issues = issueRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        issues.forEach(issue -> issue.getLabels().size());
        return issues;
    }

    public List<Issue> findByProjectIdAndStatus(UUID projectId, IssueStatus status) {
        return issueRepository.findByProjectIdAndStatus(projectId, status);
    }

    public Optional<Issue> findByProjectAndKey(UUID projectId, String issueKey) {
        return issueRepository.findByProjectIdAndIssueKey(projectId, issueKey);
    }

    public List<Issue> getSubtasks(UUID parentIssueId) {
        return issueRepository.findByParentIssueId(parentIssueId);
    }

    @Transactional
    public Issue updateIssue(UUID issueId, UpdateIssueRequest request) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        if (request.title() != null && !request.title().isBlank()) {
            issue.setTitle(request.title());
        }
        if (request.description() != null) {
            issue.setDescription(request.description());
        }
        if (request.issueType() != null) {
            issue.setIssueType(request.issueType());
        }
        if (request.status() != null) {
            issue.setStatus(request.status());
            if (request.status() == IssueStatus.DONE) {
                issue.setResolvedAt(Instant.now());
            } else {
                issue.setResolvedAt(null);
            }
        }
        if (request.priority() != null) {
            issue.setPriority(request.priority());
        }
        if (request.assigneeId() != null) {
            User assignee = userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found: " + request.assigneeId()));
            issue.setAssignee(assignee);
        }
        if (request.labels() != null) {
            issue.setLabels(new java.util.LinkedHashSet<>(request.labels()));
        }
        if (request.sortOrder() != null) {
            issue.setSortOrder(request.sortOrder());
        }
        if (request.dueDate() != null) {
            issue.setDueDate(request.dueDate());
        }

        return issueRepository.save(issue);
    }

    @Transactional
    public void deleteIssue(UUID issueId) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResourceNotFoundException("Issue not found: " + issueId);
        }
        issueRepository.deleteById(issueId);
    }

    @Transactional
    public Issue updateStatus(UUID issueId, IssueStatus newStatus) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        issue.setStatus(newStatus);
        if (newStatus == IssueStatus.DONE) {
            issue.setResolvedAt(Instant.now());
        } else {
            issue.setResolvedAt(null);
        }

        return issueRepository.save(issue);
    }

    @Transactional
    public Issue assignIssue(UUID issueId, UUID assigneeId) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        if (assigneeId != null) {
            User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found: " + assigneeId));
            issue.setAssignee(assignee);
        } else {
            issue.setAssignee(null);
        }

        return issueRepository.save(issue);
    }

    @Transactional
    public Issue updateSortOrder(UUID issueId, Double newSortOrder) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + issueId));

        issue.setSortOrder(newSortOrder != null ? newSortOrder : 0.0);
        return issueRepository.save(issue);
    }
}

