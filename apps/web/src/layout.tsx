import { Link, NavLink, Outlet } from "react-router";

const aba = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-1.5 text-sm ${isActive ? "bg-destaque-suave text-destaque" : "text-tinta-2 hover:text-tinta"}`;

export function Layout() {
  return (
    <div className="min-h-dvh">
      <header className="border-b border-borda bg-superficie">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 px-4 py-3">
          <Link to="/" className="mr-3 flex items-center gap-2 font-semibold">
            <img src="/favicon.svg" alt="" className="size-7 rounded-lg" />
            NeuroBeat
          </Link>
          <nav className="flex gap-1">
            <NavLink to="/" end className={aba}>
              Biblioteca
            </NavLink>
            <NavLink to="/playlists" className={aba}>
              Playlists
            </NavLink>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
