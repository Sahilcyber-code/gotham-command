package com.gotham.command.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gotham.command.entity.Issue;
import com.gotham.command.entity.IssueStatus;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

    List<Issue> findByProjectId(UUID projectId);

    List<Issue> findByProjectIdAndStatus(UUID projectId, IssueStatus status);

    Optional<Issue> findByProjectIdAndIssueKey(UUID projectId, String issueKey);

    List<Issue> findByAssigneeId(UUID assigneeId);

    List<Issue> findByReporterId(UUID reporterId);

    List<Issue> findByParentIssueId(UUID parentIssueId);

    List<Issue> findByProjectIdAndParentIssueIsNull(UUID projectId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"project", "reporter", "assignee"})
    List<Issue> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    boolean existsByProjectIdAndIssueKey(UUID projectId, String issueKey);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, IssueStatus status);
}
