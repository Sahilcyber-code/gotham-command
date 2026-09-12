import type { AuthUser } from "./authApi";

export interface OrganizationDto {
  id: string;
  name: string;
  slug: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export type OrganizationRole = "SUPER_ADMIN" | "ORG_ADMIN" | "PROJECT_MANAGER" | "MEMBER";

export interface OrganizationMemberDto {
  id: string;
  organizationId: string;
  user: AuthUser;
  role: OrganizationRole;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectDto {
  id: string;
  organizationId: string;
  name: string;
  projectKey: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export type ProjectRole = "PROJECT_MANAGER" | "MEMBER" | "VIEWER";

export interface ProjectMemberDto {
  id: string;
  projectId: string;
  user: AuthUser;
  role: ProjectRole;
  createdAt: string;
  updatedAt: string;
}

export type BackendStatus = "TODO" | "IN_PROGRESS" | "IN_REVIEW" | "DONE";
export type BackendPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type BackendIssueType = "TASK" | "BUG" | "STORY" | "EPIC" | "SUBTASK";

export interface SubtaskDto {
  id: string;
  parentIssueId: string;
  issueKey: string;
  title: string;
  status: BackendStatus;
  priority: BackendPriority;
  assigneeId?: string;
  sortOrder?: number;
  dueDate?: string;
  createdAt: string;
  updatedAt: string;
}

export interface IssueDto {
  id: string;
  projectId: string;
  parentIssueId?: string;
  issueKey: string;
  title: string;
  description?: string;
  issueType: BackendIssueType;
  status: BackendStatus;
  priority: BackendPriority;
  reporterId?: string;
  assigneeId?: string;
  labels: string[];
  sortOrder?: number;
  dueDate?: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
  subtasks: SubtaskDto[];
}

export interface CommentDto {
  id: string;
  issueId: string;
  author: AuthUser;
  body: string;
  createdAt: string;
  updatedAt: string;
}

// UI Representation Types for Gotham Home
export type UIStatus = "todo" | "in-progress" | "in-review" | "done";
export type UIPriority = "urgent" | "high" | "medium" | "low";

export interface UIIssue {
  id: string;       // Visual issue key e.g. "BAT-1"
  uuid: string;     // Backend UUID for API mutations
  title: string;
  description: string;
  status: UIStatus;
  priority: UIPriority;
  assignee: string;
  assigneeId?: string;
  initials: string;
  reporter: string;
  reporterId?: string;
  labels: string[];
  updated: string;
  comments: number;
  subtasks: { id: string; title: string; done: boolean }[];
}

export function backendStatusToUi(status: BackendStatus): UIStatus {
  switch (status) {
    case "TODO":
      return "todo";
    case "IN_PROGRESS":
      return "in-progress";
    case "IN_REVIEW":
      return "in-review";
    case "DONE":
      return "done";
    default:
      return "todo";
  }
}

export function uiStatusToBackend(status: UIStatus): BackendStatus {
  switch (status) {
    case "todo":
      return "TODO";
    case "in-progress":
      return "IN_PROGRESS";
    case "in-review":
      return "IN_REVIEW";
    case "done":
      return "DONE";
    default:
      return "TODO";
  }
}

export function backendPriorityToUi(priority: BackendPriority): UIPriority {
  switch (priority) {
    case "URGENT":
      return "urgent";
    case "HIGH":
      return "high";
    case "MEDIUM":
      return "medium";
    case "LOW":
      return "low";
    default:
      return "medium";
  }
}

export function uiPriorityToBackend(priority: UIPriority): BackendPriority {
  switch (priority) {
    case "urgent":
      return "URGENT";
    case "high":
      return "HIGH";
    case "medium":
      return "MEDIUM";
    case "low":
      return "LOW";
    default:
      return "MEDIUM";
  }
}

export function getInitials(name?: string): string {
  if (!name || !name.trim()) return "GC";
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function formatRelativeTime(isoString?: string): string {
  if (!isoString) return "Just now";
  try {
    const date = new Date(isoString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "Just now";
    if (diffMins < 60) return `${diffMins} min ago`;
    if (diffHours < 24) return `${diffHours} hr${diffHours > 1 ? "s" : ""} ago`;
    if (diffDays === 1) return "Yesterday";
    if (diffDays < 7) return `${diffDays} days ago`;
    return date.toLocaleDateString();
  } catch {
    return "Recently";
  }
}

export function mapIssueDtoToUi(
  dto: IssueDto,
  userLookup?: (id?: string) => string
): UIIssue {
  const assigneeName = (userLookup && dto.assigneeId) ? userLookup(dto.assigneeId) : "Unassigned";
  const reporterName = (userLookup && dto.reporterId) ? userLookup(dto.reporterId) : "System";

  const subtasks = (dto.subtasks || []).map((sub) => ({
    id: sub.id,
    title: sub.title,
    done: sub.status === "DONE",
  }));

  return {
    id: dto.issueKey || `ISSUE-${dto.id.slice(0, 4).toUpperCase()}`,
    uuid: dto.id,
    title: dto.title,
    description: dto.description || "",
    status: backendStatusToUi(dto.status),
    priority: backendPriorityToUi(dto.priority),
    assignee: assigneeName,
    assigneeId: dto.assigneeId,
    initials: getInitials(assigneeName),
    reporter: reporterName,
    reporterId: dto.reporterId,
    labels: dto.labels?.length ? dto.labels : [dto.issueType ? dto.issueType.toLowerCase() : "task"],
    updated: formatRelativeTime(dto.updatedAt || dto.createdAt),
    comments: 0,
    subtasks,
  };
}
