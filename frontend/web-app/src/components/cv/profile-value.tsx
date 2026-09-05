"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import type {
  ProfileSectionTitleProps,
  ProfileTagsProps,
  StructuredProfileValueProps
} from "@/types/cv-ui";

export function isEmptyProfileValue(
  value: unknown
): boolean {
  if (
    value ===
      null ||
    value ===
      undefined ||
    value ===
      ""
  ) {
    return true;
  }

  if (
    Array.isArray(
      value
    )
  ) {
    return (
      value.length ===
      0
    );
  }

  if (
    isRecord(
      value
    )
  ) {
    return Object
      .values(
        value
      )
      .every(
        isEmptyProfileValue
      );
  }

  return false;
}

function isRecord(
  value: unknown
): value is Record<
  string,
  unknown
> {
  return (
    typeof value ===
      "object" &&
    value !==
      null &&
    !Array.isArray(
      value
    )
  );
}

export function humanizeProfileKey(
  value: string
) {
  return value
    .replace(
      /([a-z0-9])([A-Z])/g,
      "$1 $2"
    )
    .replace(
      /_/g,
      " "
    )
    .replace(
      /^./,
      (
        character
      ) =>
        character.toUpperCase()
    );
}

export function humanizeProfileEnum(
  value: string
) {
  return value
    .toLowerCase()
    .split(
      "_"
    )
    .map(
      (
        part
      ) =>
        part
          .charAt(
            0
          )
          .toUpperCase() +
        part.slice(
          1
        )
    )
    .join(
      " "
    );
}

export function formatProfileDate(
  value: string,
  locale: string
) {
  const normalized =
    /^\d{4}-\d{2}$/.test(
      value
    )
      ? `${value}-01T00:00:00`
      : /^\d{4}$/.test(
            value
          )
        ? `${value}-01-01T00:00:00`
        : value;

  const date =
    new Date(
      normalized
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
        /^\d{4}(-\d{2})?$/.test(
          value
        )
          ? undefined
          : "2-digit",

      month:
        /^\d{4}$/.test(
          value
        )
          ? undefined
          : "short",

      year:
        "numeric"
    }
  ).format(
    date
  );
}

function looksLikeDateKey(
  key: string
) {
  return (
    key.endsWith(
      "Date"
    ) ||
    key.endsWith(
      "At"
    ) ||
    key ===
      "startDate" ||
    key ===
      "endDate"
  );
}

function looksLikeUrlKey(
  key: string
) {
  return key
    .toLowerCase()
    .includes(
      "url"
    );
}

function ExternalIcon() {
  return (
    <svg
      viewBox="0 0 18 18"
      fill="none"
      className="size-3.5"
      aria-hidden="true"
    >
      <path
        d="M7.25 4.25H4.8A1.55 1.55 0 0 0 3.25 5.8v7.4a1.55 1.55 0 0 0 1.55 1.55h7.4a1.55 1.55 0 0 0 1.55-1.55v-2.45M10 3.25h4.75V8M14.25 3.75 8 10"
        stroke="currentColor"
        strokeWidth="1.35"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function ProfileSectionTitle({
  children,
  index
}: ProfileSectionTitleProps) {
  return (
    <div className="mb-5 flex items-center gap-3">
      {index ? (
        <span className="flex size-7 shrink-0 items-center justify-center rounded-[9px] bg-[#1d1d1b] font-mono text-[8px] font-bold text-[#d9ff75]">
          {String(
            index
          ).padStart(
            2,
            "0"
          )}
        </span>
      ) : null}

      <h3 className="text-[16px] font-bold tracking-[-0.035em] text-[#292927]">
        {children}
      </h3>

      <span className="h-px flex-1 bg-black/[0.055]" />
    </div>
  );
}

export function ProfileTags({
  values
}: ProfileTagsProps) {
  if (
    values.length ===
    0
  ) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {values.map(
        (
          value,
          index
        ) => (
          <span
            key={`${value}-${index}`}
            className="rounded-full border border-black/[0.055] bg-white px-3 py-1.5 text-[9px] font-semibold text-[#55554f] shadow-[0_2px_8px_rgba(0,0,0,0.02)]"
          >
            {value}
          </span>
        )
      )}
    </div>
  );
}

export function StructuredProfileValue({
  fieldKey,
  value
}: StructuredProfileValueProps) {
  const t =
    useTranslations(
      "user.cv"
    );

  const locale =
    useLocale();

  if (
    isEmptyProfileValue(
      value
    )
  ) {
    return null;
  }

  if (
    typeof value ===
    "boolean"
  ) {
    return (
      <span className="inline-flex items-center gap-2">
        <span
          className={`size-1.5 rounded-full ${
            value
              ? "bg-[#9fbe3f]"
              : "bg-black/20"
          }`}
        />

        {value
          ? t(
              "profile.yes"
            )
          : t(
              "profile.no"
            )}
      </span>
    );
  }

  if (
    typeof value ===
    "number"
  ) {
    if (
      fieldKey
        .toLowerCase()
        .includes(
          "score"
        )
    ) {
      const percent =
        value <=
        1
          ? value *
            100
          : value;

      const normalized =
        Math.round(
          Math.min(
            Math.max(
              percent,
              0
            ),
            100
          )
        );

      return (
        <div className="flex items-center gap-3">
          <span className="text-[15px] font-bold tracking-[-0.03em] text-[#2b2b28]">
            {
              normalized
            }
            %
          </span>

          <span className="h-1.5 min-w-20 flex-1 overflow-hidden rounded-full bg-black/[0.055]">
            <span
              className="block h-full rounded-full bg-[#a9c84a]"
              style={{
                width:
                  `${normalized}%`
              }}
            />
          </span>
        </div>
      );
    }

    return (
      <>
        {value}
      </>
    );
  }

  if (
    typeof value ===
    "string"
  ) {
    if (
      looksLikeUrlKey(
        fieldKey
      ) &&
      /^https?:\/\//i.test(
        value
      )
    ) {
      return (
        <a
          href={
            value
          }
          target="_blank"
          rel="noreferrer"
          className="inline-flex max-w-full items-center gap-1.5 font-semibold text-[#3e4b18] underline decoration-[#a9c84a]/45 underline-offset-4 transition-colors hover:text-black"
        >
          <span className="truncate">
            {value}
          </span>

          <span className="shrink-0">
            <ExternalIcon />
          </span>
        </a>
      );
    }

    if (
      looksLikeDateKey(
        fieldKey
      )
    ) {
      return (
        <>
          {formatProfileDate(
            value,
            locale
          )}
        </>
      );
    }

    if (
      /^[A-Z][A-Z0-9_]+$/.test(
        value
      )
    ) {
      return (
        <span className="inline-flex rounded-full border border-black/[0.055] bg-white px-3 py-1.5 text-[9px] font-semibold text-black/52">
          {humanizeProfileEnum(
            value
          )}
        </span>
      );
    }

    return (
      <span className="whitespace-pre-line">
        {value}
      </span>
    );
  }

  if (
    Array.isArray(
      value
    )
  ) {
    const visible =
      value.filter(
        (
          item
        ) =>
          !isEmptyProfileValue(
            item
          )
      );

    if (
      visible.length ===
      0
    ) {
      return null;
    }

    const allPrimitive =
      visible.every(
        (
          item
        ) =>
          typeof item ===
            "string" ||
          typeof item ===
            "number" ||
          typeof item ===
            "boolean"
      );

    if (
      allPrimitive
    ) {
      return (
        <ProfileTags
          values={
            visible.map(
              (
                item
              ) => {
                if (
                  typeof item ===
                    "string" &&
                  /^[A-Z][A-Z0-9_]+$/.test(
                    item
                  )
                ) {
                  return humanizeProfileEnum(
                    item
                  );
                }

                return String(
                  item
                );
              }
            )
          }
        />
      );
    }

    return (
      <div className="grid gap-3 xl:grid-cols-2">
        {visible.map(
          (
            item,
            index
          ) => (
            <div
              key={
                index
              }
              className="relative overflow-hidden rounded-[17px] border border-black/[0.05] bg-white p-4 shadow-[0_3px_12px_rgba(0,0,0,0.018)]"
            >
              <span className="absolute right-3 top-3 font-mono text-[7px] font-bold tracking-[0.08em] text-black/18">
                {String(
                  index +
                    1
                ).padStart(
                  2,
                  "0"
                )}
              </span>

              <StructuredProfileValue
                fieldKey={`${fieldKey}.${index}`}
                value={
                  item
                }
              />
            </div>
          )
        )}
      </div>
    );
  }

  if (
    isRecord(
      value
    )
  ) {
    const entries =
      Object
        .entries(
          value
        )
        .filter(
          ([
            ,
            itemValue
          ]) =>
            !isEmptyProfileValue(
              itemValue
            )
        );

    if (
      entries.length ===
      0
    ) {
      return null;
    }

    return (
      <div className="grid gap-x-5 gap-y-4 sm:grid-cols-2">
        {entries.map(
          ([
            key,
            itemValue
          ]) => {
            const translationKey =
              `profile.fields.${key}`;

            const label =
              t.has(
                translationKey
              )
                ? t(
                    translationKey
                  )
                : humanizeProfileKey(
                    key
                  );

            const isWide =
              Array.isArray(
                itemValue
              ) ||
              isRecord(
                itemValue
              );

            return (
              <div
                key={
                  key
                }
                className={`min-w-0 ${
                  isWide
                    ? "sm:col-span-2"
                    : ""
                }`}
              >
                <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.12em] text-black/25">
                  {label}
                </p>

                <div className="mt-1.5 text-[10px] font-medium leading-5 text-black/54">
                  <StructuredProfileValue
                    fieldKey={
                      key
                    }
                    value={
                      itemValue
                    }
                  />
                </div>
              </div>
            );
          }
        )}
      </div>
    );
  }

  return null;
}