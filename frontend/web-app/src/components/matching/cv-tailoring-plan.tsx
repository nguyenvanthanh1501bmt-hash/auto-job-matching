"use client";

import {
    useMemo
} from "react";

import {
    useTranslations
} from "next-intl";

import type {
    CvTailoringEvidenceItem,
    CvTailoringSuggestionItem
} from "@/types/cv-tailoring";

import type {
    CvTailoringPlanProps
} from "@/types/matching-ui";

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

function QuestionIcon() {
    return (
        <span
            aria-hidden="true"
            className="flex size-6 shrink-0 items-center justify-center rounded-full border border-[#cfdde8] bg-white text-[11px] font-bold text-[#44627b]"
        >
      ?
    </span>
    );
}

function SkillTags({
                       skills
                   }: {
    skills: string[];
}) {
    if (skills.length === 0) {
        return null;
    }

    return (
        <div className="flex flex-wrap gap-1.5">
            {skills.map((skill) => (
                <span
                    key={skill}
                    className="rounded-[8px] border border-[#dbeaa5] bg-[#f3ffd6] px-2.5 py-1 text-[9px] font-medium text-[#4d5b20]"
                >
          {skill}
        </span>
            ))}
        </div>
    );
}

function priorityBorderClass(
    priority:
    CvTailoringSuggestionItem["priority"]
) {
    if (priority === "HIGH") {
        return "border-[#e7d384]";
    }

    if (priority === "MEDIUM") {
        return "border-[#dbeaa5]";
    }

    return "border-black/[0.055]";
}

function getEvidenceContext(
    evidenceIds: string[],
    evidenceById: Map<
        string,
        CvTailoringEvidenceItem
    >
) {
    const seen =
        new Set<string>();

    return evidenceIds
        .map(
            (evidenceId) =>
                evidenceById.get(
                    evidenceId
                )
        )
        .filter(
            (
                evidence
            ): evidence is CvTailoringEvidenceItem =>
                Boolean(
                    evidence?.text?.trim()
                )
        )
        .filter(
            (evidence) => {
                const text =
                    evidence.text?.trim()
                    ?? "";

                if (
                    seen.has(
                        text
                    )
                ) {
                    return false;
                }

                seen.add(
                    text
                );

                return true;
            }
        )
        .slice(
            0,
            3
        );
}

function suggestionTypeKey(
    suggestion:
    CvTailoringSuggestionItem
): "REWRITE" | "EMPHASIZE" {
    return suggestion.category ===
    "SURFACE"
        ? "EMPHASIZE"
        : "REWRITE";
}

function SuggestionCard({
                            suggestion,
                            selected,
                            evidenceById,
                            onToggle
                        }: {
    suggestion:
        CvTailoringSuggestionItem;

    selected:
        boolean;

    evidenceById:
        Map<
            string,
            CvTailoringEvidenceItem
        >;

    onToggle:
        (
            suggestionId: string
        ) => void;
}) {
    const t =
        useTranslations(
            "user.matches.tailoring"
        );

    const evidenceContext =
        getEvidenceContext(
            suggestion.evidenceIds,
            evidenceById
        );

    const isRewrite =
        suggestion.type ===
        "REWRITE";

    return (
        <article
            className={`rounded-[18px] border p-4 transition-colors sm:p-5 ${
                selected
                    ? "border-[#bedc58] bg-[#fbffe9]"
                    : `${priorityBorderClass(
                        suggestion.priority
                    )} bg-[#fafaf7]`
            }`}
        >
            <div className="flex items-start gap-3.5">
                <input
                    type="checkbox"
                    checked={selected}
                    onChange={() =>
                        onToggle(
                            suggestion.id
                        )
                    }
                    aria-label={t(
                        "suggestions.selectAria",
                        {
                            type: t(
                                `suggestionTypes.${suggestionTypeKey(
                                    suggestion
                                )}`
                            )
                        }
                    )}
                    className="mt-0.5 size-4 shrink-0 accent-[#171717]"
                />

                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-[#171717] px-2.5 py-1 font-mono text-[7px] font-semibold uppercase tracking-[0.1em] text-white">
              {t(
                  `suggestionTypes.${suggestionTypeKey(
                      suggestion
                  )}`
              )}
            </span>

                        <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-black/30">
              {t(
                  `sections.${suggestion.section}`
              )}
            </span>
                    </div>

                    {isRewrite ? (
                        <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_28px_minmax(0,1fr)] lg:items-stretch">
                            <div className="rounded-[13px] border border-black/[0.05] bg-white p-3.5">
                                <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                    {t(
                                        "suggestions.original"
                                    )}
                                </p>

                                <p className="mt-2 text-[10px] leading-[18px] text-black/48">
                                    {suggestion
                                            .original
                                            ?.trim()
                                        || t(
                                            "none"
                                        )}
                                </p>
                            </div>

                            <div className="hidden items-center justify-center text-black/30 lg:flex">
                                <ArrowIcon />
                            </div>

                            <div className="rounded-[13px] border border-[#dbeaa5] bg-[#f8ffe7] p-3.5">
                                <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-[#536226]/55">
                                    {t(
                                        "suggestions.suggested"
                                    )}
                                </p>

                                <p className="mt-2 text-[10px] font-medium leading-[18px] text-[#414b22]">
                                    {suggestion
                                            .suggested
                                            ?.trim()
                                        || t(
                                            "none"
                                        )}
                                </p>
                            </div>
                        </div>
                    ) : (
                        <div className="mt-4 rounded-[13px] border border-[#dbeaa5] bg-[#f8ffe7] p-3.5">
                            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-[#536226]/55">
                                {t(
                                    "suggestions.emphasizeSkill"
                                )}
                            </p>

                            <p className="mt-1.5 text-[14px] font-bold tracking-[-0.025em] text-[#414b22]">
                                {suggestion
                                        .suggested
                                        ?.trim()
                                    || suggestion
                                        .targetSkills[0]
                                    || t(
                                        "none"
                                    )}
                            </p>
                        </div>
                    )}

                    {suggestion
                        .targetSkills
                        .length > 0 ? (
                        <div className="mt-3">
                            <p className="mb-2 font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                {t(
                                    "suggestions.targetSkills"
                                )}
                            </p>

                            <SkillTags
                                skills={
                                    suggestion
                                        .targetSkills
                                }
                            />
                        </div>
                    ) : null}

                    {evidenceContext
                        .length > 0 ? (
                        <div className="mt-3 rounded-[13px] border border-black/[0.045] bg-white/80 p-3.5">
                            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                {t(
                                    "suggestions.evidence"
                                )}
                            </p>

                            <ul className="mt-2 space-y-2">
                                {evidenceContext.map(
                                    (evidence) => (
                                        <li
                                            key={
                                                evidence.id
                                            }
                                            className="flex gap-2 text-[9px] leading-[17px] text-black/43"
                                        >
                                            <span className="mt-[6px] size-1 shrink-0 rounded-full bg-[#a8c747]" />

                                            <span>
                        {
                            evidence.text
                        }
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

export function CvTailoringPlan({
                                    suggestions,
                                    coaching,
                                    gaps,
                                    evidence,
                                    selectedIds,
                                    onToggle
                                }: CvTailoringPlanProps) {
    const t =
        useTranslations(
            "user.matches.tailoring"
        );

    const evidenceById =
        useMemo(
            () =>
                new Map(
                    evidence.map(
                        (item) => [
                            item.id,
                            item
                        ]
                    )
                ),
            [
                evidence
            ]
        );

    const orderedSuggestions =
        useMemo(
            () =>
                [
                    ...suggestions
                ].sort(
                    (
                        a,
                        b
                    ) => {
                        const priority = {
                            HIGH: 0,
                            MEDIUM: 1,
                            LOW: 2
                        } as const;

                        return (
                            priority[
                                a.priority
                                ]
                            - priority[
                                b.priority
                                ]
                        );
                    }
                ),
            [
                suggestions
            ]
        );

    return (
        <>
            <section className="rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_5px_22px_rgba(0,0,0,0.02)] sm:p-6">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                            {t(
                                "suggestions.eyebrow"
                            )}
                        </p>

                        <h3 className="mt-1.5 text-[17px] font-bold tracking-[-0.035em] text-[#292927]">
                            {t(
                                "suggestions.title"
                            )}
                        </h3>

                        <p className="mt-2 max-w-[650px] text-[10px] leading-5 text-black/42">
                            {t(
                                "suggestions.description"
                            )}
                        </p>
                    </div>

                    <span className="shrink-0 font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-black/32">
            {t(
                "suggestions.selectedCount",
                {
                    count:
                    selectedIds.size
                }
            )}
          </span>
                </div>

                {orderedSuggestions
                    .length > 0 ? (
                    <div className="mt-5 space-y-3">
                        {orderedSuggestions.map(
                            (
                                suggestion
                            ) => (
                                <SuggestionCard
                                    key={
                                        suggestion.id
                                    }
                                    suggestion={
                                        suggestion
                                    }
                                    selected={
                                        selectedIds.has(
                                            suggestion.id
                                        )
                                    }
                                    evidenceById={
                                        evidenceById
                                    }
                                    onToggle={
                                        onToggle
                                    }
                                />
                            )
                        )}
                    </div>
                ) : (
                    <p className="mt-5 rounded-[14px] border border-dashed border-black/[0.08] bg-[#fafaf7] px-4 py-4 text-[10px] leading-5 text-black/40">
                        {t(
                            "suggestions.empty"
                        )}
                    </p>
                )}

                {coaching.length > 0 ? (
                    <div className="mt-5 space-y-3 border-t border-black/[0.05] pt-5">
                        {coaching.map(
                            (item) => (
                                <article
                                    key={
                                        item.id
                                    }
                                    className="rounded-[16px] border border-[#cfdde8] bg-[#f7fbff] p-4"
                                >
                                    <div className="flex items-start gap-3">
                                        <QuestionIcon />

                                        <div className="min-w-0 flex-1">
                      <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-black/30">
                        {t(
                            `sections.${item.section}`
                        )}
                      </span>

                                            {item
                                                .original
                                                ?.trim() ? (
                                                <div className="mt-3 rounded-[12px] border border-black/[0.04] bg-white/80 p-3">
                                                    <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-black/28">
                                                        {t(
                                                            "suggestions.original"
                                                        )}
                                                    </p>

                                                    <p className="mt-1.5 text-[9px] leading-[17px] text-black/46">
                                                        {
                                                            item.original
                                                        }
                                                    </p>
                                                </div>
                                            ) : null}

                                            {item
                                                .question
                                                ?.trim() ? (
                                                <p className="mt-3 text-[11px] font-semibold leading-5 text-[#2f4658]">
                                                    {
                                                        item.question
                                                    }
                                                </p>
                                            ) : null}

                                            {item
                                                .reason
                                                ?.trim() ? (
                                                <div className="mt-2">
                                                    <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.11em] text-[#44627b]/55">
                                                        {t(
                                                            "suggestions.reason"
                                                        )}
                                                    </p>

                                                    <p className="mt-1.5 text-[9px] leading-[17px] text-[#44627b]/72">
                                                        {
                                                            item.reason
                                                        }
                                                    </p>
                                                </div>
                                            ) : null}
                                        </div>
                                    </div>
                                </article>
                            )
                        )}
                    </div>
                ) : null}
            </section>

            {gaps.length > 0 ? (
                <section className="rounded-[22px] border border-[#eadca7] bg-[#fffaf0] p-5 sm:p-6">
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-[#745f17]/55">
                        {t(
                            "gaps.eyebrow"
                        )}
                    </p>

                    <h3 className="mt-1.5 text-[17px] font-bold tracking-[-0.035em] text-[#4d431f]">
                        {t(
                            "gaps.title"
                        )}
                    </h3>

                    <p className="mt-2 max-w-[650px] text-[10px] leading-5 text-[#675b32]/70">
                        {t(
                            "gaps.description"
                        )}
                    </p>

                    <div className="mt-4 flex flex-wrap gap-2">
                        {gaps.map(
                            (gap) =>
                                gap
                                    .skill
                                    ?.trim() ? (
                                    <span
                                        key={
                                            gap.id
                                        }
                                        className="rounded-full border border-[#eadca7] bg-white/70 px-3 py-1.5 text-[10px] font-semibold text-[#51461f]"
                                    >
                    {
                        gap.skill
                    }
                  </span>
                                ) : null
                        )}
                    </div>
                </section>
            ) : null}
        </>
    );
}