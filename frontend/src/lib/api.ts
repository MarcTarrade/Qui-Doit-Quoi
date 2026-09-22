const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function checkApiConnection(): Promise<boolean> {
  const response = await fetch(`${apiUrl}/v3/api-docs`, {
    cache: "no-store",
  });

  return response.ok;
}

export { apiUrl };