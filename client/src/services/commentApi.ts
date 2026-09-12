import { api } from "./api";
import type { CommentDto } from "./mappers";

export const commentApi = {
  async getCommentsByIssue(issueId: string): Promise<CommentDto[]> {
    const response = await api.get<CommentDto[]>(`/issues/${issueId}/comments`);
    return response.data;
  },

  async createComment(issueId: string, body: string): Promise<CommentDto> {
    const response = await api.post<CommentDto>(`/issues/${issueId}/comments`, { body });
    return response.data;
  },

  async deleteComment(commentId: string): Promise<void> {
    await api.delete(`/comments/${commentId}`);
  },
};
