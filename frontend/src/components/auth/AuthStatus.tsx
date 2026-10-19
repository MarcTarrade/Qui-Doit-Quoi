"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

import { apiRequest, apiRequestWithCsrf } from "@/lib/api";
import type { AuthUser } from "@/lib/auth-types";

type AuthStatusProps = {
  onUserChange?: (user: AuthUser | null) => void;
};

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

export default function AuthStatus({ onUserChange }: AuthStatusProps) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [error, setError] = useState("");
  const onUserChangeRef = useRef(onUserChange);

  useEffect(() => {
    onUserChangeRef.current = onUserChange;
  }, [onUserChange]);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest("/api/users/me", { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          onUserChangeRef.current?.(null);
          return;
        }
        const result: unknown = await response.json();
        const nextUser = isAuthUser(result) ? result : null;
        setUser(nextUser);
        onUserChangeRef.current?.(nextUser);
      })
      .catch(() => {
        if (!controller.signal.aborted) onUserChangeRef.current?.(null);
      })
      .finally(() => setIsLoading(false));

    return () => controller.abort();
  }, []);

  async function signOut() {
    setIsSigningOut(true);
    setError("");
    try {
      const response = await apiRequestWithCsrf("/api/auth/logout", { method: "POST" });
      if (!response.ok) {
        throw new Error(`Sign out failed (${response.status}).`);
      }
      setUser(null);
      onUserChangeRef.current?.(null);
      router.replace("/");
      router.refresh();
    } catch (signOutError) {
      setError(
        signOutError instanceof Error ? signOutError.message : "An unexpected error occurred.",
      );
    } finally {
      setIsSigningOut(false);
    }
  }

  return (
    <div className="site-header__account">
      {isLoading ? (
        <span aria-label="Loading account" className="auth-account-loading" />
      ) : user ? (
        <>
          <span className="site-header__identity">
            <span className="site-header__avatar" aria-hidden="true">
              {user.firstname.charAt(0).toUpperCase()}
            </span>
            <span className="site-header__identity-name">{user.firstname}</span>
          </span>
          <button
            className="site-header__signout"
            disabled={isSigningOut}
            onClick={signOut}
            type="button"
          >
            {isSigningOut ? "Signing out…" : "Sign out"}
          </button>
        </>
      ) : (
        <>
          <Link className="site-header__signin" href="/login">Sign in</Link>
          <Link className="site-header__signup" href="/signup">Sign up</Link>
        </>
      )}
      {error && <p className="site-header__error" role="alert">{error}</p>}
    </div>
  );
}