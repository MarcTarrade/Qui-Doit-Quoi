export const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function apiImageUrl(image: string | null): string | null {
  if (!image) return null;
  return image.startsWith("/") ? new URL(image, apiUrl).toString() : image;
}

export function apiRequest(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers);
  const locale = typeof document === "undefined" ? undefined : document.documentElement.lang;
  if (locale === "en" || locale === "fr") {
    headers.set("Accept-Language", locale);
  }

  return fetch(`${apiUrl}${path}`, {
    ...options,
    headers,
    credentials: "include",
  });
}

export async function apiRequestWithCsrf(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const csrfResponse = await apiRequest("/api/auth/csrf", { cache: "no-store" });
  if (!csrfResponse.ok) {
    throw new Error("Could not get a CSRF token.");
  }

  const csrf: unknown = await csrfResponse.json();
  if (
    typeof csrf !== "object" || csrf === null ||
    !("token" in csrf) || typeof csrf.token !== "string" ||
    !("headerName" in csrf) || typeof csrf.headerName !== "string" ||
    !/^[A-Za-z0-9-]+$/.test(csrf.headerName)
  ) {
    throw new Error("The API returned an invalid CSRF token.");
  }

  const headers = new Headers(options.headers);
  headers.set(csrf.headerName, csrf.token);
  return apiRequest(path, { ...options, headers });
}

export async function checkApiConnection(): Promise<boolean> {
  try {
    const response = await apiRequest("/api/auth/csrf", { cache: "no-store" });
    return response.ok;
  } catch {
    return false;
  }
}