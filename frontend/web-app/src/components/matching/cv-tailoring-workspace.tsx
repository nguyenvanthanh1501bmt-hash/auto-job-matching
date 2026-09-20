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
    useCurrentCvTailoringDraft,
    usePreviewCvTailoringDraft,
    useStartOrRefreshCvTailoringDraft,
    useUpdateCvTailoringDraft
} from "@/hooks/use-cv-tailoring-workspace";

import {
    Link
} from "@/i18n/navigation";

import {
    getApiErrorMessage,
    toApiError
} from "@/lib/api-error";

import {
    getAuthSession
} from "@/lib/auth-storage";

import {
    getUserCvContext
} from "@/lib/user-cv-context";

import type {
    CvTailoringCurrentMatch,
    CvTailoringPreviewScore,
    CvTailoringSuggestionItem
} from "@/types/cv-tailoring";

import type {
    CvTailoringCoachingAnswer,
    CvTailoringDraftResponse
} from "@/types/cv-tailoring-workspace";

function percent(
    value: number | null,
    digits = 1
): string {
    if (
        value === null ||
        !Number.isFinite(value)
    ) {
        return "—";
    }

    const safe = Math.min(
        Math.max(value, 0),
        1
    );

    return `${(safe * 100).toFixed(digits)}%`;
}

function formatDate(
    value: string,
    locale: string
): string {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat(
        locale,
        {
            dateStyle: "medium",
            timeStyle: "short"
        }
    ).format(date);
}

function priorityWeight(
    item: CvTailoringSuggestionItem
): number {
    if (item.priority === "HIGH") {
        return 0;
    }

    if (item.priority === "MEDIUM") {
        return 1;
    }

    return 2;
}

function Metric({
                    label,
                    value
                }: {
    label: string;
    value: number | null;
}) {
    const safe =
        value === null ||
        !Number.isFinite(value)
            ? 0
            : Math.min(
                Math.max(value, 0),
                1
            );

    return (
        <div>
            <div className="mb-1.5 flex items-center justify-between gap-3">
        <span className="text-[10px] font-medium text-black/45">
          {label}
        </span>

                <span className="font-mono text-[9px] font-semibold text-black/40">
          {percent(value)}
        </span>
            </div>

            <div className="h-1.5 overflow-hidden rounded-full bg-black/[0.055]">
                <div
                    className="h-full rounded-full bg-[#222220]"
                    style={{
                        width: `${safe * 100}%`
                    }}
                />
            </div>
        </div>
    );
}

function CurrentMatchCard({
                              match,
                              rank
                          }: {
    match: CvTailoringCurrentMatch;
    rank: number;
}) {
    const t = useTranslations(
        "user.matches.tailoring.workspace"
    );

    const tScore = useTranslations(
        "user.matches.score"
    );

    return (
        <div className="rounded-[20px] border border-black/[0.055] bg-white p-5">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                        {t("currentMatch")}
                    </p>

                    <p className="mt-2 text-[31px] font-bold tracking-[-0.055em] text-[#222220]">
                        {percent(match.finalScore)}
                    </p>
                </div>

                <div className="rounded-[12px] border border-black/[0.055] bg-[#fafaf7] px-3 py-2 text-center">
                    <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                        {t("rank")}
                    </p>

                    <p className="mt-1 text-[16px] font-bold text-[#33332f]">
                        {rank > 0 ? `#${rank}` : "—"}
                    </p>
                </div>
            </div>

            <div className="mt-5 space-y-3.5">
                <Metric
                    label={tScore("semantic")}
                    value={match.semanticScore}
                />

                <Metric
                    label={tScore("skills")}
                    value={match.skillScore}
                />

                {match.seniorityKnown ? (
                    <Metric
                        label={tScore("seniority")}
                        value={match.seniorityScore}
                    />
                ) : null}

                {match.locationKnown ? (
                    <Metric
                        label={tScore("location")}
                        value={match.locationScore}
                    />
                ) : null}

                {match.freshnessKnown ? (
                    <Metric
                        label={tScore("freshness")}
                        value={match.freshnessScore}
                    />
                ) : null}
            </div>
        </div>
    );
}

function PreviewCard({
                         label,
                         score
                     }: {
    label: string;
    score: CvTailoringPreviewScore;
}) {
    const tScore = useTranslations(
        "user.matches.score"
    );

    return (
        <div className="rounded-[18px] border border-black/[0.055] bg-[#fafaf7] p-4">
            <div className="flex items-start justify-between gap-3">
                <div>
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/30">
                        {label}
                    </p>

                    <p className="mt-2 text-[25px] font-bold tracking-[-0.05em] text-[#252523]">
                        {percent(score.finalScore)}
                    </p>
                </div>

                <span className="rounded-full border border-black/[0.06] bg-white px-2.5 py-1 font-mono text-[8px] font-semibold text-black/40">
          {score.rank !== null
              ? `#${score.rank}`
              : "—"}
        </span>
            </div>

            <div className="mt-4 space-y-3">
                <Metric
                    label={tScore("semantic")}
                    value={score.semanticScore}
                />

                <Metric
                    label={tScore("skills")}
                    value={score.skillScore}
                />

                {score.seniorityKnown === true ? (
                    <Metric
                        label={tScore("seniority")}
                        value={score.seniorityScore}
                    />
                ) : null}

                {score.locationKnown === true ? (
                    <Metric
                        label={tScore("location")}
                        value={score.locationScore}
                    />
                ) : null}

                {score.freshnessKnown === true ? (
                    <Metric
                        label={tScore("freshness")}
                        value={score.freshnessScore}
                    />
                ) : null}
            </div>
        </div>
    );
}

function EmptyPage({
                       title,
                       description
                   }: {
    title: string;
    description: string;
}) {
    const t = useTranslations(
        "user.matches.tailoring.workspace"
    );

    return (
        <div className="py-8">
            <Link
                href="/matches"
                className="text-[10px] font-semibold text-black/45 underline decoration-black/20 underline-offset-4"
            >
                ← {t("back")}
            </Link>

            <div className="mt-7 rounded-[24px] border border-black/[0.05] bg-white px-6 py-14 text-center">
                <h1 className="text-[24px] font-bold tracking-[-0.04em] text-[#252523]">
                    {title}
                </h1>

                <p className="mx-auto mt-3 max-w-[540px] text-[11px] leading-6 text-black/45">
                    {description}
                </p>
            </div>
        </div>
    );
}

export function CvTailoringWorkspace({
                                         jobId
                                     }: {
    jobId: string | null;
}) {
    const locale = useLocale();

    const t = useTranslations(
        "user.matches.tailoring.workspace"
    );

    const tSections = useTranslations(
        "user.matches.tailoring.sections"
    );

    const [
        contextResolved,
        setContextResolved
    ] = useState(false);

    const [
        candidateProfileId,
        setCandidateProfileId
    ] = useState<string | null>(null);

    const [
        draft,
        setDraft
    ] = useState<CvTailoringDraftResponse | null>(
        null
    );

    const [
        acceptedIds,
        setAcceptedIds
    ] = useState<string[]>([]);

    const [
        rejectedIds,
        setRejectedIds
    ] = useState<string[]>([]);

    const [
        coachingAnswers,
        setCoachingAnswers
    ] = useState<Record<string, string>>({});

    const [
        dirty,
        setDirty
    ] = useState(false);

    const [
        previewDirty,
        setPreviewDirty
    ] = useState(false);

    const initializedDraftIdRef = useRef<
        string | null
    >(null);

    const attemptedStartKeyRef = useRef<
        string | null
    >(null);

    useEffect(() => {
        const session = getAuthSession();

        if (!session) {
            setContextResolved(true);
            return;
        }

        const context = getUserCvContext(
            session.user.id
        );

        setCandidateProfileId(
            context?.candidateProfileId ?? null
        );

        setContextResolved(true);
    }, []);

    const currentDraftQuery =
        useCurrentCvTailoringDraft(
            candidateProfileId,
            jobId
        );

    const startMutation =
        useStartOrRefreshCvTailoringDraft();

    const updateMutation =
        useUpdateCvTailoringDraft();

    const previewMutation =
        usePreviewCvTailoringDraft();

    const applyDraftState = useCallback(
        (
            nextDraft: CvTailoringDraftResponse
        ) => {
            setDraft(nextDraft);

            setAcceptedIds(
                nextDraft.acceptedSuggestionIds
            );

            setRejectedIds(
                nextDraft.rejectedSuggestionIds
            );

            setCoachingAnswers(
                Object.fromEntries(
                    nextDraft.coachingAnswers.map(
                        (
                            answer: CvTailoringCoachingAnswer
                        ) => [
                            answer.coachingId,
                            answer.answer
                        ]
                    )
                )
            );

            setDirty(false);

            initializedDraftIdRef.current =
                nextDraft.draftId;
        },
        []
    );

    useEffect(() => {
        const queryDraft = currentDraftQuery.data;

        if (
            queryDraft &&
            initializedDraftIdRef.current !==
            queryDraft.draftId
        ) {
            applyDraftState(queryDraft);
        }
    }, [
        applyDraftState,
        currentDraftQuery.data
    ]);

    useEffect(() => {
        if (
            !contextResolved ||
            !candidateProfileId ||
            !jobId ||
            currentDraftQuery.isLoading ||
            !currentDraftQuery.isError
        ) {
            return;
        }

        if (
            toApiError(
                currentDraftQuery.error
            ).status !== 404
        ) {
            return;
        }

        const key = `${candidateProfileId}:${jobId}`;

        if (
            attemptedStartKeyRef.current === key
        ) {
            return;
        }

        attemptedStartKeyRef.current = key;

        startMutation.mutate(
            {
                candidateProfileId,
                normalizedJobId: jobId
            },
            {
                onSuccess: applyDraftState
            }
        );
    }, [
        applyDraftState,
        candidateProfileId,
        contextResolved,
        currentDraftQuery.error,
        currentDraftQuery.isError,
        currentDraftQuery.isLoading,
        jobId,
        startMutation
    ]);

    const sortedSuggestions = useMemo(
        () =>
            [...(draft?.suggestions ?? [])].sort(
                (a, b) =>
                    priorityWeight(a) -
                    priorityWeight(b)
            ),
        [draft?.suggestions]
    );

    const acceptedSet = useMemo(
        () => new Set(acceptedIds),
        [acceptedIds]
    );

    const rejectedSet = useMemo(
        () => new Set(rejectedIds),
        [rejectedIds]
    );

    function markChanged() {
        setDirty(true);
        setPreviewDirty(true);
    }

    function acceptSuggestion(id: string) {
        setAcceptedIds((current) =>
            current.includes(id)
                ? current
                : [...current, id]
        );

        setRejectedIds((current) =>
            current.filter(
                (value) => value !== id
            )
        );

        markChanged();
    }

    function rejectSuggestion(id: string) {
        setRejectedIds((current) =>
            current.includes(id)
                ? current
                : [...current, id]
        );

        setAcceptedIds((current) =>
            current.filter(
                (value) => value !== id
            )
        );

        markChanged();
    }

    function resetSuggestion(id: string) {
        setAcceptedIds((current) =>
            current.filter(
                (value) => value !== id
            )
        );

        setRejectedIds((current) =>
            current.filter(
                (value) => value !== id
            )
        );

        markChanged();
    }

    function buildUpdateRequest() {
        return {
            acceptedSuggestionIds: acceptedIds,
            rejectedSuggestionIds: rejectedIds,
            coachingAnswers: Object.entries(
                coachingAnswers
            )
                .map(
                    ([coachingId, answer]) => ({
                        coachingId,
                        answer: answer.trim()
                    })
                )
                .filter(
                    (item) => item.answer.length > 0
                )
        };
    }

    async function saveDraft(): Promise<
        CvTailoringDraftResponse | null
    > {
        if (!draft) {
            return null;
        }

        if (!dirty) {
            return draft;
        }

        const saved =
            await updateMutation.mutateAsync({
                draftId: draft.draftId,
                request: buildUpdateRequest()
            });

        applyDraftState(saved);

        return saved;
    }

    async function runPreview() {
        if (!draft) {
            return;
        }

        try {
            const saved = await saveDraft();

            const result =
                await previewMutation.mutateAsync(
                    saved?.draftId ?? draft.draftId
                );

            applyDraftState(result.draft);
            setPreviewDirty(false);
        } catch {
            // Mutation state renders the error.
        }
    }

    async function refreshAnalysis() {
        if (
            !candidateProfileId ||
            !jobId
        ) {
            return;
        }

        try {
            const refreshed =
                await startMutation.mutateAsync({
                    candidateProfileId,
                    normalizedJobId: jobId
                });

            applyDraftState(refreshed);
            previewMutation.reset();
            setPreviewDirty(false);
        } catch {
            // Mutation state renders the error.
        }
    }

    if (!jobId) {
        return (
            <EmptyPage
                title={t("noJob")}
                description={t("noJobDescription")}
            />
        );
    }

    const waitingForFirstDraft =
        currentDraftQuery.isError &&
        toApiError(
            currentDraftQuery.error
        ).status === 404 &&
        startMutation.isPending;

    if (
        !contextResolved ||
        currentDraftQuery.isLoading ||
        waitingForFirstDraft
    ) {
        return (
            <div className="mt-8 rounded-[24px] border border-black/[0.05] bg-white px-6 py-16 text-center">
                <span className="mx-auto block size-5 animate-spin rounded-full border-2 border-black/10 border-t-black/55" />

                <p className="mt-4 text-[11px] font-medium text-black/45">
                    {t("loading")}
                </p>
            </div>
        );
    }

    if (!candidateProfileId) {
        return (
            <EmptyPage
                title={t("noCv")}
                description={t("noCvDescription")}
            />
        );
    }

    const loadError = startMutation.isError
        ? startMutation.error
        : currentDraftQuery.isError &&
        toApiError(
            currentDraftQuery.error
        ).status !== 404
            ? currentDraftQuery.error
            : null;

    if (loadError || !draft) {
        return (
            <div className="py-8">
                <Link
                    href="/matches"
                    className="text-[10px] font-semibold text-black/45 underline decoration-black/20 underline-offset-4"
                >
                    ← {t("back")}
                </Link>

                <div className="mt-7 rounded-[24px] border border-[#eadca7] bg-[#fffdf6] px-6 py-14 text-center">
                    <h1 className="text-[24px] font-bold tracking-[-0.04em] text-[#252523]">
                        {t("loadError")}
                    </h1>

                    {loadError ? (
                        <p className="mx-auto mt-3 max-w-[620px] text-[11px] leading-6 text-black/45">
                            {getApiErrorMessage(loadError)}
                        </p>
                    ) : null}

                    <button
                        type="button"
                        onClick={() => {
                            attemptedStartKeyRef.current = null;
                            void currentDraftQuery.refetch();
                        }}
                        className="mt-6 inline-flex h-10 items-center rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white"
                    >
                        {t("retry")}
                    </button>
                </div>
            </div>
        );
    }

    const preview =
        previewMutation.data?.preview ?? null;

    const actionError = updateMutation.isError
        ? updateMutation.error
        : previewMutation.isError
            ? previewMutation.error
            : startMutation.isError
                ? startMutation.error
                : null;

    const applyUrl =
        draft.job.applyUrl?.trim() ||
        draft.job.detailUrl?.trim() ||
        null;

    const busy =
        startMutation.isPending ||
        updateMutation.isPending ||
        previewMutation.isPending;

    return (
        <div className="pb-16 pt-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <Link
                    href="/matches"
                    className="inline-flex h-9 items-center rounded-full border border-black/[0.06] bg-white px-4 text-[9px] font-semibold text-black/50"
                >
                    ← {t("back")}
                </Link>

                <div className="flex flex-wrap items-center gap-2">
          <span
              className={`rounded-full border px-3 py-2 text-[9px] font-semibold ${
                  dirty
                      ? "border-[#eadca7] bg-[#fff7d9] text-[#6b5715]"
                      : "border-[#dbeaa5] bg-[#f3ffd6] text-[#4d5b20]"
              }`}
          >
            {dirty
                ? t("unsaved")
                : t("saved")}
          </span>

                    <button
                        type="button"
                        onClick={() =>
                            void refreshAnalysis()
                        }
                        disabled={busy}
                        className="h-9 rounded-full border border-black/[0.065] bg-white px-4 text-[9px] font-semibold text-black/55 disabled:opacity-45"
                    >
                        {startMutation.isPending
                            ? t("refreshing")
                            : t("refresh")}
                    </button>

                    <button
                        type="button"
                        onClick={() => void saveDraft()}
                        disabled={!dirty || busy}
                        className="h-9 rounded-full border border-black/[0.065] bg-white px-4 text-[9px] font-semibold text-black/55 disabled:opacity-40"
                    >
                        {updateMutation.isPending
                            ? t("saving")
                            : t("save")}
                    </button>

                    <button
                        type="button"
                        onClick={() => void runPreview()}
                        disabled={busy}
                        className="h-9 rounded-full bg-[#171717] px-4 text-[9px] font-semibold text-white disabled:opacity-45"
                    >
                        {previewMutation.isPending
                            ? t("previewing")
                            : t("preview")}
                    </button>
                </div>
            </div>

            <header className="mt-7 border-b border-black/[0.055] pb-7">
                <p className="font-mono text-[9px] font-semibold uppercase tracking-[0.14em] text-black/30">
                    {t("eyebrow")}
                </p>

                <h1 className="mt-3 max-w-[800px] text-[31px] font-bold leading-[1.1] tracking-[-0.05em] text-[#222220] sm:text-[38px]">
                    {t("title")}
                </h1>

                <p className="mt-4 max-w-[760px] text-[11px] leading-6 text-black/45">
                    {t("description")}
                </p>
            </header>

            {actionError ? (
                <div className="mt-5 rounded-[15px] border border-[#e7c7bd] bg-[#fff7f4] px-4 py-3 text-[10px] leading-5 text-[#765044]">
                    {getApiErrorMessage(actionError)}
                </div>
            ) : null}

            <div className="mt-7 grid items-start gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
                <aside className="space-y-4 xl:sticky xl:top-5">
                    <div className="rounded-[20px] border border-black/[0.055] bg-[#fafaf7] p-5">
                        <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                            {t("targetJob")}
                        </p>

                        <h2 className="mt-3 text-[19px] font-bold leading-[1.25] tracking-[-0.035em] text-[#272725]">
                            {draft.job.title?.trim() || "—"}
                        </h2>

                        {draft.job.companyName?.trim() ? (
                            <p className="mt-2 text-[11px] font-semibold text-black/45">
                                {draft.job.companyName}
                            </p>
                        ) : null}

                        {draft.job.locationText?.trim() ? (
                            <p className="mt-3 text-[10px] leading-5 text-black/40">
                                {draft.job.locationText}
                            </p>
                        ) : null}

                        {draft.job.salaryText?.trim() ? (
                            <p className="mt-2 text-[10px] font-semibold text-black/50">
                                {draft.job.salaryText}
                            </p>
                        ) : null}

                        {applyUrl ? (
                            <a
                                href={applyUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="mt-5 inline-flex h-9 items-center rounded-full border border-black/[0.065] bg-white px-4 text-[9px] font-semibold text-black/55"
                            >
                                {t("apply")} ↗
                            </a>
                        ) : null}
                    </div>

                    <CurrentMatchCard
                        match={draft.baselineMatch}
                        rank={draft.job.rank}
                    />

                    <div className="rounded-[20px] border border-black/[0.055] bg-white p-5">
                        <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                            {t("matched")}
                        </p>

                        <div className="mt-3 flex flex-wrap gap-1.5">
                            {draft.baselineMatch.matchedSkills.length ? (
                                draft.baselineMatch.matchedSkills.map(
                                    (skill) => (
                                        <span
                                            key={skill}
                                            className="rounded-[8px] border border-[#dbeaa5] bg-[#f3ffd6] px-2.5 py-1 text-[9px] font-medium text-[#4d5b20]"
                                        >
                      {skill}
                    </span>
                                    )
                                )
                            ) : (
                                <span className="text-[10px] text-black/35">
                  —
                </span>
                            )}
                        </div>

                        <p className="mt-5 font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                            {t("missing")}
                        </p>

                        <div className="mt-3 flex flex-wrap gap-1.5">
                            {draft.baselineMatch.missingSkills.length ? (
                                draft.baselineMatch.missingSkills.map(
                                    (skill) => (
                                        <span
                                            key={skill}
                                            className="rounded-[8px] border border-black/[0.055] bg-[#f5f5f1] px-2.5 py-1 text-[9px] font-medium text-black/45"
                                        >
                      {skill}
                    </span>
                                    )
                                )
                            ) : (
                                <span className="text-[10px] text-black/35">
                  —
                </span>
                            )}
                        </div>
                    </div>

                    <p className="px-1 text-[9px] leading-5 text-black/30">
                        {t("updatedAt")}: {formatDate(
                        draft.updatedAt,
                        locale
                    )}
                    </p>
                </aside>

                <main className="min-w-0 space-y-6">
                    <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 sm:p-6">
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                            <div>
                                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                                    {t("stepSuggestions")}
                                </p>

                                <h2 className="mt-2 text-[21px] font-bold tracking-[-0.035em] text-[#272725]">
                                    {t("suggestions")}
                                </h2>

                                <p className="mt-2 max-w-[720px] text-[10px] leading-5 text-black/42">
                                    {t("suggestionsDescription")}
                                </p>
                            </div>

                            <span className="shrink-0 rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1.5 font-mono text-[8px] font-semibold text-black/35">
                {t("acceptedCount", {
                    accepted: acceptedIds.length,
                    rejected: rejectedIds.length
                })}
              </span>
                        </div>

                        <div className="mt-5 space-y-3">
                            {sortedSuggestions.length ? (
                                sortedSuggestions.map((item) => {
                                    const accepted =
                                        acceptedSet.has(item.id);

                                    const rejected =
                                        rejectedSet.has(item.id);

                                    return (
                                        <article
                                            key={item.id}
                                            className={`rounded-[18px] border p-4 sm:p-5 ${
                                                accepted
                                                    ? "border-[#cfe57d] bg-[#fbfff1]"
                                                    : rejected
                                                        ? "border-black/[0.055] bg-[#fafaf7] opacity-70"
                                                        : "border-black/[0.055] bg-white"
                                            }`}
                                        >
                                            <div className="flex flex-wrap items-start justify-between gap-3">
                                                <div className="flex flex-wrap items-center gap-2">
                          <span className="rounded-full border border-black/[0.055] bg-white px-2.5 py-1 font-mono text-[8px] font-semibold text-black/38">
                            {tSections(item.section)}
                          </span>

                                                    <span className="rounded-full bg-[#f3f3ef] px-2.5 py-1 font-mono text-[8px] font-semibold text-black/35">
                            {item.priority}
                          </span>
                                                </div>

                                                <div className="flex gap-2">
                                                    {accepted || rejected ? (
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                resetSuggestion(
                                                                    item.id
                                                                )
                                                            }
                                                            className="h-8 rounded-full border border-black/[0.065] bg-white px-3 text-[8px] font-semibold text-black/45"
                                                        >
                                                            {t("undo")}
                                                        </button>
                                                    ) : (
                                                        <>
                                                            <button
                                                                type="button"
                                                                onClick={() =>
                                                                    rejectSuggestion(
                                                                        item.id
                                                                    )
                                                                }
                                                                className="h-8 rounded-full border border-black/[0.065] bg-white px-3 text-[8px] font-semibold text-black/45"
                                                            >
                                                                {t("reject")}
                                                            </button>

                                                            <button
                                                                type="button"
                                                                onClick={() =>
                                                                    acceptSuggestion(
                                                                        item.id
                                                                    )
                                                                }
                                                                className="h-8 rounded-full border border-[#cfe57d] bg-[#efffc3] px-3 text-[8px] font-semibold text-[#3f4d17]"
                                                            >
                                                                {t("accept")}
                                                            </button>
                                                        </>
                                                    )}
                                                </div>
                                            </div>

                                            {accepted ? (
                                                <p className="mt-3 text-[9px] font-semibold text-[#617523]">
                                                    ✓ {t("accepted")}
                                                </p>
                                            ) : null}

                                            {rejected ? (
                                                <p className="mt-3 text-[9px] font-semibold text-black/35">
                                                    {t("rejected")}
                                                </p>
                                            ) : null}

                                            {item.original?.trim() ? (
                                                <div className="mt-4">
                                                    <p className="font-mono text-[8px] font-semibold uppercase text-black/28">
                                                        {t("original")}
                                                    </p>

                                                    <p className="mt-2 text-[10px] leading-5 text-black/45">
                                                        {item.original}
                                                    </p>
                                                </div>
                                            ) : null}

                                            {item.suggested?.trim() ? (
                                                <div className="mt-4 rounded-[14px] border border-[#dfeab8] bg-[#fbfff1] px-4 py-3.5">
                                                    <p className="font-mono text-[8px] font-semibold uppercase text-[#617523]/70">
                                                        {t("suggested")}
                                                    </p>

                                                    <p className="mt-2 text-[10px] font-medium leading-5 text-[#3e471e]">
                                                        {item.suggested}
                                                    </p>
                                                </div>
                                            ) : null}

                                            {item.reason?.trim() ? (
                                                <div className="mt-4">
                                                    <p className="font-mono text-[8px] font-semibold uppercase text-black/28">
                                                        {t("why")}
                                                    </p>

                                                    <p className="mt-2 text-[9px] leading-[18px] text-black/40">
                                                        {item.reason}
                                                    </p>
                                                </div>
                                            ) : null}

                                            {item.targetSkills.length ? (
                                                <div className="mt-4 flex flex-wrap gap-1.5">
                                                    {item.targetSkills.map(
                                                        (skill) => (
                                                            <span
                                                                key={skill}
                                                                className="rounded-[8px] bg-[#f3f3ef] px-2.5 py-1 text-[8px] font-medium text-black/42"
                                                            >
                                {skill}
                              </span>
                                                        )
                                                    )}
                                                </div>
                                            ) : null}
                                        </article>
                                    );
                                })
                            ) : (
                                <p className="rounded-[16px] bg-[#fafaf7] px-4 py-5 text-[10px] text-black/40">
                                    {t("noSuggestions")}
                                </p>
                            )}
                        </div>
                    </section>

                    <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 sm:p-6">
                        <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                            {t("stepCoaching")}
                        </p>

                        <h2 className="mt-2 text-[21px] font-bold tracking-[-0.035em] text-[#272725]">
                            {t("coaching")}
                        </h2>

                        <p className="mt-2 max-w-[760px] text-[10px] leading-5 text-black/42">
                            {t("coachingDescription")}
                        </p>

                        <div className="mt-5 space-y-4">
                            {draft.coaching.length ? (
                                draft.coaching.map((item) => (
                                    <article
                                        key={item.id}
                                        className="rounded-[18px] border border-[#eadca7] bg-[#fffdf5] p-4 sm:p-5"
                                    >
                                        <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full border border-[#eadca7] bg-white px-2.5 py-1 font-mono text-[8px] font-semibold text-[#746020]">
                        {tSections(item.section)}
                      </span>

                                            <span className="rounded-full bg-[#fff5cf] px-2.5 py-1 font-mono text-[8px] font-semibold text-[#746020]">
                        {item.priority}
                      </span>
                                        </div>

                                        {item.original?.trim() ? (
                                            <p className="mt-4 rounded-[12px] bg-white/80 px-3.5 py-3 text-[9px] leading-[18px] text-black/42">
                                                {item.original}
                                            </p>
                                        ) : null}

                                        <p className="mt-4 text-[11px] font-semibold leading-5 text-[#4f451f]">
                                            {item.question}
                                        </p>

                                        {item.reason?.trim() ? (
                                            <p className="mt-2 text-[9px] leading-[18px] text-[#675b32]/75">
                                                {item.reason}
                                            </p>
                                        ) : null}

                                        <textarea
                                            value={
                                                coachingAnswers[item.id] ?? ""
                                            }
                                            onChange={(event) => {
                                                setCoachingAnswers(
                                                    (current) => ({
                                                        ...current,
                                                        [item.id]:
                                                        event.target.value
                                                    })
                                                );

                                                markChanged();
                                            }}
                                            maxLength={4000}
                                            rows={4}
                                            placeholder={t(
                                                "answerPlaceholder"
                                            )}
                                            className="mt-4 w-full resize-y rounded-[14px] border border-black/[0.08] bg-white px-4 py-3 text-[10px] leading-5 outline-none placeholder:text-black/25 focus:border-black/[0.18]"
                                        />

                                        <p className="mt-2 text-[8px] leading-4 text-black/32">
                                            {t("answerHint")}
                                        </p>
                                    </article>
                                ))
                            ) : (
                                <p className="rounded-[16px] bg-[#fafaf7] px-4 py-5 text-[10px] text-black/40">
                                    {t("noCoaching")}
                                </p>
                            )}
                        </div>
                    </section>

                    <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 sm:p-6">
                        <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                            {t("stepGaps")}
                        </p>

                        <h2 className="mt-2 text-[21px] font-bold tracking-[-0.035em] text-[#272725]">
                            {t("gaps")}
                        </h2>

                        <p className="mt-2 max-w-[760px] text-[10px] leading-5 text-black/42">
                            {t("gapsDescription")}
                        </p>

                        <div className="mt-5 grid gap-3 sm:grid-cols-2">
                            {draft.gaps.length ? (
                                draft.gaps.map((gap) => (
                                    <article
                                        key={gap.id}
                                        className="rounded-[16px] border border-black/[0.055] bg-[#fafaf7] p-4"
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <p className="text-[11px] font-semibold text-[#3b3b38]">
                                                {gap.skill || "—"}
                                            </p>

                                            <span className="rounded-full bg-white px-2 py-1 font-mono text-[7px] font-semibold text-black/35">
                        {gap.priority}
                      </span>
                                        </div>

                                        {gap.reason?.trim() ? (
                                            <p className="mt-2 text-[9px] leading-[18px] text-black/40">
                                                {gap.reason}
                                            </p>
                                        ) : null}
                                    </article>
                                ))
                            ) : (
                                <p className="rounded-[16px] bg-[#fafaf7] px-4 py-5 text-[10px] text-black/40 sm:col-span-2">
                                    {t("noGaps")}
                                </p>
                            )}
                        </div>
                    </section>

                    <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 sm:p-6">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                            <div>
                                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                                    {t("stepPreview")}
                                </p>

                                <h2 className="mt-2 text-[21px] font-bold tracking-[-0.035em] text-[#272725]">
                                    {t("previewTitle")}
                                </h2>

                                <p className="mt-2 max-w-[760px] text-[10px] leading-5 text-black/42">
                                    {t("previewDescription")}
                                </p>
                            </div>

                            <button
                                type="button"
                                onClick={() => void runPreview()}
                                disabled={busy}
                                className="h-9 shrink-0 rounded-full bg-[#171717] px-4 text-[9px] font-semibold text-white disabled:opacity-45"
                            >
                                {previewMutation.isPending
                                    ? t("previewing")
                                    : t("preview")}
                            </button>
                        </div>

                        {previewDirty && preview ? (
                            <div className="mt-4 rounded-[13px] border border-[#eadca7] bg-[#fffaf0] px-4 py-3 text-[9px] leading-[18px] text-[#675b32]/80">
                                {t("previewDirty")}
                            </div>
                        ) : null}

                        {preview ? (
                            <div className="mt-5 grid gap-4 lg:grid-cols-2">
                                <PreviewCard
                                    label={t("before")}
                                    score={preview.before}
                                />

                                <PreviewCard
                                    label={t("after")}
                                    score={preview.after}
                                />
                            </div>
                        ) : (
                            <div className="mt-5 rounded-[16px] border border-dashed border-black/[0.09] bg-[#fafaf7] px-5 py-8 text-center text-[10px] text-black/38">
                                {t("noPreview")}
                            </div>
                        )}
                    </section>
                </main>
            </div>
        </div>
    );
}