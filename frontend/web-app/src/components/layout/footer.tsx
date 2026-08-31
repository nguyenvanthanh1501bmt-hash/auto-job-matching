"use client";

import {Link} from "@/i18n/navigation";

type Props = {
  tagline: string;
  notice: string;
  copyright: string;
};

export function Footer({
  tagline,
  notice,
  copyright
}: Props) {
  return (
    <footer className="flex flex-col gap-4 border-t border-black/[0.055] py-7 text-[9px] text-black/28 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3">
        <Link
          href="/"
          className="group inline-flex items-center gap-3"
        >
          <div className="relative flex size-8 items-center justify-center overflow-hidden rounded-[10px] bg-[#171717] text-white">
            <div className="absolute inset-x-0 bottom-0 h-px bg-[#d9ff75] opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="size-[15px] transition-transform duration-300 ease-out group-hover:-rotate-3 group-hover:scale-105"
              aria-hidden="true"
            >
              <path
                d="M5 17.5 10.2 6.8c.7-1.4 2.7-1.4 3.4 0L19 17.5"
                stroke="currentColor"
                strokeWidth="2.15"
                strokeLinecap="round"
              />

              <path
                d="M7.8 13h8.4"
                stroke="currentColor"
                strokeWidth="2.15"
                strokeLinecap="round"
              />
            </svg>
          </div>

          <span className="text-[15px] font-bold tracking-[-0.045em] text-[#171717]">
            AutoJob
          </span>
        </Link>

        <span className="hidden sm:inline">
          {tagline}
        </span>
      </div>

      <div className="flex flex-wrap gap-x-5 gap-y-2">
        <span>
          {notice}
        </span>

        <span>
          {copyright}
        </span>
      </div>
    </footer>
  );
}