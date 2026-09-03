"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  formatProfileDate,
  humanizeProfileEnum,
  isEmptyProfileValue,
  ProfileSectionTitle,
  ProfileTags,
  StructuredProfileValue
} from "@/components/cv/profile-value";

import {Link} from "@/i18n/navigation";

import type {
  CandidateProfileResponse
} from "@/types/cv";

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
    key:
      "workExperience",
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
    key:
      "certifications",
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
    key:
      "publications",
    fields: [
      "publications"
    ]
  },
  {
    key:
      "volunteering",
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

type Props = {
  profile:
    CandidateProfileResponse;
};

export function CandidateProfileView({
  profile
}: Props) {
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

  return (
    <div className="space-y-6">
      <section className="relative overflow-hidden rounded-[22px] border border-black/[0.055] bg-white p-6 shadow-[0_5px_24px_rgba(0,0,0,0.025)] sm:p-8">
        <div className="absolute right-0 top-0 size-40 rounded-full bg-[#d9ff75]/35 blur-3xl" />

        <div className="relative">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.15em] text-black/28">
              {t(
                "profile.eyebrow"
              )}
            </span>

            <span className="rounded-full bg-[#eaffb4] px-2.5 py-1 text-[8px] font-bold uppercase tracking-[0.08em] text-[#4b5d1d]">
              {t(
                "profile.ready"
              )}
            </span>
          </div>

          <div className="mt-5 flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
            <div className="min-w-0">
              <h2 className="text-[30px] font-bold leading-[1.05] tracking-[-0.055em] text-[#20201e] sm:text-[38px]">
                {profile.fullName?.trim() ||
                  t(
                    "profile.unknownName"
                  )}
              </h2>

              {profile.headline?.trim() ? (
                <p className="mt-2 text-[13px] font-semibold text-black/48">
                  {profile.headline}
                </p>
              ) : null}

              {updatedAt ? (
                <p className="mt-2 font-mono text-[8px] uppercase tracking-[0.1em] text-black/25">
                  {t(
                    "profile.updatedAt",
                    {
                      date:
                        updatedAt
                    }
                  )}
                </p>
              ) : null}

              <div className="mt-4 flex flex-wrap gap-2">
                {profile.experienceYears !==
                null ? (
                  <span className="rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1 text-[9px] font-semibold text-black/48">
                    {t(
                      "profile.experienceYears",
                      {
                        value:
                          profile.experienceYears
                      }
                    )}
                  </span>
                ) : null}

                {profile.seniority ? (
                  <span className="rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1 text-[9px] font-semibold text-black/48">
                    {humanizeProfileEnum(
                      profile.seniority
                    )}
                  </span>
                ) : null}

                {profile.highestEducationLevel ? (
                  <span className="rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-1 text-[9px] font-semibold text-black/48">
                    {humanizeProfileEnum(
                      profile.highestEducationLevel
                    )}
                  </span>
                ) : null}
              </div>
            </div>

            <Link
              href="/matches"
              className="inline-flex h-11 shrink-0 items-center justify-center gap-2 rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white shadow-[0_5px_18px_rgba(0,0,0,0.09)] transition-transform hover:-translate-y-0.5"
            >
              {t(
                "profile.goToMatches"
              )}

              <span aria-hidden="true">
                →
              </span>
            </Link>
          </div>
        </div>
      </section>

      {PROFILE_GROUPS.map(
        (group) => {
          const entries =
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
              );

          if (
            entries.length ===
            0
          ) {
            return null;
          }

          return (
            <section
              key={
                group.key
              }
            >
              <ProfileSectionTitle>
                {t(
                  `profile.sections.${group.key}`
                )}
              </ProfileSectionTitle>

              <div className="space-y-3">
                {entries.map(
                  ([
                    field,
                    value
                  ]) => {
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

                    return (
                      <article
                        key={
                          field
                        }
                        className="rounded-[18px] border border-black/[0.05] bg-white p-5 shadow-[0_3px_14px_rgba(0,0,0,0.02)]"
                      >
                        <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
                          {label}
                        </p>

                        <div className="mt-3 text-[11px] font-medium leading-5 text-black/55">
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
          );
        }
      )}

      {profile.sections.length >
      0 ? (
        <section>
          <ProfileSectionTitle>
            {t(
              "profile.fields.detectedSections"
            )}
          </ProfileSectionTitle>

          <div className="rounded-[18px] border border-black/[0.05] bg-white p-5">
            <ProfileTags
              values={profile.sections
                .map(
                  (
                    section
                  ) =>
                    section.heading?.trim() ||
                    (
                      section.sectionType
                        ? humanizeProfileEnum(
                            section.sectionType
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
                )}
            />
          </div>
        </section>
      ) : null}
    </div>
  );
}