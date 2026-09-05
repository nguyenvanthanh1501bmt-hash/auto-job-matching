"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  getApiErrorMessage
} from "@/lib/api-error";

import type {
  CvProcessingStatus
} from "@/types/cv";

import type {
  CurrentCvCardProps
} from "@/types/cv-ui";

function statusClass(
  status:
    CvProcessingStatus
) {
  if (
    status ===
    "PARSED"
  ) {
    return "border-[#cde978] bg-[#efffc3] text-[#4d6018]";
  }

  if (
    status ===
    "FAILED"
  ) {
    return "border-red-100 bg-red-50 text-red-700";
  }

  if (
    status ===
    "PARSING"
  ) {
    return "border-amber-100 bg-amber-50 text-amber-700";
  }

  return "border-black/[0.05] bg-[#f3f3ef] text-black/50";
}

function formatBytes(
  bytes: number,
  locale: string
) {
  if (
    bytes <
    1024
  ) {
    return `${bytes} B`;
  }

  const units = [
    "KB",
    "MB",
    "GB"
  ];

  let value =
    bytes / 1024;

  let unitIndex =
    0;

  while (
    value >=
      1024 &&
    unitIndex <
      units.length -
        1
  ) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${new Intl.NumberFormat(
    locale ===
      "vi"
      ? "vi-VN"
      : "en-US",
    {
      maximumFractionDigits:
        1
    }
  ).format(
    value
  )} ${
    units[
      unitIndex
    ]
  }`;
}

function formatDate(
  value: string,
  locale: string
) {
  const date =
    new Date(
      value
    );

  if (
    Number.isNaN(
      date.getTime()
    )
  ) {
    return value;
  }

  return new Intl.DateTimeFormat(
    locale ===
      "vi"
      ? "vi-VN"
      : "en-US",
    {
      day:
        "2-digit",

      month:
        "short",

      year:
        "numeric",

      hour:
        "2-digit",

      minute:
        "2-digit"
    }
  ).format(
    date
  );
}

function DocumentIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-5"
      aria-hidden="true"
    >
      <path
        d="M7.5 3.5h6L18 8v12.5H7.5A2.5 2.5 0 0 1 5 18V6a2.5 2.5 0 0 1 2.5-2.5Z"
        stroke="currentColor"
        strokeWidth="1.55"
        strokeLinejoin="round"
      />

      <path
        d="M13.5 3.75V8H17.7M8.5 12h6.5M8.5 15.5h5"
        stroke="currentColor"
        strokeWidth="1.55"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function CurrentCvCard({
  cv,
  isLoading,
  isError,
  error,
  status,
  isBusy,
  canRetryParse,
  onRetryParse,
  onClear
}: CurrentCvCardProps) {
  const t =
    useTranslations(
      "user.cv"
    );

  const locale =
    useLocale();

  return (
    <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_8px_28px_rgba(0,0,0,0.035)]">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/26">
            {t(
              "current.title"
            )}
          </p>

          <h2 className="mt-1 truncate text-[15px] font-bold tracking-[-0.035em] text-[#292927]">
            {cv
              ?.originalFilename ||
              t(
                "upload.title"
              )}
          </h2>
        </div>

        <div className="flex size-10 shrink-0 items-center justify-center rounded-[13px] bg-[#f3f3ef] text-black/45">
          <DocumentIcon />
        </div>
      </div>

      {isLoading ? (
        <div className="mt-5 flex items-center gap-2 rounded-[14px] bg-[#f8f8f5] px-3.5 py-3 text-[10px] text-black/38">
          <span className="size-3.5 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

          {t(
            "current.loading"
          )}
        </div>
      ) : null}

      {isError ? (
        <p className="mt-5 rounded-[14px] border border-red-100 bg-red-50 px-3.5 py-3 text-[10px] leading-5 text-red-700">
          {getApiErrorMessage(
            error
          )}
        </p>
      ) : null}

      {cv ? (
        <div className="mt-5">
          <div className="rounded-[17px] border border-black/[0.05] bg-[#fafaf7] p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/27">
                  {t(
                    "current.filename"
                  )}
                </p>

                <p
                  className="mt-1.5 truncate text-[11px] font-semibold text-black/58"
                  title={
                    cv.originalFilename
                  }
                >
                  {
                    cv.originalFilename
                  }
                </p>
              </div>

              {status ? (
                <span
                  className={`shrink-0 rounded-full border px-2.5 py-1 text-[8px] font-bold uppercase tracking-[0.06em] ${statusClass(
                    status
                  )}`}
                >
                  {t(
                    `status.${status}`
                  )}
                </span>
              ) : null}
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3 border-t border-black/[0.05] pt-4">
              <div>
                <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.12em] text-black/24">
                  {t(
                    "current.size"
                  )}
                </p>

                <p className="mt-1.5 text-[10px] font-semibold text-black/47">
                  {formatBytes(
                    cv.sizeBytes,
                    locale
                  )}
                </p>
              </div>

              <div>
                <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.12em] text-black/24">
                  {t(
                    "current.uploadedAt"
                  )}
                </p>

                <p className="mt-1.5 text-[10px] font-semibold leading-4 text-black/47">
                  {formatDate(
                    cv.uploadedAt,
                    locale
                  )}
                </p>
              </div>
            </div>
          </div>

          {cv.status ===
          "PARSED" ? (
            <div className="mt-3 flex items-start gap-2.5 rounded-[14px] border border-[#dceba9] bg-[#f6fbdc] px-3.5 py-3 text-[10px] leading-5 text-[#5d6b2e]">
              <span className="mt-[6px] size-1.5 shrink-0 rounded-full bg-[#a8c747]" />

              <span>
                {t(
                  "current.ready"
                )}
              </span>
            </div>
          ) : null}

          <div className="mt-4 grid gap-2">
            {canRetryParse ? (
              <button
                type="button"
                onClick={
                  onRetryParse
                }
                className="h-10 w-full rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white shadow-[0_5px_15px_rgba(0,0,0,0.08)] transition-transform hover:-translate-y-0.5"
              >
                {t(
                  "upload.retryParse"
                )}
              </button>
            ) : null}

            <button
              type="button"
              disabled={
                isBusy
              }
              onClick={
                onClear
              }
              className="h-10 w-full rounded-full border border-black/[0.07] bg-white px-4 text-[10px] font-semibold text-black/42 transition-colors hover:border-black/[0.14] hover:text-black disabled:cursor-not-allowed disabled:opacity-40"
            >
              {t(
                "upload.clear"
              )}
            </button>
          </div>
        </div>
      ) : null}
    </section>
  );
}