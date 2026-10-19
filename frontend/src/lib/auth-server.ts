import "server-only";

import type { AuthUser } from "@/lib/auth-types";

export const sessionCookieName = "qdq_session";
export const sessionMaxAgeSeconds = 24 * 60 * 60;

type AuthResponse = {
  token: string;
  user: AuthUser;
};

export class AuthApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "AuthApiError";
  }
}

function apiBaseUrl(): string {
  return (
    process.env.API_URL ??
    process.env.NEXT_PUBLIC_API_URL ??
    "http://localhost:8080"
  ).replace(/\/$/, "");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function toAuthUser(value: unknown): AuthUser {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.username !== "string" ||
    typeof value.firstname !== "string" ||
    typeof value.lastname !== "string" ||
    typeof value.email !== "string"
  ) {
    throw new AuthApiError("The server returned an invalid account response.", 502);
  }

  return {
    id: value.id,
    username: value.username,
    firstname: value.firstname,
    lastname: value.lastname,
    email: value.email,
    img: typeof value.img === "string" ? value.img : null,
  };
}

async function responseMessage(response: Response): Promise<string> {
  const body: unknown = await response.json().catch(() => null);
  if (isRecord(body) && typeof body.message === "string") {
    return body.message;
  }
  return "Authentication could not be completed. Please try again.";
}

export async function authenticate(
  action: "login" | "signup",
  payload: Record<string, string>,
): Promise<AuthResponse> {
  let csrfResponse: Response;
  try {
    csrfResponse = await fetch(`${apiBaseUrl()}/api/auth/csrf`, {
      cache: "no-store",
    });
  } catch {
    throw new AuthApiError("The authentication service is unavailable.", 503);
  }

  if (!csrfResponse.ok) {
    throw new AuthApiError("The authentication service is unavailable.", 503);
  }

  const csrfCookie = csrfResponse.headers
    .get("set-cookie")
    ?.match(/(?:^|,\s*)XSRF-TOKEN=([^;]+)/i)?.[1];
  const csrfBody: unknown = await csrfResponse.json().catch(() => null);
  if (
    !csrfCookie ||
    !isRecord(csrfBody) ||
    typeof csrfBody.token !== "string" ||
    typeof csrfBody.headerName !== "string" ||
    !/^[A-Za-z0-9-]+$/.test(csrfBody.headerName)
  ) {
    throw new AuthApiError("Could not establish a secure authentication request.", 503);
  }

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl()}/api/auth/${action}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Cookie: `XSRF-TOKEN=${csrfCookie}`,
        [csrfBody.headerName]: csrfBody.token,
      },
      body: JSON.stringify(payload),
      cache: "no-store",
    });
  } catch {
    throw new AuthApiError("The authentication service is unavailable.", 503);
  }

  if (!response.ok) {
    throw new AuthApiError(await responseMessage(response), response.status);
  }

  const body: unknown = await response.json().catch(() => null);
  if (!isRecord(body) || typeof body.token !== "string") {
    throw new AuthApiError("The server returned an invalid authentication response.", 502);
  }

  return { token: body.token, user: toAuthUser(body.user) };
}

export async function getAuthenticatedUser(token: string): Promise<AuthUser> {
  const tokenParts = token.split(".");
  if (tokenParts.length !== 3) {
    throw new AuthApiError("Your session has expired. Please sign in again.", 401);
  }

  let subject: unknown;
  let expiresAt: unknown;
  try {
    const claims: unknown = JSON.parse(
      Buffer.from(tokenParts[1], "base64url").toString("utf8"),
    );
    if (isRecord(claims)) {
      subject = claims.sub;
      expiresAt = claims.exp;
    }
  } catch {
    throw new AuthApiError("Your session has expired. Please sign in again.", 401);
  }

  if (
    typeof subject !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(subject) ||
    typeof expiresAt !== "number" ||
    expiresAt * 1000 <= Date.now()
  ) {
    throw new AuthApiError("Your session has expired. Please sign in again.", 401);
  }

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl()}/api/users/${subject}`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
  } catch {
    throw new AuthApiError("The authentication service is unavailable.", 503);
  }

  if (!response.ok) {
    throw new AuthApiError(await responseMessage(response), response.status);
  }

  return toAuthUser(await response.json().catch(() => null));
}