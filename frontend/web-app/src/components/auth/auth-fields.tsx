"use client";

import {
  useState,
  type ReactNode
} from "react";

import {useTranslations} from "next-intl";

type TextFieldProps = {
  id: string;
  label: string;
  value: string;

  placeholder?: string;

  type?:
    | "text"
    | "email";

  inputMode?:
    | "text"
    | "email";

  autoComplete?: string;

  maxLength?: number;
  disabled?: boolean;
  error?: string;

  onChange: (
    value: string
  ) => void;
};

export function AuthTextField({
  id,
  label,
  value,
  placeholder,
  type = "text",
  inputMode,
  autoComplete,
  maxLength,
  disabled,
  error,
  onChange
}: TextFieldProps) {
  return (
    <div>
      <label
        htmlFor={id}
        className="mb-2 block text-[11px] font-semibold text-[#454542]"
      >
        {label}
      </label>

      <input
        id={id}
        name={id}
        type={type}
        inputMode={inputMode}
        autoComplete={autoComplete}
        maxLength={maxLength}
        disabled={disabled}
        value={value}
        placeholder={placeholder}
        aria-invalid={Boolean(
          error
        )}
        onChange={(event) =>
          onChange(
            event.target.value
          )
        }
        className={`h-[48px] w-full rounded-[12px] border px-4 text-[13px] text-[#171717] outline-none transition-[border-color,background-color,box-shadow] duration-200 placeholder:text-[#b2b2ac] disabled:cursor-not-allowed disabled:opacity-50 ${
          error
            ? "border-red-300 bg-red-50/30 focus:border-red-400 focus:bg-white focus:ring-4 focus:ring-red-50"
            : "border-black/[0.075] bg-[#fafaf8] hover:border-black/[0.13] hover:bg-[#f8f8f6] focus:border-black/[0.3] focus:bg-white focus:ring-4 focus:ring-black/[0.025]"
        }`}
      />

      {error && (
        <p className="mt-1.5 text-[10px] font-medium leading-4 text-red-500">
          {error}
        </p>
      )}
    </div>
  );
}

type PasswordFieldProps = {
  id: string;
  label: string;
  value: string;

  placeholder?: string;

  autoComplete:
    | "current-password"
    | "new-password";

  disabled?: boolean;
  error?: string;

  onChange: (
    value: string
  ) => void;
};

export function AuthPasswordField({
  id,
  label,
  value,
  placeholder,
  autoComplete,
  disabled,
  error,
  onChange
}: PasswordFieldProps) {
  const t = useTranslations(
    "auth"
  );

  const [visible, setVisible] =
    useState(false);

  return (
    <div>
      <label
        htmlFor={id}
        className="mb-2 block text-[11px] font-semibold text-[#454542]"
      >
        {label}
      </label>

      <div className="relative">
        <input
          id={id}
          name={id}
          type={
            visible
              ? "text"
              : "password"
          }
          autoComplete={autoComplete}
          disabled={disabled}
          value={value}
          placeholder={placeholder}
          aria-invalid={Boolean(
            error
          )}
          onChange={(event) =>
            onChange(
              event.target.value
            )
          }
          className={`h-[48px] w-full rounded-[12px] border px-4 pr-12 text-[13px] text-[#171717] outline-none transition-[border-color,background-color,box-shadow] duration-200 placeholder:text-[#b2b2ac] disabled:cursor-not-allowed disabled:opacity-50 ${
            error
              ? "border-red-300 bg-red-50/30 focus:border-red-400 focus:bg-white focus:ring-4 focus:ring-red-50"
              : "border-black/[0.075] bg-[#fafaf8] hover:border-black/[0.13] hover:bg-[#f8f8f6] focus:border-black/[0.3] focus:bg-white focus:ring-4 focus:ring-black/[0.025]"
          }`}
        />

        <button
          type="button"
          disabled={disabled}
          onClick={() =>
            setVisible(
              (current) =>
                !current
            )
          }
          aria-label={
            visible
              ? t("password.hide")
              : t("password.show")
          }
          className="absolute right-1.5 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center rounded-[9px] text-[#aaa] transition hover:bg-black/[0.035] hover:text-[#444] disabled:pointer-events-none"
        >
          {visible ? (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="size-[16px]"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="m4 4 16 16" />

              <path d="M10.7 10.8a2 2 0 0 0 2.5 2.5" />

              <path d="M9.5 5.4A9 9 0 0 1 12 5c5.5 0 9 7 9 7a14 14 0 0 1-2.2 3.2" />

              <path d="M6.2 6.2C4.1 7.7 3 10 3 12c0 0 3.5 7 9 7a9 9 0 0 0 3-.5" />
            </svg>
          ) : (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="size-[16px]"
              stroke="currentColor"
              strokeWidth="1.7"
              aria-hidden="true"
            >
              <path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7Z" />

              <circle
                cx="12"
                cy="12"
                r="2.4"
              />
            </svg>
          )}
        </button>
      </div>

      {error && (
        <p className="mt-1.5 text-[10px] font-medium leading-4 text-red-500">
          {error}
        </p>
      )}
    </div>
  );
}

export function AuthServerError({
  message
}: {
  message: string | null;
}) {
  if (!message) {
    return null;
  }

  return (
    <div
      role="alert"
      className="mb-4 flex items-start gap-2.5 rounded-[12px] border border-red-100 bg-red-50/70 px-3.5 py-3"
    >
      <div className="mt-px flex size-[18px] shrink-0 items-center justify-center rounded-full bg-red-500 text-[9px] font-bold text-white">
        !
      </div>

      <p className="text-[10px] leading-[17px] text-red-700">
        {message}
      </p>
    </div>
  );
}

export function AuthSubmitButton({
  loading,
  loadingText,
  children
}: {
  loading: boolean;
  loadingText: string;
  children: ReactNode;
}) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="group flex h-[48px] w-full items-center justify-center rounded-[12px] bg-[#171717] text-[11px] font-semibold text-white transition-[transform,background-color,box-shadow] duration-200 hover:-translate-y-px hover:bg-black hover:shadow-[0_9px_24px_rgba(0,0,0,0.12)] active:translate-y-0 active:scale-[0.995] disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading ? (
        <span className="flex items-center gap-2.5">
          <span className="size-[13px] animate-spin rounded-full border-2 border-white/25 border-t-white" />

          {loadingText}
        </span>
      ) : (
        <span className="flex items-center gap-2.5">
          {children}

          <svg
            viewBox="0 0 20 20"
            fill="none"
            className="size-3.5 transition-transform duration-300 group-hover:translate-x-0.5"
            aria-hidden="true"
          >
            <path
              d="M4 10h11M11 6l4 4-4 4"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
      )}
    </button>
  );
}