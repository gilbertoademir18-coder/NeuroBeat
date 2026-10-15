import { Link, Outlet } from "react-router";

export function Layout() {
  return (
    <div className="min-h-dvh">
      <header className="border-b border-borda bg-superficie">
        <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 font-semibold">
            <img src="/favicon.svg" alt="" className="size-7 rounded-lg" />
            NeuroBeat
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
