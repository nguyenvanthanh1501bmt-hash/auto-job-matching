"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  ProfileSectionNav
} from "@/components/cv/profile-section-nav";

import {
  ProfileSkillsGrid
} from "@/components/cv/profile-skills-grid";

import {
  formatProfileDate,
  humanizeProfileEnum,
  isEmptyProfileValue,
  ProfileSectionTitle,
  ProfileTags,
  StructuredProfileValue
} from "@/components/cv/profile-value";

import {
  Link
} from "@/i18n/navigation";

import type {
  CandidateProfileViewProps,
  ProfileSectionNavItem
} from "@/types/cv-ui";

const PROFILE_GROUPS = [
  {
    key: "overview",

    fields: [
      "professionalSummary",
      "careerObjective",
      "experienceYears",
      "seniority",
      "highestEducationLevel",
      "recentJobTitles",
      "recentCompanies"
    ]
  },

  {
    key: "contact",

    fields: [
      "contact",
      "links"
    ]
  },

  {
    key: "preferences",

    fields: [
      "targetJobTitles",
      "targetIndustries",
      "preferredLocations",
      "preferredWorkModes",
      "preferredEmploymentTypes",
      "expectedSalaryText",
      "availabilityText"
    ]
  },

  {
    key: "skills",

    fields: [
      "skills"
    ]
  },

  {
    key: "workExperience",

    fields: [
      "workExperiences"
    ]
  },

  {
    key: "projects",

    fields: [
      "projects"
    ]
  },

  {
    key: "education",

    fields: [
      "educations"
    ]
  },

  {
    key: "certifications",

    fields: [
      "certifications"
    ]
  },

  {
    key: "licenses",

    fields: [
      "licenses"
    ]
  },

  {
    key: "languages",

    fields: [
      "languages"
    ]
  },

  {
    key: "awards",

    fields: [
      "awards"
    ]
  },

  {
    key: "publications",

    fields: [
      "publications"
    ]
  },

  {
    key: "volunteering",

    fields: [
      "volunteerExperiences"
    ]
  },

  {
    key: "activities",

    fields: [
      "activities"
    ]
  },

  {
    key: "training",

    fields: [
      "trainingCourses"
    ]
  },

  {
    key: "interests",

    fields: [
      "interests"
    ]
  },

  {
    key: "parser",

    fields: [
      "detectedLanguage",
      "parserVersion",
      "parserWarnings",
      "parseQuality"
    ]
  }
] as const;

function normalizeScore(
  value:
    | number
    | null
    | undefined
) {
  if (
    typeof value !==
      "number" ||
    !Number.isFinite(
      value
    )
  ) {
    return null;
  }

  const normalized =
    value <= 1
      ? value * 100
      : value;

  return Math.round(
    Math.min(
      Math.max(
        normalized,
        0
      ),
      100
    )
  );
}

function initials(
  name:
    | string
    | null
) {
  const normalized =
    name?.trim();

  if (
    !normalized
  ) {
    return "AI";
  }

  return normalized
    .split(/\s+/)
    .slice(-2)
    .map(
      (part) =>
        part
          .charAt(0)
          .toUpperCase()
    )
    .join("");
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
        d="M4.5 10h10.25M11 6.25 14.75 10 11 13.75"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function QualityRing({
  score
}: {
  score:
    | number
    | null;
}) {
  const displayScore =
    score ?? 0;

  return (
    <div className="relative flex size-[88px] shrink-0 items-center justify-center rounded-full bg-white shadow-[0_6px_24px_rgba(0,0,0,0.045)]">
      <svg
        viewBox="0 0 100 100"
        className="absolute inset-0 size-full -rotate-90"
        aria-hidden="true"
      >
        <circle
          cx="50"
          cy="50"
          r="42"
          fill="none"
          stroke="rgba(0,0,0,0.055)"
          strokeWidth="5"
        />

        {score !== null ? (
          <circle
            cx="50"
            cy="50"
            r="42"
            fill="none"
            stroke="#a9c84a"
            strokeWidth="5"
            strokeLinecap="round"
            strokeDasharray={`${displayScore * 2.6389} 263.89`}
          />
        ) : null}
      </svg>

      <div className="relative text-center">
        <p className="text-[24px] font-bold leading-none tracking-[-0.055em] text-[#2c3219]">
          {score ??
            "—"}
        </p>

        {score !==
        null ? (
          <p className="mt-1 font-mono text-[7px] font-semibold text-black/28">
            %
          </p>
        ) : null}
      </div>
    </div>
  );
}

export function CandidateProfileView({
  profile
}: CandidateProfileViewProps) {
  const t =
    useTranslations(
      "user.cv"
    );

  const locale =
    useLocale();

  const profileRecord =
    profile as unknown as Record<
      string,
      unknown
    >;

  const updatedAt =
    profile.updatedAt
      ? formatProfileDate(
          profile.updatedAt,
          locale
        )
      : null;

  const qualityScore =
    normalizeScore(
      profile
        .parseQuality
        ?.overallScore
    );

  const visibleGroups =
    PROFILE_GROUPS
      .map(
        (group) => ({
          ...group,

          entries:
            group.fields
              .map(
                (field) =>
                  [
                    field,
                    profileRecord[
                      field
                    ]
                  ] as const
              )
              .filter(
                ([
                  ,
                  value
                ]) =>
                  !isEmptyProfileValue(
                    value
                  )
              )
        })
      )
      .filter(
        (group) =>
          group
            .entries
            .length >
          0
      );

  const stats = [
    {
      label:
        t(
          "profile.sections.skills"
        ),

      value:
        profile
          .skills
          .length
    },

    {
      label:
        t(
          "profile.sections.workExperience"
        ),

      value:
        profile
          .workExperiences
          .length
    },

    {
      label:
        t(
          "profile.sections.projects"
        ),

      value:
        profile
          .projects
          .length
    },

    {
      label:
        t(
          "profile.sections.education"
        ),

      value:
        profile
          .educations
          .length
    }
  ];

  const navItems: ProfileSectionNavItem[] =
    [
      ...visibleGroups.map(
        (group) => ({
          id:
            `profile-${group.key}`,

          label:
            t(
              `profile.sections.${group.key}`
            )
        })
      ),

      ...(profile.sections.length >
      0
        ? [
            {
              id:
                "profile-detected-sections",

              label:
                t(
                  "profile.fields.detectedSections"
                )
            }
          ]
        : [])
    ];

  return (
    <div className="space-y-6">
      <section className="relative overflow-hidden rounded-[24px] border border-black/[0.055] bg-white shadow-[0_10px_36px_rgba(0,0,0,0.035)]">
        <div className="absolute inset-x-0 top-0 h-[3px] bg-gradient-to-r from-[#d9ff75] via-[#b8d752] to-transparent" />

        <div className="pointer-events-none absolute -right-24 -top-28 size-72 rounded-full bg-[#d9ff75]/22 blur-3xl" />

        <div className="relative p-6 sm:p-7 lg:p-8">
          <div className="flex flex-col gap-4 border-b border-black/[0.05] pb-6 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-wrap items-center gap-3">
              <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.16em] text-black/28">
                {t(
                  "profile.eyebrow"
                )}
              </span>

              <span className="h-px w-8 bg-black/[0.07]" />

              <span className="inline-flex items-center gap-1.5 rounded-full border border-[#d0e57e] bg-[#f2ffcd] px-2.5 py-1 text-[8px] font-bold uppercase tracking-[0.08em] text-[#596b1d]">
                <span className="size-1.5 rounded-full bg-[#9fbb3d]" />

                {t(
                  "profile.ready"
                )}
              </span>
            </div>

            {updatedAt ? (
              <p className="font-mono text-[8px] uppercase tracking-[0.1em] text-black/24">
                {t(
                  "profile.updatedAt",
                  {
                    date:
                      updatedAt
                  }
                )}
              </p>
            ) : null}
          </div>

          <div className="mt-7 grid gap-7 xl:grid-cols-[minmax(0,1fr)_280px] xl:items-center">
            <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
              <div className="flex size-[66px] shrink-0 items-center justify-center rounded-[20px] border border-[#d4e78c] bg-[#efffc3] text-[18px] font-bold tracking-[-0.04em] text-[#596d1a] shadow-[0_8px_24px_rgba(140,163,55,0.08)]">
                {initials(
                  profile
                    .fullName
                )}
              </div>

              <div className="min-w-0 flex-1">
                <h2 className="max-w-[620px] text-[30px] font-bold leading-[1.05] tracking-[-0.052em] text-[#20201e] sm:text-[34px] lg:text-[36px]">
                  {profile
                    .fullName
                    ?.trim() ||
                    t(
                      "profile.unknownName"
                    )}
                </h2>

                {profile
                  .headline
                  ?.trim() ? (
                  <p className="mt-2.5 max-w-[620px] text-[11px] font-medium leading-5 text-black/43">
                    {
                      profile
                        .headline
                    }
                  </p>
                ) : null}

                <div className="mt-4 flex flex-wrap gap-2">
                  {profile
                    .experienceYears !==
                  null ? (
                    <span className="rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1.5 text-[9px] font-semibold text-black/45">
                      {t(
                        "profile.experienceYears",
                        {
                          value:
                            profile
                              .experienceYears
                        }
                      )}
                    </span>
                  ) : null}

                  {profile
                    .seniority ? (
                    <span className="rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1.5 text-[9px] font-semibold text-black/45">
                      {humanizeProfileEnum(
                        profile
                          .seniority
                      )}
                    </span>
                  ) : null}

                  {profile
                    .highestEducationLevel ? (
                    <span className="rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1.5 text-[9px] font-semibold text-black/45">
                      {humanizeProfileEnum(
                        profile
                          .highestEducationLevel
                      )}
                    </span>
                  ) : null}
                </div>

                <div className="mt-6">
                  <Link
                    href="/matches"
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-full bg-[#171717] px-[18px] text-[10px] font-semibold text-white shadow-[0_5px_16px_rgba(0,0,0,0.08)] transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-[0_8px_20px_rgba(0,0,0,0.11)]"
                  >
                    {t(
                      "profile.goToMatches"
                    )}

                    <ArrowIcon />
                  </Link>
                </div>
              </div>
            </div>

            <div className="rounded-[20px] border border-[#dfeaaa] bg-[#f9ffe9] p-5">
              <div className="flex items-center justify-between gap-5">
                <div>
                  <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/28">
                    {t(
                      "profile.fields.quality"
                    )}
                  </p>

                  <p className="mt-2 text-[13px] font-bold tracking-[-0.025em] text-[#2c2c29]">
                    {t(
                      "profile.quality.overall"
                    )}
                  </p>

                  <p className="mt-1.5 max-w-[120px] text-[9px] leading-4 text-black/32">
                    {t(
                      "profile.ready"
                    )}
                  </p>
                </div>

                <QualityRing
                  score={
                    qualityScore
                  }
                />
              </div>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 border-t border-black/[0.05] bg-[#fafaf7] lg:grid-cols-4">
          {stats.map(
            (
              stat,
              index
            ) => (
              <div
                key={
                  stat.label
                }
                className={`px-5 py-5 sm:px-6 ${
                  index < 3
                    ? "lg:border-r lg:border-black/[0.05]"
                    : ""
                } ${
                  index %
                    2 ===
                  0
                    ? "border-r border-black/[0.05]"
                    : ""
                } ${
                  index < 2
                    ? "border-b border-black/[0.05] lg:border-b-0"
                    : ""
                }`}
              >
                <div className="flex items-end justify-between gap-4">
                  <div>
                    <p className="text-[23px] font-bold leading-none tracking-[-0.05em] text-[#282826]">
                      {
                        stat.value
                      }
                    </p>

                    <p className="mt-2 text-[8px] font-semibold text-black/31">
                      {
                        stat.label
                      }
                    </p>
                  </div>

                  <span className="mb-1 size-1.5 rounded-full bg-[#b5d354]" />
                </div>
              </div>
            )
          )}
        </div>
      </section>

      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_108px]">
        <main className="min-w-0 space-y-5">
          {visibleGroups.map(
            (
              group,
              groupIndex
            ) => (
              <section
                id={`profile-${group.key}`}
                key={
                  group.key
                }
                className="scroll-mt-28 rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_5px_22px_rgba(0,0,0,0.025)] sm:p-6"
              >
                <ProfileSectionTitle
                  index={
                    groupIndex +
                    1
                  }
                >
                  {t(
                    `profile.sections.${group.key}`
                  )}
                </ProfileSectionTitle>

                <div className="grid gap-3 lg:grid-cols-2">
                  {group.entries.map(
                    ([
                      field,
                      value
                    ]) => {
                      if (
                        field ===
                        "skills"
                      ) {
                        return (
                          <div
                            key={
                              field
                            }
                            className="lg:col-span-2"
                          >
                            <ProfileSkillsGrid
                              skills={
                                profile.skills
                              }
                            />
                          </div>
                        );
                      }

                      const fieldKey =
                        `profile.fields.${field}`;

                      const label =
                        t.has(
                          fieldKey
                        )
                          ? t(
                              fieldKey
                            )
                          : field;

                      const isWide =
                        Array.isArray(
                          value
                        ) ||
                        (
                          typeof value ===
                            "object" &&
                          value !==
                            null
                        );

                      return (
                        <article
                          key={
                            field
                          }
                          className={`rounded-[16px] border border-black/[0.045] bg-[#fafaf7] p-4 ${
                            isWide
                              ? "lg:col-span-2"
                              : ""
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <span className="size-1 rounded-full bg-[#b5d354]" />

                            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.12em] text-black/25">
                              {
                                label
                              }
                            </p>
                          </div>

                          <div className="mt-3 text-[10px] font-medium leading-5 text-black/54">
                            <StructuredProfileValue
                              fieldKey={
                                field
                              }
                              value={
                                value
                              }
                            />
                          </div>
                        </article>
                      );
                    }
                  )}
                </div>
              </section>
            )
          )}

          {profile
            .sections
            .length >
          0 ? (
            <section
              id="profile-detected-sections"
              className="scroll-mt-28 rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_5px_22px_rgba(0,0,0,0.025)] sm:p-6"
            >
              <ProfileSectionTitle
                index={
                  visibleGroups.length +
                  1
                }
              >
                {t(
                  "profile.fields.detectedSections"
                )}
              </ProfileSectionTitle>

              <div className="rounded-[16px] border border-black/[0.045] bg-[#fafaf7] p-4">
                <ProfileTags
                  values={
                    profile
                      .sections
                      .map(
                        (
                          section
                        ) =>
                          section
                            .heading
                            ?.trim() ||
                          (
                            section
                              .sectionType
                              ? humanizeProfileEnum(
                                  section
                                    .sectionType
                                )
                              : null
                          )
                      )
                      .filter(
                        (
                          value
                        ): value is string =>
                          Boolean(
                            value
                          )
                      )
                  }
                />
              </div>
            </section>
          ) : null}
        </main>

        <ProfileSectionNav
          items={
            navItems
          }
        />
      </div>
    </div>
  );
}