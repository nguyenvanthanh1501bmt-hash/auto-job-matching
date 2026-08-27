"use client";

import {getApiErrorMessage} from "@/lib/api-error";
import type {
  AdminButtonProps,
  AdminEmbeddingStatusBadgeProps,
  AdminErrorMessageProps,
  AdminFieldProps,
  AdminPageHeadingProps,
  AdminPanelProps,
  AdminResultBoxProps,
  AdminToggleProps
} from "@/types/admin-ui";

export const inputClassName =
  "h-11 w-full rounded-xl border border-black/[0.09] bg-white px-3.5 text-[13px] text-[#222] outline-none transition placeholder:text-[#aaa] focus:border-black/25 focus:ring-4 focus:ring-black/[0.035]";

export const selectClassName =
  "h-11 w-full rounded-xl border border-black/[0.09] bg-white px-3.5 text-[13px] text-[#222] outline-none transition focus:border-black/25 focus:ring-4 focus:ring-black/[0.035]";

export function formatDate(
  value: string | null,
  locale: string
) {
  if (!value) {
    return "—";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  /*
   * API trả timestamp trung lập; locale của UI quyết định format
   * để ngày giờ đồng bộ với ngôn ngữ người dùng đang chọn.
   */
  const dateLocale =
    locale.toLowerCase().startsWith("vi")
      ? "vi-VN"
      : "en-US";

  return new Intl.DateTimeFormat(dateLocale, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

export function numberFromInput(
  value: string,
  fallback: number
) {
  const parsed = Number(value);

  if (!Number.isFinite(parsed)) {
    return fallback;
  }

  return Math.trunc(parsed);
}

export function ErrorMessage({
  error
}: AdminErrorMessageProps) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-3 text-[12px] leading-5 text-red-700">
      {getApiErrorMessage(error)}
    </div>
  );
}

export function Field({
  label,
  hint,
  children
}: AdminFieldProps) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[12px] font-semibold text-[#555]">
        {label}
      </span>

      {children}

      {hint ? (
        <span className="mt-1.5 block text-[11px] leading-4 text-[#999]">
          {hint}
        </span>
      ) : null}
    </label>
  );
}

export function Panel({
  title,
  description,
  action,
  children,
  className = ""
}: AdminPanelProps) {
  return (
    <section
      className={`rounded-2xl border border-black/[0.065] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.02)] ${className}`}
    >
      <div className="flex flex-col gap-3 border-b border-black/[0.055] px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-[15px] font-semibold tracking-[-0.02em] text-[#202020]">
            {title}
          </h2>

          {description ? (
            <p className="mt-1 max-w-2xl text-[12px] leading-5 text-[#888]">
              {description}
            </p>
          ) : null}
        </div>

        {action}
      </div>

      <div className="p-5">
        {children}
      </div>
    </section>
  );
}

export function PrimaryButton({
  children,
  disabled = false,
  type = "button",
  onClick
}: AdminButtonProps) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className="inline-flex h-10 items-center justify-center rounded-xl bg-[#171717] px-4 text-[12px] font-semibold text-white transition hover:bg-black disabled:cursor-not-allowed disabled:opacity-45"
    >
      {children}
    </button>
  );
}

export function SecondaryButton({
  children,
  disabled = false,
  type = "button",
  onClick
}: AdminButtonProps) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className="inline-flex h-10 items-center justify-center rounded-xl border border-black/[0.09] bg-white px-4 text-[12px] font-semibold text-[#444] transition hover:border-black/15 hover:bg-[#f7f7f5] disabled:cursor-not-allowed disabled:opacity-45"
    >
      {children}
    </button>
  );
}

export function Toggle({
  checked,
  onChange,
  label
}: AdminToggleProps) {
  return (
    <div className="inline-flex items-center gap-2.5 text-[12px] font-medium text-[#555]">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={`relative h-6 w-10 rounded-full transition ${
          checked
            ? "bg-[#171717]"
            : "bg-[#ddd]"
        }`}
      >
        <span
          className={`absolute top-1 size-4 rounded-full bg-white shadow-sm transition-all ${
            checked
              ? "left-5"
              : "left-1"
          }`}
        />
      </button>

      <span>{label}</span>
    </div>
  );
}

export function ResultBox({
  title,
  value
}: AdminResultBoxProps) {
  return (
    <div className="overflow-hidden rounded-xl border border-black/[0.07] bg-[#fafaf8]">
      <div className="border-b border-black/[0.06] px-3.5 py-2.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-[#777]">
        {title}
      </div>

      <pre className="max-h-80 overflow-auto whitespace-pre-wrap break-words p-3.5 font-mono text-[11px] leading-5 text-[#444]">
        {JSON.stringify(value, null, 2)}
      </pre>
    </div>
  );
}

export function StatusBadge({
  status
}: AdminEmbeddingStatusBadgeProps) {
  const classes =
    status === "READY"
      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
      : status === "FAILED"
        ? "border-red-200 bg-red-50 text-red-700"
        : "border-amber-200 bg-amber-50 text-amber-700";

  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-1 text-[10px] font-bold tracking-[0.04em] ${classes}`}
    >
      {status}
    </span>
  );
}

export function PageHeading({
  eyebrow,
  title,
  description
}: AdminPageHeadingProps) {
  return (
    <div className="pb-1">
      <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#999]">
        {eyebrow}
      </p>

      <h1 className="mt-2 text-[28px] font-semibold tracking-[-0.045em] text-[#171717] sm:text-[32px]">
        {title}
      </h1>

      <p className="mt-2 max-w-3xl text-[13px] leading-6 text-[#777]">
        {description}
      </p>
    </div>
  );
}