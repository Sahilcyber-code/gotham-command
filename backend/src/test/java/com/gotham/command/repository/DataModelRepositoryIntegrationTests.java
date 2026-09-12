package com.gotham.command.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.gotham.command.entity.AuthProvider;
import com.gotham.command.entity.Comment;
import com.gotham.command.entity.Issue;
import com.gotham.command.entity.IssuePriority;
import com.gotham.command.entity.IssueStatus;
import com.gotham.command.entity.IssueType;
import com.gotham.command.entity.Organization;
import com.gotham.command.entity.OrganizationMember;
import com.gotham.command.entity.OrganizationRole;
import com.gotham.command.entity.Project;
import com.gotham.command.entity.ProjectMember;
import com.gotham.command.entity.ProjectRole;
import com.gotham.command.entity.RefreshToken;
import com.gotham.command.entity.User;
import com.gotham.command.service.IssueService;
import com.gotham.command.service.OrganizationService;
import com.gotham.command.service.ProjectService;
import com.gotham.command.service.RefreshTokenService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataModelRepositoryIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IssueService issueService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @PersistenceContext
    private EntityManager entityManager;

    private User testUser;
    private Organization testOrg;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("batman@gotham.city");
        testUser.setFullName("Bruce Wayne");
        testUser.setUsername("darkknight");
        testUser.setProvider(AuthProvider.LOCAL);
        testUser.setProviderId("local-batman-1");
        testUser.setPasswordHash("$2a$10$encryptedHashExampleValue");
        testUser = userRepository.save(testUser);

        testOrg = new Organization();
        testOrg.setName("Wayne Enterprises");
        testOrg.setSlug("wayne-enterprises");
        testOrg.setDescription("Applied Sciences Division");
        testOrg = organizationRepository.save(testOrg);

        testProject = new Project();
        testProject.setOrganization(testOrg);
        testProject.setName("Gotham Watchtower");
        testProject.setProjectKey("WATCH");
        testProject.setDescription("Orbital monitoring platform");
        testProject = projectRepository.save(testProject);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Should persist and query User with UUID primary key and correct fields")
    void testUserPersistence() {
        Optional<User> found = userRepository.findByEmail("batman@gotham.city");
        assertThat(found).isPresent();
        User u = found.get();
        assertThat(u.getId()).isNotNull();
        assertThat(u.getFullName()).isEqualTo("Bruce Wayne");
        assertThat(u.isActive()).isTrue();
        assertThat(u.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(u.getCreatedAt()).isNotNull();
        assertThat(u.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject duplicate user email with integrity constraint violation")
    void testDuplicateUserEmailConstraint() {
        User duplicate = new User();
        duplicate.setEmail("batman@gotham.city");
        duplicate.setFullName("Imposter Wayne");
        duplicate.setProvider(AuthProvider.LOCAL);
        duplicate.setProviderId("imposter-1");

        userRepository.save(duplicate);
        Exception ex = assertThrows(Exception.class, () -> entityManager.flush());
        assertThat(ex).isInstanceOfAny(
            DataIntegrityViolationException.class,
            org.hibernate.exception.ConstraintViolationException.class
        );
    }

    @Test
    @DisplayName("Should persist Organization and OrganizationMember with roles")
    void testOrganizationAndMembership() {
        User memberUser = new User();
        memberUser.setEmail("lucius@wayne.corp");
        memberUser.setFullName("Lucius Fox");
        memberUser.setProvider(AuthProvider.LOCAL);
        memberUser.setProviderId("fox-1");
        memberUser = userRepository.save(memberUser);

        OrganizationMember member = new OrganizationMember();
        member.setOrganization(testOrg);
        member.setUser(memberUser);
        member.setRole(OrganizationRole.ORG_ADMIN);
        organizationMemberRepository.save(member);

        entityManager.flush();
        entityManager.clear();

        List<OrganizationMember> members = organizationMemberRepository.findByOrganizationId(testOrg.getId());
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getRole()).isEqualTo(OrganizationRole.ORG_ADMIN);
        assertThat(members.get(0).getUser().getEmail()).isEqualTo("lucius@wayne.corp");
    }

    @Test
    @DisplayName("Should reject duplicate organization slug")
    void testDuplicateOrganizationSlugConstraint() {
        Organization duplicate = new Organization();
        duplicate.setName("Another Wayne Corp");
        duplicate.setSlug("wayne-enterprises"); // Duplicate slug

        organizationRepository.save(duplicate);
        Exception ex = assertThrows(Exception.class, () -> entityManager.flush());
        assertThat(ex).isInstanceOfAny(
            DataIntegrityViolationException.class,
            org.hibernate.exception.ConstraintViolationException.class
        );
    }

    @Test
    @DisplayName("Should reject duplicate project key within the same organization")
    void testDuplicateProjectKeyInSameOrgConstraint() {
        Project duplicate = new Project();
        duplicate.setOrganization(testOrg);
        duplicate.setName("Duplicate Project");
        duplicate.setProjectKey("WATCH"); // Same key in same org

        projectRepository.save(duplicate);
        Exception ex = assertThrows(Exception.class, () -> entityManager.flush());
        assertThat(ex).isInstanceOfAny(
            DataIntegrityViolationException.class,
            org.hibernate.exception.ConstraintViolationException.class
        );
    }

    @Test
    @DisplayName("Should allow same project key in different organizations")
    void testSameProjectKeyInDifferentOrgs() {
        Organization secondOrg = new Organization();
        secondOrg.setName("Arkham Asylum");
        secondOrg.setSlug("arkham-asylum");
        secondOrg = organizationRepository.save(secondOrg);

        Project projectInSecondOrg = new Project();
        projectInSecondOrg.setOrganization(secondOrg);
        projectInSecondOrg.setName("Security Protocols");
        projectInSecondOrg.setProjectKey("WATCH"); // Same key, different org

        Project saved = projectRepository.save(projectInSecondOrg);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(projectRepository.findByOrganizationId(secondOrg.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Should persist issue with metadata, status, priority, and reporter/assignee")
    void testIssuePersistence() {
        User robin = new User();
        robin.setEmail("robin@gotham.city");
        robin.setFullName("Tim Drake");
        robin.setProvider(AuthProvider.LOCAL);
        robin.setProviderId("robin-1");
        robin = userRepository.save(robin);

        Issue issue = new Issue();
        issue.setProject(testProject);
        issue.setIssueKey("WATCH-1");
        issue.setTitle("Calibrate Satellite Sensor Array");
        issue.setDescription("Align sensors to Gotham harbor quadrant");
        issue.setIssueType(IssueType.TASK);
        issue.setStatus(IssueStatus.TODO);
        issue.setPriority(IssuePriority.HIGH);
        issue.setReporter(testUser);
        issue.setAssignee(robin);
        issue.setSortOrder(100.0);
        issue.setDueDate(LocalDate.now().plusDays(7));

        Issue saved = issueRepository.save(issue);
        entityManager.flush();
        entityManager.clear();

        Optional<Issue> reloaded = issueRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        Issue i = reloaded.get();
        assertThat(i.getIssueKey()).isEqualTo("WATCH-1");
        assertThat(i.getPriority()).isEqualTo(IssuePriority.HIGH);
        assertThat(i.getIssueType()).isEqualTo(IssueType.TASK);
        assertThat(i.getReporter().getEmail()).isEqualTo("batman@gotham.city");
        assertThat(i.getAssignee().getEmail()).isEqualTo("robin@gotham.city");
        assertThat(i.getDueDate()).isNotNull();
        assertThat(i.getSortOrder()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should support subtask hierarchy with self-referencing parent and cascade")
    void testSubtaskHierarchy() {
        Issue parent = new Issue();
        parent.setProject(testProject);
        parent.setIssueKey("WATCH-10");
        parent.setTitle("Parent Epic Story");
        parent.setIssueType(IssueType.STORY);
        parent.setStatus(IssueStatus.IN_PROGRESS);
        parent.setPriority(IssuePriority.MEDIUM);
        parent = issueRepository.save(parent);

        Issue subtask1 = new Issue();
        subtask1.setProject(testProject);
        subtask1.setIssueKey("WATCH-11");
        subtask1.setTitle("Subtask: Module A");
        subtask1.setIssueType(IssueType.SUBTASK);
        subtask1.setStatus(IssueStatus.TODO);
        subtask1.setPriority(IssuePriority.LOW);
        subtask1.setParentIssue(parent);

        Issue subtask2 = new Issue();
        subtask2.setProject(testProject);
        subtask2.setIssueKey("WATCH-12");
        subtask2.setTitle("Subtask: Module B");
        subtask2.setIssueType(IssueType.SUBTASK);
        subtask2.setStatus(IssueStatus.TODO);
        subtask2.setPriority(IssuePriority.LOWEST);
        subtask2.setParentIssue(parent);

        parent.addSubtask(subtask1);
        parent.addSubtask(subtask2);

        issueRepository.save(parent);
        entityManager.flush();
        entityManager.clear();

        List<Issue> subtasks = issueRepository.findByParentIssueId(parent.getId());
        assertThat(subtasks).hasSize(2);
        assertThat(subtasks).extracting(Issue::getTitle)
            .containsExactlyInAnyOrder("Subtask: Module A", "Subtask: Module B");

        List<Issue> rootIssues = issueRepository.findByProjectIdAndParentIssueIsNull(testProject.getId());
        assertThat(rootIssues).hasSize(1);
        assertThat(rootIssues.get(0).getIssueKey()).isEqualTo("WATCH-10");
    }

    @Test
    @DisplayName("Should persist comments and order by created_at ascending")
    void testCommentsPersistence() {
        Issue issue = new Issue();
        issue.setProject(testProject);
        issue.setIssueKey("WATCH-20");
        issue.setTitle("Security breach reported");
        issue.setIssueType(IssueType.BUG);
        issue.setStatus(IssueStatus.TODO);
        issue.setPriority(IssuePriority.HIGHEST);
        issue = issueRepository.save(issue);

        Comment c1 = new Comment();
        c1.setIssue(issue);
        c1.setAuthor(testUser);
        c1.setBody("Investigating perimeter camera feeds.");
        commentRepository.save(c1);

        Comment c2 = new Comment();
        c2.setIssue(issue);
        c2.setAuthor(testUser);
        c2.setBody("Perimeter confirmed secure.");
        commentRepository.save(c2);

        entityManager.flush();
        entityManager.clear();

        List<Comment> comments = commentRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId());
        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).getBody()).isEqualTo("Investigating perimeter camera feeds.");
        assertThat(comments.get(1).getBody()).isEqualTo("Perimeter confirmed secure.");
    }

    @Test
    @DisplayName("Should manage RefreshTokens, enforce uniqueness, and support revocation")
    void testRefreshTokenLifecycle() {
        String tokenString = "jwt-sample-refresh-token-" + UUID.randomUUID();
        RefreshToken token = refreshTokenService.createRefreshToken(
            testUser,
            tokenString,
            Instant.now().plusSeconds(86400)
        );

        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenService.isValid(tokenString)).isTrue();

        refreshTokenService.revokeToken(tokenString);
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenService.isValid(tokenString)).isFalse();
        Optional<RefreshToken> reloaded = refreshTokenRepository.findByToken(tokenString);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().isRevoked()).isTrue();
        assertThat(reloaded.get().getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update issue status and set resolvedAt when marked DONE via IssueService")
    void testIssueStatusTransition() {
        Issue issue = new Issue();
        issue.setTitle("Emergency Response Protocol");
        issue.setIssueType(IssueType.TASK);
        Issue created = issueService.createIssue(testProject.getId(), issue, testUser.getId());

        assertThat(created.getStatus()).isEqualTo(IssueStatus.TODO);
        assertThat(created.getResolvedAt()).isNull();

        Issue done = issueService.updateStatus(created.getId(), IssueStatus.DONE);
        assertThat(done.getStatus()).isEqualTo(IssueStatus.DONE);
        assertThat(done.getResolvedAt()).isNotNull();

        Issue reopened = issueService.updateStatus(created.getId(), IssueStatus.IN_PROGRESS);
        assertThat(reopened.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
        assertThat(reopened.getResolvedAt()).isNull();
    }
}
