import { api } from "./api";
import type { ProjectDto, ProjectMemberDto, ProjectRole } from "./mappers";

export interface CreateProjectPayload {
  name: string;
  projectKey: string;
  description?: string;
}

export interface UpdateProjectPayload {
  name?: string;
  description?: string;
}

export const projectApi = {
  async getProjectsByOrganization(organizationId: string): Promise<ProjectDto[]> {
    const response = await api.get<ProjectDto[]>(`/organizations/${organizationId}/projects`);
    return response.data;
  },

  async getProject(id: string): Promise<ProjectDto> {
    const response = await api.get<ProjectDto>(`/projects/${id}`);
    return response.data;
  },

  async createProject(organizationId: string, payload: CreateProjectPayload): Promise<ProjectDto> {
    const response = await api.post<ProjectDto>(`/organizations/${organizationId}/projects`, payload);
    return response.data;
  },

  async updateProject(id: string, payload: UpdateProjectPayload): Promise<ProjectDto> {
    const response = await api.put<ProjectDto>(`/projects/${id}`, payload);
    return response.data;
  },

  async deleteProject(id: string): Promise<void> {
    await api.delete(`/projects/${id}`);
  },

  async getProjectMembers(id: string): Promise<ProjectMemberDto[]> {
    const response = await api.get<ProjectMemberDto[]>(`/projects/${id}/members`);
    return response.data;
  },

  async addProjectMember(
    id: string,
    payload: { userId: string; role: ProjectRole }
  ): Promise<ProjectMemberDto> {
    const response = await api.post<ProjectMemberDto>(`/projects/${id}/members`, payload);
    return response.data;
  },

  async updateProjectMemberRole(
    id: string,
    userId: string,
    payload: { role: ProjectRole }
  ): Promise<ProjectMemberDto> {
    const response = await api.put<ProjectMemberDto>(`/projects/${id}/members/${userId}`, payload);
    return response.data;
  },

  async removeProjectMember(id: string, userId: string): Promise<void> {
    await api.delete(`/projects/${id}/members/${userId}`);
  },
};
