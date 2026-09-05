"use client";

import type {
  WorkspaceSearchInputProps
} from "@/types/user-ui";

function SearchIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <circle
        cx="11"
        cy="11"
        r="6"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <path
        d="m16 16 4 4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

function ClearIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-3"
      aria-hidden="true"
    >
      <path
        d="m6 6 8 8m0-8-8 8"
        stroke="currentColor"
        strokeWidth="1.45"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function WorkspaceSearchInput({
  value,
  placeholder,
  disabled = false,
  className = "",
  onValueChange
}: WorkspaceSearchInputProps) {
  const hasValue =
    value.trim().length >
    0;

  return (
    <div
      className={`relative ${className}`}
    >
      <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-black/28">
        <SearchIcon />
      </span>

      <input
        type="search"
        value={
          value
        }
        disabled={
          disabled
        }
        autoComplete="off"
        aria-label={
          placeholder
        }
        placeholder={
          placeholder
        }
        onChange={(
          event
        ) =>
          onValueChange(
            event.target.value
          )
        }
        className={`h-11 w-full rounded-[13px] border border-black/[0.065] bg-white pl-11 text-[11px] font-medium text-[#333] outline-none transition-[border-color,box-shadow,background-color] placeholder:text-black/25 focus:border-black/[0.13] focus:shadow-[0_0_0_3px_rgba(0,0,0,0.025)] disabled:cursor-not-allowed disabled:bg-black/[0.025] disabled:opacity-55 ${
          hasValue
            ? "pr-10"
            : "pr-4"
        }`}
      />

      {hasValue &&
      !disabled ? (
        <button
          type="button"
          onClick={() =>
            onValueChange(
              ""
            )
          }
          aria-label="Clear search"
          className="absolute right-2.5 top-1/2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-black/25 transition-[background-color,color] hover:bg-black/[0.045] hover:text-black/55"
        >
          <ClearIcon />
        </button>
      ) : null}
    </div>
  );
}