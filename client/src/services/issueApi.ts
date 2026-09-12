import { api } from "./api";
import type { BackendIssueType, BackendPriority, BackendStatus, IssueDto } from "./mappers";

export interface CreateIssuePayload {
  title: string;
  description?: string;
  issueType: BackendIssueType;
  status?: BackendStatus;
  priority?: BackendPriority;
  assigneeId?: string;
  labels?: string[];
  sortOrder?: number;
  dueDate?: string;
}

export interface UpdateIssuePayload {
  title?: string;
  description?: string;
  issueType?: BackendIssueType;
  status?: BackendStatus;
  priority?: BackendPriority;
  assigneeId?: string | null;
  labels?: string[];
  sortOrder?: number;
  dueDate?: string;
}

export const issueApi = {
  async getIssuesByProject(projectId: string): Promise<IssueDto[]> {
    const response = await api.get<IssueDto[]>(`/projects/${projectId}/issues`);
    return response.data;
  },

  async getIssue(id: string): Promise<IssueDto> {
    const response = await api.get<IssueDto>(`/issues/${id}`);
    return response.data;
  },

  async createIssue(projectId: string, payload: CreateIssuePayload): Promise<IssueDto> {
    const response = await api.post<IssueDto>(`/projects/${projectId}/issues`, payload);
    return response.data;
  },

  async updateIssue(id: string, payload: UpdateIssuePayload): Promise<IssueDto> {
    const response = await api.put<IssueDto>(`/issues/${id}`, payload);
    return response.data;
  },

  async deleteIssue(id: string): Promise<void> {
    await api.delete(`/issues/${id}`);
  },

  async createSubtask(id: string, payload: CreateIssuePayload): Promise<IssueDto> {
    const response = await api.post<IssueDto>(`/issues/${id}/subtasks`, payload);
    return response.data;
  },

  async updateStatus(id: string, status: BackendStatus): Promise<IssueDto> {
    const response = await api.patch<IssueDto>(`/issues/${id}/status`, { status });
    return response.data;
  },

  async updateAssignee(id: string, assigneeId?: string | null): Promise<IssueDto> {
    const response = await api.patch<IssueDto>(`/issues/${id}/assignee`, { assigneeId: assigneeId ?? null });
    return response.data;
  },

  async updateSortOrder(id: string, sortOrder: number): Promise<IssueDto> {
    const response = await api.patch<IssueDto>(`/issues/${id}/sort-order`, { sortOrder });
    return response.data;
  },
};
