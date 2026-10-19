"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";

import GroupSectionNav from "@/components/groups/GroupSectionNav";
import { apiRequest, apiRequestWithCsrf } from "@/lib/api";

type Group = {
  id: string;
  name: string;
  currency: string;
};

type GroupMember = {
  id: string;
  name: string;
};

type ExpenseCategory = "FOOD" | "TRANSPORT" | "HOUSING" | "ENTERTAINMENT";

type GroupExpense = {
  id: string;
  description: string;
  amount: number;
  category: ExpenseCategory;
  paidBy: string;
  createdAt: string;
};

type LoadState = "loading" | "ready" | "error";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isExpenseCategory(value: unknown): value is ExpenseCategory {
  return (
    value === "FOOD" ||
    value === "TRANSPORT" ||
    value === "HOUSING" ||
    value === "ENTERTAINMENT"
  );
}

function parsePayer(value: unknown): string {
  if (value === null) return "Unknown";
  if (!isRecord(value) || typeof value.name !== "string") {
    throw new Error("The API returned an invalid expense payer.");
  }
  return value.name;
}

function parseGroup(value: unknown): Group {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.name !== "string" ||
    typeof value.currency !== "string" ||
    !/^[A-Z]{3}$/.test(value.currency)
  ) {
    throw new Error("The API returned invalid group details.");
  }

  return { id: value.id, name: value.name, currency: value.currency };
}

function parseExpenses(value: unknown): GroupExpense[] {
  if (!Array.isArray(value)) {
    throw new Error("The API returned an invalid expenses list.");
  }

  return value.map((item) => {
    if (
      !isRecord(item) ||
      typeof item.id !== "string" ||
      typeof item.description !== "string" ||
      typeof item.amount !== "number" ||
      !Number.isSafeInteger(item.amount) ||
      item.amount < 0 ||
      !isExpenseCategory(item.category) ||
      typeof item.createdAt !== "string" ||
      Number.isNaN(Date.parse(item.createdAt))
    ) {
      throw new Error("The API returned an invalid expense.");
    }

    const expense: GroupExpense = {
      id: item.id,
      description: item.description,
      amount: item.amount,
      category: item.category,
      paidBy: parsePayer(item.paidBy),
      createdAt: item.createdAt,
    };
    return expense;
  }).sort((first, second) => Date.parse(second.createdAt) - Date.parse(first.createdAt));
}

function parseMembers(value: unknown): GroupMember[] {
  if (!Array.isArray(value)) {
    throw new Error("The API returned an invalid members list.");
  }

  return value.map((item) => {
    if (
      !isRecord(item) ||
      typeof item.id !== "string" ||
      typeof item.name !== "string"
    ) {
      throw new Error("The API returned an invalid group member.");
    }
    return { id: item.id, name: item.name };
  });
}

function expenseIcon(category: ExpenseCategory, isMobile: boolean) {
  if (category === "FOOD") {
    return isMobile ? "/group-detail/food-mobile.svg" : "/group-detail/food-desktop.svg";
  }
  if (category === "TRANSPORT") {
    return isMobile ? "/group-detail/transport-mobile.svg" : "/group-detail/transport-desktop.svg";
  }
  return null;
}

function formatExpenseAmount(amount: number, currency: string) {
  return new Intl.NumberFormat("en-GB", {
    style: "currency",
    currency,
  }).format(amount / 100);
}

function formatExpenseDate(date: string) {
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(date));
}

export default function GroupDetailPage() {
  const params = useParams<{ groupId: string }>();
  const groupId = params.groupId;
  const [group, setGroup] = useState<Group | null>(null);
  const [expenses, setExpenses] = useState<GroupExpense[]>([]);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [loadError, setLoadError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [isAddExpenseOpen, setIsAddExpenseOpen] = useState(false);
  const [isAddingExpense, setIsAddingExpense] = useState(false);
  const [addExpenseError, setAddExpenseError] = useState("");
  const dialogRef = useRef<HTMLDialogElement>(null);

  const reloadGroup = useCallback(() => {
    setLoadError("");
    setLoadState("loading");
    setGroup(null);
    setExpenses([]);
    setMembers([]);
    setRefreshKey((current) => current + 1);
  }, []);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (isAddExpenseOpen && !dialog.open) dialog.showModal();
    if (!isAddExpenseOpen && dialog.open) dialog.close();
  }, [isAddExpenseOpen]);

  useEffect(() => {
    const controller = new AbortController();

    async function loadGroupDetails() {
      try {
        const [groupResponse, expensesResponse, membersResponse] = await Promise.all([
          apiRequest(`/api/groups/${encodeURIComponent(groupId)}`, {
            cache: "no-store",
            signal: controller.signal,
          }),
          apiRequest(`/api/groups/${encodeURIComponent(groupId)}/expenses`, {
            cache: "no-store",
            signal: controller.signal,
          }),
          apiRequest(`/api/groups/${encodeURIComponent(groupId)}/members`, {
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
        if (!expensesResponse.ok) {
          throw new Error(`Unable to load expenses (${expensesResponse.status}).`);
        }
        if (!membersResponse.ok) {
          throw new Error(`Unable to load group members (${membersResponse.status}).`);
        }

        const [loadedGroup, loadedExpenses, loadedMembers] = await Promise.all([
          groupResponse.json().then(parseGroup),
          expensesResponse.json().then(parseExpenses),
          membersResponse.json().then(parseMembers),
        ]);

        if (!controller.signal.aborted) {
          setGroup(loadedGroup);
          setExpenses(loadedExpenses);
          setMembers(loadedMembers);
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

    void loadGroupDetails();
    return () => controller.abort();
  }, [groupId, refreshKey]);

  async function addExpense(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!group) return;

    const formData = new FormData(event.currentTarget);
    const description = String(formData.get("description") ?? "").trim();
    const amountValue = Number(formData.get("amount"));
    const amount = Math.round(amountValue * 100);
    const category = String(formData.get("category") ?? "");
    const paidById = String(formData.get("paidBy") ?? "");
    if (!Number.isFinite(amountValue) || amountValue <= 0 || amount <= 0 || !Number.isSafeInteger(amount)) {
      setAddExpenseError("Enter a valid amount.");
      return;
    }

    setAddExpenseError("");
    setIsAddingExpense(true);
    try {
      const response = await apiRequestWithCsrf(`/api/groups/${encodeURIComponent(group.id)}/expenses`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          description,
          amount,
          category,
          paidBy: { id: paidById },
        }),
      });
      if (!response.ok) {
        throw new Error(`Expense creation failed (${response.status}).`);
      }

      setIsAddExpenseOpen(false);
      reloadGroup();
    } catch (error) {
      setAddExpenseError(
        error instanceof Error ? error.message : "An unexpected error occurred.",
      );
    } finally {
      setIsAddingExpense(false);
    }
  }

  return (
    <main className="group-detail">
      <header className="group-detail__header">
        <Link aria-label="Back to groups" className="group-detail__back" href="/">
          <Image alt="" height={32} src="/group-detail/back-mobile.svg" width={32} />
        </Link>
        <h1>{group?.name ?? (loadState === "loading" ? "Loading group…" : "Group details")}</h1>
      </header>

      <GroupSectionNav activeSection="expenses" groupId={groupId} />

      <section aria-labelledby="expenses-title" className="group-detail__content">
        <h2 className="visually-hidden" id="expenses-title">Group expenses</h2>
        {loadState === "loading" && (
          <p className="group-detail__status" role="status">Loading expenses…</p>
        )}
        {loadState === "error" && (
          <div className="group-detail__message" role="alert">
            <p>{loadError}</p>
            <button onClick={reloadGroup} type="button">Try again</button>
          </div>
        )}
        {loadState === "ready" && expenses.length === 0 && (
          <div className="group-detail__message">
            <p>No expenses in this group yet.</p>
            {members.length === 0 && <p>Add a member before creating an expense.</p>}
          </div>
        )}
        {loadState === "ready" && expenses.length > 0 && (
          <ul className="group-expense-list">
            {expenses.map((expense) => {
              const icon = expenseIcon(expense.category, false);
              const mobileIcon = expenseIcon(expense.category, true);
              return (
                <li className="group-expense-card" key={expense.id}>
                  {icon && mobileIcon ? (
                    <>
                      <Image alt="" className="group-expense-card__icon group-expense-card__icon--desktop" height={40} src={icon} unoptimized width={40} />
                      <Image alt="" className="group-expense-card__icon group-expense-card__icon--mobile" height={32} src={mobileIcon} unoptimized width={32} />
                    </>
                  ) : (
                    <span aria-hidden="true" className={`group-expense-card__category-icon group-expense-card__category-icon--${expense.category.toLowerCase()}`}>
                      {expense.category.charAt(0)}
                    </span>
                  )}
                  <div className="group-expense-card__details">
                    <h3>{expense.description}</h3>
                    <p className="group-expense-card__payer">Paid by {expense.paidBy}</p>
                  </div>
                  <time className="group-expense-card__date" dateTime={expense.createdAt}>
                    {formatExpenseDate(expense.createdAt)}
                  </time>
                  <p className="group-expense-card__amount">
                    {group && formatExpenseAmount(expense.amount, group.currency)}
                  </p>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {loadState === "ready" && members.length > 0 && (
        <button
          aria-label="Add an expense"
          className="group-detail__add"
          onClick={() => {
            setAddExpenseError("");
            setIsAddExpenseOpen(true);
          }}
          type="button"
        >
          <Image alt="" className="group-detail__add-circle" height={60} src="/group-detail/add-circle-mobile.svg" width={60} />
          <Image alt="" className="group-detail__add-icon" height={30} src="/group-detail/add-mobile.svg" width={30} />
        </button>
      )}

      {isAddExpenseOpen && (
        <dialog
          aria-labelledby="add-expense-title"
          className="group-detail__dialog"
          onCancel={(event) => {
            event.preventDefault();
            if (!isAddingExpense) setIsAddExpenseOpen(false);
          }}
          ref={dialogRef}
        >
          <form className="group-detail__form" onSubmit={addExpense}>
            <div className="group-detail__form-header">
              <h2 id="add-expense-title">Add an expense</h2>
              <button
                aria-label="Close"
                disabled={isAddingExpense}
                onClick={() => setIsAddExpenseOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>
            <label>
              Description
              <input autoComplete="off" maxLength={1000} name="description" required />
            </label>
            <label>
              Amount ({group?.currency})
              <input min="0.01" name="amount" required step="0.01" type="number" />
            </label>
            <label>
              Category
              <select defaultValue="FOOD" name="category">
                <option value="FOOD">Food</option>
                <option value="TRANSPORT">Transport</option>
                <option value="HOUSING">Housing</option>
                <option value="ENTERTAINMENT">Entertainment</option>
              </select>
            </label>
            <label>
              Paid by
              <select defaultValue={members[0]?.id} name="paidBy" required>
                {members.map((member) => (
                  <option key={member.id} value={member.id}>{member.name}</option>
                ))}
              </select>
            </label>
            {addExpenseError && <p className="group-detail__form-error" role="alert">{addExpenseError}</p>}
            <button className="group-detail__form-submit" disabled={isAddingExpense} type="submit">
              {isAddingExpense ? "Adding…" : "Add expense"}
            </button>
          </form>
        </dialog>
      )}
    </main>
  );
}
