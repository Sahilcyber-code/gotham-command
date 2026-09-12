/**
 * Gotham Command - Frontend Authentication Client
 * Bridges into centralized authApi and api.ts in-memory token state.
 */

import { authApi, type AuthUser, type TokenResponse } from "@/services/authApi";
import { getAccessToken, setAccessToken, BACKEND_BASE_URL } from "@/services/api";

export type { AuthUser, TokenResponse };

let cachedUser: AuthUser | null = null;

export const authService = {
  getAccessToken(): string | null {
    return getAccessToken();
  },

  setAccessToken(token: string | null): void {
    setAccessToken(token);
  },

  getCurrentUserCached(): AuthUser | null {
    return cachedUser;
  },

  setCurrentUser(user: AuthUser | null): void {
    cachedUser = user;
  },

  loginWithGoogle(): void {
    authApi.loginWithGoogle();
  },

  async getMe(): Promise<AuthUser> {
    const user = await authApi.getMe();
    this.setCurrentUser(user);
    return user;
  },

  async refreshToken(): Promise<TokenResponse | null> {
    try {
      const res = await authApi.refreshToken();
      return res;
    } catch {
      this.setCurrentUser(null);
      return null;
    }
  },

  async logout(): Promise<void> {
    try {
      await authApi.logout();
    } finally {
      this.setCurrentUser(null);
    }
  },
};
