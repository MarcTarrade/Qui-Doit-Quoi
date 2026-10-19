"use client";

import Link from "next/link";

import AuthStatus from "@/components/auth/AuthStatus";
import type { AuthUser } from "@/lib/auth-types";

type SiteHeaderProps = {
  onUserChange?: (user: AuthUser | null) => void;
};

export default function SiteHeader({ onUserChange }: SiteHeaderProps) {
  return (
    <header className="site-header">
      <Link className="site-header__brand" href="/">
        Qui doit quoi
      </Link>
      <AuthStatus onUserChange={onUserChange} />
    </header>
  );
}
