package com.gotham.command.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gotham.command.dto.comment.CreateCommentRequest;
import com.gotham.command.dto.issue.CreateIssueRequest;
import com.gotham.command.dto.issue.UpdateIssueRequest;
import com.gotham.command.dto.issue.UpdateStatusRequest;
import com.gotham.command.dto.organization.AddOrganizationMemberRequest;
import com.gotham.command.dto.organization.CreateOrganizationRequest;
import com.gotham.command.dto.organization.UpdateOrganizationMemberRequest;
import com.gotham.command.dto.organization.UpdateOrganizationRequest;
import com.gotham.command.dto.project.AddProjectMemberRequest;
import com.gotham.command.dto.project.CreateProjectRequest;
import com.gotham.command.dto.project.UpdateProjectMemberRequest;
import com.gotham.command.dto.project.UpdateProjectRequest;
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
import com.gotham.command.entity.User;
import com.gotham.command.repository.CommentRepository;
import com.gotham.command.repository.IssueRepository;
import com.gotham.command.repository.OrganizationMemberRepository;
import com.gotham.command.repository.OrganizationRepository;
import com.gotham.command.repository.ProjectMemberRepository;
import com.gotham.command.repository.ProjectRepository;
import com.gotham.command.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private JwtService jwtService;

    // Test Users
    private User superAdminUser;
    private User orgAdminUser;
    private User orgPmUser;
    private User orgMemberUser;
    private User viewerUser;
    private User outsiderUser;

    // Auth Tokens
    private String superAdminToken;
    private String orgAdminToken;
    private String orgPmToken;
    private String orgMemberToken;
    private String viewerToken;
    private String outsiderToken;

    // Test Entities
    private Organization orgA;
    private Organization orgB;
    private Project projectA;
    private Project projectB;
    private Issue issueA1;
    private Comment commentA1;

    @BeforeEach
    void setUp() {
        // Create users
        superAdminUser = createUser("superadmin@gotham.test", "Bruce Wayne SuperAdmin");
        orgAdminUser = createUser("orgadmin@gotham.test", "Lucius Fox OrgAdmin");
        orgPmUser = createUser("orgpm@gotham.test", "Jim Gordon OrgPm");
        orgMemberUser = createUser("orgmember@gotham.test", "Harvey Bullock Member");
        viewerUser = createUser("viewer@gotham.test", "Vicki Vale Viewer");
        outsiderUser = createUser("outsider@metropolis.test", "Lex Luthor Outsider");

        superAdminToken = jwtService.generateAccessToken(superAdminUser);
        orgAdminToken = jwtService.generateAccessToken(orgAdminUser);
        orgPmToken = jwtService.generateAccessToken(orgPmUser);
        orgMemberToken = jwtService.generateAccessToken(orgMemberUser);
        viewerToken = jwtService.generateAccessToken(viewerUser);
        outsiderToken = jwtService.generateAccessToken(outsiderUser);

        // Create Org A
        orgA = new Organization();
        orgA.setName("Wayne Enterprises");
        orgA.setSlug("wayne-ent-" + UUID.randomUUID().toString().substring(0, 8));
        orgA = organizationRepository.save(orgA);

        // Assign Org A Memberships
        addOrgMember(orgA, superAdminUser, OrganizationRole.SUPER_ADMIN);
        addOrgMember(orgA, orgAdminUser, OrganizationRole.ORG_ADMIN);
        addOrgMember(orgA, orgPmUser, OrganizationRole.PROJECT_MANAGER);
        addOrgMember(orgA, orgMemberUser, OrganizationRole.MEMBER);
        addOrgMember(orgA, viewerUser, OrganizationRole.MEMBER);

        // Create Org B (outsider org)
        orgB = new Organization();
        orgB.setName("LexCorp");
        orgB.setSlug("lexcorp-" + UUID.randomUUID().toString().substring(0, 8));
        orgB = organizationRepository.save(orgB);
        addOrgMember(orgB, outsiderUser, OrganizationRole.ORG_ADMIN);

        // Create Project A under Org A
        projectA = new Project();
        projectA.setName("Batmobile Diagnostics");
        projectA.setProjectKey("BAT");
        projectA.setOrganization(orgA);
        projectA = projectRepository.save(projectA);

        // Add Project A Memberships
        addProjectMember(projectA, orgPmUser, ProjectRole.PROJECT_MANAGER);
        addProjectMember(projectA, orgMemberUser, ProjectRole.MEMBER);
        addProjectMember(projectA, viewerUser, ProjectRole.VIEWER);

        // Create Project B under Org B
        projectB = new Project();
        projectB.setName("Warsuit Prototype");
        projectB.setProjectKey("WAR");
        projectB.setOrganization(orgB);
        projectB = projectRepository.save(projectB);
        addProjectMember(projectB, outsiderUser, ProjectRole.PROJECT_MANAGER);

        // Create Issue under Project A (reported by orgMemberUser)
        issueA1 = new Issue();
        issueA1.setProject(projectA);
        issueA1.setTitle("Engine Overheating");
        issueA1.setDescription("Turbine heat levels exceeding safety thresholds.");
        issueA1.setIssueType(IssueType.BUG);
        issueA1.setStatus(IssueStatus.TODO);
        issueA1.setPriority(IssuePriority.HIGH);
        issueA1.setIssueKey("BAT-1");
        issueA1.setReporter(orgMemberUser);
        issueA1.setAssignee(orgMemberUser);
        issueA1 = issueRepository.save(issueA1);

        // Create Comment under Issue A1
        commentA1 = new Comment();
        commentA1.setIssue(issueA1);
        commentA1.setAuthor(orgMemberUser);
        commentA1.setBody("Investigating coolant valves.");
        commentA1 = commentRepository.save(commentA1);
    }

    private User createUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setDisplayName(name);
        user.setUsername(email.split("@")[0]);
        user.setProvider(AuthProvider.LOCAL);
        user.setProviderId("local-" + UUID.randomUUID());
        user.setPasswordHash("$2a$10$dummyHashForTestingOnly");
        user.setActive(true);
        return userRepository.save(user);
    }

    private void addOrgMember(Organization org, User user, OrganizationRole role) {
        OrganizationMember member = new OrganizationMember();
        member.setOrganization(org);
        member.setUser(user);
        member.setRole(role);
        organizationMemberRepository.save(member);
    }

    private void addProjectMember(Project project, User user, ProjectRole role) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(role);
        projectMemberRepository.save(member);
    }

    // =========================================================================
    // 1. SUPER_ADMIN SYSTEM-WIDE BYPASS TESTS
    // =========================================================================
    @Nested
    @DisplayName("SUPER_ADMIN System-Wide Bypass Tests")
    class SuperAdminTests {

        @Test
        @DisplayName("SUPER_ADMIN can view any organization")
        void testSuperAdminCanViewAnyOrg() throws Exception {
            mockMvc.perform(get("/api/organizations/" + orgB.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgB.getId().toString()));
        }

        @Test
        @DisplayName("SUPER_ADMIN can update any organization")
        void testSuperAdminCanUpdateAnyOrg() throws Exception {
            UpdateOrganizationRequest req = new UpdateOrganizationRequest("LexCorp Rebranded", "Updated by SuperAdmin");
            mockMvc.perform(put("/api/organizations/" + orgB.getId())
                    .header("Authorization", "Bearer " + superAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("LexCorp Rebranded"));
        }

        @Test
        @DisplayName("SUPER_ADMIN can view any project")
        void testSuperAdminCanViewAnyProject() throws Exception {
            mockMvc.perform(get("/api/projects/" + projectB.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("WAR"));
        }

        @Test
        @DisplayName("SUPER_ADMIN can update any project")
        void testSuperAdminCanUpdateAnyProject() throws Exception {
            UpdateProjectRequest req = new UpdateProjectRequest("Warsuit Mark II", "Updated description");
            mockMvc.perform(put("/api/projects/" + projectB.getId())
                    .header("Authorization", "Bearer " + superAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Warsuit Mark II"));
        }

        @Test
        @DisplayName("SUPER_ADMIN can view any issue")
        void testSuperAdminCanViewAnyIssue() throws Exception {
            mockMvc.perform(get("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueKey").value("BAT-1"));
        }

        @Test
        @DisplayName("SUPER_ADMIN can edit any issue")
        void testSuperAdminCanEditAnyIssue() throws Exception {
            UpdateIssueRequest req = new UpdateIssueRequest("Engine Overheating Fixed", null, null, IssueStatus.IN_PROGRESS, null, null, null, null, null);
            mockMvc.perform(put("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + superAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Engine Overheating Fixed"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("SUPER_ADMIN can view any comment")
        void testSuperAdminCanViewAnyComment() throws Exception {
            mockMvc.perform(get("/api/issues/" + issueA1.getId() + "/comments")
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Investigating coolant valves."));
        }

        @Test
        @DisplayName("SUPER_ADMIN can delete any comment")
        void testSuperAdminCanDeleteAnyComment() throws Exception {
            mockMvc.perform(delete("/api/comments/" + commentA1.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isNoContent());

            assertThat(commentRepository.findById(commentA1.getId())).isEmpty();
        }

        @Test
        @DisplayName("SUPER_ADMIN can delete any issue")
        void testSuperAdminCanDeleteAnyIssue() throws Exception {
            mockMvc.perform(delete("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isNoContent());

            assertThat(issueRepository.findById(issueA1.getId())).isEmpty();
        }

        @Test
        @DisplayName("SUPER_ADMIN can grant SUPER_ADMIN to another user")
        void testSuperAdminCanGrantSuperAdmin() throws Exception {
            User newAdmin = createUser("newadmin@gotham.test", "New Admin");
            AddOrganizationMemberRequest req = new AddOrganizationMemberRequest(newAdmin.getId(), OrganizationRole.SUPER_ADMIN);

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/members")
                    .header("Authorization", "Bearer " + superAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
        }

        @Test
        @DisplayName("SUPER_ADMIN can delete any project")
        void testSuperAdminCanDeleteAnyProject() throws Exception {
            mockMvc.perform(delete("/api/projects/" + projectB.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isNoContent());

            assertThat(projectRepository.findById(projectB.getId())).isEmpty();
        }

        @Test
        @DisplayName("SUPER_ADMIN can delete any organization")
        void testSuperAdminCanDeleteAnyOrg() throws Exception {
            mockMvc.perform(delete("/api/organizations/" + orgB.getId())
                    .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isNoContent());

            assertThat(organizationRepository.findById(orgB.getId())).isEmpty();
        }
    }

    // =========================================================================
    // 2. ORG_ADMIN TESTS & ESCALATION PROTECTIONS
    // =========================================================================
    @Nested
    @DisplayName("ORG_ADMIN Tests & Escalation Protections")
    class OrgAdminTests {

        @Test
        @DisplayName("ORG_ADMIN can view own organization")
        void testOrgAdminCanViewOwnOrg() throws Exception {
            mockMvc.perform(get("/api/organizations/" + orgA.getId())
                    .header("Authorization", "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgA.getId().toString()));
        }

        @Test
        @DisplayName("ORG_ADMIN can update own organization")
        void testOrgAdminCanUpdateOwnOrg() throws Exception {
            UpdateOrganizationRequest req = new UpdateOrganizationRequest("Wayne Enterprises Updated", "New Mission");
            mockMvc.perform(put("/api/organizations/" + orgA.getId())
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wayne Enterprises Updated"));
        }

        @Test
        @DisplayName("ORG_ADMIN can manage members in own organization")
        void testOrgAdminCanAddAndRemoveMember() throws Exception {
            User candidate = createUser("candidate@gotham.test", "Candidate");
            AddOrganizationMemberRequest addReq = new AddOrganizationMemberRequest(candidate.getId(), OrganizationRole.MEMBER);

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/members")
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));

            mockMvc.perform(delete("/api/organizations/" + orgA.getId() + "/members/" + candidate.getId())
                    .header("Authorization", "Bearer " + orgAdminToken))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("ORG_ADMIN CANNOT grant SUPER_ADMIN role (400 Bad Request)")
        void testOrgAdminCannotGrantSuperAdmin() throws Exception {
            User candidate = createUser("elevated@gotham.test", "Elevated Candidate");
            AddOrganizationMemberRequest addReq = new AddOrganizationMemberRequest(candidate.getId(), OrganizationRole.SUPER_ADMIN);

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/members")
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("ORG_ADMIN CANNOT remove the final ORG_ADMIN (400 Bad Request)")
        void testOrgAdminCannotRemoveFinalOrgAdmin() throws Exception {
            // orgAdminUser is the only ORG_ADMIN in orgA (superAdmin has SUPER_ADMIN)
            mockMvc.perform(delete("/api/organizations/" + orgA.getId() + "/members/" + orgAdminUser.getId())
                    .header("Authorization", "Bearer " + orgAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("ORG_ADMIN CANNOT modify their own role (self-promotion block) (400 Bad Request)")
        void testOrgAdminCannotSelfPromote() throws Exception {
            UpdateOrganizationMemberRequest req = new UpdateOrganizationMemberRequest(OrganizationRole.SUPER_ADMIN);

            mockMvc.perform(put("/api/organizations/" + orgA.getId() + "/members/" + orgAdminUser.getId())
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("ORG_ADMIN can create projects in own organization")
        void testOrgAdminCanCreateProject() throws Exception {
            CreateProjectRequest req = new CreateProjectRequest("Batwing Avionics", "WING", "Avionics sub-system");

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/projects")
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("WING"));
        }

        @Test
        @DisplayName("ORG_ADMIN can delete projects in own organization")
        void testOrgAdminCanDeleteProject() throws Exception {
            mockMvc.perform(delete("/api/projects/" + projectA.getId())
                    .header("Authorization", "Bearer " + orgAdminToken))
                .andExpect(status().isNoContent());

            assertThat(projectRepository.findById(projectA.getId())).isEmpty();
        }

        @Test
        @DisplayName("ORG_ADMIN CANNOT manage another organization (403 Forbidden)")
        void testOrgAdminCannotManageOtherOrg() throws Exception {
            UpdateOrganizationRequest req = new UpdateOrganizationRequest("Hostile Takeover", "Illegal attempt");

            mockMvc.perform(put("/api/organizations/" + orgB.getId())
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ORG_ADMIN CANNOT manage projects in another organization (403 Forbidden)")
        void testOrgAdminCannotManageProjectsInOtherOrg() throws Exception {
            UpdateProjectRequest req = new UpdateProjectRequest("Hacked Project", "Hacked");

            mockMvc.perform(put("/api/projects/" + projectB.getId())
                    .header("Authorization", "Bearer " + orgAdminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 3. PROJECT_MANAGER TESTS
    // =========================================================================
    @Nested
    @DisplayName("PROJECT_MANAGER Tests")
    class ProjectManagerTests {

        @Test
        @DisplayName("Org PM can create project under organization")
        void testOrgPmCanCreateProject() throws Exception {
            CreateProjectRequest req = new CreateProjectRequest("Sonar Tracking", "SNR", "Sonar surveillance");

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/projects")
                    .header("Authorization", "Bearer " + orgPmToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("SNR"));
        }

        @Test
        @DisplayName("Project PM can update project metadata")
        void testProjectPmCanUpdateProject() throws Exception {
            UpdateProjectRequest req = new UpdateProjectRequest("Batmobile Armor Upgrade", "Armor hardening");

            mockMvc.perform(put("/api/projects/" + projectA.getId())
                    .header("Authorization", "Bearer " + orgPmToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Batmobile Armor Upgrade"));
        }

        @Test
        @DisplayName("Project PM can add/remove project members from org")
        void testProjectPmCanManageMembers() throws Exception {
            // viewerUser is in orgA, can be promoted in projectA
            UpdateProjectMemberRequest req = new UpdateProjectMemberRequest(ProjectRole.MEMBER);

            mockMvc.perform(put("/api/projects/" + projectA.getId() + "/members/" + viewerUser.getId())
                    .header("Authorization", "Bearer " + orgPmToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        }

        @Test
        @DisplayName("Project PM CANNOT add a non-org user to project (400 Bad Request)")
        void testProjectPmCannotAddNonOrgUser() throws Exception {
            AddProjectMemberRequest req = new AddProjectMemberRequest(outsiderUser.getId(), ProjectRole.MEMBER);

            mockMvc.perform(post("/api/projects/" + projectA.getId() + "/members")
                    .header("Authorization", "Bearer " + orgPmToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }

        @Test
        @DisplayName("Project PM can edit any issue in project")
        void testProjectPmCanEditAnyIssue() throws Exception {
            UpdateIssueRequest req = new UpdateIssueRequest("Engine Overheating Resolved by PM", null, null, null, null, null, null, null, null);

            mockMvc.perform(put("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + orgPmToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Engine Overheating Resolved by PM"));
        }

        @Test
        @DisplayName("Project PM can delete any issue in project")
        void testProjectPmCanDeleteAnyIssue() throws Exception {
            mockMvc.perform(delete("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + orgPmToken))
                .andExpect(status().isNoContent());

            assertThat(issueRepository.findById(issueA1.getId())).isEmpty();
        }

        @Test
        @DisplayName("Project PM CANNOT delete the project (requires ORG_ADMIN) (403 Forbidden)")
        void testProjectPmCannotDeleteProject() throws Exception {
            mockMvc.perform(delete("/api/projects/" + projectA.getId())
                    .header("Authorization", "Bearer " + orgPmToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Org PM CANNOT delete the organization (403 Forbidden)")
        void testOrgPmCannotDeleteOrg() throws Exception {
            mockMvc.perform(delete("/api/organizations/" + orgA.getId())
                    .header("Authorization", "Bearer " + orgPmToken))
                .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 4. MEMBER TESTS
    // =========================================================================
    @Nested
    @DisplayName("MEMBER Tests")
    class MemberTests {

        @Test
        @DisplayName("Org Member can view organization")
        void testMemberCanViewOrg() throws Exception {
            mockMvc.perform(get("/api/organizations/" + orgA.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgA.getId().toString()));
        }

        @Test
        @DisplayName("Project Member can view project")
        void testMemberCanViewProject() throws Exception {
            mockMvc.perform(get("/api/projects/" + projectA.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectA.getId().toString()));
        }

        @Test
        @DisplayName("Project Member can create issue in project")
        void testMemberCanCreateIssue() throws Exception {
            CreateIssueRequest req = new CreateIssueRequest(
                "Tire Pressure Low",
                "Left rear wheel PSI is below 30.",
                IssueType.TASK,
                IssueStatus.TODO,
                IssuePriority.MEDIUM,
                null,
                null,
                0.0,
                null
            );

            mockMvc.perform(post("/api/projects/" + projectA.getId() + "/issues")
                    .header("Authorization", "Bearer " + orgMemberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Tire Pressure Low"));
        }

        @Test
        @DisplayName("Project Member can update issue in project")
        void testMemberCanUpdateIssue() throws Exception {
            UpdateIssueRequest req = new UpdateIssueRequest("Updated Title by Member", null, null, null, null, null, null, null, null);

            mockMvc.perform(put("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + orgMemberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title by Member"));
        }

        @Test
        @DisplayName("Project Member can create subtask")
        void testMemberCanCreateSubtask() throws Exception {
            CreateIssueRequest req = new CreateIssueRequest(
                "Check coolant pump subtask",
                "Inspect motor wiring",
                IssueType.SUBTASK,
                IssueStatus.TODO,
                IssuePriority.LOW,
                null,
                null,
                1.0,
                null
            );

            mockMvc.perform(post("/api/issues/" + issueA1.getId() + "/subtasks")
                    .header("Authorization", "Bearer " + orgMemberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Check coolant pump subtask"))
                .andExpect(jsonPath("$.issueType").value("SUBTASK"));
        }

        @Test
        @DisplayName("Project Member can create comment on issue")
        void testMemberCanCreateComment() throws Exception {
            CreateCommentRequest req = new CreateCommentRequest("Coolant replaced successfully.");

            mockMvc.perform(post("/api/issues/" + issueA1.getId() + "/comments")
                    .header("Authorization", "Bearer " + orgMemberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Coolant replaced successfully."));
        }

        @Test
        @DisplayName("Project Member can delete own issue")
        void testMemberCanDeleteOwnIssue() throws Exception {
            // issueA1 reporter is orgMemberUser
            mockMvc.perform(delete("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isNoContent());

            assertThat(issueRepository.findById(issueA1.getId())).isEmpty();
        }

        @Test
        @DisplayName("Project Member CANNOT delete other user's issue (403 Forbidden)")
        void testMemberCannotDeleteOtherUserIssue() throws Exception {
            // Re-assign reporter to superAdminUser
            issueA1.setReporter(superAdminUser);
            issueRepository.save(issueA1);

            mockMvc.perform(delete("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Project Member can delete own comment")
        void testMemberCanDeleteOwnComment() throws Exception {
            // commentA1 author is orgMemberUser
            mockMvc.perform(delete("/api/comments/" + commentA1.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isNoContent());

            assertThat(commentRepository.findById(commentA1.getId())).isEmpty();
        }

        @Test
        @DisplayName("Project Member CANNOT delete other user's comment (403 Forbidden)")
        void testMemberCannotDeleteOtherUserComment() throws Exception {
            commentA1.setAuthor(superAdminUser);
            commentRepository.save(commentA1);

            mockMvc.perform(delete("/api/comments/" + commentA1.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Org Member CANNOT create project in organization (403 Forbidden)")
        void testMemberCannotCreateProject() throws Exception {
            CreateProjectRequest req = new CreateProjectRequest("Unauthorized Project", "UNAUTH", "Desc");

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/projects")
                    .header("Authorization", "Bearer " + orgMemberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Org Member CANNOT delete organization (403 Forbidden)")
        void testMemberCannotDeleteOrg() throws Exception {
            mockMvc.perform(delete("/api/organizations/" + orgA.getId())
                    .header("Authorization", "Bearer " + orgMemberToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Org Member CANNOT manage organization members (403 Forbidden)")
        void testMemberCannotManageOrgMembers() throws Exception {
            User testU = createUser("dummy@gotham.test", "Dummy");
            AddOrganizationMemberRequest req = new AddOrganizationMemberRequest(testU.getId(), OrganizationRole.MEMBER);

            mockMvc.perform(post("/api/organizations/" + orgA.getId() + "/members")
                    .header("Authorization", "Bearer " + orgMemberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 5. VIEWER TESTS
    // =========================================================================
    @Nested
    @DisplayName("VIEWER Tests")
    class ViewerTests {

        @Test
        @DisplayName("Viewer can view project")
        void testViewerCanViewProject() throws Exception {
            mockMvc.perform(get("/api/projects/" + projectA.getId())
                    .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Viewer can view issue")
        void testViewerCanViewIssue() throws Exception {
            mockMvc.perform(get("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Viewer can view comments")
        void testViewerCanViewComments() throws Exception {
            mockMvc.perform(get("/api/issues/" + issueA1.getId() + "/comments")
                    .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Viewer CANNOT create issue (403 Forbidden)")
        void testViewerCannotCreateIssue() throws Exception {
            CreateIssueRequest req = new CreateIssueRequest(
                "Viewer Bug",
                "Viewer should not be able to create",
                IssueType.BUG,
                IssueStatus.TODO,
                IssuePriority.LOW,
                null,
                null,
                0.0,
                null
            );

            mockMvc.perform(post("/api/projects/" + projectA.getId() + "/issues")
                    .header("Authorization", "Bearer " + viewerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Viewer CANNOT edit issue (403 Forbidden)")
        void testViewerCannotEditIssue() throws Exception {
            UpdateIssueRequest req = new UpdateIssueRequest("Viewer Edit", null, null, null, null, null, null, null, null);

            mockMvc.perform(put("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + viewerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Viewer CANNOT patch issue status (403 Forbidden)")
        void testViewerCannotPatchStatus() throws Exception {
            UpdateStatusRequest req = new UpdateStatusRequest(IssueStatus.DONE);

            mockMvc.perform(patch("/api/issues/" + issueA1.getId() + "/status")
                    .header("Authorization", "Bearer " + viewerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Viewer CANNOT create subtask (403 Forbidden)")
        void testViewerCannotCreateSubtask() throws Exception {
            CreateIssueRequest req = new CreateIssueRequest(
                "Viewer Subtask",
                "Subtask body",
                IssueType.SUBTASK,
                IssueStatus.TODO,
                IssuePriority.LOW,
                null,
                null,
                0.0,
                null
            );

            mockMvc.perform(post("/api/issues/" + issueA1.getId() + "/subtasks")
                    .header("Authorization", "Bearer " + viewerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Viewer CANNOT create comment (403 Forbidden)")
        void testViewerCannotCreateComment() throws Exception {
            CreateCommentRequest req = new CreateCommentRequest("Viewer commentary");

            mockMvc.perform(post("/api/issues/" + issueA1.getId() + "/comments")
                    .header("Authorization", "Bearer " + viewerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 6. NON-MEMBER / CROSS-TENANT ISOLATION TESTS
    // =========================================================================
    @Nested
    @DisplayName("Non-Member / Cross-Tenant IDOR Protection Tests")
    class CrossTenantTests {

        @Test
        @DisplayName("Outsider CANNOT view organization A (403 Forbidden)")
        void testOutsiderCannotViewOrgA() throws Exception {
            mockMvc.perform(get("/api/organizations/" + orgA.getId())
                    .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Outsider CANNOT view project in organization A (403 Forbidden)")
        void testOutsiderCannotViewProjectA() throws Exception {
            mockMvc.perform(get("/api/projects/" + projectA.getId())
                    .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Outsider CANNOT view issue in organization A (403 Forbidden)")
        void testOutsiderCannotViewIssueA() throws Exception {
            mockMvc.perform(get("/api/issues/" + issueA1.getId())
                    .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request returns 401 Unauthorized")
        void testUnauthenticatedRequestReturns401() throws Exception {
            mockMvc.perform(get("/api/organizations/" + orgA.getId()))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/projects/" + projectA.getId()))
                .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/issues/" + issueA1.getId()))
                .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 7. CASCADE MEMBER CLEANUP TESTS
    // =========================================================================
    @Nested
    @DisplayName("Cascade Member Cleanup Tests")
    class CascadeCleanupTests {

        @Test
        @DisplayName("Removing a member from organization automatically cleans up project memberships")
        void testCascadeCleanupOnOrgMemberRemoval() throws Exception {
            // Confirm orgMemberUser is in projectA
            assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectA.getId(), orgMemberUser.getId())).isTrue();

            // Org Admin removes orgMemberUser from orgA
            mockMvc.perform(delete("/api/organizations/" + orgA.getId() + "/members/" + orgMemberUser.getId())
                    .header("Authorization", "Bearer " + orgAdminToken))
                .andExpect(status().isNoContent());

            // Check that org membership is deleted
            assertThat(organizationMemberRepository.existsByOrganizationIdAndUserId(orgA.getId(), orgMemberUser.getId())).isFalse();

            // Check that project membership was cascaded and removed
            assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectA.getId(), orgMemberUser.getId())).isFalse();
        }
    }
}
