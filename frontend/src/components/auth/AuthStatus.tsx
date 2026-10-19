"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { apiRequest, apiRequestWithCsrf } from "@/lib/api";
import type { AuthUser } from "@/lib/auth-types";

function isAuthUser(value: unknown): value is AuthUser {
  if (typeof value !== "object" || value === null) return false;
  return (
    "id" in value && typeof value.id === "string" &&
    "firstname" in value && typeof value.firstname === "string" &&
    "username" in value && typeof value.username === "string" &&
    "lastname" in value && typeof value.lastname === "string" &&
    "email" in value && typeof value.email === "string"
  );
}

export default function AuthStatus() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSigningOut, setIsSigningOut] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest("/api/users/me", { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) return;
        const result: unknown = await response.json();
        if (isAuthUser(result)) setUser(result);
      })
      .catch(() => undefined)
      .finally(() => setIsLoading(false));

    return () => controller.abort();
  }, []);

  async function signOut() {
    setIsSigningOut(true);
    try {
      const response = await apiRequestWithCsrf("/api/auth/logout", { method: "POST" });
      if (response.ok) setUser(null);
    } catch {
      return;
    } finally {
      setIsSigningOut(false);
    }
  }

  if (isLoading) {
    return <span aria-label="Loading account" className="h-8 w-28 animate-pulse bg-[#18231d]/10" />;
  }

  if (user) {
    return (
      <div className="flex items-center gap-4">
        <span className="hidden text-sm text-[#536158] sm:inline">{user.firstname}</span>
        <button
          className="text-xs font-semibold uppercase tracking-[0.12em] text-[#536158] underline underline-offset-4 hover:text-[#18231d] disabled:opacity-60"
          disabled={isSigningOut}
          onClick={signOut}
          type="button"
        >
          {isSigningOut ? "Signing out" : "Sign out"}
        </button>
      </div>
    );
  }

  return (
    <nav aria-label="Account" className="flex items-center gap-4 text-xs font-semibold uppercase tracking-[0.12em]">
      <Link className="text-[#536158] hover:text-[#18231d]" href="/login">Sign in</Link>
      <Link className="border border-[#18231d]/25 px-3 py-2 text-[#18231d] hover:border-[#c35d3e]" href="/signup">
        Sign up
      </Link>
    </nav>
  );
}