import { api, BACKEND_BASE_URL, setAccessToken } from "./api";

export interface AuthUser {
  id: string;
  provider: string;
  providerId: string;
  username: string;
  email: string;
  displayName: string;
  fullName: string;
  avatarUrl?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
}

export const authApi = {
  async getMe(): Promise<AuthUser> {
    const response = await api.get<AuthUser>("/auth/me");
    return response.data;
  },

  async refreshToken(): Promise<TokenResponse> {
    const response = await api.post<TokenResponse>("/auth/refresh");
    if (response.data?.accessToken) {
      setAccessToken(response.data.accessToken);
    }
    return response.data;
  },

  async logout(): Promise<void> {
    try {
      await api.post("/auth/logout");
    } finally {
      setAccessToken(null);
    }
  },

  loginWithGoogle(): void {
    window.location.href = `${BACKEND_BASE_URL}/oauth2/authorization/google`;
  },
};
