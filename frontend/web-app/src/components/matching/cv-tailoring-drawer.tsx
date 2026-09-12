"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState
} from "react";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  useAnalyzeCvTailoring,
  usePreviewCvTailoring
} from "@/hooks/use-cv-tailoring";

import {
  getApiErrorMessage,
  toApiError
} from "@/lib/api-error";

import type {
  CvTailoringEvidenceItem,
  CvTailoringPreviewScore,
  CvTailoringSuggestionItem
} from "@/types/cv-tailoring";

type CvTailoringDrawerProps = {
  candidateProfileId: string | null;
  jobId: string | null;
  onClose: () => void;
};

type ScoreRow = {
  key:
    | "semantic"
    | "skills"
    | "seniority"
    | "location"
    | "freshness";
  value: number | null;
  known: boolean;
};

function CloseIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="m5.5 5.5 9 9m0-9-9 9"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function SparkIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="M10 2.4c.45 2.96 1.92 4.43 4.88 4.88C11.92 7.73 10.45 9.2 10 12.16 9.55 9.2 8.08 7.73 5.12 7.28 8.08 6.83 9.55 5.36 10 2.4Z"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />

      <path
        d="M15.2 11.7c.2 1.32.86 1.98 2.18 2.18-1.32.2-1.98.86-2.18 2.18-.2-1.32-.86-1.98-2.18-2.18 1.32-.2 1.98-.86 2.18-2.18Z"
        fill="currentColor"
      />
    </svg>
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
        d="M4 10h12m-4-4 4 4-4 4"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function WarningIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="M9.03 3.22 2.7 14.08A1.12 1.12 0 0 0 3.67 15.76h12.66a1.12 1.12 0 0 0 .97-1.68L10.97 3.22a1.12 1.12 0 0 0-1.94 0Z"
        stroke="currentColor"
        strokeWidth="1.35"
        strokeLinejoin="round"
      />

      <path
        d="M10 7v3.8m0 2.3v.1"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

function toPercent(
  value: number | null
): number | null {
  if (
    value === null ||
    !Number.isFinite(value)
  ) {
    return null;
  }

  return Math.round(
    Math.min(
      Math.max(value, 0),
      1
    ) * 100
  );
}

function ScoreBar({
  label,
  value
}: {
  label: string;
  value: number;
}) {
  const percent =
    toPercent(value) ?? 0;

  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between gap-3">
        <span className="text-[9px] font-medium text-black/40">
          {label}
        </span>

        <span className="font-mono text-[8px] font-semibold text-black/35">
          {percent}%
        </span>
      </div>

      <div className="h-1.5 overflow-hidden rounded-full bg-black/[0.055]">
        <div
          className="h-full rounded-full bg-[#20201e]"
          style={{
            width: `${percent}%`
          }}
        />
      </div>
    </div>
  );
}

function SkillTags({
  skills,
  tone = "positive"
}: {
  skills: string[];
  tone?: "positive" | "neutral";
}) {
  if (skills.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-1.5">
      {skills.map(
        (skill) => (
          <span
            key={skill}
            className={
              tone === "positive"
                ? "rounded-[8px] border border-[#dbeaa5] bg-[#f3ffd6] px-2.5 py-1 text-[9px] font-medium text-[#4d5b20]"
                : "rounded-[8px] border border-black/[0.055] bg-[#f5f5f1] px-2.5 py-1 text-[9px] font-medium text-black/45"
            }
          >
            {skill}
          </span>
        )
      )}
    </div>
  );
}

function TailoringLoading() {
  return (
    <div className="animate-pulse space-y-5 px-5 py-7 sm:px-8 sm:py-9">
      <div className="rounded-[20px] border border-black/[0.04] bg-white p-5">
        <div className="h-3 w-24 rounded-full bg-black/[0.05]" />
        <div className="mt-4 h-7 w-[72%] rounded-[7px] bg-black/[0.06]" />
        <div className="mt-2 h-3 w-[42%] rounded-full bg-black/[0.04]" />
        <div className="mt-6 h-24 rounded-[15px] bg-black/[0.035]" />
      </div>

      {Array.from({
        length: 3
      }).map((_, index) => (
        <div
          key={index}
          className="h-[185px] rounded-[20px] border border-black/[0.04] bg-white"
        />
      ))}
    </div>
  );
}

function getEvidenceContext(
  suggestion: CvTailoringSuggestionItem,
  evidenceById: Map<
    string,
    CvTailoringEvidenceItem
  >
): CvTailoringEvidenceItem[] {
  const seen = new Set<string>();

  return suggestion.evidenceIds
    .map((evidenceId) =>
      evidenceById.get(evidenceId)
    )
    .filter(
      (
        evidence
      ): evidence is CvTailoringEvidenceItem =>
        Boolean(
          evidence?.text?.trim()
        )
    )
    .filter((evidence) => {
      const text =
        evidence.text?.trim() ?? "";

      if (seen.has(text)) {
        return false;
      }

      seen.add(text);
      return true;
    })
    .slice(0, 2);
}

function requiresReanalysis(
  error: unknown
): boolean {
  const apiError =
    toApiError(error);

  return (
    apiError.status === 404 ||
    apiError.status === 409
  );
}

function PreviewScoreCard({
  label,
  snapshot
}: {
  label: string;
  snapshot: CvTailoringPreviewScore;
}) {
  const t =
    useTranslations(
      "user.matches.tailoring"
    );

  const tScore =
    useTranslations(
      "user.matches.score"
    );

  const scoreRows: ScoreRow[] = [
    {
      key: "semantic",
      value: snapshot.semanticScore,
      known:
        snapshot.semanticScore !== null
    },
    {
      key: "skills",
      value: snapshot.skillScore,
      known:
        snapshot.skillKnown === true
    },
    {
      key: "seniority",
      value:
        snapshot.seniorityScore,
      known:
        snapshot.seniorityKnown === true
    },
    {
      key: "location",
      value: snapshot.locationScore,
      known:
        snapshot.locationKnown === true
    },
    {
      key: "freshness",
      value: snapshot.freshnessScore,
      known:
        snapshot.freshnessKnown === true
    }
  ];

  const finalPercent =
    toPercent(
      snapshot.finalScore
    );

  return (
    <article className="rounded-[17px] border border-black/[0.055] bg-[#fafaf7] p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/30">
            {label}
          </p>

          <span
            className={`mt-2 inline-flex rounded-full border px-2.5 py-1 text-[8px] font-semibold ${
              snapshot.status === "MATCHED"
                ? "border-[#dbeaa5] bg-[#f3ffd6] text-[#4d5b20]"
                : "border-[#eadca7] bg-[#fff7d9] text-[#6b5715]"
            }`}
          >
            {t(
              `preview.status.${snapshot.status}`
            )}
          </span>
        </div>

        <div className="flex gap-2">
          <div className="rounded-[11px] border border-black/[0.05] bg-white px-3 py-2 text-center">
            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.1em] text-black/28">
              {t("currentMatch.rank")}
            </p>

            <p className="mt-1 text-[15px] font-bold text-[#31312e]">
              {snapshot.rank !== null
                ? `#${snapshot.rank}`
                : "—"}
            </p>
          </div>

          <div className="rounded-[11px] border border-black/[0.05] bg-white px-3 py-2 text-center">
            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.1em] text-black/28">
              {t("currentMatch.score")}
            </p>

            <p className="mt-1 text-[15px] font-bold text-[#31312e]">
              {finalPercent !== null
                ? `${finalPercent}%`
                : "—"}
            </p>
          </div>
        </div>
      </div>

      {snapshot.reason?.trim() ? (
        <div className="mt-3 rounded-[12px] border border-[#eadca7] bg-[#fffaf0] px-3 py-2.5 text-[9px] leading-[17px] text-[#675b32]/78">
          {snapshot.reason}
        </div>
      ) : null}

      <div className="mt-4 space-y-3">
        {scoreRows
          .filter(
            (row) =>
              row.known &&
              row.value !== null
          )
          .map((row) => (
            <ScoreBar
              key={row.key}
              label={tScore(
                row.key
              )}
              value={row.value ?? 0}
            />
          ))}
      </div>

      <div className="mt-4 space-y-3 border-t border-black/[0.05] pt-4">
        <div>
          <p className="mb-2 font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
            {t("matchedSkills")}
          </p>

          {snapshot.matchedSkills.length > 0 ? (
            <SkillTags
              skills={snapshot.matchedSkills}
            />
          ) : (
            <p className="text-[9px] text-black/32">
              {t("none")}
            </p>
          )}
        </div>

        <div>
          <p className="mb-2 font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
            {t("missingSkills")}
          </p>

          {snapshot.missingSkills.length > 0 ? (
            <SkillTags
              skills={snapshot.missingSkills}
              tone="neutral"
            />
          ) : (
            <p className="text-[9px] text-black/32">
              {t("none")}
            </p>
          )}
        </div>
      </div>
    </article>
  );
}

export function CvTailoringDrawer({
  candidateProfileId,
  jobId,
  onClose
}: CvTailoringDrawerProps) {
  const t =
    useTranslations(
      "user.matches.tailoring"
    );

  const tScore =
    useTranslations(
      "user.matches.score"
    );

  const locale = useLocale();

  const [
    acceptedSuggestionIds,
    setAcceptedSuggestionIds
  ] = useState<string[]>([]);

  const lastOpenedKeyRef =
    useRef<string | null>(null);

  const analyzeMutation =
    useAnalyzeCvTailoring();

  const previewMutation =
    usePreviewCvTailoring();

  const {
    mutate: analyze,
    reset: resetAnalyze
  } = analyzeMutation;

  const {
    mutate: preview,
    reset: resetPreview
  } = previewMutation;

  const runAnalyze = useCallback(() => {
    if (
      !candidateProfileId?.trim() ||
      !jobId?.trim()
    ) {
      return;
    }

    setAcceptedSuggestionIds([]);
    resetPreview();
    resetAnalyze();

    analyze({
      candidateProfileId,
      normalizedJobId: jobId
    });
  }, [
    analyze,
    candidateProfileId,
    jobId,
    resetAnalyze,
    resetPreview
  ]);

  useEffect(() => {
    if (
      !candidateProfileId?.trim() ||
      !jobId?.trim()
    ) {
      lastOpenedKeyRef.current = null;
      resetPreview();
      resetAnalyze();
      return;
    }

    const openedKey =
      `${candidateProfileId.trim()}:${jobId.trim()}`;

    if (
      lastOpenedKeyRef.current ===
      openedKey
    ) {
      return;
    }

    lastOpenedKeyRef.current =
      openedKey;

    runAnalyze();
  }, [
    candidateProfileId,
    jobId,
    resetAnalyze,
    resetPreview,
    runAnalyze
  ]);

  useEffect(() => {
    if (!jobId) {
      return;
    }

    const body = document.body;
    const oldOverflow =
      body.style.overflow;
    const oldPaddingRight =
      body.style.paddingRight;

    const scrollbarWidth =
      window.innerWidth -
      document.documentElement.clientWidth;

    body.style.overflow = "hidden";

    if (scrollbarWidth > 0) {
      body.style.paddingRight =
        `${scrollbarWidth}px`;
    }

    function handleKeyDown(
      event: KeyboardEvent
    ) {
      if (event.key === "Escape") {
        onClose();
      }
    }

    document.addEventListener(
      "keydown",
      handleKeyDown
    );

    return () => {
      document.removeEventListener(
        "keydown",
        handleKeyDown
      );

      body.style.overflow =
        oldOverflow;
      body.style.paddingRight =
        oldPaddingRight;
    };
  }, [jobId, onClose]);

  const analysis =
    analyzeMutation.data;

  const previewResult =
    previewMutation.data;

  const acceptedIdSet =
    useMemo(
      () =>
        new Set(
          acceptedSuggestionIds
        ),
      [acceptedSuggestionIds]
    );

  const evidenceById =
    useMemo(() => {
      return new Map(
        (analysis?.evidence ?? []).map(
          (evidence) => [
            evidence.id,
            evidence
          ]
        )
      );
    }, [analysis]);

  if (!jobId) {
    return null;
  }

  function toggleSuggestion(
    suggestionId: string
  ) {
    resetPreview();

    setAcceptedSuggestionIds(
      (current) =>
        current.includes(
          suggestionId
        )
          ? current.filter(
              (id) =>
                id !== suggestionId
            )
          : [
              ...current,
              suggestionId
            ]
    );
  }

  function runPreview() {
    if (
      !analysis ||
      !candidateProfileId?.trim() ||
      previewMutation.isPending
    ) {
      return;
    }

    resetPreview();

    preview({
      candidateProfileId,
      normalizedJobId: jobId,
      analysisId:
        analysis.analysisId,
      acceptedSuggestionIds
    });
  }

  const expiresAt = analysis
    ? new Date(
        analysis.expiresAt
      )
    : null;

  const expiresAtText =
    expiresAt &&
    !Number.isNaN(
      expiresAt.getTime()
    )
      ? new Intl.DateTimeFormat(
          locale,
          {
            dateStyle: "medium",
            timeStyle: "short"
          }
        ).format(expiresAt)
      : null;

  const currentScoreRows: ScoreRow[] =
    analysis
      ? [
          {
            key: "semantic",
            value:
              analysis.currentMatch
                .semanticScore,
            known: true
          },
          {
            key: "skills",
            value:
              analysis.currentMatch
                .skillScore,
            known:
              analysis.currentMatch
                .skillKnown
          },
          {
            key: "seniority",
            value:
              analysis.currentMatch
                .seniorityScore,
            known:
              analysis.currentMatch
                .seniorityKnown
          },
          {
            key: "location",
            value:
              analysis.currentMatch
                .locationScore,
            known:
              analysis.currentMatch
                .locationKnown
          },
          {
            key: "freshness",
            value:
              analysis.currentMatch
                .freshnessScore,
            known:
              analysis.currentMatch
                .freshnessKnown
          }
        ]
      : [];

  const previewErrorNeedsAnalyze =
    previewMutation.isError &&
    requiresReanalysis(
      previewMutation.error
    );

  const beforePercent =
    previewResult
      ? toPercent(
          previewResult.before
            .finalScore
        )
      : null;

  const afterPercent =
    previewResult
      ? toPercent(
          previewResult.after
            .finalScore
        )
      : null;

  return (
    <div className="fixed inset-0 z-[220] overflow-hidden">
      <button
        type="button"
        aria-label={t("close")}
        onClick={onClose}
        className="absolute inset-0 bg-black/22"
      />

      <aside
        role="dialog"
        aria-modal="true"
        aria-label={t("title")}
        className="absolute inset-y-0 right-0 flex w-full max-w-[860px] flex-col border-l border-black/[0.055] bg-[#f7f7f4] shadow-[-18px_0_55px_rgba(0,0,0,0.08)]"
      >
        <header className="flex min-h-[78px] shrink-0 items-center justify-between gap-4 border-b border-black/[0.055] bg-[#f7f7f4] px-5 py-4 sm:px-7">
          <div className="min-w-0">
            <div className="flex items-center gap-2.5">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-[9px] bg-[#171717] text-[#d9ff75]">
                <SparkIcon />
              </span>

              <div className="min-w-0">
                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                  {t("eyebrow")}
                </p>

                <h2 className="mt-0.5 truncate text-[14px] font-bold tracking-[-0.025em] text-[#292927]">
                  {t("title")}
                </h2>
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label={t("close")}
            className="flex size-9 shrink-0 items-center justify-center rounded-full border border-black/[0.065] bg-white text-black/45 transition-colors hover:text-black"
          >
            <CloseIcon />
          </button>
        </header>

        <div className="min-h-0 flex-1 overscroll-contain overflow-y-auto">
          {analyzeMutation.isPending ||
          (!analysis &&
            !analyzeMutation.isError) ? (
            <TailoringLoading />
          ) : null}

          {analyzeMutation.isError ? (
            <div className="px-5 py-7 sm:px-8 sm:py-9">
              <div className="rounded-[20px] border border-red-950/10 bg-white p-5 sm:p-6">
                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-red-900/45">
                  {t("analyzeError.eyebrow")}
                </p>

                <h3 className="mt-2 text-[18px] font-bold tracking-[-0.035em] text-[#292927]">
                  {t("analyzeError.title")}
                </h3>

                <p className="mt-2 text-[11px] leading-5 text-black/48">
                  {getApiErrorMessage(
                    analyzeMutation.error
                  )}
                </p>

                <button
                  type="button"
                  onClick={runAnalyze}
                  className="mt-5 inline-flex h-10 items-center gap-2 rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white"
                >
                  <SparkIcon />
                  {t("actions.tryAnalyzeAgain")}
                </button>
              </div>
            </div>
          ) : null}

          {analysis &&
          !analyzeMutation.isPending &&
          !analyzeMutation.isError ? (
            <div className="space-y-5 px-5 py-7 sm:px-8 sm:py-9">
              <section className="overflow-hidden rounded-[22px] border border-black/[0.055] bg-white shadow-[0_5px_22px_rgba(0,0,0,0.025)]">
                <div className="p-5 sm:p-6">
                  <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                        {t("currentMatch.eyebrow")}
                      </p>

                      <h3 className="mt-2 text-[20px] font-bold leading-[1.2] tracking-[-0.04em] text-[#222220] sm:text-[23px]">
                        {analysis.job.title?.trim() ||
                          t("jobUntitled")}
                      </h3>

                      {analysis.job.companyName?.trim() ? (
                        <p className="mt-2 text-[11px] font-semibold text-black/48">
                          {analysis.job.companyName}
                        </p>
                      ) : null}

                      <div className="mt-3 flex flex-wrap gap-2 text-[9px] text-black/42">
                        {analysis.job.locationText?.trim() ? (
                          <span className="rounded-full border border-black/[0.055] bg-[#f7f7f3] px-2.5 py-1">
                            {analysis.job.locationText}
                          </span>
                        ) : null}

                        {analysis.job.salaryText?.trim() ? (
                          <span className="rounded-full border border-black/[0.055] bg-[#f7f7f3] px-2.5 py-1">
                            {analysis.job.salaryText}
                          </span>
                        ) : null}
                      </div>
                    </div>

                    <div className="flex shrink-0 items-center gap-3">
                      <div className="rounded-[15px] border border-black/[0.055] bg-[#fafaf6] px-4 py-3 text-center">
                        <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.12em] text-black/30">
                          {t("currentMatch.rank")}
                        </p>

                        <p className="mt-1 text-[20px] font-bold tracking-[-0.04em] text-[#262624]">
                          #{analysis.job.rank}
                        </p>
                      </div>

                      <div className="rounded-[15px] border border-[#dbeaa5] bg-[#f3ffd6] px-4 py-3 text-center">
                        <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.12em] text-[#536226]/60">
                          {t("currentMatch.score")}
                        </p>

                        <p className="mt-1 text-[20px] font-bold tracking-[-0.04em] text-[#3f4d17]">
                          {toPercent(
                            analysis.currentMatch
                              .finalScore
                          )}%
                        </p>
                      </div>
                    </div>
                  </div>

                  {expiresAtText ? (
                    <p className="mt-5 border-t border-black/[0.05] pt-4 text-[9px] text-black/35">
                      {t("expiresAt", {
                        value: expiresAtText
                      })}
                    </p>
                  ) : null}
                </div>

                <div className="grid gap-5 border-t border-black/[0.05] bg-[#fafaf7] p-5 sm:grid-cols-2 sm:p-6">
                  <div>
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                      {t("currentMatch.breakdown")}
                    </p>

                    <div className="mt-4 space-y-3.5">
                      {currentScoreRows
                        .filter(
                          (row) =>
                            row.known &&
                            row.value !== null
                        )
                        .map((row) => (
                          <ScoreBar
                            key={row.key}
                            label={tScore(
                              row.key
                            )}
                            value={row.value ?? 0}
                          />
                        ))}
                    </div>
                  </div>

                  <div className="space-y-4">
                    <div>
                      <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                        {t("matchedSkills")}
                      </p>

                      <div className="mt-2">
                        <SkillTags
                          skills={
                            analysis.currentMatch
                              .matchedSkills
                          }
                        />

                        {analysis.currentMatch
                          .matchedSkills.length === 0 ? (
                          <p className="text-[10px] text-black/35">
                            {t("none")}
                          </p>
                        ) : null}
                      </div>
                    </div>

                    <div>
                      <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                        {t("missingSkills")}
                      </p>

                      <div className="mt-2">
                        <SkillTags
                          skills={
                            analysis.currentMatch
                              .missingSkills
                          }
                          tone="neutral"
                        />

                        {analysis.currentMatch
                          .missingSkills.length === 0 ? (
                          <p className="text-[10px] text-black/35">
                            {t("none")}
                          </p>
                        ) : null}
                      </div>
                    </div>
                  </div>
                </div>
              </section>

              <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_5px_22px_rgba(0,0,0,0.02)] sm:p-6">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                      {t("suggestions.eyebrow")}
                    </p>

                    <h3 className="mt-1.5 text-[17px] font-bold tracking-[-0.035em] text-[#292927]">
                      {t("suggestions.title")}
                    </h3>

                    <p className="mt-2 max-w-[610px] text-[10px] leading-5 text-black/42">
                      {t("suggestions.description")}
                    </p>
                  </div>

                  <span className="shrink-0 rounded-full border border-black/[0.055] bg-[#f7f7f3] px-3 py-1.5 font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-black/38">
                    {t("suggestions.selectedCount", {
                      count:
                        acceptedSuggestionIds.length
                    })}
                  </span>
                </div>

                {analysis.suggestions.length === 0 ? (
                  <div className="mt-5 rounded-[15px] border border-dashed border-black/[0.08] bg-[#fafaf7] px-4 py-5 text-[10px] leading-5 text-black/40">
                    {t("suggestions.empty")}
                  </div>
                ) : (
                  <div className="mt-5 space-y-3">
                    {analysis.suggestions.map(
                      (suggestion) => {
                        const selected =
                          acceptedIdSet.has(
                            suggestion.id
                          );

                        const evidenceContext =
                          getEvidenceContext(
                            suggestion,
                            evidenceById
                          );

                        return (
                          <article
                            key={suggestion.id}
                            className={`rounded-[18px] border p-4 transition-colors sm:p-5 ${
                              selected
                                ? "border-[#bedc58] bg-[#fbffe9]"
                                : "border-black/[0.055] bg-[#fafaf7]"
                            }`}
                          >
                            <div className="flex items-start gap-3.5">
                              <input
                                type="checkbox"
                                checked={selected}
                                onChange={() =>
                                  toggleSuggestion(
                                    suggestion.id
                                  )
                                }
                                aria-label={t(
                                  "suggestions.selectAria",
                                  {
                                    type: t(
                                      `suggestionTypes.${suggestion.type}`
                                    )
                                  }
                                )}
                                className="mt-0.5 size-4 shrink-0 accent-[#171717]"
                              />

                              <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                  <span className="rounded-full bg-[#171717] px-2.5 py-1 font-mono text-[7px] font-semibold uppercase tracking-[0.1em] text-white">
                                    {t(
                                      `suggestionTypes.${suggestion.type}`
                                    )}
                                  </span>

                                  <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-black/30">
                                    {t(
                                      `sections.${suggestion.section}`
                                    )}
                                  </span>
                                </div>

                                {suggestion.type ===
                                  "REWRITE" ? (
                                  <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_28px_minmax(0,1fr)] lg:items-stretch">
                                    <div className="rounded-[13px] border border-black/[0.05] bg-white p-3.5">
                                      <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                        {t("suggestions.original")}
                                      </p>

                                      <p className="mt-2 text-[10px] leading-[18px] text-black/48">
                                        {suggestion.original?.trim() ||
                                          t("none")}
                                      </p>
                                    </div>

                                    <div className="hidden items-center justify-center text-black/30 lg:flex">
                                      <ArrowIcon />
                                    </div>

                                    <div className="rounded-[13px] border border-[#dbeaa5] bg-[#f8ffe7] p-3.5">
                                      <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-[#536226]/55">
                                        {t("suggestions.suggested")}
                                      </p>

                                      <p className="mt-2 text-[10px] font-medium leading-[18px] text-[#414b22]">
                                        {suggestion.suggested?.trim() ||
                                          t("none")}
                                      </p>
                                    </div>
                                  </div>
                                ) : (
                                  <div className="mt-4 rounded-[13px] border border-[#dbeaa5] bg-[#f8ffe7] p-3.5">
                                    <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-[#536226]/55">
                                      {t("suggestions.emphasizeSkill")}
                                    </p>

                                    <p className="mt-1.5 text-[14px] font-bold tracking-[-0.025em] text-[#414b22]">
                                      {suggestion.suggested?.trim() ||
                                        suggestion.targetSkills[0] ||
                                        t("none")}
                                    </p>
                                  </div>
                                )}

                                {suggestion.reason?.trim() ? (
                                  <p className="mt-3 text-[10px] leading-[18px] text-black/46">
                                    <span className="font-semibold text-black/60">
                                      {t("suggestions.reason")}:{" "}
                                    </span>
                                    {suggestion.reason}
                                  </p>
                                ) : null}

                                {suggestion.targetSkills.length > 0 ? (
                                  <div className="mt-3">
                                    <p className="mb-2 font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                      {t("suggestions.targetSkills")}
                                    </p>

                                    <SkillTags
                                      skills={
                                        suggestion.targetSkills
                                      }
                                    />
                                  </div>
                                ) : null}

                                {evidenceContext.length > 0 ? (
                                  <div className="mt-3 rounded-[13px] border border-black/[0.045] bg-white/80 p-3.5">
                                    <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                      {t("suggestions.evidence")}
                                    </p>

                                    <ul className="mt-2 space-y-2">
                                      {evidenceContext.map(
                                        (evidence) => (
                                          <li
                                            key={evidence.id}
                                            className="flex gap-2 text-[9px] leading-[17px] text-black/43"
                                          >
                                            <span className="mt-[6px] size-1 shrink-0 rounded-full bg-[#a8c747]" />
                                            <span>
                                              {evidence.text}
                                            </span>
                                          </li>
                                        )
                                      )}
                                    </ul>
                                  </div>
                                ) : null}
                              </div>
                            </div>
                          </article>
                        );
                      }
                    )}
                  </div>
                )}
              </section>

              {analysis.gaps.length > 0 ? (
                <section className="rounded-[22px] border border-[#eadca7] bg-[#fffaf0] p-5 sm:p-6">
                  <div className="flex items-start gap-3">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-[10px] bg-[#fff1bd] text-[#745f17]">
                      <WarningIcon />
                    </span>

                    <div>
                      <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-[#745f17]/55">
                        {t("gaps.eyebrow")}
                      </p>

                      <h3 className="mt-1.5 text-[16px] font-bold tracking-[-0.03em] text-[#4d431f]">
                        {t("gaps.title")}
                      </h3>

                      <p className="mt-2 text-[10px] leading-5 text-[#675b32]/70">
                        {t("gaps.description")}
                      </p>
                    </div>
                  </div>

                  <div className="mt-4 space-y-2.5">
                    {analysis.gaps.map(
                      (gap) => (
                        <div
                          key={gap.id}
                          className="rounded-[14px] border border-[#eadca7] bg-white/65 px-4 py-3.5"
                        >
                          {gap.skill?.trim() ? (
                            <p className="text-[11px] font-bold text-[#51461f]">
                              {gap.skill}
                            </p>
                          ) : null}

                          {gap.reason?.trim() ? (
                            <p className="mt-1.5 text-[9px] leading-[17px] text-[#675b32]/72">
                              {gap.reason}
                            </p>
                          ) : null}
                        </div>
                      )
                    )}
                  </div>
                </section>
              ) : null}

              <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_5px_22px_rgba(0,0,0,0.02)] sm:p-6">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                      {t("preview.eyebrow")}
                    </p>

                    <h3 className="mt-1.5 text-[17px] font-bold tracking-[-0.035em] text-[#292927]">
                      {t("preview.title")}
                    </h3>

                    <p className="mt-2 max-w-[560px] text-[10px] leading-5 text-black/42">
                      {t("preview.description")}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={runPreview}
                    disabled={
                      previewMutation.isPending
                    }
                    className="inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {previewMutation.isPending ? (
                      <span className="size-3.5 animate-spin rounded-full border-2 border-white/25 border-t-white" />
                    ) : (
                      <SparkIcon />
                    )}

                    {previewMutation.isPending
                      ? t("actions.previewing")
                      : t("actions.preview")}
                  </button>
                </div>

                {previewMutation.isError ? (
                  <div
                    role="alert"
                    className={`mt-5 rounded-[15px] border px-4 py-4 ${
                      previewErrorNeedsAnalyze
                        ? "border-[#eadca7] bg-[#fffaf0]"
                        : "border-red-950/10 bg-[#fffafa]"
                    }`}
                  >
                    <p className="text-[11px] font-bold text-[#383834]">
                      {previewErrorNeedsAnalyze
                        ? t("preview.reanalyzeTitle")
                        : t("preview.errorTitle")}
                    </p>

                    <p className="mt-1.5 text-[9px] leading-[17px] text-black/45">
                      {previewErrorNeedsAnalyze
                        ? t("preview.reanalyzeDescription")
                        : getApiErrorMessage(
                            previewMutation.error
                          )}
                    </p>

                    {previewErrorNeedsAnalyze ? (
                      <>
                        <p className="mt-1 text-[8px] leading-[16px] text-black/32">
                          {getApiErrorMessage(
                            previewMutation.error
                          )}
                        </p>

                        <button
                          type="button"
                          onClick={runAnalyze}
                          className="mt-3 inline-flex h-9 items-center gap-2 rounded-full bg-[#171717] px-4 text-[9px] font-semibold text-white"
                        >
                          <SparkIcon />
                          {t("actions.analyzeAgain")}
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={runPreview}
                        className="mt-3 text-[9px] font-semibold text-black underline decoration-black/20 underline-offset-4"
                      >
                        {t("actions.tryPreviewAgain")}
                      </button>
                    )}
                  </div>
                ) : null}

                {previewResult ? (
                  <div className="mt-5 border-t border-black/[0.05] pt-5">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p className="text-[11px] font-bold text-[#333330]">
                          {beforePercent !== null &&
                          afterPercent !== null
                            ? t("preview.scoreTransition", {
                                before: beforePercent,
                                after: afterPercent
                              })
                            : t("preview.resultTitle")}
                        </p>

                        <p className="mt-1 text-[9px] leading-[17px] text-black/38">
                          {t("preview.temporaryNotice")}
                        </p>
                      </div>

                      <span className="shrink-0 font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-black/32">
                        {t("preview.appliedCount", {
                          count:
                            previewResult
                              .appliedSuggestionIds
                              .length
                        })}
                      </span>
                    </div>

                    <div className="mt-4 grid gap-3 xl:grid-cols-2">
                      <PreviewScoreCard
                        label={t("preview.before")}
                        snapshot={
                          previewResult.before
                        }
                      />

                      <PreviewScoreCard
                        label={t("preview.after")}
                        snapshot={
                          previewResult.after
                        }
                      />
                    </div>
                  </div>
                ) : null}
              </section>
            </div>
          ) : null}
        </div>
      </aside>
    </div>
  );
}