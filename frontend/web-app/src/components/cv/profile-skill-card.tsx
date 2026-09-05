"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  formatProfileDate,
  humanizeProfileEnum
} from "@/components/cv/profile-value";

import type {
  ProfileSkillCardProps
} from "@/types/cv-ui";

function SparkIcon() {
  return (
    <svg
      viewBox="0 0 18 18"
      fill="none"
      className="size-3"
      aria-hidden="true"
    >
      <path
        d="M9 2.5c.42 2.72 1.78 4.08 4.5 4.5C10.78 7.42 9.42 8.78 9 11.5 8.58 8.78 7.22 7.42 4.5 7 7.22 6.58 8.58 5.22 9 2.5Z"
        stroke="currentColor"
        strokeWidth="1.25"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function ProfileSkillCard({
  skill,
  index
}: ProfileSkillCardProps) {
  const t =
    useTranslations(
      "user.cv"
    );

  const locale =
    useLocale();

  const displayName =
    skill.name?.trim() ||
    skill.normalizedName?.trim() ||
    "—";

  const normalizedName =
    skill.normalizedName?.trim();

  const showNormalizedName =
    Boolean(
      normalizedName &&
      normalizedName
        .toLocaleLowerCase() !==
        displayName
          .toLocaleLowerCase()
    );

  const category =
    skill.category &&
    skill.category !==
      "OTHER"
      ? humanizeProfileEnum(
          skill.category
        )
      : null;

  const proficiency =
    skill.proficiencyText?.trim() ||
    (
      skill.normalizedProficiency &&
      skill.normalizedProficiency !==
        "UNKNOWN"
        ? humanizeProfileEnum(
            skill.normalizedProficiency
          )
        : null
    );

  const hasExperience =
    skill.yearsOfExperience !==
      null &&
    skill.yearsOfExperience !==
      undefined;

  const lastUsed =
    skill.lastUsedDate
      ? formatProfileDate(
          skill.lastUsedDate,
          locale
        )
      : null;

  const evidence =
    skill.evidenceSources
      .map(
        (source) =>
          source
            .trim()
      )
      .filter(
        Boolean
      )
      .map(
        (source) =>
          humanizeProfileEnum(
            source
          )
      );

  const metaItems =
    [
      proficiency,

      hasExperience
        ? t(
            "profile.experienceYears",
            {
              value:
                skill.yearsOfExperience
            }
          )
        : null,

      lastUsed
    ].filter(
      (
        value
      ): value is string =>
        Boolean(
          value
        )
    );

  return (
    <article className="group relative overflow-hidden rounded-[15px] border border-black/[0.05] bg-[#fafaf7] px-4 py-3.5 transition-[background-color,border-color,box-shadow,transform] duration-200 hover:-translate-y-px hover:border-[#ccdD8b] hover:bg-white hover:shadow-[0_6px_20px_rgba(0,0,0,0.025)]">
      <div className="pointer-events-none absolute inset-y-3 left-0 w-[2px] rounded-r-full bg-[#bdd75c] opacity-0 transition-opacity group-hover:opacity-100" />

      <div className="flex min-w-0 items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2">
            <span className="shrink-0 text-[#8fa82f]">
              <SparkIcon />
            </span>

            <h4 className="truncate text-[12px] font-bold tracking-[-0.025em] text-[#282826]">
              {
                displayName
              }
            </h4>
          </div>

          {showNormalizedName ? (
            <p className="ml-5 mt-0.5 truncate font-mono text-[6.5px] uppercase tracking-[0.08em] text-black/20">
              {
                normalizedName
              }
            </p>
          ) : null}
        </div>

        {category ? (
          <span className="shrink-0 rounded-full border border-black/[0.045] bg-white px-2 py-1 text-[6.5px] font-semibold uppercase tracking-[0.05em] text-black/27">
            {
              category
            }
          </span>
        ) : (
          <span className="font-mono text-[6px] text-black/12">
            {String(
              index + 1
            ).padStart(
              2,
              "0"
            )}
          </span>
        )}
      </div>

      {metaItems.length >
      0 ? (
        <div className="ml-5 mt-2.5 flex flex-wrap items-center gap-x-2 gap-y-1">
          {metaItems.map(
            (
              item,
              itemIndex
            ) => (
              <div
                key={`${item}-${itemIndex}`}
                className="flex items-center gap-2"
              >
                {itemIndex >
                0 ? (
                  <span className="size-[2px] rounded-full bg-black/15" />
                ) : null}

                <span className="text-[7.5px] font-medium text-black/34">
                  {
                    item
                  }
                </span>
              </div>
            )
          )}
        </div>
      ) : null}

      {evidence.length >
      0 ? (
        <div className="ml-5 mt-2 flex min-w-0 items-center gap-1.5">
          <span className="font-mono text-[6px] font-semibold uppercase tracking-[0.08em] text-black/18">
            {t(
              "profile.fields.evidenceSources"
            )}
          </span>

          <span className="text-[6px] text-black/12">
            /
          </span>

          <p className="min-w-0 truncate text-[7px] font-medium text-black/26">
            {evidence.join(
              " · "
            )}
          </p>
        </div>
      ) : null}
    </article>
  );
}