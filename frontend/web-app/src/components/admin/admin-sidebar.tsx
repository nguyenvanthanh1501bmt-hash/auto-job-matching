"use client";

import {useTranslations} from "next-intl";

import type {AdminSidebarProps} from "@/types/admin-ui";

const NAV_ITEMS = [
  {id: "overview", index: "01"},
  {id: "crawler", index: "02"},
  {id: "jobs", index: "03"},
  {id: "embeddings", index: "04"},
  {id: "parser", index: "05"}
] as const;

export function AdminSidebar({
  activeSection,
  onSectionChange
}: AdminSidebarProps) {
  const t = useTranslations("admin.sidebar");

  return (
    <aside className="min-w-0 lg:border-r lg:border-black/[0.045] lg:pr-8">
      <div className="lg:sticky lg:top-[106px]">
        <div className="hidden lg:block">
          <div className="mb-6 flex items-center gap-3">
            <span className="font-mono text-[10px] font-medium uppercase tracking-[0.16em] text-[#aaa]">
              {t("workspace")}
            </span>

            <span className="h-px flex-1 bg-black/[0.07]" />

            <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />
          </div>
        </div>

        {/*
         * Mobile giữ navigation ngang để tiết kiệm không gian.
         * Desktop tăng hierarchy nhưng vẫn dùng chung behavior.
         */}
        <nav className="-mx-5 flex gap-1.5 overflow-x-auto px-5 pb-3 sm:-mx-8 sm:px-8 lg:mx-0 lg:block lg:space-y-2 lg:overflow-visible lg:px-0 lg:pb-0">
          {NAV_ITEMS.map((item) => {
            const active = activeSection === item.id;

            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onSectionChange(item.id)}
                className={`group relative flex h-12 shrink-0 items-center rounded-[13px] border px-4 text-left transition-[background-color,border-color,box-shadow,color,transform] duration-200 lg:h-[52px] lg:w-full lg:px-4.5 ${
                  active
                    ? "border-black/[0.065] bg-white text-[#242422] shadow-[0_4px_16px_rgba(0,0,0,0.035)]"
                    : "border-transparent text-[#70706a] hover:border-black/[0.04] hover:bg-white/60 hover:text-[#222]"
                }`}
              >
                <span
                  className={`w-7 shrink-0 font-mono text-[9px] font-medium leading-none transition-colors lg:text-[10px] ${
                    active
                      ? "text-[#999]"
                      : "text-[#b5b5af]"
                  }`}
                >
                  {item.index}
                </span>

                <span className="text-[14px] font-semibold tracking-[-0.02em] lg:text-[15px] xl:text-[16px]">
                  {t(item.id)}
                </span>

                <span
                  className={`ml-auto size-1.5 shrink-0 rounded-full transition-[opacity,transform] duration-200 ${
                    active
                      ? "scale-100 bg-[#d9ff75] opacity-100 ring-1 ring-black/[0.05]"
                      : "scale-75 bg-black/10 opacity-0 group-hover:opacity-100"
                  }`}
                />
              </button>
            );
          })}
        </nav>

        <div className="mt-9 hidden lg:block">
          <div className="rounded-[14px] border border-black/[0.045] bg-white/45 px-4 py-4">
            <div className="flex items-center gap-2.5">
              <span className="size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />

              <span className="font-mono text-[9px] font-medium uppercase tracking-[0.13em] text-[#999]">
                {t("systemActive")}
              </span>
            </div>

            <p className="mt-2.5 text-[11px] leading-[18px] text-[#999]">
              {t("systemDescription")}
            </p>
          </div>
        </div>
      </div>
    </aside>
  );
}