"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

export function isEmptyProfileValue(
  value: unknown
): boolean {
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return true;
  }

  if (
    Array.isArray(value)
  ) {
    return (
      value.length === 0
    );
  }

  if (
    isRecord(value)
  ) {
    return Object
      .values(value)
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
    value !== null &&
    !Array.isArray(value)
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
    .split("_")
    .map(
      (part) =>
        part
          .charAt(0)
          .toUpperCase() +
        part.slice(1)
    )
    .join(" ");
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
    locale === "vi"
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

      year: "numeric"
    }
  ).format(date);
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
    key === "startDate" ||
    key === "endDate"
  );
}

function looksLikeUrlKey(
  key: string
) {
  return (
    key
      .toLowerCase()
      .includes("url")
  );
}

export function ProfileSectionTitle({
  children
}: {
  children:
    React.ReactNode;
}) {
  return (
    <div className="mb-4 flex items-center gap-3">
      <h3 className="text-[15px] font-bold tracking-[-0.03em] text-[#292927]">
        {children}
      </h3>

      <span className="h-px flex-1 bg-black/[0.055]" />
    </div>
  );
}

export function ProfileTags({
  values
}: {
  values: string[];
}) {
  if (
    values.length === 0
  ) {
    return null;
  }

  return (
    <div className="flex flex-wrap gap-1.5">
      {values.map(
        (
          value,
          index
        ) => (
          <span
            key={`${value}-${index}`}
            className="rounded-[8px] bg-[#f0f0ec] px-2.5 py-1 text-[9px] font-semibold text-[#5c5c56]"
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
}: {
  fieldKey: string;
  value: unknown;
}) {
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
      <>
        {value
          ? t(
              "profile.yes"
            )
          : t(
              "profile.no"
            )}
      </>
    );
  }

  if (
    typeof value ===
    "number"
  ) {
    if (
      fieldKey
        .toLowerCase()
        .includes("score")
    ) {
      const percent =
        value <= 1
          ? value * 100
          : value;

      return (
        <>
          {Math.round(
            percent
          )}
          %
        </>
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
          href={value}
          target="_blank"
          rel="noreferrer"
          className="break-all font-semibold underline decoration-black/15 underline-offset-4 hover:text-black"
        >
          {value}
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
        <>
          {humanizeProfileEnum(
            value
          )}
        </>
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
        (item) =>
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
          values={visible.map(
            (item) => {
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
          )}
        />
      );
    }

    return (
      <div className="grid gap-3 lg:grid-cols-2">
        {visible.map(
          (
            item,
            index
          ) => (
            <div
              key={index}
              className="rounded-[16px] border border-black/[0.05] bg-[#fafaf7] p-4"
            >
              <StructuredProfileValue
                fieldKey={`${fieldKey}.${index}`}
                value={item}
              />
            </div>
          )
        )}
      </div>
    );
  }

  if (
    isRecord(value)
  ) {
    const entries =
      Object
        .entries(value)
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
      <div className="grid gap-x-6 gap-y-5 sm:grid-cols-2">
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

            return (
              <div
                key={key}
                className={
                  Array.isArray(
                    itemValue
                  ) ||
                  isRecord(
                    itemValue
                  )
                    ? "sm:col-span-2"
                    : ""
                }
              >
                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
                  {label}
                </p>

                <div className="mt-1.5 text-[11px] font-medium leading-5 text-black/55">
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