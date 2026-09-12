import { api } from "./api";
import type { OrganizationDto, OrganizationMemberDto, OrganizationRole } from "./mappers";

export interface CreateOrganizationPayload {
  name: string;
  slug: string;
  description?: string;
}

export interface UpdateOrganizationPayload {
  name?: string;
  description?: string;
}

export const organizationApi = {
  async getOrganizations(): Promise<OrganizationDto[]> {
    const response = await api.get<OrganizationDto[]>("/organizations");
    return response.data;
  },

  async getOrganization(id: string): Promise<OrganizationDto> {
    const response = await api.get<OrganizationDto>(`/organizations/${id}`);
    return response.data;
  },

  async createOrganization(payload: CreateOrganizationPayload): Promise<OrganizationDto> {
    const response = await api.post<OrganizationDto>("/organizations", payload);
    return response.data;
  },

  async updateOrganization(id: string, payload: UpdateOrganizationPayload): Promise<OrganizationDto> {
    const response = await api.put<OrganizationDto>(`/organizations/${id}`, payload);
    return response.data;
  },

  async deleteOrganization(id: string): Promise<void> {
    await api.delete(`/organizations/${id}`);
  },

  async getOrganizationMembers(id: string): Promise<OrganizationMemberDto[]> {
    const response = await api.get<OrganizationMemberDto[]>(`/organizations/${id}/members`);
    return response.data;
  },

  async addOrganizationMember(
    id: string,
    payload: { userId: string; role: OrganizationRole }
  ): Promise<OrganizationMemberDto> {
    const response = await api.post<OrganizationMemberDto>(`/organizations/${id}/members`, payload);
    return response.data;
  },

  async updateOrganizationMemberRole(
    id: string,
    userId: string,
    payload: { role: OrganizationRole }
  ): Promise<OrganizationMemberDto> {
    const response = await api.put<OrganizationMemberDto>(`/organizations/${id}/members/${userId}`, payload);
    return response.data;
  },

  async removeOrganizationMember(id: string, userId: string): Promise<void> {
    await api.delete(`/organizations/${id}/members/${userId}`);
  },
};
