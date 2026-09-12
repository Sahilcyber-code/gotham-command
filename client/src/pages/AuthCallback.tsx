import { useEffect, useState } from "react";
import { useLocation } from "wouter";
import { authService } from "@/lib/auth";

export default function AuthCallback() {
  const [, setLocation] = useLocation();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const errorParam = params.get("error");

    if (errorParam) {
      setError("Authentication failed. Please verify your credentials and try again.");
      return;
    }

    // Exchange HttpOnly refresh cookie for in-memory access token
    authService
      .refreshToken()
      .then((res) => {
        if (res?.accessToken) {
          return authService.getMe().then(() => setLocation("/"));
        } else {
          setError("Failed to establish secure session. Please try logging in again.");
        }
      })
      .catch(() => {
        setError("Secure session negotiation failed. Please try again.");
      });
  }, [setLocation]);

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#070b12] p-4 text-[#e2e8f0]">
        <div className="w-full max-w-md rounded-lg border border-[#334155]/60 bg-[#0f172a]/80 p-6 backdrop-blur-md shadow-2xl">
          <h2 className="mb-2 text-xl font-semibold tracking-tight text-red-400">Authentication Error</h2>
          <p className="mb-4 text-sm text-[#94a3b8]">{error}</p>
          <button
            onClick={() => setLocation("/")}
            className="w-full rounded bg-amber-500/20 px-4 py-2 text-sm font-medium text-amber-300 border border-amber-500/30 hover:bg-amber-500/30 transition-colors"
          >
            Return to Command Center
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#070b12] text-[#94a3b8]">
      <div className="flex flex-col items-center gap-3">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-amber-500/30 border-t-amber-400" />
        <p className="text-sm font-mono tracking-wider text-amber-400/80">AUTHENTICATING GOTHAM CREDENTIALS...</p>
      </div>
    </div>
  );
}
