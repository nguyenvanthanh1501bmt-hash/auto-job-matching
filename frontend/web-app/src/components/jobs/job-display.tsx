import type {ReactNode} from "react";

import type {
  NormalizedJobSummary
} from "@/types/job";

export type JobExperienceDisplay =
  | {
      type: "range";
      min: number;
      max: number;
    }
  | {
      type: "minimum";
      value: number;
    }
  | {
      type: "maximum";
      value: number;
    };

export function formatJobDate(
  value: string | null,
  locale: string
): string | null {
  if (!value) {
    return null;
  }

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
      year: "numeric"
    }
  ).format(date);
}

export function formatJobMoney(
  value: number,
  currency: string | null,
  locale: string
): string {
  const numberLocale =
    locale === "vi"
      ? "vi-VN"
      : "en-US";

  if (!currency) {
    return new Intl.NumberFormat(
      numberLocale
    ).format(value);
  }

  try {
    return new Intl.NumberFormat(
      numberLocale,
      {
        style: "currency",
        currency,
        maximumFractionDigits: 0
      }
    ).format(value);
  } catch {
    return `${new Intl.NumberFormat(
      numberLocale
    ).format(value)} ${currency}`;
  }
}

export function getStructuredJobSalary(
  job: NormalizedJobSummary,
  locale: string
): string | null {
  if (
    job.salaryMin !== null &&
    job.salaryMax !== null
  ) {
    return `${formatJobMoney(
      job.salaryMin,
      job.currency,
      locale
    )} – ${formatJobMoney(
      job.salaryMax,
      job.currency,
      locale
    )}`;
  }

  if (
    job.salaryMin !== null
  ) {
    return `${formatJobMoney(
      job.salaryMin,
      job.currency,
      locale
    )}+`;
  }

  if (
    job.salaryMax !== null
  ) {
    return `≤ ${formatJobMoney(
      job.salaryMax,
      job.currency,
      locale
    )}`;
  }

  return null;
}

export function getJobExperience(
  job: NormalizedJobSummary
): JobExperienceDisplay | null {
  if (
    job.experienceMin !== null &&
    job.experienceMax !== null
  ) {
    return {
      type: "range",
      min: job.experienceMin,
      max: job.experienceMax
    };
  }

  if (
    job.experienceMin !== null
  ) {
    return {
      type: "minimum",
      value: job.experienceMin
    };
  }

  if (
    job.experienceMax !== null
  ) {
    return {
      type: "maximum",
      value: job.experienceMax
    };
  }

  return null;
}

export function JobMetaPill({
  children
}: {
  children: ReactNode;
}) {
  return (
    <span className="inline-flex rounded-full border border-black/[0.055] bg-[#fafaf8] px-2.5 py-1 text-[9px] font-medium text-black/48">
      {children}
    </span>
  );
}

export function ArrowRightIcon() {
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

export function ExternalLinkIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="M11.5 4.5h4v4M9.5 10.5l6-6"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M15 11v3.25A1.75 1.75 0 0 1 13.25 16H5.75A1.75 1.75 0 0 1 4 14.25v-7.5A1.75 1.75 0 0 1 5.75 5H9"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}