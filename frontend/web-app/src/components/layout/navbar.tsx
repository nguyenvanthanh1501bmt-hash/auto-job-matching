"use client";

import {LanguageSwitcher} from "@/components/ui/language-switcher";
import {Link} from "@/i18n/navigation";

type NavbarItem = {
  href: string;
  label: string;
};

type NavbarAction = {
  href: "/login" | "/jobs" | "/admin";
  label: string;
  showArrow?: boolean;
};

type Props = {
  pathname?: string;
  items?: NavbarItem[];
  action: NavbarAction;
};

function ArrowIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="M4.5 10h10.25M11 6.25 14.75 10 11 13.75"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function Navbar({
  pathname,
  items = [],
  action
}: Props) {
  return (
    <header className="sticky top-0 z-[100] w-full border-b border-black/[0.04] bg-[#f7f7f4]/88 backdrop-blur-xl">
      <div className="mx-auto flex h-[82px] max-w-[1440px] items-center justify-between px-5 sm:px-8 lg:px-12 xl:px-16">
        <Link
          href="/"
          className="group inline-flex items-center gap-3"
        >
          <div className="relative flex size-9 items-center justify-center overflow-hidden rounded-[11px] bg-[#171717] text-white">
            <div className="absolute inset-x-0 bottom-0 h-px bg-[#d9ff75] opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="size-[17px] transition-transform duration-300 ease-out group-hover:-rotate-3 group-hover:scale-105"
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

          <span className="text-[18px] font-bold tracking-[-0.045em] text-[#171717]">
            AutoJob
          </span>
        </Link>

        <div className="flex items-center gap-2 sm:gap-3">
          {items.length > 0 && (
            <nav className="mr-2 hidden items-center gap-7 lg:flex">
              {items.map((item) => (
                <a
                  key={`${item.href}-${item.label}`}
                  href={item.href}
                  className="text-[11px] font-medium text-black/45 transition-colors duration-200 hover:text-black"
                >
                  {item.label}
                </a>
              ))}
            </nav>
          )}

          <LanguageSwitcher pathname={pathname} />

          <Link
            href={action.href}
            className="hidden h-9 items-center gap-2 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)] transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_7px_18px_rgba(0,0,0,0.11)] sm:inline-flex"
          >
            {action.label}

            {action.showArrow && (
              <ArrowIcon />
            )}
          </Link>
        </div>
      </div>
    </header>
  );
}