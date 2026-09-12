# Gotham Command — Role-Based Access Control (RBAC) & Multi-Tenant Authorization

## 1. Overview & Architecture

Gotham Command enforces strict multi-tenant authorization layered on top of Spring Security JWT authentication:

```
GLOBAL AUTHENTICATION (JWT)
        ↓
USER (Global Entity)
        ↓
ORGANIZATION (Tenant Boundary)
        ↓
ORGANIZATION MEMBER + ROLE
  - SUPER_ADMIN
  - ORG_ADMIN
  - PROJECT_MANAGER
  - MEMBER
        ↓
PROJECT (Workspace Unit)
        ↓
PROJECT MEMBER + ROLE
  - PROJECT_MANAGER
  - MEMBER
  - VIEWER
        ↓
RESOURCE PERMISSIONS (Issues, Subtasks, Comments)
```

- **Global Authentication**: Handled via JWT access tokens (`Authorization: Bearer <jwt>`) verified by `JwtAuthenticationFilter`, producing an authenticated `SecurityContext` containing the user's ID.
- **Tenant Authorization**: Handled via method security (`@EnableMethodSecurity`, `@PreAuthorize`) and `RbacAuthorizationService` (`@authorizationService.canX(authentication, ...)`), evaluating organization and project memberships dynamically against the authoritative PostgreSQL database.

---

## 2. Role Hierarchy & Semantics

### 2.1 Organization Roles (`OrganizationRole`)

| Role | Scope | Description |
| :--- | :--- | :--- |
| **`SUPER_ADMIN`** | System-Wide / All Orgs | Global superuser with complete bypass across all organizations, projects, issues, comments, and members. Can promote other users to `SUPER_ADMIN`. |
| **`ORG_ADMIN`** | Organization | Full administrative ownership of the organization. Can update/delete the organization, manage all organization members, create projects, delete projects, and manage all projects within that organization. |
| **`PROJECT_MANAGER`** | Organization | Organization member with authorization to create new projects under that organization and manage projects where assigned. |
| **`MEMBER`** | Organization | Standard member of the organization. Can view the organization and view/participate in projects where granted access. |

### 2.2 Project Roles (`ProjectRole`)

| Role | Scope | Description |
| :--- | :--- | :--- |
| **`PROJECT_MANAGER`** | Project | Manager of the specific project. Can update project metadata, manage project members (add/update/remove), create/edit/delete issues, and moderate comments. |
| **`MEMBER`** | Project | Active contributor to the project. Can create issues, edit issues, update status/assignee/sort order, create subtasks, and post comments. |
| **`VIEWER`** | Project | Read-only observer. Can view project details, issues, subtasks, and comments. Explicitly prohibited from creating/editing/deleting issues, modifying status, or adding comments. |

---

## 3. Comprehensive Permission Matrix

The following matrix documents authorization rules across all operations.  
A `*` denotes that the role is authorized for that operation.

| Resource Operation | HTTP Endpoint | SUPER_ADMIN | ORG_ADMIN | PROJECT_MANAGER (Org) | MEMBER (Org) | PROJECT_MANAGER (Project) | MEMBER (Project) | VIEWER (Project) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **View Organization** | `GET /api/organizations/{id}` | `*` | `*` | `*` | `*` | — | — | — |
| **Update Organization** | `PUT /api/organizations/{id}` | `*` | `*` | — | — | — | — | — |
| **Delete Organization** | `DELETE /api/organizations/{id}` | `*` | `*` | — | — | — | — | — |
| **List Org Members** | `GET /api/organizations/{id}/members` | `*` | `*` | `*` | `*` | — | — | — |
| **Add Org Member** | `POST /api/organizations/{id}/members` | `*` | `*` | — | — | — | — | — |
| **Update Org Member Role** | `PUT /api/organizations/{id}/members/{uId}` | `*` | `*` | — | — | — | — | — |
| **Remove Org Member** | `DELETE /api/organizations/{id}/members/{uId}` | `*` | `*` | — | — | — | — | — |
| **Create Project** | `POST /api/organizations/{id}/projects` | `*` | `*` | `*` | — | — | — | — |
| **View Project** | `GET /api/projects/{id}` | `*` | `*` | `*`¹ | `*`¹ | `*` | `*` | `*` |
| **Update Project** | `PUT /api/projects/{id}` | `*` | `*` | — | — | `*` | — | — |
| **Delete Project** | `DELETE /api/projects/{id}` | `*` | `*` | — | — | — | — | — |
| **List Project Members**| `GET /api/projects/{id}/members` | `*` | `*` | `*`¹ | `*`¹ | `*` | `*` | `*` |
| **Add Project Member** | `POST /api/projects/{id}/members` | `*` | `*` | — | — | `*` | — | — |
| **Update Project Member Role** | `PUT /api/projects/{id}/members/{uId}` | `*` | `*` | — | — | `*` | — | — |
| **Remove Project Member** | `DELETE /api/projects/{id}/members/{uId}` | `*` | `*` | — | — | `*` | — | — |
| **View Issue** | `GET /api/issues/{id}` | `*` | `*` | `*`¹ | `*`¹ | `*` | `*` | `*` |
| **Create Issue** | `POST /api/projects/{id}/issues` | `*` | `*` | — | — | `*` | `*` | — |
| **Edit Issue / Patch Status** | `PUT /api/issues/{id}`, `PATCH /api/issues/{id}/*` | `*` | `*` | — | — | `*` | `*` | — |
| **Create Subtask** | `POST /api/issues/{id}/subtasks` | `*` | `*` | — | — | `*` | `*` | — |
| **Delete Issue** | `DELETE /api/issues/{id}` | `*` | `*` | — | — | `*` | `*`² | — |
| **View Comments** | `GET /api/issues/{id}/comments` | `*` | `*` | `*`¹ | `*`¹ | `*` | `*` | `*` |
| **Create Comment** | `POST /api/issues/{id}/comments` | `*` | `*` | — | — | `*` | `*` | — |
| **Delete Comment** | `DELETE /api/comments/{id}` | `*` | `*` | — | — | `*` | `*`³ | — |

*Notes:*  
¹ If the user is a member of the organization and has not been restricted to a VIEWER project role.  
² Project `MEMBER` can only delete an issue if they are the original reporter of that issue.  
³ Project `MEMBER` can only delete a comment if they are the author of that comment.

---

## 4. Privilege Escalation Prevention & Integrity Constraints

1. **SUPER_ADMIN Escalation Protection**:
   - Only an authenticated user with existing `SUPER_ADMIN` status can grant `SUPER_ADMIN` to any user (`addMember` or `updateMemberRole`).
   - If an `ORG_ADMIN` attempts to assign `SUPER_ADMIN`, the service throws an `IllegalArgumentException` returning `400 Bad Request`.
2. **Self-Promotion Block**:
   - Users cannot modify their own organization role or project role (`operatorUserId.equals(targetUserId)` check).
   - Users cannot escalate their own privileges or demote themselves, preventing accidental or malicious privilege elevation or lockout.
3. **Final ORG_ADMIN Protection**:
   - An organization must always have at least one `ORG_ADMIN`.
   - Attempting to delete or demote the final `ORG_ADMIN` throws an `IllegalStateException` returning `400 Bad Request` unless performed by a system-wide `SUPER_ADMIN`.
4. **Project Membership Prerequisite**:
   - A user cannot be added to a project unless they already belong to the organization owning that project.
   - Prevents unauthorized external users from being granted access to a project inside a tenant.
5. **Cascading Member Cleanup**:
   - When a user is removed from an organization (`removeMember`), they are automatically and transactionally removed from all projects under that organization (`projectMemberRepository.deleteByProjectIdAndUserId`).
6. **Viewer Restriction**:
   - `VIEWER` project members are strictly read-only.
   - They cannot create issues, update issues, patch status, patch assignees, change sort order, create subtasks, or add comments.

---

## 5. HTTP Status Code Semantics: 401 vs 403

| Status Code | Reason | Example |
| :--- | :--- | :--- |
| **`401 Unauthorized`** | Authentication is missing or invalid. | No `Authorization` header, invalid JWT signature, expired JWT token, or user account marked inactive (`active = false`). |
| **`403 Forbidden`** | Authenticated user lacks permission. | User is authenticated but is not a member of the organization, has `MEMBER` role trying to delete the organization, has `VIEWER` role trying to edit an issue, or is trying to modify a project in another organization. |
| **`404 Not Found`** | Resource does not exist. | Requesting an organization, project, issue, or comment ID that does not exist in the database. |
| **`400 Bad Request`** | Validation or business integrity failure. | Attempting to grant `SUPER_ADMIN` without authorization, self-promoting, demoting the last `ORG_ADMIN`, or adding a non-org user to a project. |

---

## 6. Cross-Tenant IDOR Protection & Boundary Isolation

1. **Parent Hierarchy Verification**:
   - Every issue and comment operation navigates through its entity relationships (`Comment -> Issue -> Project -> Organization`) to verify tenant membership before granting access.
   - Attackers cannot pass an issue ID or project ID belonging to Tenant B and access it using credentials from Tenant A.
2. **Scoped Uniqueness**:
   - Project keys are unique within an organization (`UK_PROJECTS_ORG_PROJECT_KEY`). Two different organizations can each have a project with key `APP`.
   - Issue keys are generated sequentially per project (`PROJ-1`, `PROJ-2`).
3. **Data Sanitization**:
   - DTOs never expose sensitive security fields such as `passwordHash` or refresh tokens.
   - Error responses use structured `ApiErrorResponse` and never leak internal stack traces or database schema details.
