"use client";

import { useEffect, useState } from "react";

import { apiUrl, checkApiConnection } from "@/lib/api";

export default function Home() {
  const [apiStatus, setApiStatus] = useState<"checking" | "online" | "offline">("checking");

  useEffect(() => {
    checkApiConnection()
      .then((connected) => setApiStatus(connected ? "online" : "offline"))
      .catch(() => setApiStatus("offline"));
  }, []);

  return (
    <main className="min-h-screen bg-[#f3f0e8] px-6 py-8 text-[#18231d] sm:px-10 lg:px-16">
      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-6xl flex-col justify-between">
        <header className="flex items-center justify-between border-b border-[#18231d]/15 pb-5">
          <p className="text-sm font-semibold uppercase tracking-[0.18em]">Qui doit quoi</p>
          <span className="text-xs uppercase tracking-[0.16em] text-[#536158]">Workspace</span>
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
