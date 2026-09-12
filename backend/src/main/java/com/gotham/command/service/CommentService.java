package com.gotham.command.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.Comment;
import com.gotham.command.entity.Issue;
import com.gotham.command.entity.User;
import com.gotham.command.repository.CommentRepository;
import com.gotham.command.repository.IssueRepository;
import com.gotham.command.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    public CommentService(
        CommentRepository commentRepository,
        IssueRepository issueRepository,
        UserRepository userRepository
    ) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Comment addComment(UUID issueId, UUID authorId, String body) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        User author = userRepository.findById(authorId)
            .orElseThrow(() -> new IllegalArgumentException("Author not found: " + authorId));

        Comment comment = new Comment();
        comment.setIssue(issue);
        comment.setAuthor(author);
        comment.setBody(body);

        return commentRepository.save(comment);
    }

    public List<Comment> getCommentsByIssue(UUID issueId) {
        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    public List<Comment> getCommentsByAuthor(UUID authorId) {
        return commentRepository.findByAuthorId(authorId);
    }

    public Optional<Comment> findById(UUID id) {
        return commentRepository.findById(id);
    }

    @Transactional
    public void deleteComment(UUID commentId) {
        commentRepository.deleteById(commentId);
    }
}
