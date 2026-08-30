"use client";

import {useLayoutEffect, useState} from "react";
import {useTranslations} from "next-intl";

import {useAdminRawJobs} from "@/hooks/use-admin-tools";
import {DEFAULT_RAW_JOB_LIMIT} from "@/services/admin-job.service";
import {LIVE_CRAWLER_SOURCES} from "@/types/admin-crawler";
import type {
  AdminDashboardProps,
  AdminMetricsPanelProps,
  AdminOperationCardProps,
  AdminOperationIconProps,
  AdminOperationsPanelProps,
  AdminOverviewSectionProps,
  AdminRecentJobsProps,
  AdminSection
} from "@/types/admin-ui";

import {AdminHeader} from "./admin-header";
import {AdminSidebar} from "./admin-sidebar";
import {CrawlerSection} from "./crawler-section";
import {EmbeddingsSection} from "./embeddings-section";
import {JobsSection} from "./jobs-section";
import {formatDate} from "./admin-ui";

const ADMIN_SECTION_STORAGE_KEY = "autojob.admin.section";

function isAdminSection(
  value: string | null
): value is AdminSection {
  return (
    value === "overview" ||
    value === "crawler" ||
    value === "jobs" ||
    value === "embeddings"
  );
}

function ArrowIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
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
  );
}

function OperationIcon({
  type
}: AdminOperationIconProps) {
  if (type === "crawler") {
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        className="size-[18px]"
        aria-hidden="true"
      >
        <path
          d="M6 7h12M6 12h8M6 17h5"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
        />

        <path
          d="M18 14v6M15 17h6"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
        />
      </svg>
    );
  }

  if (type === "jobs") {
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        className="size-[18px]"
        aria-hidden="true"
      >
        <rect
          x="4"
          y="7"
          width="16"
          height="12"
          rx="2.5"
          stroke="currentColor"
          strokeWidth="1.7"
        />

        <path
          d="M9 7V5.8C9 4.8 9.8 4 10.8 4h2.4C14.2 4 15 4.8 15 5.8V7M4 11.5h16"
          stroke="currentColor"
          strokeWidth="1.7"
        />
      </svg>
    );
  }

  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[18px]"
      aria-hidden="true"
    >
      <circle
        cx="6"
        cy="12"
        r="2"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <circle
        cx="18"
        cy="7"
        r="2"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <circle
        cx="18"
        cy="17"
        r="2"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <path
        d="m8 11 8-3M8 13l8 3"
        stroke="currentColor"
        strokeWidth="1.7"
      />
    </svg>
  );
}

function DashboardHeading() {
  const t = useTranslations("admin.overview");

  return (
    <section className="relative pt-1">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -left-20 -top-20 size-64 rounded-full bg-[#d9ff75]/[0.07] blur-3xl"
      />

      <div className="relative flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.07]" />

            <p className="font-mono text-[10px] font-medium uppercase tracking-[0.16em] text-[#92928c]">
              {t("eyebrow")}
            </p>
          </div>

          <h1 className="mt-4 text-[38px] font-semibold leading-[1.02] tracking-[-0.055em] text-[#171717] sm:text-[44px]">
            {t("title")}
          </h1>

          <p className="mt-4 max-w-[760px] text-[14px] leading-[1.75] text-[#74746f]">
            {t("description")}
          </p>
        </div>

        <div className="flex shrink-0 items-center gap-2.5 rounded-full border border-black/[0.055] bg-white px-4 py-2.5 shadow-[0_2px_12px_rgba(0,0,0,0.025)]">
          <span className="relative flex size-2">
            <span className="absolute inset-0 animate-ping rounded-full bg-[#d9ff75] opacity-25" />
            <span className="relative size-2 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />
          </span>

          <span className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#898983]">
            {t("systemActive")}
          </span>
        </div>
      </div>
    </section>
  );
}

function MetricsPanel({
  rawJobCount,
  loading
}: AdminMetricsPanelProps) {
  const t = useTranslations("admin.overview");

  const metrics = [
    {
      index: "01",
      label: t("cards.rawJobs.label"),
      value: loading ? "..." : String(rawJobCount),
      detail: t("cards.rawJobs.detail")
    },
    {
      index: "02",
      label: t("cards.liveSources.label"),
      value: String(LIVE_CRAWLER_SOURCES.length),
      detail: t("cards.liveSources.detail")
    }
  ];

  return (
    <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
      <div className="flex items-center justify-between border-b border-black/[0.045] px-6 py-4">
        <div className="flex items-center gap-2.5">
          <span className="font-mono text-[9px] uppercase tracking-[0.15em] text-[#aaa]">
            {t("workspaceSnapshot")}
          </span>

          <span className="h-px w-10 bg-black/[0.08]" />
        </div>

        <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />
      </div>

      <div className="grid sm:grid-cols-2">
        {metrics.map((metric, index) => (
          <div
            key={metric.index}
            className={`relative px-6 py-6 ${
              index !== metrics.length - 1
                ? "sm:border-r sm:border-black/[0.05]"
                : ""
            } ${
              index !== metrics.length - 1
                ? "border-b border-black/[0.05] sm:border-b-0"
                : ""
            }`}
          >
            <div className="flex items-center justify-between gap-4">
              <p className="text-[13px] font-semibold tracking-[-0.015em] text-[#74746f]">
                {metric.label}
              </p>

              <span className="font-mono text-[9px] text-[#b7b7b1]">
                {metric.index}
              </span>
            </div>

            <p className="mt-5 text-[36px] font-semibold leading-none tracking-[-0.055em] text-[#20201e]">
              {metric.value}
            </p>

            <p className="mt-4 max-w-[220px] text-[11px] leading-[18px] text-[#aaa]">
              {metric.detail}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

function OperationCard({
  type,
  index,
  title,
  description,
  onClick
}: AdminOperationCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group relative flex min-h-[128px] w-full flex-col justify-between overflow-hidden rounded-[16px] border border-black/[0.055] bg-[#fafaf8] p-5 text-left transition-[background-color,border-color,transform,box-shadow] duration-200 hover:-translate-y-px hover:border-black/[0.09] hover:bg-white hover:shadow-[0_10px_28px_rgba(0,0,0,0.035)]"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-8 -top-8 size-24 rounded-full bg-[#d9ff75]/[0.06] blur-2xl opacity-0 transition-opacity duration-300 group-hover:opacity-100"
      />

      <div className="relative flex w-full items-start justify-between gap-4">
        <div className="flex size-10 items-center justify-center rounded-[11px] border border-black/[0.055] bg-white text-[#555] shadow-[0_2px_8px_rgba(0,0,0,0.02)]">
          <OperationIcon type={type} />
        </div>

        <span className="font-mono text-[9px] text-[#b5b5af]">
          {index}
        </span>
      </div>

      <div className="relative mt-5 flex items-end gap-4">
        <div className="min-w-0 flex-1">
          <h3 className="text-[15px] font-semibold tracking-[-0.025em] text-[#292927]">
            {title}
          </h3>

          <p className="mt-1.5 text-[11px] leading-[18px] text-[#969690]">
            {description}
          </p>
        </div>

        <span className="flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.055] bg-white text-[#aaa] transition-[color,border-color,transform] group-hover:translate-x-0.5 group-hover:border-black/[0.1] group-hover:text-[#333]">
          <ArrowIcon />
        </span>
      </div>
    </button>
  );
}

function OperationsPanel({
  onNavigate
}: AdminOperationsPanelProps) {
  const t = useTranslations("admin.overview");

  const operations = [
    {
      type: "crawler" as const,
      index: "01",
      title: t("quickActions.crawler.title"),
      description: t("quickActions.crawler.description")
    },
    {
      type: "jobs" as const,
      index: "02",
      title: t("quickActions.jobs.title"),
      description: t("quickActions.jobs.description")
    },
    {
      type: "embeddings" as const,
      index: "03",
      title: t("quickActions.embeddings.title"),
      description: t("quickActions.embeddings.description")
    }
  ];

  return (
    <section className="rounded-[20px] border border-black/[0.055] bg-white p-5 shadow-[0_12px_40px_rgba(0,0,0,0.025)] sm:p-6">
      <div className="mb-5 flex items-start justify-between gap-5">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="font-mono text-[9px] uppercase tracking-[0.15em] text-[#aaa]">
              {t("quickAccess")}
            </span>

            <span className="h-px w-8 bg-black/[0.08]" />
          </div>

          <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
            {t("pipeline.title")}
          </h2>

          <p className="mt-1.5 text-[12px] leading-5 text-[#92928c]">
            {t("pipeline.description")}
          </p>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {operations.map((operation) => (
          <OperationCard
            key={operation.type}
            {...operation}
            onClick={() => onNavigate(operation.type)}
          />
        ))}
      </div>
    </section>
  );
}

function RecentJobs({
  locale,
  jobs,
  loading,
  onOpenJobs
}: AdminRecentJobsProps) {
  const t = useTranslations("admin.overview");

  const recentJobs = [...jobs]
    .sort((a, b) => {
      const first = a.collectedAt
        ? new Date(a.collectedAt).getTime()
        : 0;

      const second = b.collectedAt
        ? new Date(b.collectedAt).getTime()
        : 0;

      return second - first;
    })
    .slice(0, 6);

  return (
    <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
      <div className="flex items-start justify-between gap-5 border-b border-black/[0.045] px-5 py-5 sm:px-6">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="font-mono text-[9px] uppercase tracking-[0.15em] text-[#aaa]">
              {t("activity")}
            </span>

            <span className="h-px w-8 bg-black/[0.08]" />
          </div>

          <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
            {t("recent.title")}
          </h2>

          <p className="mt-1.5 text-[12px] leading-5 text-[#92928c]">
            {t("recent.description")}
          </p>
        </div>

        <button
          type="button"
          onClick={onOpenJobs}
          className="mt-1 shrink-0 rounded-full border border-black/[0.055] bg-[#fafaf8] px-3.5 py-2 text-[10px] font-semibold text-[#777] transition hover:border-black/[0.1] hover:bg-white hover:text-[#222]"
        >
          {t("recent.openJobs")} →
        </button>
      </div>

      {loading ? (
        <div className="flex min-h-[385px] items-center justify-center">
          <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />
        </div>
      ) : null}

      {!loading && recentJobs.length === 0 ? (
        <div className="flex min-h-[385px] items-center justify-center px-8 text-center">
          <div>
            <div className="mx-auto flex size-10 items-center justify-center rounded-full border border-black/[0.05] bg-[#fafaf8]">
              <span className="size-1.5 rounded-full bg-[#d9ff75]" />
            </div>

            <p className="mx-auto mt-4 max-w-[260px] text-[12px] leading-5 text-[#999]">
              {t("recent.empty")}
            </p>
          </div>
        </div>
      ) : null}

      {!loading && recentJobs.length > 0 ? (
        <div>
          {recentJobs.map((job, index) => (
            <div
              key={job.id}
              className={`group flex items-center gap-4 px-5 py-[16px] transition-colors hover:bg-[#fafaf8] sm:px-6 ${
                index !== recentJobs.length - 1
                  ? "border-b border-black/[0.045]"
                  : ""
              }`}
            >
              <span className="w-5 shrink-0 font-mono text-[9px] text-[#b5b5af]">
                {String(index + 1).padStart(2, "0")}
              </span>

              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] font-semibold tracking-[-0.015em] text-[#2d2d2a]">
                  {job.title || "—"}
                </p>

                <div className="mt-1.5 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-[10px] text-[#999]">
                  <span className="max-w-[180px] truncate">
                    {job.companyName || "—"}
                  </span>

                  <span className="text-black/20">
                    /
                  </span>

                  <span className="font-mono text-[9px] font-medium text-[#8d8d87]">
                    {job.sourceCode || "—"}
                  </span>
                </div>
              </div>

              <span className="hidden shrink-0 text-right text-[9px] leading-4 text-[#aaa] sm:block">
                {formatDate(job.collectedAt, locale)}
              </span>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function OperationalNote() {
  const t = useTranslations("admin.overview");

  return (
    <div className="flex items-start gap-3.5 rounded-[16px] border border-black/[0.045] bg-white/55 px-5 py-4">
      <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.05] bg-white">
        <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />
      </div>

      <div>
        <p className="text-[12px] font-semibold text-[#565650]">
          {t("notice.title")}
        </p>

        <p className="mt-1.5 max-w-4xl text-[12px] leading-5 text-[#969690]">
          {t("notice.description")}
        </p>
      </div>
    </div>
  );
}

function OverviewSection({
  locale,
  onNavigate
}: AdminOverviewSectionProps) {
  const rawJobsQuery = useAdminRawJobs(
    DEFAULT_RAW_JOB_LIMIT,
    true
  );

  const rawJobs = rawJobsQuery.data ?? [];

  return (
    <div className="space-y-6">
      <DashboardHeading />

      <MetricsPanel
        rawJobCount={rawJobs.length}
        loading={rawJobsQuery.isLoading}
      />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,0.95fr)_minmax(440px,1.05fr)]">
        <OperationsPanel
          onNavigate={onNavigate}
        />

        <RecentJobs
          locale={locale}
          jobs={rawJobs}
          loading={rawJobsQuery.isLoading}
          onOpenJobs={() => onNavigate("jobs")}
        />
      </div>

      <OperationalNote />
    </div>
  );
}

export function AdminDashboard({
  adminName,
  locale
}: AdminDashboardProps) {
  const [section, setSection] =
    useState<AdminSection>("overview");

  useLayoutEffect(() => {
    const savedSection = window.sessionStorage.getItem(
      ADMIN_SECTION_STORAGE_KEY
    );

    if (isAdminSection(savedSection)) {
      setSection(savedSection);
    }
  }, []);

  function changeSection(nextSection: AdminSection) {
    setSection(nextSection);

    window.sessionStorage.setItem(
      ADMIN_SECTION_STORAGE_KEY,
      nextSection
    );
  }

  return (
    <main
      className="min-h-screen bg-[#f7f7f4] text-[#171717] selection:bg-[#171717] selection:text-white"
      style={{
        backgroundImage: `
          radial-gradient(circle at 14% 14%, rgba(255,255,255,.94), transparent 28%),
          radial-gradient(circle at 88% 80%, rgba(255,255,255,.76), transparent 25%),
          linear-gradient(rgba(0,0,0,.012) 1px, transparent 1px),
          linear-gradient(90deg, rgba(0,0,0,.012) 1px, transparent 1px)
        `,
        backgroundSize: "auto, auto, 32px 32px, 32px 32px"
      }}
    >
      <AdminHeader
        adminName={adminName}
        locale={locale}
      />

      <div className="mx-auto max-w-[1440px] px-5 sm:px-8 lg:px-12 xl:px-16">
        <div className="grid min-w-0 gap-7 py-5 lg:grid-cols-[210px_minmax(0,1fr)] lg:gap-10 lg:py-8 xl:grid-cols-[225px_minmax(0,1fr)] xl:gap-12">
          <AdminSidebar
            activeSection={section}
            onSectionChange={changeSection}
          />

          <div className="min-w-0 pb-14">
            <div className="mx-auto max-w-[1160px]">
              {section === "overview" ? (
                <OverviewSection
                  locale={locale}
                  onNavigate={changeSection}
                />
              ) : null}

              {section === "crawler" ? (
                <CrawlerSection />
              ) : null}

              {section === "jobs" ? (
                <JobsSection />
              ) : null}

              {section === "embeddings" ? (
                <EmbeddingsSection />
              ) : null}
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}