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

let refreshPromise: Promise<TokenResponse> | null = null;

export const authApi = {
  async getMe(): Promise<AuthUser> {
    const response = await api.get<AuthUser>("/auth/me");
    return response.data;
  },

  async refreshToken(): Promise<TokenResponse> {
    if (!refreshPromise) {
      refreshPromise = api
        .post<TokenResponse>("/auth/refresh")
        .then((response) => {
          if (response.data?.accessToken) {
            setAccessToken(response.data.accessToken);
          }
          return response.data;
        })
        .finally(() => {
          refreshPromise = null;
        });
    }

    return refreshPromise;
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
