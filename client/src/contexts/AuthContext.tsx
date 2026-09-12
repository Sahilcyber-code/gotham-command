import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { authApi, type AuthUser } from "@/services/authApi";
import { getAccessToken, setAccessToken } from "@/services/api";

export interface AuthContextType {
  user: AuthUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  loginWithGoogle: () => void;
  logout: () => Promise<void>;
  refreshSession: () => Promise<AuthUser | null>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const refreshSession = useCallback(async (): Promise<AuthUser | null> => {
    try {
      // First try to fetch user with existing in-memory token
      if (getAccessToken()) {
        try {
          const me = await authApi.getMe();
          setUser(me);
          return me;
        } catch {
          // In-memory token may be expired; fall through to refresh
        }
      }

      // Refresh using HttpOnly cookie
      const tokenRes = await authApi.refreshToken();
      if (tokenRes?.accessToken) {
        setAccessToken(tokenRes.accessToken);
        const me = await authApi.getMe();
        setUser(me);
        return me;
      }
      setUser(null);
      return null;
    } catch {
      setUser(null);
      setAccessToken(null);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshSession();
  }, [refreshSession]);

  const loginWithGoogle = useCallback(() => {
    authApi.loginWithGoogle();
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      setAccessToken(null);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken: getAccessToken(),
        isAuthenticated: !!user,
        isLoading,
        loginWithGoogle,
        logout,
        refreshSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
