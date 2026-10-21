import { expect, test, type Page, type Route } from "@playwright/test";

const USER_ID = "user-test-1";
const GROUP_ID = "group-test-1";

type ApiGroup = {
  id: string;
  name: string;
  description: string | null;
  img: string | null;
  currency: string;
};

type ApiMember = {
  id: string;
  name: string;
};

type ApiExpense = {
  id: string;
  description: string;
  amount: number;
  category: "FOOD" | "TRANSPORT" | "HOUSING" | "ENTERTAINMENT";
  paidBy: ApiMember;
  createdAt: string;
};

type ApiUser = {
  id: string;
  firstname: string;
  lastname: string;
  username: string;
  email: string;
};

type ApiOptions = {
  authenticated?: boolean;
  groups?: ApiGroup[];
  members?: ApiMember[];
  expenses?: ApiExpense[];
  failCount?: Record<string, number>;
};

type WriteRequest = {
  method: string;
  path: string;
  body: Record<string, unknown>;
};

class MockApi {
  readonly groups: ApiGroup[];
  readonly members = new Map<string, ApiMember[]>();
  readonly expenses = new Map<string, ApiExpense[]>();
  readonly writes: WriteRequest[] = [];
  readonly requests: string[] = [];
  readonly failedRequests: string[] = [];
  private readonly failures: Map<string, number>;
  private authenticated: boolean;
  private nextGroupId = 1;
  private nextMemberId = 1;
  private nextExpenseId = 1;

  constructor(options: ApiOptions = {}) {
    this.groups = structuredClone(options.groups ?? []);
    this.authenticated = options.authenticated ?? false;
    this.failures = new Map(Object.entries(options.failCount ?? {}));
    for (const group of this.groups) {
      this.members.set(
        group.id,
        structuredClone(group.id === GROUP_ID ? options.members ?? [] : []),
      );
      this.expenses.set(
        group.id,
        structuredClone(group.id === GROUP_ID ? options.expenses ?? [] : []),
      );
    }
  }

  async install(page: Page) {
    await page.route("**/api/**", async (route) => this.handle(route));
  }

  private async fulfill(route: Route, body: unknown, status = 200) {
    await route.fulfill({
      status,
      contentType: "application/json",
      headers: this.corsHeaders(),
      body: JSON.stringify(body),
    });
  }

  private corsHeaders() {
    return {
      "access-control-allow-origin": "http://localhost:3001",
      "access-control-allow-credentials": "true",
      "access-control-allow-headers": "content-type,x-xsrf-token",
      "access-control-allow-methods": "GET,POST,PUT,DELETE,OPTIONS",
    };
  }

  private async handle(route: Route) {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    this.requests.push(`${method} ${path}`);

    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: this.corsHeaders() });
      return;
    }

    const failuresRemaining = this.failures.get(path) ?? 0;
    if (method === "GET" && failuresRemaining > 0) {
      this.failures.set(path, failuresRemaining - 1);
      this.failedRequests.push(path);
      await this.fulfill(route, { message: "Temporary test failure." }, 503);
      return;
    }

    if (method === "GET" && path === "/api/auth/csrf") {
      await this.fulfill(route, {
        token: "playwright-csrf-token",
        headerName: "X-XSRF-TOKEN",
      });
      return;
    }

    if (method === "GET" && path === "/api/users/me") {
      await this.fulfill(
        route,
        this.authenticated
          ? this.user()
          : { status: 401, message: "Not signed in." },
        this.authenticated ? 200 : 401,
      );
      return;
    }

    if (method === "POST" && path === "/api/auth/logout") {
      this.authenticated = false;
      this.writes.push({ method, path, body: {} });
      await route.fulfill({ status: 204, headers: this.corsHeaders() });
      return;
    }

    if (
      method === "POST" &&
      (path === "/api/auth/login" || path === "/api/auth/signup")
    ) {
      const body = this.body(route);
      this.writes.push({ method, path, body });
      if (body.password === "wrong-password") {
        await this.fulfill(route, { message: "Invalid email or password." }, 401);
        return;
      }
      this.authenticated = true;
      await this.fulfill(route, this.user());
      return;
    }

    if (method === "GET" && path === `/api/groups/user/${USER_ID}`) {
      await this.fulfill(route, this.groups);
      return;
    }

    const groupMatch = path.match(/^\/api\/groups\/([^/]+)$/);
    if (groupMatch) {
      const groupId = decodeURIComponent(groupMatch[1]);
      const group = this.groups.find((item) => item.id === groupId);
      if (method === "GET") {
        await this.fulfill(
          route,
          group ?? { status: 404, message: "Group not found." },
          group ? 200 : 404,
        );
        return;
      }
      if (method === "POST") {
        const body = this.body(route);
        this.writes.push({ method, path, body });
        const created: ApiGroup = {
          id: `created-group-${this.nextGroupId++}`,
          name: String(body.name),
          description: typeof body.description === "string" ? body.description : null,
          img: null,
          currency: String(body.currency),
        };
        this.groups.push(created);
        this.members.set(created.id, [{ id: `member-${this.nextMemberId++}`, name: "Marc" }]);
        this.expenses.set(created.id, []);
        await this.fulfill(route, created, 201);
        return;
      }
      if (method === "PUT" && group) {
        const body = this.body(route);
        this.writes.push({ method, path, body });
        Object.assign(group, body);
        await this.fulfill(route, group);
        return;
      }
    }

    const membersMatch = path.match(/^\/api\/groups\/([^/]+)\/members$/);
    if (membersMatch) {
      const groupId = decodeURIComponent(membersMatch[1]);
      const groupMembers = this.members.get(groupId) ?? [];
      if (method === "GET") {
        await this.fulfill(route, groupMembers);
        return;
      }
      if (method === "POST") {
        const body = this.body(route);
        this.writes.push({ method, path, body });
        const member = {
          id: `member-${this.nextMemberId++}`,
          name: String(body.name),
        };
        groupMembers.push(member);
        this.members.set(groupId, groupMembers);
        await this.fulfill(route, member, 201);
        return;
      }
    }

    const memberMatch = path.match(/^\/api\/members\/([^/]+)$/);
    if (method === "DELETE" && memberMatch) {
      const memberId = decodeURIComponent(memberMatch[1]);
      this.writes.push({ method, path, body: {} });
      for (const [groupId, groupMembers] of this.members) {
        this.members.set(
          groupId,
          groupMembers.filter((member) => member.id !== memberId),
        );
      }
      await route.fulfill({ status: 204, headers: this.corsHeaders() });
      return;
    }

    const expensesMatch = path.match(/^\/api\/groups\/([^/]+)\/expenses$/);
    if (expensesMatch) {
      const groupId = decodeURIComponent(expensesMatch[1]);
      const groupExpenses = this.expenses.get(groupId) ?? [];
      if (method === "GET") {
        await this.fulfill(route, groupExpenses);
        return;
      }
      if (method === "POST") {
        const body = this.body(route);
        this.writes.push({ method, path, body });
        const payerId =
          typeof body.paidBy === "object" &&
          body.paidBy !== null &&
          "id" in body.paidBy
            ? String(body.paidBy.id)
            : "";
        const payer = (this.members.get(groupId) ?? []).find(
          (member) => member.id === payerId,
        );
        const expense: ApiExpense = {
          id: `expense-${this.nextExpenseId++}`,
          description: String(body.description),
          amount: Number(body.amount),
          category: body.category as ApiExpense["category"],
          paidBy: payer ?? { id: payerId, name: "Unknown" },
          createdAt: "2026-10-19T10:00:00Z",
        };
        groupExpenses.push(expense);
        this.expenses.set(groupId, groupExpenses);
        await this.fulfill(route, expense, 201);
        return;
      }
    }

    const settlementsMatch = path.match(/^\/api\/groups\/([^/]+)\/settlements$/);
    if (method === "GET" && settlementsMatch) {
      const groupId = decodeURIComponent(settlementsMatch[1]);
      await this.fulfill(route, this.calculateSettlements(groupId));
      return;
    }

    await this.fulfill(
      route,
      { message: `No test API handler for ${method} ${path}.` },
      404,
    );
  }

  private body(route: Route): Record<string, unknown> {
    const value: unknown = route.request().postDataJSON();
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
      throw new Error("The frontend sent an invalid test request body.");
    }
    return value as Record<string, unknown>;
  }

  private user(): ApiUser {
    return {
      id: USER_ID,
      firstname: "Marc",
      lastname: "Tarrade",
      username: "marc",
      email: "marc@example.com",
    };
  }

  private calculateSettlements(groupId: string) {
    const groupMembers = this.members.get(groupId) ?? [];
    const groupExpenses = this.expenses.get(groupId) ?? [];
    if (groupMembers.length === 0) return [];

    const balances = new Map(groupMembers.map((member) => [member.id, 0]));
    const total = groupExpenses.reduce((sum, expense) => sum + expense.amount, 0);
    for (const expense of groupExpenses) {
      if (balances.has(expense.paidBy.id)) {
        balances.set(
          expense.paidBy.id,
          (balances.get(expense.paidBy.id) ?? 0) + expense.amount,
        );
      }
    }
    const equalShare = Math.floor(total / groupMembers.length);
    const remainder = total % groupMembers.length;
    groupMembers.forEach((member, index) => {
      balances.set(
        member.id,
        (balances.get(member.id) ?? 0) -
          equalShare -
          (index < remainder ? 1 : 0),
      );
    });

    const debtors = groupMembers
      .map((member) => ({ member, amount: -(balances.get(member.id) ?? 0) }))
      .filter((balance) => balance.amount > 0);
    const creditors = groupMembers
      .map((member) => ({ member, amount: balances.get(member.id) ?? 0 }))
      .filter((balance) => balance.amount > 0);
    const settlements: {
      fromMemberId: string;
      fromMemberName: string;
      toMemberId: string;
      toMemberName: string;
      amount: number;
    }[] = [];

    while (debtors.length > 0 && creditors.length > 0) {
      const debtor = debtors[0];
      const creditor = creditors[0];
      const amount = Math.min(debtor.amount, creditor.amount);
      settlements.push({
        fromMemberId: debtor.member.id,
        fromMemberName: debtor.member.name,
        toMemberId: creditor.member.id,
        toMemberName: creditor.member.name,
        amount,
      });
      debtor.amount -= amount;
      creditor.amount -= amount;
      if (debtor.amount === 0) debtors.shift();
      if (creditor.amount === 0) creditors.shift();
    }
    return settlements;
  }
}

function testGroup(overrides: Partial<ApiGroup> = {}): ApiGroup {
  return {
    id: GROUP_ID,
    name: "Lapland trip",
    description: "Shared trip costs",
    img: null,
    currency: "EUR",
    ...overrides,
  };
}

function testMembers(): ApiMember[] {
  return [
    { id: "member-marc", name: "Marc" },
    { id: "member-alex", name: "Alex" },
  ];
}

test("sign up, sign in, and sign out interactions work", async ({ page }) => {
  const api = new MockApi();
  await api.install(page);

  await page.goto("/");
  await page.getByRole("link", { name: "Sign up" }).click();
  await expect(page.getByRole("heading", { name: "Qui Doit Quoi" })).toBeVisible();
  await page.getByRole("link", { name: "Log in" }).click();
  await expect(page.getByLabel("Email")).toBeVisible();
  await page.getByRole("link", { name: "Sign up here" }).click();
  await expect(page.getByLabel("Username")).toBeVisible();

  await page.getByLabel("Username").fill("marc");
  await page.getByLabel("Last name").fill("Tarrade");
  await page.getByLabel("First name").fill("Marc");
  await page.getByLabel("Email").fill("marc@example.com");
  await page.getByLabel("Password", { exact: true }).fill("safe-password");
  await page.getByLabel("Confirm password").fill("different-password");
  await page.getByRole("button", { name: "Sign up" }).click();
  await expect(page.getByText("Passwords do not match.", { exact: true })).toBeVisible();
  expect(api.writes.filter((write) => write.path === "/api/auth/signup")).toHaveLength(0);

  await page.getByLabel("Confirm password").fill("safe-password");
  await page.getByRole("button", { name: "Sign up" }).click();
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "My groups" })).toBeVisible();
  await expect(page.getByText("Marc", { exact: true })).toBeVisible();
  expect(api.writes.find((write) => write.path === "/api/auth/signup")?.body).toEqual({
    username: "marc",
    lastname: "Tarrade",
    firstname: "Marc",
    email: "marc@example.com",
    password: "safe-password",
  });

  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign up" })).toBeVisible();
  expect(api.writes.some((write) => write.path === "/api/auth/logout")).toBe(true);

  await page.getByRole("link", { name: "Sign in" }).click();
  await page.getByLabel("Email").fill("marc@example.com");
  await page.getByLabel("Password").fill("wrong-password");
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByText("Invalid email or password.")).toBeVisible();

  await page.getByLabel("Password").fill("safe-password");
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("heading", { name: "My groups" })).toBeVisible();
  expect(api.writes.filter((write) => write.path === "/api/auth/login").at(-1)?.body).toEqual({
    email: "marc@example.com",
    password: "safe-password",
  });
});

test("creates a group, adds an expense, and displays the calculated balance", async ({
  page,
}) => {
  const api = new MockApi({ authenticated: true });
  await api.install(page);

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "My groups" })).toBeVisible();
  await page.getByRole("button", { name: "New group" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("button", { name: "New group" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByLabel("Group name").fill("Lapland trip");
  await page.getByLabel("Description").fill("Ski holiday");
  await page.getByLabel("Currency").selectOption("EUR");
  await page.getByRole("button", { name: "Create group" }).click();
  await expect(page.getByRole("link", { name: /Lapland trip/ })).toBeVisible();

  const groupRequest = api.writes.find((write) => write.method === "POST" && write.path.startsWith("/api/groups/"));
  expect(groupRequest?.body).toEqual({
    name: "Lapland trip",
    description: "Ski holiday",
    currency: "EUR",
  });

  await page.getByRole("link", { name: /Lapland trip/ }).click();
  await expect(page.getByRole("heading", { name: "Lapland trip" })).toBeVisible();
  await expect(page.getByText("No expenses in this group yet.")).toBeVisible();
  const backToGroupsLink = page.getByRole("link", { name: "Back to groups" });
  await expect(backToGroupsLink).toBeVisible();
  await backToGroupsLink.click();
  await expect(page).toHaveURL("/");
  await page.getByRole("link", { name: /Lapland trip/ }).click();

  await page.getByRole("link", { name: "Settings" }).click();
  await page.getByLabel("Add a member").fill("Alex");
  await page.getByRole("button", { name: "Add member" }).click();
  await expect(page.getByText("Alex was added to the group.")).toBeVisible();
  expect(api.members.get("created-group-1")).toHaveLength(2);

  await page.getByRole("link", { name: "Expenses", exact: true }).click();
  await page.getByRole("button", { name: "Add an expense" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByLabel("Description").fill("Ski passes");
  await page.getByLabel(/Amount/).fill("60");
  await page.getByLabel("Category").selectOption("ENTERTAINMENT");
  await page.getByLabel("Paid by").selectOption({ label: "Marc" });
  await page.getByRole("button", { name: "Add expense" }).click();
  await expect(page.getByText("Ski passes")).toBeVisible();
  expect(api.writes.find((write) => write.path.endsWith("/expenses"))?.body).toMatchObject({
    description: "Ski passes",
    amount: 6000,
    category: "ENTERTAINMENT",
    paidBy: { id: "member-1" },
  });

  await page.getByRole("link", { name: "Balances" }).click();
  await expect(page.getByRole("heading", { name: "Debt breakdown" })).toBeVisible();
  const balancesBackLink = page.getByRole("link", { name: "Back to groups" });
  await expect(balancesBackLink).toHaveAttribute("href", "/");
  await expect(page.getByText("Alex owes Marc")).toBeVisible();
  await expect(page.getByText("€30.00")).toBeVisible();
  await balancesBackLink.click();
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "My groups" })).toBeVisible();
});

test("group settings update details and support add, cancel, and remove member actions", async ({
  page,
}) => {
  const api = new MockApi({
    authenticated: true,
    groups: [testGroup()],
    members: testMembers(),
  });
  await api.install(page);

  await page.goto(`/groups/${GROUP_ID}/settings`);
  await expect(page.getByRole("heading", { name: "Lapland trip" })).toBeVisible();
  const settingsBackLink = page.getByRole("link", { name: "Back to groups" });
  await expect(settingsBackLink).toHaveAttribute("href", "/");

  await page.getByLabel("Group name").fill("Updated trip");
  await page.getByLabel("Currency").fill("usd");
  await page.getByLabel("Description").fill("Updated description");
  await page.getByRole("button", { name: "Edit group image URL" }).click();
  const imageUrlInput = page.getByRole("textbox", { name: "Image URL" });
  await expect(imageUrlInput).toBeFocused();
  await imageUrlInput.fill("https://example.test/trip.png");
  const saveButton = page.getByRole("button", { name: "Save changes" });
  const saveButtonX = (await saveButton.boundingBox())?.x;
  expect(saveButtonX).toBeDefined();
  await saveButton.click();
  await expect(page.getByText("Group settings saved.")).toBeVisible();
  expect((await saveButton.boundingBox())?.x).toBe(saveButtonX);
  await expect(page.getByRole("heading", { name: "Updated trip" })).toBeVisible();
  expect(api.writes.find((write) => write.method === "PUT")?.body).toEqual({
    name: "Updated trip",
    description: "Updated description",
    img: "https://example.test/trip.png",
    currency: "USD",
  });

  await page.getByLabel("Add a member").fill("Casey");
  await page.getByRole("button", { name: "Add member" }).click();
  await expect(page.getByText("Casey was added to the group.")).toBeVisible();
  await expect(page.getByText("Casey", { exact: true })).toBeVisible();

  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("button", { name: "Remove Casey" }).click();
  await expect(page.getByText("Casey", { exact: true })).toBeVisible();

  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Remove Casey" }).click();
  await expect(page.getByText("Casey", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Casey was removed from the group.")).toBeVisible();
  await settingsBackLink.click();
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { name: "My groups" })).toBeVisible();
});

test("mobile group actions open and cancel the expense form", async ({ page }) => {
  const api = new MockApi({
    authenticated: true,
    groups: [testGroup()],
    members: testMembers(),
  });
  await api.install(page);
  await page.setViewportSize({ width: 393, height: 852 });

  await page.goto(`/groups/${GROUP_ID}`);
  await page.locator("button.group-detail__add").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Back to group" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page).toHaveURL(`/groups/${GROUP_ID}`);

  await page.locator("button.group-detail__add").click();
  await page.getByLabel("Description").fill("Cancelled expense");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(api.writes.some((write) => write.path.endsWith("/expenses"))).toBe(false);
});

test("retry controls recover from temporary group and balance API failures", async ({
  page,
}) => {
  const api = new MockApi({
    authenticated: true,
    groups: [testGroup()],
    members: testMembers(),
    failCount: {
      [`/api/groups/${GROUP_ID}/expenses`]: 2,
      [`/api/groups/${GROUP_ID}/settlements`]: 2,
    },
  });
  await api.install(page);

  await page.goto(`/groups/${GROUP_ID}`);
  await expect
    .poll(() => api.requests)
    .toContain(`GET /api/groups/${GROUP_ID}/expenses`);
  await expect
    .poll(() => api.failedRequests)
    .toContain(`/api/groups/${GROUP_ID}/expenses`);
  await expect(page.getByText("Unable to load expenses (503).")).toBeVisible();
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByRole("button", { name: "Add an expense" })).toBeVisible();

  await page.getByRole("link", { name: "Balances" }).click();
  await expect(page.getByText("Unable to load group balances (503).")).toBeVisible();
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByText("Everyone is settled up.")).toBeVisible();
});

test("home retry and mobile create-group controls are interactive", async ({ page }) => {
  const api = new MockApi({
    authenticated: true,
    failCount: {
      [`/api/groups/user/${USER_ID}`]: 1,
    },
  });
  await api.install(page);
  await page.setViewportSize({ width: 393, height: 852 });

  await page.goto("/");
  await expect(page.getByText("Unable to load groups (503).")).toBeVisible();
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByText("You don’t have any groups yet.")).toBeVisible();

  await page.getByRole("button", { name: "Create a group" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(api.writes.some((write) => write.method === "POST")).toBe(false);
});
