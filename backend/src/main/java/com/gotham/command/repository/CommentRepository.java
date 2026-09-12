package com.gotham.command.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gotham.command.entity.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByIssueIdOrderByCreatedAtAsc(UUID issueId);

    List<Comment> findByAuthorId(UUID authorId);

    long countByIssueId(UUID issueId);
}
