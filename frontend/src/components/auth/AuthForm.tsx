"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { apiRequestWithCsrf } from "@/lib/api";

type AuthFormProps = {
  mode: "login" | "signup";
};

export default function AuthForm({ mode }: AuthFormProps) {
  const router = useRouter();
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isSignup = mode === "signup";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const values = Object.fromEntries(new FormData(event.currentTarget).entries());
    if (isSignup && values.password !== values.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setIsPending(true);
    const payload = Object.fromEntries(
      Object.entries(values)
        .filter(([key]) => key !== "confirmPassword")
        .map(([key, value]) => [
          key,
          key === "password" ? String(value) : String(value).trim(),
        ]),
    );

    try {
      const response = await apiRequestWithCsrf(`/api/auth/${mode}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const result: unknown = await response.json().catch(() => null);
      if (!response.ok) {
        const message =
          typeof result === "object" && result !== null && "message" in result &&
          typeof result.message === "string"
            ? result.message
            : "Authentication could not be completed. Please try again.";
        setError(message);
        return;
      }

      router.replace("/");
      router.refresh();
    } catch {
      setError("Could not connect to the authentication service. Please try again.");
    } finally {
      setIsPending(false);
    }
  }

  return (
    <main className="auth-page">
      <section aria-labelledby="auth-title" className="auth-card">
        <header className="auth-header">
          <h1 className="auth-brand" id="auth-title">
            Qui Doit Quoi
          </h1>
        </header>

        <form className={`auth-form auth-form--${mode}`} onSubmit={handleSubmit}>
          <label className="auth-field">
            <span>{isSignup ? "Username" : "Email"}</span>
            <input
              autoComplete={isSignup ? "username" : "email"}
              maxLength={isSignup ? 50 : undefined}
              name={isSignup ? "username" : "email"}
              required
              type={isSignup ? "text" : "email"}
            />
          </label>

          {isSignup && (
            <>
              <label className="auth-field">
                <span>Last name</span>
                <input
                  autoComplete="family-name"
                  maxLength={100}
                  name="lastname"
                  required
                />
              </label>
              <label className="auth-field">
                <span>First name</span>
                <input
                  autoComplete="given-name"
                  maxLength={100}
                  name="firstname"
                  required
                />
              </label>
              <label className="auth-field">
                <span>Email</span>
                <input autoComplete="email" name="email" required type="email" />
              </label>
            </>
          )}

          <label className="auth-field">
            <span>Password</span>
            <input
              autoComplete={isSignup ? "new-password" : "current-password"}
              minLength={isSignup ? 8 : undefined}
              name="password"
              required
              type="password"
            />
          </label>

          {isSignup && (
            <label className="auth-field">
              <span>Confirm password</span>
              <input
                autoComplete="new-password"
                minLength={8}
                name="confirmPassword"
                required
                type="password"
              />
            </label>
          )}

          {error && (
            <p aria-live="polite" className="auth-error" role="alert">
              {error}
            </p>
          )}

          <button className="action-button auth-submit" disabled={isPending} type="submit">
            {isPending ? "Please wait..." : isSignup ? "Sign up" : "Log in"}
          </button>
        </form>

        <p className={`auth-switch auth-switch--${mode}`}>
          {isSignup && "Already have an account? "}
          <Link className="auth-switch-link" href={isSignup ? "/login" : "/signup"}>
            {isSignup ? (
              "Log in"
            ) : (
              <>
                Sign up here
                <Image
                  alt=""
                  aria-hidden="true"
                  className="auth-switch-icon"
                  height={20}
                  src="/auth/open-in-new.svg"
                  width={20}
                />
              </>
            )}
          </Link>
        </p>
      </section>
    </main>
  );
}