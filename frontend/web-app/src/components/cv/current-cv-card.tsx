"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  getApiErrorMessage
} from "@/lib/api-error";

import type {
  CvProcessingStatus,
  RawCvResponse
} from "@/types/cv";

type Props = {
  cv:
    | RawCvResponse
    | undefined;

  isLoading: boolean;
  isError: boolean;

  error: unknown;

  status:
    | CvProcessingStatus
    | null;

  isBusy: boolean;

  canRetryParse: boolean;

  onRetryParse: () => void;
  onClear: () => void;
};

function statusClass(
  status: CvProcessingStatus
) {
  if (
    status === "PARSED"
  ) {
    return "border-[#cde978] bg-[#eaffb4] text-[#485c16]";
  }

  if (
    status === "FAILED"
  ) {
    return "border-red-100 bg-red-50 text-red-700";
  }

  if (
    status === "PARSING"
  ) {
    return "border-amber-100 bg-amber-50 text-amber-700";
  }

  return "border-black/[0.04] bg-[#f3f3ef] text-black/50";
}

function formatBytes(
  bytes: number,
  locale: string
) {
  if (
    bytes < 1024
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
    value >= 1024 &&
    unitIndex <
      units.length - 1
  ) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${new Intl.NumberFormat(
    locale === "vi"
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
    new Date(value);

  if (
    Number.isNaN(
      date.getTime()
    )
  ) {
    return value;
  }

  return new Intl.DateTimeFormat(
    locale === "vi"
      ? "vi-VN"
      : "en-US",
    {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    }
  ).format(date);
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
}: Props) {
  const t =
    useTranslations(
      "user.cv"
    );

  const locale =
    useLocale();

  return (
    <section className="rounded-[20px] border border-black/[0.055] bg-white p-5 shadow-[0_4px_18px_rgba(0,0,0,0.025)]">
      <h2 className="text-[13px] font-bold tracking-[-0.025em] text-[#333]">
        {t(
          "current.title"
        )}
      </h2>

      {isLoading ? (
        <div className="mt-4 flex items-center gap-2 text-[10px] text-black/35">
          <span className="size-3.5 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

          {t(
            "current.loading"
          )}
        </div>
      ) : null}

      {isError ? (
        <p className="mt-4 rounded-[12px] bg-red-50 px-3 py-3 text-[10px] leading-5 text-red-700">
          {getApiErrorMessage(
            error
          )}
        </p>
      ) : null}

      {cv ? (
        <div className="mt-4 space-y-4">
          <div>
            <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
              {t(
                "current.filename"
              )}
            </p>

            <p className="mt-1.5 break-all text-[11px] font-semibold text-black/55">
              {cv.originalFilename}
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
                {t(
                  "current.size"
                )}
              </p>

              <p className="mt-1.5 text-[10px] font-medium text-black/50">
                {formatBytes(
                  cv.sizeBytes,
                  locale
                )}
              </p>
            </div>

            <div>
              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
                {t(
                  "current.status"
                )}
              </p>

              {status ? (
                <span
                  className={`mt-1.5 inline-flex rounded-full border px-2.5 py-1 text-[9px] font-semibold ${statusClass(
                    status
                  )}`}
                >
                  {t(
                    `status.${status}`
                  )}
                </span>
              ) : null}
            </div>
          </div>

          <div>
            <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
              {t(
                "current.uploadedAt"
              )}
            </p>

            <p className="mt-1.5 text-[10px] font-medium text-black/50">
              {formatDate(
                cv.uploadedAt,
                locale
              )}
            </p>
          </div>

          {cv.status ===
          "PARSED" ? (
            <p className="rounded-[12px] bg-[#f5f9ea] px-3 py-3 text-[10px] leading-5 text-[#61712c]">
              {t(
                "current.ready"
              )}
            </p>
          ) : null}

          {canRetryParse ? (
            <button
              type="button"
              onClick={
                onRetryParse
              }
              className="h-9 w-full rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white"
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
            className="h-9 w-full rounded-full border border-black/[0.065] bg-white px-4 text-[10px] font-semibold text-black/45 hover:text-black disabled:opacity-40"
          >
            {t(
              "upload.clear"
            )}
          </button>
        </div>
      ) : null}
    </section>
  );
}