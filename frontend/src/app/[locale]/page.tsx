"use client";

import Image from "next/image";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { useLocale, useTranslations } from "next-intl";

import SiteHeader from "@/components/auth/SiteHeader";
import type { AuthUser } from "@/lib/auth-types";
import {
  apiImageUrl,
  apiRequest,
  apiRequestWithCsrf,
} from "@/lib/api";
import { getCurrencies } from "@/lib/currencies";

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
  const locale = useLocale();
  const t = useTranslations("Landing");
  const dashboardT = useTranslations("Dashboard");
  const commonT = useTranslations("Common");
  const currencies = getCurrencies(locale);
  const router = useRouter();
  const pathname = usePathname();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const groupUserId = user?.id ?? "";

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
          throw new Error(dashboardT("loadFailed", { status: response.status }));
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
          error instanceof Error && error.message.startsWith("Unable to load groups")
            ? error.message
            : commonT("unexpectedError"),
        );
        setGroupsState("error");
      }
    }

    void loadGroups();
    return () => controller.abort();
  }, [commonT, dashboardT, groupUserId, refreshKey]);

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
        throw new Error(dashboardT("creationFailed", { status: response.status }));
      }

      setIsCreateOpen(false);
      reloadGroups();
    } catch (error) {
      setCreateError(
        error instanceof Error && error.message.startsWith("La création") || error instanceof Error && error.message.startsWith("Group creation")
          ? error.message
          : commonT("unexpectedError"),
      );
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <main className={authResolved && user ? "home-dashboard" : "home-public"}>
      <SiteHeader
        onUserChange={handleUserChange}
      />

      {!authResolved ? (
        <div className="home-loading__content">
          <p>{t("loading")}</p>
        </div>
      ) : user ? (
        <>
          <div className="home-dashboard__content">
            <header className="home-dashboard__header">
              <h1>{dashboardT("myGroups")}</h1>
              <button
                className="home-create-button home-create-button--desktop"
                onClick={openCreateGroup}
                type="button"
              >
                <Image alt="" height={30} src="/groups/add-icon.svg" width={30} />
                <span>{dashboardT("newGroup")}</span>
              </button>
            </header>

            <section aria-labelledby="groups-heading" className="home-groups">
              <h2 className="visually-hidden" id="groups-heading">{dashboardT("yourGroups")}</h2>
              {groupsState === "loading" && (
                <p className="home-groups__status" role="status">{dashboardT("loadingGroups")}</p>
              )}
              {groupsState === "error" && (
                <div className="home-groups__message" role="alert">
                  <p>{groupsError}</p>
                  <button onClick={reloadGroups} type="button">{dashboardT("tryAgain")}</button>
                </div>
              )}
              {groupsState === "ready" && groups.length === 0 && (
                <div className="home-groups__message">
                  <p>{dashboardT("empty")}</p>
                </div>
              )}
              {groupsState === "ready" && groups.length > 0 && (
                <ul className="home-group-list">
                  {groups.map((group) => (
                    <li key={group.id}>
                      <Link className="home-group-card" href={`/groups/${encodeURIComponent(group.id)}`}>
                        <Image
                          alt=""
                          className="home-group-card__avatar home-group-card__avatar--desktop"
                          crossOrigin={group.img?.startsWith("/api/") ? "use-credentials" : undefined}
                          height={59}
                          src={apiImageUrl(group.img) || "/groups/group-avatar.svg"}
                          unoptimized
                          width={59}
                        />
                        <Image
                          alt=""
                          className="home-group-card__avatar home-group-card__avatar--mobile"
                          crossOrigin={group.img?.startsWith("/api/") ? "use-credentials" : undefined}
                          height={50}
                          src={apiImageUrl(group.img) || "/groups/group-avatar-mobile.svg"}
                          unoptimized
                          width={50}
                        />
                        <div className="home-group-card__details">
                          <h3>{group.name}</h3>
                          <div className="home-group-card__metadata">
                            <span>{dashboardT("memberCount", { count: group.memberCount })}</span>
                            <Image alt="" className="home-group-card__separator" height={5} src="/groups/separator-dot.svg" width={5} />
                            <span className="home-group-card__expenses">
                              {dashboardT("expenseCount", { count: group.expenseCount })}
                            </span>
                          </div>
                        </div>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <button
            aria-label={dashboardT("createGroupAria")}
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
                  <h2 id="create-group-title">{dashboardT("createGroupTitle")}</h2>
                  <button
                    aria-label={dashboardT("close")}
                    disabled={isCreating}
                    onClick={() => setIsCreateOpen(false)}
                    type="button"
                  >
                    ×
                  </button>
                </div>
                <label>
                  {dashboardT("groupName")}
                  <input autoComplete="off" maxLength={100} name="name" required />
                </label>
                <label>
                  {dashboardT("description")}
                  <textarea maxLength={1000} name="description" rows={3} />
                </label>
                <label>
                  {dashboardT("currency")}
                  <select defaultValue="EUR" name="currency">
                    {currencies.map(({ code, label }) => (
                      <option key={code} value={code}>{label}</option>
                    ))}
                  </select>
                </label>
                {createError && <p className="home-create-form__error" role="alert">{createError}</p>}
                <button className="home-create-form__submit" disabled={isCreating} type="submit">
                  {isCreating ? dashboardT("creating") : dashboardT("create")}
                </button>
              </form>
            </dialog>
          )}
        </>
      ) : (
        <div className="home-landing">
          <section aria-labelledby="landing-title" className="home-landing__intro">
            <label className="home-landing__language">
              <span>{t("language")}</span>
              <select
                onChange={(event) => {
                  const nextLocale = event.target.value;
                  if (nextLocale !== "en" && nextLocale !== "fr") return;
                  router.replace(pathname, { locale: nextLocale });
                }}
                value={locale}
              >
                <option value="en">{t("english")}</option>
                <option value="fr">{t("french")}</option>
              </select>
            </label>
            <p className="home-landing__eyebrow">{t("eyebrow")}</p>
            <h1 id="landing-title">{t("title")}</h1>
            <p className="home-landing__description">{t("description")}</p>
            <Link className="home-landing__primary-action" href="/signup">
              {t("getStarted")}
            </Link>
          </section>

          <section aria-labelledby="how-it-works" className="home-landing__steps">
            <h2 id="how-it-works">{t("howItWorks")}</h2>
            <ol>
              <li><strong>{t("steps.create.title")}</strong><span>{t("steps.create.description")}</span></li>
              <li><strong>{t("steps.add.title")}</strong><span>{t("steps.add.description")}</span></li>
              <li><strong>{t("steps.settle.title")}</strong><span>{t("steps.settle.description")}</span></li>
            </ol>
          </section>

          <footer className="home-landing__footer">
            <span>Qui doit quoi</span>
            <span>{t("footer")}</span>
          </footer>
        </div>
      )}
    </main>
  );
}
