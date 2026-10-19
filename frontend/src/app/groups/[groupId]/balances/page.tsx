"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import GroupSectionNav from "@/components/groups/GroupSectionNav";
import { apiRequest } from "@/lib/api";

type Group = {
  name: string;
  currency: string;
};

type Settlement = {
  fromMemberName: string;
  toMemberName: string;
  amount: number;
};

type LoadState = "loading" | "ready" | "error";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function parseGroup(value: unknown): Group {
  if (
    !isRecord(value) ||
    typeof value.name !== "string" ||
    typeof value.currency !== "string" ||
    !/^[A-Z]{3}$/.test(value.currency)
  ) {
    throw new Error("The API returned invalid group details.");
  }
  return { name: value.name, currency: value.currency };
}

function parseSettlements(value: unknown): Settlement[] {
  if (!Array.isArray(value)) {
    throw new Error("The API returned an invalid balances list.");
  }

  return value.map((item) => {
    if (
      !isRecord(item) ||
      typeof item.fromMemberName !== "string" ||
      typeof item.toMemberName !== "string" ||
      typeof item.amount !== "number" ||
      !Number.isSafeInteger(item.amount) ||
      item.amount <= 0
    ) {
      throw new Error("The API returned an invalid balance.");
    }

    return {
      fromMemberName: item.fromMemberName,
      toMemberName: item.toMemberName,
      amount: item.amount,
    };
  });
}

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency,
  }).format(amount / 100);
}

export default function GroupBalancesPage() {
  const { groupId } = useParams<{ groupId: string }>();
  const [group, setGroup] = useState<Group | null>(null);
  const [settlements, setSettlements] = useState<Settlement[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  const retry = useCallback(() => {
    setLoadError("");
    setLoadState("loading");
    setGroup(null);
    setSettlements([]);
    setRefreshKey((current) => current + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    async function loadBalances() {
      try {
        const encodedGroupId = encodeURIComponent(groupId);
        const [groupResponse, settlementsResponse] = await Promise.all([
          apiRequest(`/api/groups/${encodedGroupId}`, {
            cache: "no-store",
            signal: controller.signal,
          }),
          apiRequest(`/api/groups/${encodedGroupId}/settlements`, {
            cache: "no-store",
            signal: controller.signal,
          }),
        ]);

        if (!groupResponse.ok) {
          throw new Error(
            groupResponse.status === 404
              ? "This group could not be found."
              : `Unable to load group details (${groupResponse.status}).`,
          );
        }
        if (!settlementsResponse.ok) {
          throw new Error(
            `Unable to load group balances (${settlementsResponse.status}).`,
          );
        }

        const [loadedGroup, loadedSettlements] = await Promise.all([
          groupResponse.json().then(parseGroup),
          settlementsResponse.json().then(parseSettlements),
        ]);

        if (!controller.signal.aborted) {
          setGroup(loadedGroup);
          setSettlements(loadedSettlements);
          setLoadState("ready");
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setLoadError(
          error instanceof Error ? error.message : "An unexpected error occurred.",
        );
        setLoadState("error");
      }
    }

    void loadBalances();
    return () => controller.abort();
  }, [groupId, refreshKey]);

  return (
    <main className="group-detail">
      <header className="group-detail__header">
        <Link
          aria-label="Back to group expenses"
          className="group-detail__back"
          href={`/groups/${encodeURIComponent(groupId)}`}
        >
          <Image alt="" height={32} src="/group-detail/back-mobile.svg" width={32} />
        </Link>
        <h1>{group?.name ?? (loadState === "loading" ? "Loading group…" : "Group details")}</h1>
        <Link
          aria-label="Group settings"
          className="group-detail__settings-link"
          href={`/groups/${encodeURIComponent(groupId)}/settings`}
        >
          <Image alt="" height={32} src="/group-detail/more-mobile.svg" width={32} />
        </Link>
      </header>

      <GroupSectionNav activeSection="balances" groupId={groupId} />

      <section aria-labelledby="balances-title" className="group-detail__content">
        {loadState === "loading" && (
          <p className="group-detail__status" role="status">Loading balances…</p>
        )}
        {loadState === "error" && (
          <div className="group-detail__message" role="alert">
            <p>{loadError}</p>
            <button onClick={retry} type="button">Try again</button>
          </div>
        )}
        {loadState === "ready" && group && (
          <div className="group-balances">
            <h2 id="balances-title">Debt breakdown</h2>
            {settlements.length === 0 ? (
              <p className="group-balances__empty">Everyone is settled up.</p>
            ) : (
              <ul className="group-balances__list">
                {settlements.map((settlement, index) => (
                  <li
                    className="group-balances__row"
                    key={`${settlement.fromMemberName}-${settlement.toMemberName}-${index}`}
                  >
                    <span>
                      {settlement.fromMemberName} owes {settlement.toMemberName}
                    </span>
                    <strong>{formatAmount(settlement.amount, group.currency)}</strong>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </section>
    </main>
  );
}
