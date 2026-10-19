"use client";

import Image from "next/image";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";

import AuthStatus from "@/components/auth/AuthStatus";
import type { AuthUser } from "@/lib/auth-types";
import { apiRequest, apiRequestWithCsrf, apiUrl, checkApiConnection } from "@/lib/api";

type Group = {
  id: string;
  name: string;
  img: string | null;
  memberCount: number;
  expenseCount: number;
};

type GroupRecord = {
  id: string;
  name: string;
  img: string | null;
};

type LoadState = "idle" | "loading" | "ready" | "error";
type ApiStatus = "checking" | "online" | "offline";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseGroups(value: unknown): GroupRecord[] {
  if (!Array.isArray(value)) {
    throw new Error("The API returned an invalid groups list.");
  }

  return value.map((item) => {
    if (
      !isRecord(item) ||
      typeof item.id !== "string" ||
      typeof item.name !== "string" ||
      (item.img !== null && typeof item.img !== "string")
    ) {
      throw new Error("The API returned an invalid group.");
    }

    return { id: item.id, name: item.name, img: item.img };
  });
}

function parseItemCount(value: unknown, label: string): number {
  if (!Array.isArray(value)) {
    throw new Error(`The API returned an invalid ${label} list.`);
  }
  return value.length;
}

async function fetchCount(path: string, signal: AbortSignal, label: string) {
  const response = await apiRequest(path, { cache: "no-store", signal });
  if (!response.ok) {
    throw new Error(`Unable to load ${label} (${response.status}).`);
  }
  return parseItemCount(await response.json(), label);
}

export default function Home() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authResolved, setAuthResolved] = useState(false);
  const [groups, setGroups] = useState<Group[]>([]);
  const [groupsState, setGroupsState] = useState<LoadState>("idle");
  const [groupsError, setGroupsError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [apiStatus, setApiStatus] = useState<ApiStatus>("checking");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const groupUserId = user?.id ?? "";

  useEffect(() => {
    checkApiConnection()
      .then((connected) => setApiStatus(connected ? "online" : "offline"))
      .catch(() => setApiStatus("offline"));
  }, []);

  const handleUserChange = useCallback((nextUser: AuthUser | null) => {
    if (user?.id !== nextUser?.id) {
      setUser(nextUser);
      setGroups([]);
      setGroupsError("");
      setGroupsState(nextUser ? "loading" : "idle");
    }
    setAuthResolved(true);
  }, [user?.id]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (isCreateOpen && !dialog.open) dialog.showModal();
    if (!isCreateOpen && dialog.open) dialog.close();
  }, [isCreateOpen]);

  useEffect(() => {
    if (!groupUserId) return;

    const controller = new AbortController();

    async function loadGroups() {
      try {
        const response = await apiRequest(`/api/groups/user/${encodeURIComponent(groupUserId)}`, {
          cache: "no-store",
          signal: controller.signal,
        });
        if (!response.ok) {
          throw new Error(`Unable to load groups (${response.status}).`);
        }

        const groupRecords = parseGroups(await response.json());
        const loadedGroups = await Promise.all(
          groupRecords.map(async (group) => {
            const [memberCount, expenseCount] = await Promise.all([
              fetchCount(`/api/groups/${encodeURIComponent(group.id)}/members`, controller.signal, "members"),
              fetchCount(`/api/groups/${encodeURIComponent(group.id)}/expenses`, controller.signal, "expenses"),
            ]);
            return { ...group, memberCount, expenseCount };
          }),
        );

        if (!controller.signal.aborted) {
          setGroups(loadedGroups);
          setGroupsState("ready");
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setGroupsError(
          error instanceof Error ? error.message : "Une erreur inattendue est survenue.",
        );
        setGroupsState("error");
      }
    }

    void loadGroups();
    return () => controller.abort();
  }, [groupUserId, refreshKey]);

  function reloadGroups() {
    setGroupsError("");
    setGroupsState("loading");
    setRefreshKey((current) => current + 1);
  }

  function openCreateGroup() {
    setCreateError("");
    setIsCreateOpen(true);
  }

  async function createGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;

    const formData = new FormData(event.currentTarget);
    const name = String(formData.get("name") ?? "").trim();
    const description = String(formData.get("description") ?? "").trim();
    const currency = String(formData.get("currency") ?? "").trim().toUpperCase();
    setCreateError("");
    setIsCreating(true);

    try {
      const response = await apiRequestWithCsrf(`/api/groups/${encodeURIComponent(user.id)}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, description, currency }),
      });
      if (!response.ok) {
        throw new Error(`Group creation failed (${response.status}).`);
      }

      setIsCreateOpen(false);
      reloadGroups();
    } catch (error) {
      setCreateError(
        error instanceof Error ? error.message : "An unexpected error occurred.",
      );
    } finally {
      setIsCreating(false);
    }
  }

  if (!authResolved) {
    return (
      <main className="home-loading">
        <AuthStatus onUserChange={handleUserChange} showStatus={false} />
        <p>Loading your workspace…</p>
      </main>
    );
  }

  if (!user) {
    return (
      <main className="min-h-screen bg-[#f3f0e8] px-6 py-8 text-[#18231d] sm:px-10 lg:px-16">
        <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-6xl flex-col justify-between">
          <header className="flex items-center justify-between border-b border-[#18231d]/15 pb-5">
            <p className="text-sm font-semibold uppercase tracking-[0.18em]">Qui doit quoi</p>
          </header>

          <section className="grid gap-12 py-16 lg:grid-cols-[1.4fr_0.8fr] lg:items-end">
            <div>
              <p className="mb-5 text-sm font-medium uppercase tracking-[0.16em] text-[#c35d3e]">Shared expenses, made clear</p>
              <h1 className="max-w-3xl text-6xl font-semibold leading-[0.95] tracking-[-0.04em] sm:text-8xl">
                Keep the group math human.
              </h1>
              <p className="mt-8 max-w-xl text-lg leading-8 text-[#536158]">
                Your workspace is ready for groups, members, expenses, and simple settlements.
              </p>
            </div>

            <div className="border-l border-[#18231d]/20 pl-6 lg:mb-2">
              <p className="text-xs uppercase tracking-[0.16em] text-[#536158]">API connection</p>
              <div className="mt-4 flex items-center gap-3">
                <span className={`h-3 w-3 rounded-full ${apiStatus === "online" ? "bg-[#4f8a61]" : apiStatus === "offline" ? "bg-[#c35d3e]" : "animate-pulse bg-[#c7a64b]"}`} />
                <p className="text-xl font-medium">
                  {apiStatus === "checking" ? "Checking backend" : apiStatus === "online" ? "Backend online" : "Backend unavailable"}
                </p>
              </div>
              <p className="mt-3 break-all font-mono text-xs text-[#536158]">{apiUrl}</p>
            </div>
          </section>

          <footer className="flex flex-col gap-2 border-t border-[#18231d]/15 pt-5 text-sm text-[#536158] sm:flex-row sm:justify-between">
            <span>React + Next.js</span>
            <span>Connected to the Qui-Doit-Quoi API</span>
          </footer>
        </div>
      </main>
    );
  }

  return (
    <main className="home-dashboard">
      <div className="home-dashboard__content">
        <header className="home-dashboard__header">
          <h1>My groups</h1>
          <button
            className="home-create-button home-create-button--desktop"
            onClick={openCreateGroup}
            type="button"
          >
            <Image alt="" height={30} src="/groups/add-icon.svg" width={30} />
            <span>New group</span>
          </button>
        </header>

        <section aria-labelledby="groups-heading" className="home-groups">
          <h2 className="visually-hidden" id="groups-heading">Your groups</h2>
          {groupsState === "loading" && (
            <p className="home-groups__status" role="status">Loading groups…</p>
          )}
          {groupsState === "error" && (
            <div className="home-groups__message" role="alert">
              <p>{groupsError}</p>
              <button onClick={reloadGroups} type="button">
                Try again
              </button>
            </div>
          )}
          {groupsState === "ready" && groups.length === 0 && (
            <div className="home-groups__message">
              <p>You don’t have any groups yet.</p>
            </div>
          )}
          {groupsState === "ready" && groups.length > 0 && (
            <ul className="home-group-list">
              {groups.map((group) => (
                <li className="home-group-card" key={group.id}>
                  <Image
                    alt=""
                    className="home-group-card__avatar home-group-card__avatar--desktop"
                    height={59}
                    src={group.img || "/groups/group-avatar.svg"}
                    unoptimized
                    width={59}
                  />
                  <Image
                    alt=""
                    className="home-group-card__avatar home-group-card__avatar--mobile"
                    height={50}
                    src={group.img || "/groups/group-avatar-mobile.svg"}
                    unoptimized
                    width={50}
                  />
                  <div className="home-group-card__details">
                    <h3>{group.name}</h3>
                    <div className="home-group-card__metadata">
                      <span>{group.memberCount} {group.memberCount === 1 ? "member" : "members"}</span>
                      <Image alt="" className="home-group-card__separator" height={5} src="/groups/separator-dot.svg" width={5} />
                      <span className="home-group-card__expenses">
                        {group.expenseCount} {group.expenseCount === 1 ? "expense" : "expenses"}
                      </span>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

      </div>

      <button
        aria-label="Create a group"
        className="home-mobile-add"
        onClick={openCreateGroup}
        type="button"
      >
        <Image alt="" height={31} src="/groups/add-mobile.svg" width={31} />
      </button>

      {isCreateOpen && (
        <dialog
          aria-labelledby="create-group-title"
          className="home-create-dialog"
          onCancel={(event) => {
            event.preventDefault();
            if (!isCreating) setIsCreateOpen(false);
          }}
          ref={dialogRef}
        >
          <form className="home-create-form" onSubmit={createGroup}>
            <div className="home-create-form__header">
              <h2 id="create-group-title">New group</h2>
              <button
                aria-label="Close"
                disabled={isCreating}
                onClick={() => setIsCreateOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>
            <label>
              Group name
              <input autoComplete="off" maxLength={100} name="name" required />
            </label>
            <label>
              Description
              <textarea maxLength={1000} name="description" rows={3} />
            </label>
            <label>
              Currency
              <select defaultValue="EUR" name="currency">
                <option value="EUR">EUR — Euro</option>
                <option value="USD">USD — US Dollar</option>
                <option value="GBP">GBP — Pound sterling</option>
                <option value="CAD">CAD — Canadian dollar</option>
                <option value="CHF">CHF — Swiss franc</option>
              </select>
            </label>
            {createError && <p className="home-create-form__error" role="alert">{createError}</p>}
            <button className="home-create-form__submit" disabled={isCreating} type="submit">
              {isCreating ? "Creating…" : "Create group"}
            </button>
          </form>
        </dialog>
      )}
    </main>
  );
}
