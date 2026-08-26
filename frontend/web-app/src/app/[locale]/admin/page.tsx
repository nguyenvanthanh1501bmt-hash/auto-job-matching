import {LogoutButton} from "@/components/auth/logout-button";

export default function AdminPage() {
  return (
    <main className="min-h-screen bg-[#f8f8f6] text-[#171717]">
      <header className="border-b border-black/[0.06] bg-white">
        <div className="mx-auto flex h-16 max-w-[1280px] items-center justify-between px-6">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-xl bg-[#171717] text-sm font-bold text-white">
              A
            </div>

            <div>
              <p className="text-[14px] font-semibold tracking-[-0.02em]">
                AutoJob
              </p>

              <p className="text-[10px] font-medium uppercase tracking-[0.14em] text-[#999]">
                Admin
              </p>
            </div>
          </div>

          <LogoutButton />
        </div>
      </header>

      <div className="mx-auto max-w-[1280px] px-6 py-10">
        <p className="text-sm text-[#888]">
          Admin dashboard
        </p>

        <h1 className="mt-2 text-[32px] font-semibold tracking-[-0.045em]">
          Admin
        </h1>
      </div>
    </main>
  );
}