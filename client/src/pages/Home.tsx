import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  Bell,
  BookOpen,
  BriefcaseBusiness,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleDot,
  Clock3,
  Command,
  Filter,
  LayoutDashboard,
  ListFilter,
  Menu,
  MessageSquare,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Search,
  Settings2,
  Shield,
  SlidersHorizontal,
  Sparkles,
  Target,
  Trash2,
  UserRound,
  UsersRound,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/contexts/AuthContext";
import { organizationApi } from "@/services/organizationApi";
import { projectApi } from "@/services/projectApi";
import { issueApi, type CreateIssuePayload, type UpdateIssuePayload } from "@/services/issueApi";
import { commentApi } from "@/services/commentApi";
import {
  type UIStatus,
  type UIPriority,
  type UIIssue,
  type OrganizationDto,
  type ProjectDto,
  type OrganizationMemberDto,
  type OrganizationRole,
  type ProjectRole,
  type IssueDto,
  type BackendIssueType,
  uiStatusToBackend,
  backendStatusToUi,
  uiPriorityToBackend,
  backendPriorityToUi,
  getInitials,
  formatRelativeTime,
  mapIssueDtoToUi,
} from "@/services/mappers";

type View = "overview" | "my-work" | "list" | "board" | "members" | "settings";

const statusMeta: Record<UIStatus, { label: string; color: string }> = {
  todo: { label: "TO DO", color: "slate" },
  "in-progress": { label: "IN PROGRESS", color: "gold" },
  "in-review": { label: "IN REVIEW", color: "blue" },
  done: { label: "DONE", color: "green" },
};

const priorityMeta: Record<UIPriority, { label: string; icon: typeof ArrowUp; color: string }> = {
  urgent: { label: "URGENT", icon: AlertTriangle, color: "urgent" },
  high: { label: "HIGH", icon: ArrowUp, color: "high" },
  medium: { label: "MEDIUM", icon: ArrowUpRight, color: "medium" },
  low: { label: "LOW", icon: ArrowDown, color: "low" },
};

function AppMark({ small = false }: { small?: boolean }) {
  return (
    <div className={`app-mark ${small ? "app-mark-small" : ""}`} aria-label="Gotham">
      <svg viewBox="0 0 54 30" role="img" aria-hidden="true">
        <path d="M4 7.5 14 11 21 4.5l6 6 7-6.5 7 7 8-3.5-4 10.2c-2.5 4.2-7.3 7-12.2 7H23c-5.1 0-9.8-2.8-12.3-7L4 7.5Z" />
      </svg>
    </div>
  );
}

function Avatar({ initials, tone = "gold" }: { initials: string; tone?: string }) {
  return <span className={`avatar avatar-${tone}`}>{initials}</span>;
}

function StatusPill({ status }: { status: UIStatus }) {
  const meta = statusMeta[status] || statusMeta.todo;
  return (
    <span className={`status-pill status-${meta.color}`}>
      <span className="status-dot" />
      {meta.label}
    </span>
  );
}

function PriorityBadge({ priority }: { priority: UIPriority }) {
  const meta = priorityMeta[priority] || priorityMeta.medium;
  const Icon = meta.icon;
  return (
    <span className={`priority priority-${meta.color}`}>
      <Icon size={13} strokeWidth={2.5} />
      {meta.label}
    </span>
  );
}

function ColumnsIcon(props: { size?: number }) {
  return (
    <svg
      width={props.size ?? 17}
      height={props.size ?? 17}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <rect x="4" y="4" width="4" height="16" rx="1" />
      <rect x="10" y="4" width="4" height="16" rx="1" />
      <rect x="16" y="4" width="4" height="16" rx="1" />
    </svg>
  );
}

export default function Home() {
  const queryClient = useQueryClient();
  const { user, isAuthenticated, isLoading: authLoading, loginWithGoogle, logout: authLogout } = useAuth();

  // Navigation & UI State
  const [view, setView] = useState<View>("overview");
  const [activeIssue, setActiveIssue] = useState<UIIssue | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(() => window.innerWidth > 760);
  const [workspaceOpen, setWorkspaceOpen] = useState(false);
  // Support URL query parameters (?orgId=...&projectId=...)
  const [selectedOrgId, setSelectedOrgId] = useState<string | null>(() => {
    if (typeof window !== "undefined") {
      const p = new URLSearchParams(window.location.search).get("orgId");
      if (p) return p;
    }
    return null;
  });
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(() => {
    if (typeof window !== "undefined") {
      const p = new URLSearchParams(window.location.search).get("projectId");
      if (p) return p;
    }
    return null;
  });

  // Filters & Search
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<UIStatus | "all">("all");
  const [priorityFilter, setPriorityFilter] = useState<UIPriority | "all">("all");
  const [assigneeFilter, setAssigneeFilter] = useState<string>("all");

  // Modals & Panels
  const [showCreate, setShowCreate] = useState(false);
  const [showCreateOrg, setShowCreateOrg] = useState(false);
  const [showCreateProject, setShowCreateProject] = useState(false);
  const [showInviteMember, setShowInviteMember] = useState(false);
  const [showCommand, setShowCommand] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);

  // Issue Create inputs
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPriority, setNewPriority] = useState<UIPriority>("medium");
  const [newAssigneeId, setNewAssigneeId] = useState<string>("");

  // Comment input
  const [comment, setComment] = useState("");

  // Org Create inputs
  const [orgName, setOrgName] = useState("");
  const [orgSlug, setOrgSlug] = useState("");
  const [orgDesc, setOrgDesc] = useState("");

  // Project Create inputs
  const [projName, setProjName] = useState("");
  const [projKey, setProjKey] = useState("");
  const [projDesc, setProjDesc] = useState("");

  // Member Invite inputs
  const [inviteUserId, setInviteUserId] = useState("");
  const [inviteRole, setInviteRole] = useState<OrganizationRole>("MEMBER");

  // 1. Fetch Organizations
  const { data: organizations = [], isLoading: orgsLoading } = useQuery({
    queryKey: ["organizations"],
    queryFn: organizationApi.getOrganizations,
    enabled: isAuthenticated,
  });

  // Active Organization Resolution
  const activeOrg = useMemo(() => {
    if (!organizations.length) return null;
    if (selectedOrgId) {
      const found = organizations.find((o) => o.id === selectedOrgId);
      if (found) return found;
    }
    return organizations[0];
  }, [organizations, selectedOrgId]);

  // Keep selectedOrgId in sync
  useEffect(() => {
    if (activeOrg && activeOrg.id !== selectedOrgId) {
      setSelectedOrgId(activeOrg.id);
    }
  }, [activeOrg, selectedOrgId]);

  // 2. Fetch Projects for Active Organization
  const { data: projects = [], isLoading: projectsLoading } = useQuery({
    queryKey: ["organization-projects", activeOrg?.id],
    queryFn: () => projectApi.getProjectsByOrganization(activeOrg!.id),
    enabled: !!activeOrg?.id,
  });

  // Active Project Resolution
  const activeProject = useMemo(() => {
    if (!projects.length) return null;
    if (selectedProjectId) {
      const found = projects.find((p) => p.id === selectedProjectId);
      if (found) return found;
    }
    return projects[0];
  }, [projects, selectedProjectId]);

  // Keep selectedProjectId in sync
  useEffect(() => {
    if (activeProject && activeProject.id !== selectedProjectId) {
      setSelectedProjectId(activeProject.id);
    } else if (!projectsLoading && projects.length === 0 && selectedProjectId !== null) {
      setSelectedProjectId(null);
    }
  }, [activeProject, selectedProjectId, projectsLoading, projects.length]);

  // Sync URL search parameters so tenancy context persists across reloads and tabs
  useEffect(() => {
    if (typeof window !== "undefined" && activeOrg?.id && activeProject?.id) {
      const url = new URL(window.location.href);
      if (url.searchParams.get("orgId") !== activeOrg.id || url.searchParams.get("projectId") !== activeProject.id) {
        url.searchParams.set("orgId", activeOrg.id);
        url.searchParams.set("projectId", activeProject.id);
        window.history.replaceState(null, "", url.toString());
      }
    }
  }, [activeOrg?.id, activeProject?.id]);

  // 3. Fetch Organization Members
  const { data: orgMembers = [] } = useQuery({
    queryKey: ["organization-members", activeOrg?.id],
    queryFn: () => organizationApi.getOrganizationMembers(activeOrg!.id),
    enabled: !!activeOrg?.id,
  });

  // 4. Fetch Project Members
  const { data: projectMembers = [] } = useQuery({
    queryKey: ["project-members", activeProject?.id],
    queryFn: () => projectApi.getProjectMembers(activeProject!.id),
    enabled: !!activeProject?.id,
  });

  // Current User Roles in Active Tenant & Project
  const userOrgMember = useMemo(
    () => orgMembers.find((m) => m.user?.id === user?.id),
    [orgMembers, user?.id]
  );
  const userProjectMember = useMemo(
    () => projectMembers.find((m) => m.user?.id === user?.id),
    [projectMembers, user?.id]
  );

  const isSuperAdmin = userOrgMember?.role === "SUPER_ADMIN";
  const isOrgAdmin = isSuperAdmin || userOrgMember?.role === "ORG_ADMIN";
  const isOrgPm = isOrgAdmin || userOrgMember?.role === "PROJECT_MANAGER";
  const isProjectManager = isOrgAdmin || userProjectMember?.role === "PROJECT_MANAGER";
  const isViewer = !isOrgAdmin && userProjectMember?.role === "VIEWER";

  // Lookup helper for user display names
  const userLookup = useMemo(() => {
    const map = new Map<string, string>();
    orgMembers.forEach((m) => {
      if (m.user) {
        map.set(m.user.id, m.user.displayName || m.user.fullName || m.user.email);
      }
    });
    return (id?: string) => (id ? map.get(id) || "Assigned Agent" : "Unassigned");
  }, [orgMembers]);

  // 5. Fetch Issues for Active Project
  const { data: rawIssues = [], isLoading: issuesLoading } = useQuery({
    queryKey: ["project-issues", activeProject?.id],
    queryFn: () => issueApi.getIssuesByProject(activeProject!.id),
    enabled: !!activeProject?.id,
  });

  const issues: UIIssue[] = useMemo(() => {
    return rawIssues.map((dto) => mapIssueDtoToUi(dto, userLookup));
  }, [rawIssues, userLookup]);

  // 6. Fetch Comments for Active Issue in Drawer
  const { data: activeIssueComments = [] } = useQuery({
    queryKey: ["issue-comments", activeIssue?.uuid],
    queryFn: () => commentApi.getCommentsByIssue(activeIssue!.uuid),
    enabled: !!activeIssue?.uuid,
  });

  // Keyboard Shortcuts
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setShowCommand(true);
      }
      if (event.key === "Escape") {
        setShowCommand(false);
        setShowNotifications(false);
        setWorkspaceOpen(false);
        setShowCreate(false);
        setShowCreateOrg(false);
        setShowCreateProject(false);
        setShowInviteMember(false);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  // Compute Statistics from real issues
  const counts = useMemo(
    () => ({
      total: issues.length,
      todo: issues.filter((issue) => issue.status === "todo").length,
      progress: issues.filter((issue) => issue.status === "in-progress").length,
      review: issues.filter((issue) => issue.status === "in-review").length,
      done: issues.filter((issue) => issue.status === "done").length,
    }),
    [issues]
  );

  // Filtered Issues
  const filteredIssues = useMemo(() => {
    return issues.filter((issue) => {
      const matchesSearch = `${issue.id} ${issue.title} ${issue.assignee} ${issue.labels.join(" ")}`
        .toLowerCase()
        .includes(search.toLowerCase());
      const matchesStatus = statusFilter === "all" || issue.status === statusFilter;
      const matchesPriority = priorityFilter === "all" || issue.priority === priorityFilter;
      const matchesAssignee = assigneeFilter === "all"
        || (assigneeFilter === "unassigned" ? !issue.assigneeId : issue.assigneeId === assigneeFilter);
      return matchesSearch && matchesStatus && matchesPriority && matchesAssignee;
    });
  }, [assigneeFilter, issues, priorityFilter, search, statusFilter]);

  // =========================================================================
  // MUTATIONS (REAL SPRING BOOT ENDPOINTS)
  // =========================================================================

  // Move Issue Status (Board Drag-and-drop & Drawer status) with optimistic update
  const moveIssueMutation = useMutation({
    mutationFn: ({ uuid, status }: { uuid: string; status: UIStatus }) =>
      issueApi.updateStatus(uuid, uiStatusToBackend(status)),
    onMutate: async ({ uuid, status }) => {
      await queryClient.cancelQueries({ queryKey: ["project-issues", activeProject?.id] });
      const previousIssues = queryClient.getQueryData<IssueDto[]>(["project-issues", activeProject?.id]);
      if (previousIssues) {
        queryClient.setQueryData<IssueDto[]>(
          ["project-issues", activeProject?.id],
          previousIssues.map((i) => (i.id === uuid ? { ...i, status: uiStatusToBackend(status) } : i))
        );
      }
      return { previousIssues };
    },
    onError: (err: any, _, context) => {
      if (context?.previousIssues) {
        queryClient.setQueryData(["project-issues", activeProject?.id], context.previousIssues);
      }
      const msg =
        err.response?.status === 403
          ? "Permission denied: You cannot modify issues in this project."
          : err.response?.data?.message || "Failed to update issue status";
      toast.error(msg);
    },
    onSuccess: (updated) => {
      const uiSt = backendStatusToUi(updated.status);
      toast.success(`Issue moved to ${statusMeta[uiSt]?.label || updated.status}`);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["project-issues", activeProject?.id] });
    },
  });

  const moveIssue = (uuidOrKey: string, status: UIStatus) => {
    const target = issues.find((i) => i.id === uuidOrKey || i.uuid === uuidOrKey);
    if (!target) return;
    moveIssueMutation.mutate({ uuid: target.uuid, status });
  };

  // Create Issue Mutation
  const createIssueMutation = useMutation({
    mutationFn: (payload: CreateIssuePayload) => issueApi.createIssue(activeProject!.id, payload),
    onSuccess: (newIssue) => {
      queryClient.invalidateQueries({ queryKey: ["project-issues", activeProject?.id] });
      setNewTitle("");
      setNewDescription("");
      setShowCreate(false);
      setView("list");
      toast.success(`${newIssue.issueKey} created successfully`);
    },
    onError: (err: any) => {
      const msg =
        err.response?.status === 403
          ? "Permission denied: You do not have permission to create issues."
          : err.response?.data?.message || "Failed to create issue";
      toast.error(msg);
    },
  });

  const handleCreateIssue = () => {
    if (!newTitle.trim()) return;
    if (!activeProject) {
      toast.error("Select a project before creating an issue.");
      return;
    }
    createIssueMutation.mutate({
      title: newTitle.trim(),
      description: newDescription.trim() || undefined,
      issueType: "TASK",
      status: "TODO",
      priority: uiPriorityToBackend(newPriority),
      assigneeId: newAssigneeId || undefined,
    });
  };

  // Delete Issue Mutation
  const deleteIssueMutation = useMutation({
    mutationFn: (uuid: string) => issueApi.deleteIssue(uuid),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-issues", activeProject?.id] });
      setActiveIssue(null);
      toast.success("Issue deleted");
    },
    onError: (err: any) => {
      const msg =
        err.response?.status === 403
          ? "Permission denied: You cannot delete this issue."
          : err.response?.data?.message || "Failed to delete issue";
      toast.error(msg);
    },
  });

  // Update Issue Details Mutation (Title, Priority, Assignee)
  const updateIssueMutation = useMutation({
    mutationFn: ({ uuid, updates }: { uuid: string; updates: UpdateIssuePayload }) =>
      issueApi.updateIssue(uuid, updates),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ["project-issues", activeProject?.id] });
      if (activeIssue?.uuid === updated.id) {
        setActiveIssue(mapIssueDtoToUi(updated, userLookup));
      }
    },
    onError: (err: any) => {
      const msg =
        err.response?.status === 403
          ? "Permission denied: You cannot modify this issue."
          : err.response?.data?.message || "Failed to update issue";
      toast.error(msg);
    },
  });

  // Add Subtask Mutation
  const createSubtaskMutation = useMutation({
    mutationFn: ({ parentUuid, title }: { parentUuid: string; title: string }) =>
      issueApi.createSubtask(parentUuid, {
        title,
        issueType: "SUBTASK",
        status: "TODO",
        priority: "MEDIUM",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-issues", activeProject?.id] });
      toast.success("Subtask added");
    },
    onError: (err: any) => {
      const msg =
        err.response?.status === 403
          ? "Permission denied: You cannot add subtasks."
          : err.response?.data?.message || "Failed to add subtask";
      toast.error(msg);
    },
  });

  // Add Comment Mutation
  const createCommentMutation = useMutation({
    mutationFn: ({ issueUuid, body }: { issueUuid: string; body: string }) =>
      commentApi.createComment(issueUuid, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["issue-comments", activeIssue?.uuid] });
      setComment("");
      toast.success("Comment added to live activity");
    },
    onError: (err: any) => {
      const msg =
        err.response?.status === 403
          ? "Permission denied: You cannot post comments in this project."
          : err.response?.data?.message || "Failed to post comment";
      toast.error(msg);
    },
  });

  // Delete Comment Mutation
  const deleteCommentMutation = useMutation({
    mutationFn: (commentId: string) => commentApi.deleteComment(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["issue-comments", activeIssue?.uuid] });
      toast.success("Comment deleted");
    },
    onError: (err: any) => {
      const msg =
        err.response?.status === 403
          ? "Permission denied: You cannot delete this comment."
          : err.response?.data?.message || "Failed to delete comment";
      toast.error(msg);
    },
  });

  // Create Organization Mutation
  const createOrgMutation = useMutation({
    mutationFn: () =>
      organizationApi.createOrganization({
        name: orgName.trim(),
        slug: orgSlug.trim().toLowerCase(),
        description: orgDesc.trim() || undefined,
      }),
    onSuccess: (newOrg) => {
      queryClient.invalidateQueries({ queryKey: ["organizations"] });
      setSelectedOrgId(newOrg.id);
      setShowCreateOrg(false);
      setOrgName("");
      setOrgSlug("");
      setOrgDesc("");
      toast.success(`Organization ${newOrg.name} established`);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to create organization");
    },
  });

  // Delete Organization Mutation
  const deleteOrgMutation = useMutation({
    mutationFn: (id: string) => organizationApi.deleteOrganization(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organizations"] });
      setSelectedOrgId(null);
      setView("overview");
      toast.success("Organization deleted");
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to delete organization");
    },
  });

  // Create Project Mutation
  const createProjectMutation = useMutation({
    mutationFn: () =>
      projectApi.createProject(activeOrg!.id, {
        name: projName.trim(),
        projectKey: projKey.trim().toUpperCase(),
        description: projDesc.trim() || undefined,
      }),
    onSuccess: (newProj) => {
      queryClient.invalidateQueries({ queryKey: ["organization-projects", activeOrg?.id] });
      setSelectedProjectId(newProj.id);
      setShowCreateProject(false);
      setProjName("");
      setProjKey("");
      setProjDesc("");
      toast.success(`Project [${newProj.projectKey}] ${newProj.name} created`);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to create project");
    },
  });

  // Delete Project Mutation
  const deleteProjectMutation = useMutation({
    mutationFn: (id: string) => projectApi.deleteProject(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organization-projects", activeOrg?.id] });
      setSelectedProjectId(null);
      toast.success("Project deleted");
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to delete project");
    },
  });

  const updateProjectMutation = useMutation({
    mutationFn: (updates: { name: string; description?: string }) =>
      projectApi.updateProject(activeProject!.id, updates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organization-projects", activeOrg?.id] });
      toast.success("Project details updated");
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to update project");
    },
  });

  // Invite/Add Org Member Mutation
  const addOrgMemberMutation = useMutation({
    mutationFn: () =>
      organizationApi.addOrganizationMember(activeOrg!.id, {
        userId: inviteUserId.trim(),
        role: inviteRole,
      }),
    onSuccess: (newMember) => {
      queryClient.invalidateQueries({ queryKey: ["organization-members", activeOrg?.id] });
      setShowInviteMember(false);
      setInviteUserId("");
      toast.success(`Member added as ${newMember.role}`);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to add member to organization");
    },
  });

  // Remove Org Member Mutation
  const removeOrgMemberMutation = useMutation({
    mutationFn: (userId: string) => organizationApi.removeOrganizationMember(activeOrg!.id, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organization-members", activeOrg?.id] });
      queryClient.invalidateQueries({ queryKey: ["project-members", activeProject?.id] });
      toast.success("Member removed from organization");
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to remove member");
    },
  });

  // Update Org Member Role Mutation
  const updateOrgMemberRoleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: OrganizationRole }) =>
      organizationApi.updateOrganizationMemberRole(activeOrg!.id, userId, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organization-members", activeOrg?.id] });
      toast.success("Member role updated");
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to update member role");
    },
  });

  // 1. Loading State
  if (authLoading) {
    return (
      <div className="login-screen">
        <div className="login-grid" />
        <div className="login-city">
          <span />
          <span />
          <span />
          <span />
          <span />
          <span />
          <span />
        </div>
        <div className="login-card" style={{ textAlign: "center" }}>
          <AppMark />
          <span className="eyebrow">WAYNE ENTERPRISES // PRIVATE NETWORK</span>
          <h2 style={{ marginTop: "1rem", color: "#f59e0b" }}>INITIALIZING GOTHAM COMMAND...</h2>
          <p style={{ color: "#94a3b8", fontSize: "0.85rem" }}>Verifying secure terminal authorization credentials...</p>
        </div>
      </div>
    );
  }

  // 2. Unauthenticated State
  if (!isAuthenticated || !user) {
    return <LoginScreen onLogin={loginWithGoogle} />;
  }

  const pageTitle =
    view === "overview"
      ? "Overview"
      : view === "my-work"
      ? "My Work"
      : view === "list"
      ? "Issue list"
      : view === "board"
      ? "Board"
      : view === "members"
      ? "Members"
      : "Settings";

  const userInitials = getInitials(user.displayName || user.fullName);
  const userDisplayTitle = isSuperAdmin
    ? "Super Administrator"
    : isOrgAdmin
    ? "Organization Admin"
    : isProjectManager
    ? "Project Manager"
    : "Member";

  return (
    <div className="gotham-app">
      <aside className={`sidebar ${sidebarOpen ? "sidebar-open" : "sidebar-closed"}`}>
        <div className="sidebar-top">
          <div className="brand-row">
            <AppMark />
            {sidebarOpen && (
              <div>
                <div className="brand-name">GOTHAM</div>
                <div className="brand-subtitle">PROJECT OPERATIONS</div>
              </div>
            )}
          </div>
          <button
            className="icon-button sidebar-toggle"
            onClick={() => setSidebarOpen((open) => !open)}
            aria-label="Toggle navigation"
          >
            {sidebarOpen ? <PanelLeftClose size={17} /> : <PanelLeftOpen size={17} />}
          </button>
        </div>

        {/* WORKSPACE / ORGANIZATION SELECTOR */}
        <div className="workspace-wrap">
          <button className="workspace-switcher" onClick={() => setWorkspaceOpen((open) => !open)}>
            <span className="workspace-icon">
              {activeOrg ? activeOrg.name.slice(0, 2).toUpperCase() : "GC"}
            </span>
            {sidebarOpen && (
              <span className="workspace-copy">
                <strong>{activeOrg ? activeOrg.name : "No Organization"}</strong>
                <small>WORKSPACE</small>
              </span>
            )}
            {sidebarOpen && <ChevronDown size={15} className={workspaceOpen ? "rotate-180" : ""} />}
          </button>
          {workspaceOpen && sidebarOpen && (
            <div className="workspace-menu">
              {organizations.map((org) => (
                <button
                  key={org.id}
                  onClick={() => {
                    setSelectedOrgId(org.id);
                    setSelectedProjectId(null);
                    setWorkspaceOpen(false);
                    // Clear previous project/issue queries for tenant isolation
                    queryClient.removeQueries({ queryKey: ["organization-projects"] });
                    queryClient.removeQueries({ queryKey: ["project-issues"] });
                    toast.success(`Switched to ${org.name}`);
                  }}
                  className="workspace-option"
                >
                  <span className="workspace-icon workspace-gold">
                    {org.name.slice(0, 2).toUpperCase()}
                  </span>
                  <span>
                    <strong>{org.name}</strong>
                    <small>{org.slug}</small>
                  </span>
                  {activeOrg?.id === org.id && <Check size={14} />}
                </button>
              ))}
              <button
                className="workspace-create"
                onClick={() => {
                  setWorkspaceOpen(false);
                  setShowCreateOrg(true);
                }}
              >
                <Plus size={14} /> Create workspace
              </button>
            </div>
          )}
        </div>

        {/* MAIN NAVIGATION */}
        <nav className="main-nav">
          {[
            { id: "overview", label: "Overview", icon: LayoutDashboard },
            { id: "my-work", label: "My work", icon: BriefcaseBusiness },
          ].map((item) => (
            <button
              key={item.id}
              data-testid={`nav-${item.id}`}
              data-view={item.id}
              className={`nav-item ${view === item.id ? "active" : ""}`}
              onClick={() => setView(item.id as View)}
            >
              <item.icon size={17} />
              {sidebarOpen && <span>{item.label}</span>}
              {sidebarOpen && item.id === "my-work" && (
                <span className="nav-count">{issues.filter((i) => i.assigneeId === user.id).length}</span>
              )}
            </button>
          ))}

          {sidebarOpen && <div className="nav-label">PROJECT</div>}
          {[
            { id: "list", label: "Issue list", icon: ListFilter },
            { id: "board", label: "Board", icon: ColumnsIcon },
          ].map((item) => (
            <button
              key={item.id}
              data-testid={`nav-${item.id}`}
              data-view={item.id}
              className={`nav-item ${view === item.id ? "active" : ""}`}
              onClick={() => setView(item.id as View)}
            >
              <item.icon size={17} />
              {sidebarOpen && <span>{item.label}</span>}
            </button>
          ))}

          {/* PROJECT SELECTOR / ACTIVE PROJECT INDICATOR */}
          {sidebarOpen && activeProject && (
            <div className="project-link" style={{ cursor: "pointer" }}>
              <span className="project-dot" />
              <select
                data-testid="project-select"
                style={{
                  background: "transparent",
                  border: "none",
                  color: "#e2e8f0",
                  fontWeight: 600,
                  fontSize: "0.85rem",
                  width: "100%",
                  cursor: "pointer",
                  outline: "none",
                }}
                value={activeProject.id}
                onChange={(e) => {
                  setSelectedProjectId(e.target.value);
                  queryClient.removeQueries({ queryKey: ["project-issues"] });
                }}
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id} style={{ background: "#0f172a", color: "#e2e8f0" }}>
                    [{p.projectKey}] {p.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {sidebarOpen && isOrgPm && (
            <button
              className="nav-item"
              style={{ fontSize: "0.8rem", opacity: 0.85 }}
              onClick={() => setShowCreateProject(true)}
            >
              <Plus size={15} />
              <span>New Project</span>
            </button>
          )}

          {sidebarOpen && <div className="nav-label nav-label-spaced">MANAGE</div>}
          {[
            { id: "members", label: "Members", icon: UsersRound },
            { id: "settings", label: "Settings", icon: Settings2 },
          ].map((item) => (
            <button
              key={item.id}
              className={`nav-item ${view === item.id ? "active" : ""}`}
              onClick={() => setView(item.id as View)}
            >
              <item.icon size={17} />
              {sidebarOpen && <span>{item.label}</span>}
            </button>
          ))}
        </nav>

        {/* SIDEBAR FOOTER */}
        <div className="sidebar-footer">
          <div className="system-status">
            <span className="live-dot" />
            {sidebarOpen && <span>PostgreSQL Live // RBAC Verified</span>}
          </div>
          <button
            className="profile-card"
            onClick={() =>
              toast("Agent Identity", {
                description: `${user.displayName || user.fullName} · ${userDisplayTitle}`,
              })
            }
          >
            <Avatar initials={userInitials} />
            {sidebarOpen && (
              <span>
                <strong>{user.displayName || user.fullName}</strong>
                <small>{userDisplayTitle}</small>
              </span>
            )}
            {sidebarOpen && <MoreHorizontal size={16} />}
          </button>
        </div>
      </aside>

      <main className="main-content">
        {/* TOPBAR */}
        <header className="topbar">
          <div className="mobile-brand">
            <button
              className="icon-button"
              onClick={() => setSidebarOpen((open) => !open)}
              aria-label="Toggle navigation"
            >
              <Menu size={19} />
            </button>
            <AppMark small />
            <span>GOTHAM</span>
          </div>
          <div className="breadcrumbs">
            <span>{activeOrg ? activeOrg.name.toUpperCase() : "GOTHAM COMMAND"}</span>
            <ChevronRight size={14} />
            <strong>{pageTitle.toUpperCase()}</strong>
          </div>
          <div className="topbar-actions">
            <button className="command-trigger" onClick={() => setShowCommand(true)}>
              <Search size={15} />
              <span>Search operations</span>
              <kbd>⌘ K</kbd>
            </button>
            <div className="notification-wrap">
              <button
                className="icon-button"
                onClick={() => setShowNotifications((open) => !open)}
                aria-label="Notifications"
              >
                <Bell size={18} />
                <span className="notification-dot" />
              </button>
              {showNotifications && (
                <div className="notification-popover">
                  <div className="popover-heading">
                    <strong>Operations Feed</strong>
                    <span>{counts.total} active</span>
                  </div>
                  <div className="notification-item">
                    <span className="notification-mark gold">
                      <Activity size={14} />
                    </span>
                    <span>
                      <strong>{activeProject ? activeProject.name : "System"}</strong> is synchronized
                      <small>Authoritative PostgreSQL Data</small>
                    </span>
                  </div>
                </div>
              )}
            </div>
            <button className="top-avatar" onClick={authLogout} title="Lock session">
              <Avatar initials={userInitials} />
            </button>
          </div>
        </header>

        {/* PAGE VIEWS */}
        <div className="page-shell">
          {view === "overview" && (
            <Overview
              counts={counts}
              issues={issues}
              activeProject={activeProject}
              user={user}
              onOpenIssue={setActiveIssue}
              onCreate={() => setShowCreate(true)}
              onView={(next) => setView(next)}
              isViewer={isViewer}
            />
          )}
          {view === "my-work" && (
            <MyWork
              issues={issues}
              user={user}
              onOpenIssue={setActiveIssue}
              onView={(next) => setView(next)}
            />
          )}
          {view === "list" && (
            <ListView
              issues={filteredIssues}
              search={search}
              setSearch={setSearch}
              statusFilter={statusFilter}
              setStatusFilter={setStatusFilter}
              priorityFilter={priorityFilter}
              setPriorityFilter={setPriorityFilter}
              assigneeFilter={assigneeFilter}
              setAssigneeFilter={setAssigneeFilter}
              orgMembers={orgMembers}
              onOpenIssue={setActiveIssue}
              onCreate={() => setShowCreate(true)}
              isViewer={isViewer}
            />
          )}
          {view === "board" && (
            <BoardView
              issues={filteredIssues}
              onOpenIssue={setActiveIssue}
              onMove={moveIssue}
              onCreate={() => setShowCreate(true)}
              isViewer={isViewer}
            />
          )}
          {view === "members" && (
            <MembersView
              orgMembers={orgMembers}
              isOrgAdmin={isOrgAdmin}
              onInvite={() => setShowInviteMember(true)}
              onRemoveMember={(userId) => removeOrgMemberMutation.mutate(userId)}
              onUpdateRole={(userId, role) => updateOrgMemberRoleMutation.mutate({ userId, role })}
            />
          )}
          {view === "settings" && (
            <SettingsView
              activeOrg={activeOrg}
              activeProject={activeProject}
              isOrgAdmin={isOrgAdmin}
              onLogout={authLogout}
              onDeleteOrg={() => activeOrg && deleteOrgMutation.mutate(activeOrg.id)}
              onDeleteProject={() => activeProject && deleteProjectMutation.mutate(activeProject.id)}
              onUpdateProject={(updates) => updateProjectMutation.mutate(updates)}
            />
          )}
        </div>
      </main>

      {/* ISSUE DRAWER */}
      {activeIssue && (
        <IssueDrawer
          issue={activeIssue}
          comments={activeIssueComments}
          comment={comment}
          setComment={setComment}
          onClose={() => setActiveIssue(null)}
          onMove={moveIssue}
          onUpdatePriority={(uuid, priority) =>
            updateIssueMutation.mutate({ uuid, updates: { priority: uiPriorityToBackend(priority) } })
          }
          orgMembers={orgMembers}
          onUpdateAssignee={(uuid, assigneeId) =>
            updateIssueMutation.mutate({ uuid, updates: { assigneeId } })
          }
          onUpdateIssue={(uuid, updates) => updateIssueMutation.mutate({ uuid, updates })}
          onComment={() =>
            comment.trim() &&
            createCommentMutation.mutate({ issueUuid: activeIssue.uuid, body: comment.trim() })
          }
          onDeleteComment={(commentId) => deleteCommentMutation.mutate(commentId)}
          onDelete={() => deleteIssueMutation.mutate(activeIssue.uuid)}
          onAddSubtask={(title) =>
            createSubtaskMutation.mutate({ parentUuid: activeIssue.uuid, title })
          }
          isViewer={isViewer}
        />
      )}

      {/* CREATE ISSUE MODAL */}
      {showCreate && (
        <CreateIssueModal
          title={newTitle}
          setTitle={setNewTitle}
          description={newDescription}
          setDescription={setNewDescription}
          priority={newPriority}
          setPriority={setNewPriority}
          assigneeId={newAssigneeId}
          setAssigneeId={setNewAssigneeId}
          orgMembers={orgMembers}
          projectName={activeProject?.name || "Gotham Command"}
          onClose={() => setShowCreate(false)}
          onCreate={handleCreateIssue}
        />
      )}

      {/* CREATE WORKSPACE MODAL */}
      {showCreateOrg && (
        <div
          className="modal-backdrop"
          onClick={(e) => e.target === e.currentTarget && setShowCreateOrg(false)}
        >
          <div className="create-modal">
            <div className="modal-header">
              <div>
                <span className="panel-kicker">ORGANIZATION SETUP</span>
                <h2>Create New Workspace</h2>
              </div>
              <button className="icon-button" onClick={() => setShowCreateOrg(false)}>
                <X size={18} />
              </button>
            </div>
            <label className="modal-field">
              <span>Organization Name</span>
              <input
                autoFocus
                placeholder="e.g. Wayne Security Ops"
                value={orgName}
                onChange={(e) => setOrgName(e.target.value)}
              />
            </label>
            <label className="modal-field">
              <span>Slug (Unique identifier)</span>
              <input
                placeholder="e.g. wayne-sec"
                value={orgSlug}
                onChange={(e) => setOrgSlug(e.target.value)}
              />
            </label>
            <label className="modal-field">
              <span>Description (Optional)</span>
              <textarea
                placeholder="Operational purpose..."
                value={orgDesc}
                onChange={(e) => setOrgDesc(e.target.value)}
              />
            </label>
            <div className="modal-footer">
              <span />
              <div>
                <button className="ghost-button" onClick={() => setShowCreateOrg(false)}>
                  Cancel
                </button>
                <button
                  className="gold-button"
                  onClick={() => createOrgMutation.mutate()}
                  disabled={!orgName.trim() || !orgSlug.trim()}
                >
                  Create Organization <ArrowUpRight size={15} />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* CREATE PROJECT MODAL */}
      {showCreateProject && (
        <div
          className="modal-backdrop"
          onClick={(e) => e.target === e.currentTarget && setShowCreateProject(false)}
        >
          <div className="create-modal">
            <div className="modal-header">
              <div>
                <span className="panel-kicker">{activeOrg?.name.toUpperCase()} / NEW PROJECT</span>
                <h2>Initialize Project Workspace</h2>
              </div>
              <button className="icon-button" onClick={() => setShowCreateProject(false)}>
                <X size={18} />
              </button>
            </div>
            <label className="modal-field">
              <span>Project Name</span>
              <input
                autoFocus
                placeholder="e.g. Batmobile Diagnostics"
                value={projName}
                onChange={(e) => setProjName(e.target.value)}
              />
            </label>
            <label className="modal-field">
              <span>Project Key (Uppercase letters, e.g. BAT, SEC)</span>
              <input
                placeholder="e.g. BAT"
                value={projKey}
                onChange={(e) => setProjKey(e.target.value.toUpperCase())}
              />
            </label>
            <label className="modal-field">
              <span>Description</span>
              <textarea
                placeholder="Project objective..."
                value={projDesc}
                onChange={(e) => setProjDesc(e.target.value)}
              />
            </label>
            <div className="modal-footer">
              <span />
              <div>
                <button className="ghost-button" onClick={() => setShowCreateProject(false)}>
                  Cancel
                </button>
                <button
                  className="gold-button"
                  onClick={() => createProjectMutation.mutate()}
                  disabled={!projName.trim() || !projKey.trim()}
                >
                  Create Project <ArrowUpRight size={15} />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* INVITE MEMBER MODAL */}
      {showInviteMember && (
        <div
          className="modal-backdrop"
          onClick={(e) => e.target === e.currentTarget && setShowInviteMember(false)}
        >
          <div className="create-modal">
            <div className="modal-header">
              <div>
                <span className="panel-kicker">ACCESS CONTROL</span>
                <h2>Add Member to {activeOrg?.name}</h2>
              </div>
              <button className="icon-button" onClick={() => setShowInviteMember(false)}>
                <X size={18} />
              </button>
            </div>
            <label className="modal-field">
              <span>User UUID</span>
              <input
                autoFocus
                placeholder="User UUID from PostgreSQL"
                value={inviteUserId}
                onChange={(e) => setInviteUserId(e.target.value)}
              />
            </label>
            <label className="modal-field">
              <span>Organization Role</span>
              <select
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as OrganizationRole)}
              >
                <option value="MEMBER">MEMBER (Standard Access)</option>
                <option value="PROJECT_MANAGER">PROJECT_MANAGER (Can Create Projects)</option>
                <option value="ORG_ADMIN">ORG_ADMIN (Full Tenant Management)</option>
                {isSuperAdmin && <option value="SUPER_ADMIN">SUPER_ADMIN (System-wide Bypass)</option>}
              </select>
            </label>
            <div className="modal-footer">
              <span />
              <div>
                <button className="ghost-button" onClick={() => setShowInviteMember(false)}>
                  Cancel
                </button>
                <button
                  className="gold-button"
                  onClick={() => addOrgMemberMutation.mutate()}
                  disabled={!inviteUserId.trim()}
                >
                  Assign Role <ArrowUpRight size={15} />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* COMMAND PALETTE */}
      {showCommand && (
        <CommandPalette
          issues={issues}
          onClose={() => setShowCommand(false)}
          onCreate={() => {
            setShowCommand(false);
            setShowCreate(true);
          }}
          onNavigate={(next) => {
            setShowCommand(false);
            setView(next);
          }}
          onSelectIssue={(issue) => {
            setShowCommand(false);
            setActiveIssue(issue);
          }}
        />
      )}
    </div>
  );
}

// =========================================================================
// SUBCOMPONENTS PRESERVING EXACT GOTHAM COMMAND DESIGN
// =========================================================================

function PageHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <div className="eyebrow">
          <span className="eyebrow-line" />
          {eyebrow}
        </div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}

function Overview({
  counts,
  issues,
  activeProject,
  user,
  onOpenIssue,
  onCreate,
  onView,
  isViewer,
}: {
  counts: { total: number; todo: number; progress: number; review: number; done: number };
  issues: UIIssue[];
  activeProject: ProjectDto | null;
  user: any;
  onOpenIssue: (issue: UIIssue) => void;
  onCreate: () => void;
  onView: (view: View) => void;
  isViewer: boolean;
}) {
  const percentComplete = counts.total > 0 ? Math.round((counts.done / counts.total) * 100) : 0;

  return (
    <div>
      <PageHeading
        eyebrow={`${activeProject ? activeProject.name.toUpperCase() : "GOTHAM"} / COMMAND CENTER`}
        title={`Good evening, ${user?.displayName || user?.fullName || "Agent"}.`}
        description={
          activeProject
            ? `Here’s the current pulse of [${activeProject.projectKey}] ${activeProject.name}.`
            : "Select or initialize a project workspace to commence operations."
        }
        action={
          !isViewer && (
            <button className="gold-button" onClick={onCreate}>
              <Plus size={16} /> Create issue
            </button>
          )
        }
      />
      <div className="signal-strip">
        <div className="signal-copy">
          <span className="signal-pulse">
            <span />
          </span>
          <span>
            <strong>Operations nominal</strong>
            <small>Live Spring Boot REST + PostgreSQL Connection</small>
          </span>
        </div>
        <div className="signal-metrics">
          <span>
            <Shield size={14} /> 99.98% uptime
          </span>
          <span>
            <Activity size={14} /> {counts.progress} in progress
          </span>
          <span>
            <Clock3 size={14} /> Real-time state
          </span>
        </div>
      </div>
      <div className="stats-grid">
        <StatCard label="Total issues" value={counts.total} detail="Active project records" tone="gold" icon={Target} />
        <StatCard label="To do" value={counts.todo} detail="Queued operations" tone="slate" icon={CircleDot} />
        <StatCard label="In progress" value={counts.progress} detail="Active threads" tone="blue" icon={Activity} />
        <StatCard label="Completed" value={counts.done} detail="Resolved issues" tone="green" icon={CheckCircle2} />
      </div>
      <div className="content-grid overview-grid">
        <section className="panel progress-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">PROJECT HEALTH</span>
              <h2>{activeProject ? activeProject.name : "Select Project"}</h2>
            </div>
            <button className="ghost-button" onClick={() => onView("list")}>
              View issues <ArrowUpRight size={14} />
            </button>
          </div>
          <p className="panel-description">
            {activeProject?.description || "Security monitoring and incident operations"}
          </p>
          <div className="progress-overview">
            <div className="progress-ring">
              <div>
                <strong>{percentComplete}%</strong>
                <small>complete</small>
              </div>
            </div>
            <div className="progress-breakdown">
              <div className="breakdown-row">
                <span>
                  <i className="legend-dot gold" />
                  In progress
                </span>
                <strong>{counts.progress}</strong>
              </div>
              <div className="breakdown-row">
                <span>
                  <i className="legend-dot blue" />
                  In review
                </span>
                <strong>{counts.review}</strong>
              </div>
              <div className="breakdown-row">
                <span>
                  <i className="legend-dot green" />
                  Done
                </span>
                <strong>{counts.done}</strong>
              </div>
              <div className="breakdown-row">
                <span>
                  <i className="legend-dot slate" />
                  Remaining
                </span>
                <strong>{counts.todo}</strong>
              </div>
            </div>
          </div>
          <div className="thin-progress">
            <span style={{ width: `${percentComplete}%` }} />
          </div>
          <div className="progress-footer">
            <span>Authoritative Data</span>
            <strong>{counts.total - counts.done} issues open</strong>
          </div>
        </section>

        <section className="panel activity-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">LIVE FEED</span>
              <h2>Recent activity</h2>
            </div>
            <button className="icon-button">
              <MoreHorizontal size={17} />
            </button>
          </div>
          <div className="activity-list">
            {issues.slice(0, 4).map((issue) => (
              <ActivityItem
                key={issue.id}
                icon="status"
                title={`${issue.assignee} working on`}
                subject={issue.id}
                time={issue.updated}
              />
            ))}
            {issues.length === 0 && (
              <div style={{ color: "#64748b", padding: "1rem", fontSize: "0.85rem" }}>
                No recent activity recorded for this workspace.
              </div>
            )}
          </div>
          <button className="panel-link" onClick={() => onView("list")}>
            View full queue <ArrowUpRight size={14} />
          </button>
        </section>
      </div>

      <div className="content-grid lower-grid">
        <section className="panel issues-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">PRIORITY QUEUE</span>
              <h2>Recently updated</h2>
            </div>
            <button className="ghost-button" onClick={() => onView("list")}>
              View all <ArrowUpRight size={14} />
            </button>
          </div>
          <div className="issue-preview-list">
            {issues.slice(0, 4).map((issue) => (
              <IssueRow key={issue.id} issue={issue} onClick={() => onOpenIssue(issue)} />
            ))}
            {issues.length === 0 && (
              <div style={{ color: "#64748b", padding: "1.5rem", textAlign: "center", fontSize: "0.85rem" }}>
                No issues found in this project.
              </div>
            )}
          </div>
        </section>

        <section className="panel members-panel">
          <div className="panel-header">
            <div>
              <span className="panel-kicker">THE WATCH</span>
              <h2>Project members</h2>
            </div>
            <button className="icon-button" onClick={() => onView("members")}>
              <ArrowUpRight size={16} />
            </button>
          </div>
          <div className="member-stack">
            <MemberRow initials={getInitials(user?.displayName)} name={user?.displayName || "You"} role="Current User" active />
          </div>
          <button className="panel-link" onClick={() => onView("members")}>
            Manage team <ArrowUpRight size={14} />
          </button>
        </section>
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  detail,
  tone,
  icon: Icon,
}: {
  label: string;
  value: number;
  detail: string;
  tone: string;
  icon: typeof Target;
}) {
  return (
    <div className={`stat-card stat-${tone}`}>
      <div className="stat-icon">
        <Icon size={17} />
      </div>
      <span className="stat-label">{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </div>
  );
}

function ActivityItem({
  icon,
  title,
  subject,
  time,
}: {
  icon: string;
  title: string;
  subject: string;
  time: string;
}) {
  return (
    <div className="activity-item">
      <span className={`activity-icon activity-${icon}`}>
        {icon === "comment" ? (
          <MessageSquare size={13} />
        ) : icon === "status" ? (
          <ArrowUpRight size={13} />
        ) : (
          <UserRound size={13} />
        )}
      </span>
      <p>
        {title} <strong>{subject}</strong>
        <small>{time}</small>
      </p>
    </div>
  );
}

function IssueRow({ issue, onClick }: { issue: UIIssue; onClick: () => void }) {
  return (
    <button className="issue-row" onClick={onClick}>
      <span className="issue-key">{issue.id}</span>
      <span className="issue-title">{issue.title}</span>
      <StatusPill status={issue.status} />
      <PriorityBadge priority={issue.priority} />
      <Avatar initials={issue.initials} />
    </button>
  );
}

function MemberRow({
  initials,
  name,
  role,
  active,
}: {
  initials: string;
  name: string;
  role: string;
  active?: boolean;
}) {
  return (
    <div className="member-row">
      <div className="avatar-wrap">
        <Avatar initials={initials} />
        <span className={active ? "member-online" : "member-away"} />
      </div>
      <span>
        <strong>{name}</strong>
        <small>{role}</small>
      </span>
      <MoreHorizontal size={15} />
    </div>
  );
}

function MyWork({
  issues,
  user,
  onOpenIssue,
  onView,
}: {
  issues: UIIssue[];
  user: any;
  onOpenIssue: (issue: UIIssue) => void;
  onView: (view: View) => void;
}) {
  const mine = issues.filter((issue) => issue.assigneeId === user?.id || issue.assignee === user?.displayName);

  return (
    <div>
      <PageHeading
        eyebrow="PERSONAL QUEUE"
        title="My work"
        description="Your assigned operations across this workspace."
        action={
          <button className="ghost-button strong" onClick={() => onView("list")}>
            Open issue list <ArrowUpRight size={15} />
          </button>
        }
      />
      <div className="work-tabs">
        <button className="active">
          Assigned to me <span>{mine.length}</span>
        </button>
      </div>
      <section className="panel full-panel">
        <div className="panel-header">
          <div>
            <span className="panel-kicker">NEXT ACTIONS</span>
            <h2>Issues requiring your attention</h2>
          </div>
          <span className="muted-label">{mine.length} issues</span>
        </div>
        <div className="issue-preview-list">
          {mine.map((issue) => (
            <IssueRow key={issue.id} issue={issue} onClick={() => onOpenIssue(issue)} />
          ))}
          {mine.length === 0 && (
            <div style={{ color: "#64748b", padding: "2rem", textAlign: "center", fontSize: "0.85rem" }}>
              No tasks currently assigned to you in this project.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function ListView({
  issues,
  search,
  setSearch,
  statusFilter,
  setStatusFilter,
  priorityFilter,
  setPriorityFilter,
  assigneeFilter,
  setAssigneeFilter,
  orgMembers,
  onOpenIssue,
  onCreate,
  isViewer,
}: {
  issues: UIIssue[];
  search: string;
  setSearch: (value: string) => void;
  statusFilter: UIStatus | "all";
  setStatusFilter: (value: UIStatus | "all") => void;
  priorityFilter: UIPriority | "all";
  setPriorityFilter: (value: UIPriority | "all") => void;
  assigneeFilter: string;
  setAssigneeFilter: (value: string) => void;
  orgMembers: OrganizationMemberDto[];
  onOpenIssue: (issue: UIIssue) => void;
  onCreate: () => void;
  isViewer: boolean;
}) {
  return (
    <div>
      <PageHeading
        eyebrow="GOTHAM SECURITY / ISSUES"
        title="Issue list"
        description="Search, filter, and triage every open thread in real time."
        action={
          !isViewer && (
            <button className="gold-button" onClick={onCreate}>
              <Plus size={16} /> Create issue
            </button>
          )
        }
      />
      <section className="panel list-panel">
        <div className="list-toolbar">
          <div className="search-field">
            <Search size={16} />
            <input
              placeholder="Search by key, title, assignee, or label"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="filter-wrap">
            <Filter size={15} />
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as UIStatus | "all")}>
              <option value="all">All statuses</option>
              <option value="todo">To do</option>
              <option value="in-progress">In progress</option>
              <option value="in-review">In review</option>
              <option value="done">Done</option>
            </select>
            <select value={priorityFilter} onChange={(e) => setPriorityFilter(e.target.value as UIPriority | "all")}>
              <option value="all">All priorities</option>
              <option value="urgent">Urgent</option>
              <option value="high">High</option>
              <option value="medium">Medium</option>
              <option value="low">Low</option>
            </select>
            <select value={assigneeFilter} onChange={(e) => setAssigneeFilter(e.target.value)}>
              <option value="all">All assignees</option>
              <option value="unassigned">Unassigned</option>
              {orgMembers.map((member) => (
                <option key={member.user.id} value={member.user.id}>
                  {member.user.displayName || member.user.fullName || member.user.email}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="table-wrap">
          <div className="issue-table table-header">
            <span>KEY</span>
            <span>SUMMARY</span>
            <span>STATUS</span>
            <span>PRIORITY</span>
            <span>ASSIGNEE</span>
            <span>UPDATED</span>
            <span />
          </div>
          {issues.map((issue) => (
            <button
              className="issue-table"
              key={issue.id}
              data-testid={`issue-row-${issue.uuid}`}
              data-issue-id={issue.uuid}
              data-issue-key={issue.id}
              data-issue-status={issue.status}
              onClick={() => onOpenIssue(issue)}
            >
              <span className="issue-key">{issue.id}</span>
              <span className="table-summary">
                <strong>{issue.title}</strong>
                <small>{issue.labels.map((label) => `#${label}`).join("  ")}</small>
              </span>
              <StatusPill status={issue.status} />
              <PriorityBadge priority={issue.priority} />
              <span className="assignee-cell">
                <Avatar initials={issue.initials} />
                {issue.assignee}
              </span>
              <span className="updated-cell">{issue.updated}</span>
              <MoreHorizontal size={16} />
            </button>
          ))}
          {issues.length === 0 && (
            <div className="empty-state">
              <Search size={24} />
              <strong>No issues match this query</strong>
              <span>Adjust filters or create a new issue for this project.</span>
            </div>
          )}
        </div>
        <div className="table-footer">
          <span>Showing {issues.length} issues</span>
        </div>
      </section>
    </div>
  );
}

function BoardView({
  issues,
  onOpenIssue,
  onMove,
  onCreate,
  isViewer,
}: {
  issues: UIIssue[];
  onOpenIssue: (issue: UIIssue) => void;
  onMove: (id: string, status: UIStatus) => void;
  onCreate: () => void;
  isViewer: boolean;
}) {
  const [dragged, setDragged] = useState<string | null>(null);
  const statuses: UIStatus[] = ["todo", "in-progress", "in-review", "done"];

  return (
    <div>
      <PageHeading
        eyebrow="GOTHAM SECURITY / DELIVERY"
        title="Board"
        description="Move work through the operation with a clear line of sight."
        action={
          !isViewer && (
            <button className="gold-button" onClick={onCreate}>
              <Plus size={16} /> Create issue
            </button>
          )
        }
      />
      <div className="board-toolbar">
        <div className="board-summary">
          <span className="live-dot" />
          Live board <strong>{issues.length} visible issues</strong>
        </div>
      </div>
      <div className="board-grid">
        {statuses.map((status) => (
          <div
            className={`board-column column-${status}`}
            key={status}
            data-status={status}
            data-testid={`board-column-${status}`}
            onDragOver={(event) => event.preventDefault()}
            onDrop={() => {
              if (dragged && !isViewer) {
                onMove(dragged, status);
                setDragged(null);
              }
            }}
          >
            <div className="column-header">
              <span className="column-title">
                <span className="column-mark" />
                {statusMeta[status].label}
              </span>
              <span className="column-count">
                {issues.filter((issue) => issue.status === status).length}
              </span>
              <MoreHorizontal size={16} />
            </div>
            <div className="column-body">
              {issues
                .filter((issue) => issue.status === status)
                .map((issue) => (
                  <button
                    className={`board-card ${dragged === issue.uuid ? "is-dragging" : ""}`}
                    key={issue.id}
                    data-testid={`board-card-${issue.uuid}`}
                    data-issue-id={issue.uuid}
                    data-issue-key={issue.id}
                    data-issue-status={issue.status}
                    draggable={!isViewer}
                    onDragStart={() => !isViewer && setDragged(issue.uuid)}
                    onDragEnd={() => setDragged(null)}
                    onClick={() => onOpenIssue(issue)}
                  >
                    <div className="card-meta">
                      <span className="issue-key">{issue.id}</span>
                      <MoreHorizontal size={15} />
                    </div>
                    <strong>{issue.title}</strong>
                    <div className="card-labels">
                      {issue.labels.slice(0, 2).map((label) => (
                        <span key={label}>{label}</span>
                      ))}
                    </div>
                    <div className="card-footer">
                      <PriorityBadge priority={issue.priority} />
                      <span className="card-comments">
                        <MessageSquare size={13} />
                        {issue.subtasks.length}
                      </span>
                      <Avatar initials={issue.initials} />
                    </div>
                  </button>
                ))}
              {issues.filter((issue) => issue.status === status).length === 0 && (
                <div className="column-empty">Drop an issue here</div>
              )}
            </div>
            {!isViewer && (
              <button className="add-card" onClick={onCreate}>
                <Plus size={14} /> Add issue
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function MembersView({
  orgMembers,
  isOrgAdmin,
  onInvite,
  onRemoveMember,
  onUpdateRole,
}: {
  orgMembers: OrganizationMemberDto[];
  isOrgAdmin: boolean;
  onInvite: () => void;
  onRemoveMember: (userId: string) => void;
  onUpdateRole: (userId: string, role: OrganizationRole) => void;
}) {
  return (
    <div>
      <PageHeading
        eyebrow="GOTHAM OPERATIONS / ACCESS"
        title="Members"
        description="Manage the operatives and specialists who keep Gotham moving."
        action={
          isOrgAdmin && (
            <button className="gold-button" onClick={onInvite}>
              <Plus size={16} /> Add member
            </button>
          )
        }
      />
      <section className="panel members-table-panel">
        <div className="members-table table-header">
          <span>MEMBER</span>
          <span>ROLE</span>
          <span>STATUS</span>
          <span>ACTIONS</span>
        </div>
        {orgMembers.map((member) => (
          <div className="members-table member-table-row" key={member.id}>
            <span className="member-identity">
              <Avatar initials={getInitials(member.user?.displayName || member.user?.fullName)} />
              <span>
                <strong>{member.user?.displayName || member.user?.fullName || "Agent"}</strong>
                <small>{member.user?.email}</small>
              </span>
            </span>
            <span className="role-cell">
              {isOrgAdmin ? (
                <select
                  value={member.role}
                  onChange={(e) => onUpdateRole(member.user.id, e.target.value as OrganizationRole)}
                  style={{
                    background: "#0f172a",
                    color: "#f59e0b",
                    border: "1px solid rgba(245,158,11,0.2)",
                    borderRadius: "4px",
                    padding: "2px 6px",
                  }}
                >
                  <option value="MEMBER">MEMBER</option>
                  <option value="PROJECT_MANAGER">PROJECT_MANAGER</option>
                  <option value="ORG_ADMIN">ORG_ADMIN</option>
                  <option value="SUPER_ADMIN">SUPER_ADMIN</option>
                </select>
              ) : (
                member.role
              )}
            </span>
            <span>
              <span className={`member-state ${member.user?.active ? "active" : "away"}`}>
                <span />
                {member.user?.active ? "Active" : "Inactive"}
              </span>
            </span>
            <span>
              {isOrgAdmin && (
                <button
                  className="icon-button"
                  title="Remove Member"
                  onClick={() => onRemoveMember(member.user.id)}
                >
                  <Trash2 size={15} color="#ef4444" />
                </button>
              )}
            </span>
          </div>
        ))}
        {orgMembers.length === 0 && (
          <div style={{ padding: "2rem", textAlign: "center", color: "#64748b" }}>
            No members registered in this organization.
          </div>
        )}
      </section>
    </div>
  );
}

function SettingsView({
  activeOrg,
  activeProject,
  isOrgAdmin,
  onLogout,
  onDeleteOrg,
  onDeleteProject,
  onUpdateProject,
}: {
  activeOrg: OrganizationDto | null;
  activeProject: ProjectDto | null;
  isOrgAdmin: boolean;
  onLogout: () => void;
  onDeleteOrg: () => void;
  onDeleteProject: () => void;
  onUpdateProject: (updates: { name: string; description?: string }) => void;
}) {
  const [projectName, setProjectName] = useState(activeProject?.name || "");
  const [projectDescription, setProjectDescription] = useState(activeProject?.description || "");

  useEffect(() => {
    setProjectName(activeProject?.name || "");
    setProjectDescription(activeProject?.description || "");
  }, [activeProject?.description, activeProject?.name]);

  return (
    <div>
      <PageHeading
        eyebrow="GOTHAM OPERATIONS / CONFIGURATION"
        title="Settings"
        description="Tune the workspace and project environments to fit operational requirements."
      />
      <div className="settings-layout">
        <div className="settings-nav">
          <button className="active">
            <Settings2 size={16} /> General
          </button>
          <button onClick={() => toast("Integrations", { description: "Spring Boot REST + PostgreSQL active." })}>
            <BookOpen size={16} /> PostgreSQL
          </button>
        </div>
        <div className="settings-content">
          <section className="panel settings-panel">
            <div className="panel-header">
              <div>
                <span className="panel-kicker">ORGANIZATION IDENTITY</span>
                <h2>{activeOrg?.name || "Workspace"}</h2>
              </div>
            </div>
            <label className="setting-field">
              <span>Workspace Name</span>
              <input readOnly value={activeOrg?.name || ""} />
            </label>
            <label className="setting-field">
              <span>Slug</span>
              <input readOnly value={activeOrg?.slug || ""} />
            </label>
            <label className="setting-field">
              <span>Active Project</span>
              <input
                readOnly
                value={activeProject ? `[${activeProject.projectKey}] ${activeProject.name}` : "None"}
              />
            </label>
            {activeProject && (
              <>
                <label className="setting-field">
                  <span>Project Name</span>
                  <input value={projectName} onChange={(e) => setProjectName(e.target.value)} />
                </label>
                <label className="setting-field">
                  <span>Project Description</span>
                  <textarea value={projectDescription} onChange={(e) => setProjectDescription(e.target.value)} />
                </label>
                <button
                  className="gold-button"
                  disabled={!projectName.trim()}
                  onClick={() => onUpdateProject({ name: projectName.trim(), description: projectDescription })}
                >
                  Save project details
                </button>
              </>
            )}
          </section>

          {/* DANGER ZONE */}
          {isOrgAdmin && (
            <section className="panel settings-panel" style={{ border: "1px solid rgba(239,68,68,0.3)" }}>
              <div className="panel-header">
                <div>
                  <span className="panel-kicker" style={{ color: "#ef4444" }}>
                    DANGER ZONE
                  </span>
                  <h2>Destructive Actions</h2>
                </div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "1rem", marginTop: "1rem" }}>
                {activeProject && (
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <strong>Delete Project [{activeProject.projectKey}]</strong>
                      <p style={{ color: "#94a3b8", fontSize: "0.8rem", margin: 0 }}>
                        Permanently deletes all project issues and comments.
                      </p>
                    </div>
                    <button
                      className="ghost-button danger"
                      onClick={() => {
                        if (confirm(`Are you sure you want to delete project ${activeProject.name}?`)) {
                          onDeleteProject();
                        }
                      }}
                    >
                      Delete Project
                    </button>
                  </div>
                )}
                {activeOrg && (
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <strong>Delete Organization {activeOrg.name}</strong>
                      <p style={{ color: "#94a3b8", fontSize: "0.8rem", margin: 0 }}>
                        Permanently deletes organization, projects, and memberships.
                      </p>
                    </div>
                    <button
                      className="ghost-button danger"
                      onClick={() => {
                        if (confirm(`Are you sure you want to delete organization ${activeOrg.name}?`)) {
                          onDeleteOrg();
                        }
                      }}
                    >
                      Delete Workspace
                    </button>
                  </div>
                )}
              </div>
            </section>
          )}

          <section className="panel settings-panel">
            <div className="panel-header">
              <div>
                <span className="panel-kicker">SESSION</span>
                <h2>Security controls</h2>
              </div>
            </div>
            <button className="lock-button" onClick={onLogout}>
              <PanelLeftClose size={15} /> Lock current session
            </button>
          </section>
        </div>
      </div>
    </div>
  );
}

function IssueDrawer({
  issue,
  comments,
  orgMembers,
  comment,
  setComment,
  onClose,
  onMove,
  onUpdatePriority,
  onUpdateAssignee,
  onUpdateIssue,
  onComment,
  onDeleteComment,
  onDelete,
  onAddSubtask,
  isViewer,
}: {
  issue: UIIssue;
  comments: any[];
  orgMembers: OrganizationMemberDto[];
  comment: string;
  setComment: (value: string) => void;
  onClose: () => void;
  onMove: (uuid: string, status: UIStatus) => void;
  onUpdatePriority: (uuid: string, priority: UIPriority) => void;
  onUpdateAssignee: (uuid: string, assigneeId: string | null) => void;
  onUpdateIssue: (uuid: string, updates: UpdateIssuePayload) => void;
  onComment: () => void;
  onDeleteComment: (commentId: string) => void;
  onDelete: () => void;
  onAddSubtask: (title: string) => void;
  isViewer: boolean;
}) {
  const [subtaskTitle, setSubtaskTitle] = useState("");
  const [description, setDescription] = useState(issue.description);
  const [labels, setLabels] = useState(issue.labels.filter((label) => label !== issue.labels[0] || issue.labels.length > 1).join(", "));
  const completed = issue.subtasks.filter((task) => task.done).length;

  useEffect(() => setDescription(issue.description), [issue.description]);
  useEffect(() => setLabels(issue.labels.join(", ")), [issue.labels]);

  return (
    <div className="drawer-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <aside
        className="issue-drawer"
        data-testid="issue-drawer"
        data-issue-id={issue.uuid}
        data-issue-key={issue.id}
        data-issue-status={issue.status}
      >
        <div className="drawer-top">
          <span className="issue-key">{issue.id}</span>
          <div>
            <button className="icon-button" onClick={onClose}>
              <X size={18} />
            </button>
          </div>
        </div>
        <div className="drawer-scroll">
          <div className="drawer-heading">
            <div className="drawer-type">TASK <ChevronRight size={13} /> GOTHAM OPERATIONS</div>
            <h2>{issue.title}</h2>
            {!isViewer && (
              <div className="drawer-actions">
                <button
                  className="ghost-button danger"
                  onClick={() => {
                    if (confirm("Delete this issue?")) onDelete();
                  }}
                >
                  <Trash2 size={14} /> Delete
                </button>
              </div>
            )}
          </div>
          <div className="detail-grid">
            <label>
              <span>STATUS</span>
              <select
                disabled={isViewer}
                value={issue.status}
                onChange={(e) => onMove(issue.uuid, e.target.value as UIStatus)}
              >
                <option value="todo">To do</option>
                <option value="in-progress">In progress</option>
                <option value="in-review">In review</option>
                <option value="done">Done</option>
              </select>
            </label>
            <label>
              <span>PRIORITY</span>
              <select
                disabled={isViewer}
                value={issue.priority}
                onChange={(e) => onUpdatePriority(issue.uuid, e.target.value as UIPriority)}
              >
                <option value="urgent">Urgent</option>
                <option value="high">High</option>
                <option value="medium">Medium</option>
                <option value="low">Low</option>
              </select>
            </label>
            <label>
              <span>ASSIGNEE</span>
              <select
                disabled={isViewer}
                value={issue.assigneeId || ""}
                onChange={(e) => onUpdateAssignee(issue.uuid, e.target.value || null)}
              >
                <option value="">Unassigned</option>
                {orgMembers.map((member) => (
                  <option key={member.user.id} value={member.user.id}>
                    {member.user.displayName || member.user.fullName || member.user.email}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>REPORTER</span>
              <button className="detail-select" type="button">
                <Avatar initials="JD" />
                {issue.reporter}
              </button>
            </label>
            <label>
              <span>TYPE</span>
              <select
                disabled={isViewer}
                value={issue.labels[0]?.toUpperCase() || "TASK"}
                onChange={(e) => onUpdateIssue(issue.uuid, { issueType: e.target.value as BackendIssueType })}
              >
                <option value="TASK">Task</option>
                <option value="BUG">Bug</option>
                <option value="STORY">Story</option>
                <option value="EPIC">Epic</option>
              </select>
            </label>
          </div>

          <div className="drawer-section">
            <div className="section-title">
              <span>DESCRIPTION</span>
            </div>
            {isViewer ? (
              <p className="description-copy">{issue.description || "No description provided."}</p>
            ) : (
              <>
                <textarea value={description} onChange={(e) => setDescription(e.target.value)} />
                <button
                  className="ghost-button small"
                  onClick={() => onUpdateIssue(issue.uuid, { description })}
                >
                  Save description
                </button>
              </>
            )}
            {!isViewer && (
              <>
                <label className="modal-field">
                  <span>Labels <small>Comma-separated</small></span>
                  <input value={labels} onChange={(e) => setLabels(e.target.value)} />
                </label>
                <button
                  className="ghost-button small"
                  onClick={() => onUpdateIssue(issue.uuid, {
                    labels: labels.split(",").map((label) => label.trim().toLowerCase()).filter(Boolean),
                  })}
                >
                  Save labels
                </button>
              </>
            )}
          </div>

          {/* SUBTASKS */}
          <div className="drawer-section">
            <div className="section-title">
              <span>
                SUBTASKS{" "}
                <em>
                  {completed} / {issue.subtasks.length}
                </em>
              </span>
            </div>
            <div className="subtask-list">
              {issue.subtasks.map((task) => (
                <div className="subtask-row" key={task.id || task.title}>
                  <span className={`subtask-check ${task.done ? "checked" : ""}`}>
                    {task.done && <Check size={12} />}
                  </span>
                  <span className={task.done ? "completed-text" : ""}>{task.title}</span>
                </div>
              ))}
            </div>
            {!isViewer && (
              <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.75rem" }}>
                <input
                  style={{
                    background: "#0f172a",
                    border: "1px solid rgba(255,255,255,0.1)",
                    color: "#e2e8f0",
                    borderRadius: "4px",
                    padding: "4px 8px",
                    fontSize: "0.85rem",
                    flex: 1,
                  }}
                  placeholder="New subtask title..."
                  value={subtaskTitle}
                  onChange={(e) => setSubtaskTitle(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && subtaskTitle.trim()) {
                      onAddSubtask(subtaskTitle.trim());
                      setSubtaskTitle("");
                    }
                  }}
                />
                <button
                  className="ghost-button small"
                  disabled={!subtaskTitle.trim()}
                  onClick={() => {
                    onAddSubtask(subtaskTitle.trim());
                    setSubtaskTitle("");
                  }}
                >
                  <Plus size={13} /> Add
                </button>
              </div>
            )}
          </div>

          {/* COMMENTS & ACTIVITY */}
          <div className="drawer-section activity-section">
            <div className="section-title">
              <span>COMMENTS & LIVE ACTIVITY</span>
            </div>
            {!isViewer && (
              <div className="comment-box">
                <Avatar initials="ME" />
                <textarea
                  placeholder="Write a comment..."
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                />
                <button onClick={onComment} disabled={!comment.trim()}>
                  <ArrowUp size={15} />
                </button>
              </div>
            )}
            {comments.map((c) => (
              <div className="comment-item" key={c.id}>
                <Avatar initials={getInitials(c.author?.displayName || c.author?.fullName)} tone="blue" />
                <div style={{ width: "100%" }}>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <p>
                      <strong>{c.author?.displayName || c.author?.fullName || "Agent"}</strong>
                    </p>
                    <button
                      onClick={() => onDeleteComment(c.id)}
                      className="icon-button"
                      style={{ padding: 2 }}
                    >
                      <Trash2 size={12} color="#64748b" />
                    </button>
                  </div>
                  <p style={{ margin: "4px 0", color: "#cbd5e1" }}>{c.body}</p>
                  <small>{formatRelativeTime(c.createdAt)}</small>
                </div>
              </div>
            ))}
            {comments.length === 0 && (
              <div style={{ color: "#64748b", fontSize: "0.85rem", padding: "0.5rem 0" }}>
                No comments posted yet.
              </div>
            )}
          </div>
        </div>
      </aside>
    </div>
  );
}

function CreateIssueModal({
  title,
  setTitle,
  description,
  setDescription,
  priority,
  setPriority,
  assigneeId,
  setAssigneeId,
  orgMembers,
  projectName,
  onClose,
  onCreate,
}: {
  title: string;
  setTitle: (value: string) => void;
  description: string;
  setDescription: (value: string) => void;
  priority: UIPriority;
  setPriority: (value: UIPriority) => void;
  assigneeId: string;
  setAssigneeId: (value: string) => void;
  orgMembers: OrganizationMemberDto[];
  projectName: string;
  onClose: () => void;
  onCreate: () => void;
}) {
  return (
    <div className="modal-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="create-modal">
        <div className="modal-header">
          <div>
            <span className="panel-kicker">{projectName.toUpperCase()}</span>
            <h2>Create issue</h2>
          </div>
          <button className="icon-button" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        <label className="modal-field">
          <span>Summary</span>
          <input
            autoFocus
            placeholder="What needs to be done?"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        <label className="modal-field">
          <span>
            Description <small>Optional</small>
          </span>
          <textarea
            placeholder="Add context for the team..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <div className="modal-grid">
          <label className="modal-field">
            <span>Priority</span>
            <select value={priority} onChange={(e) => setPriority(e.target.value as UIPriority)}>
              <option value="urgent">Urgent</option>
              <option value="high">High</option>
              <option value="medium">Medium</option>
              <option value="low">Low</option>
            </select>
          </label>
          <label className="modal-field">
            <span>Assignee</span>
            <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
              <option value="">Unassigned</option>
              {orgMembers.map((m) => (
                <option key={m.user.id} value={m.user.id}>
                  {m.user.displayName || m.user.fullName || m.user.email}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="modal-footer">
          <span>
            <Command size={13} /> Authoritative PostgreSQL Persistence
          </span>
          <div>
            <button className="ghost-button" onClick={onClose}>
              Cancel
            </button>
            <button className="gold-button" onClick={onCreate} disabled={!title.trim()}>
              Create issue <ArrowUpRight size={15} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function CommandPalette({
  issues,
  onClose,
  onCreate,
  onNavigate,
  onSelectIssue,
}: {
  issues: UIIssue[];
  onClose: () => void;
  onCreate: () => void;
  onNavigate: (view: View) => void;
  onSelectIssue: (issue: UIIssue) => void;
}) {
  const [filter, setFilter] = useState("");
  const matched = issues.filter((i) =>
    `${i.id} ${i.title}`.toLowerCase().includes(filter.toLowerCase())
  );

  return (
    <div className="modal-backdrop command-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="command-palette">
        <div className="command-search">
          <Search size={17} />
          <input
            autoFocus
            placeholder="Search issues, projects, or actions"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
          <kbd>ESC</kbd>
        </div>
        <div className="command-section">
          <span>QUICK ACTIONS</span>
          <button onClick={onCreate}>
            <Plus size={16} />
            <span>Create new issue</span>
            <kbd>⌘ I</kbd>
          </button>
          <button onClick={() => onNavigate("board")}>
            <ColumnsIcon size={16} />
            <span>Open board</span>
            <kbd>G B</kbd>
          </button>
          <button onClick={() => onNavigate("list")}>
            <ListFilter size={16} />
            <span>Browse issue list</span>
            <kbd>G L</kbd>
          </button>
        </div>
        <div className="command-section">
          <span>PROJECT ISSUES</span>
          {matched.slice(0, 4).map((i) => (
            <button key={i.id} onClick={() => onSelectIssue(i)}>
              <span className="command-key">{i.id}</span>
              <span>{i.title}</span>
              <ArrowUpRight size={14} />
            </button>
          ))}
          {matched.length === 0 && (
            <span style={{ padding: "0.5rem 1rem", color: "#64748b", fontSize: "0.85rem" }}>
              No matching issues.
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function LoginScreen({ onLogin }: { onLogin: () => void }) {
  return (
    <div className="login-screen">
      <div className="login-grid" />
      <div className="login-city">
        <span />
        <span />
        <span />
        <span />
        <span />
        <span />
        <span />
      </div>
      <div className="login-topline">
        <div className="brand-row">
          <AppMark />
          <div>
            <div className="brand-name">GOTHAM</div>
            <div className="brand-subtitle">PROJECT OPERATIONS</div>
          </div>
        </div>
        <span>SECURE ACCESS // 2026.09</span>
      </div>
      <div className="login-card">
        <AppMark />
        <span className="eyebrow">WAYNE ENTERPRISES // PRIVATE NETWORK</span>
        <h1>Welcome to Gotham</h1>
        <p>Secure project operations for the teams keeping the city moving.</p>
        <button className="gold-button login-button" onClick={onLogin}>
          Access system with Google <ArrowUpRight size={16} />
        </button>
        <div className="login-note">
          <Shield size={14} /> Protected by Spring Security &amp; Google OAuth 2.0
        </div>
      </div>
      <div className="login-footer">
        <span>© 2026 Gotham Operations</span>
        <span>All systems monitored</span>
      </div>
    </div>
  );
}

export { Home };
