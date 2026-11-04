"use client";

import Image from "next/image";
import { Link, useRouter } from "@/i18n/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

import { apiImageUrl, apiRequest, apiRequestWithCsrf } from "@/lib/api";
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
    "email" in value && typeof value.email === "string" &&
    "img" in value && (value.img === null || typeof value.img === "string")
  );
}

export default function AuthStatus({ onUserChange }: AuthStatusProps) {
  const router = useRouter();
  const t = useTranslations("Landing.auth");
  const profileT = useTranslations("Profile");
  const commonT = useTranslations("Common");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [error, setError] = useState("");
  const onUserChangeRef = useRef(onUserChange);
  const avatarUrl = apiImageUrl(user?.img ?? null);

  useEffect(() => {
    onUserChangeRef.current = onUserChange;
  }, [onUserChange]);

  useEffect(() => {
    function updateUser(event: Event) {
      if (!(event instanceof CustomEvent) || !isAuthUser(event.detail)) return;
      setUser(event.detail);
      onUserChangeRef.current?.(event.detail);
    }

    window.addEventListener("auth-user-updated", updateUser);
    return () => window.removeEventListener("auth-user-updated", updateUser);
  }, []);

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
        throw new Error(t("signOutFailed", { status: response.status }));
      }
      setUser(null);
      onUserChangeRef.current?.(null);
      router.replace("/");
      router.refresh();
    } catch (signOutError) {
      setError(
        signOutError instanceof Error && signOutError.name !== "TypeError"
          ? signOutError.message
          : commonT("unexpectedError"),
      );
    } finally {
      setIsSigningOut(false);
    }
  }

  return (
    <div className="site-header__account">
      {isLoading ? (
        <span aria-label={t("loadingAccount")} className="auth-account-loading" />
      ) : user ? (
        <>
          <Link
            aria-label={profileT("openProfile")}
            className="site-header__identity"
            href="/profile"
          >
            {avatarUrl ? (
              <Image
                alt=""
                className="site-header__avatar site-header__avatar-image"
                height={32}
                src={avatarUrl}
                unoptimized
                width={32}
              />
            ) : (
              <span className="site-header__avatar" aria-hidden="true">
                {user.firstname.charAt(0).toUpperCase()}
              </span>
            )}
            <span className="site-header__identity-name">{user.firstname}</span>
          </Link>
          <button
            className="site-header__signout"
            disabled={isSigningOut}
            onClick={signOut}
            type="button"
          >
            {isSigningOut ? t("signingOut") : t("signOut")}
          </button>
        </>
      ) : (
        <>
          <Link className="site-header__signin" href="/login">{t("signIn")}</Link>
          <Link className="site-header__signup" href="/signup">{t("signUp")}</Link>
        </>
      )}
      {error && <p className="site-header__error" role="alert">{error}</p>}
    </div>
  );
}