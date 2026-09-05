"use client";

import {
  useEffect,
  useRef,
  useState
} from "react";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  ExternalLinkIcon,
  formatJobDate,
  getJobExperience,
  getStructuredJobSalary,
  JobMetaPill
} from "@/components/jobs/job-display";

import {
  useNormalizedJob,
  usePrefetchNormalizedJob
} from "@/hooks/use-jobs";

import {
  getApiErrorMessage
} from "@/lib/api-error";

import type {
  JobDetailDrawerProps,
  JobDetailItemProps,
  JobTextSectionProps
} from "@/types/job-ui";

const DRAWER_ENTER_DURATION =
  240;

const DRAWER_EXIT_DURATION =
  210;

/*
 * Cho animation kết thúc rồi mới subscribe query.
 * Thêm một frame nhỏ để browser hoàn tất compositing.
 */
const CONTENT_DELAY =
  DRAWER_ENTER_DURATION +
  32;

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

function DetailItem({
  label,
  value
}: JobDetailItemProps) {
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return null;
  }

  return (
    <div className="rounded-[15px] border border-black/[0.05] bg-white px-4 py-3.5">
      <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
        {label}
      </p>

      <div className="mt-1.5 text-[11px] font-medium leading-5 text-black/55">
        {value}
      </div>
    </div>
  );
}

function TextSection({
  title,
  content
}: JobTextSectionProps) {
  if (
    !content?.trim()
  ) {
    return null;
  }

  return (
    <section className="border-t border-black/[0.055] pt-6 [contain-intrinsic-size:260px] [content-visibility:auto]">
      <h3 className="text-[13px] font-bold tracking-[-0.025em] text-[#292927]">
        {title}
      </h3>

      <p className="mt-3 whitespace-pre-line text-[12px] leading-6 text-black/52">
        {content}
      </p>
    </section>
  );
}

function DrawerLoading() {
  return (
    <div className="px-5 py-7 sm:px-8 sm:py-9">
      <div className="animate-pulse">
        <div className="flex gap-2">
          <div className="h-6 w-16 rounded-full bg-black/[0.055]" />

          <div className="h-6 w-20 rounded-full bg-black/[0.045]" />
        </div>

        <div className="mt-6 h-9 w-[74%] rounded-[8px] bg-black/[0.06]" />

        <div className="mt-3 h-4 w-[38%] rounded-[6px] bg-black/[0.04]" />

        <div className="mt-8 grid gap-3 sm:grid-cols-2">
          {Array.from({
            length: 6
          }).map(
            (
              _,
              index
            ) => (
              <div
                key={
                  index
                }
                className="h-[68px] rounded-[15px] bg-black/[0.035]"
              />
            )
          )}
        </div>
      </div>
    </div>
  );
}

export function JobDetailDrawer({
  jobId,
  onClose
}: JobDetailDrawerProps) {
  const t =
    useTranslations(
      "user.jobs"
    );

  const locale =
    useLocale();

  /*
   * shellJobId:
   * giữ shell tồn tại trong thời gian exit animation.
   *
   * queryJobId:
   * chỉ được set SAU KHI animation mở hoàn tất.
   */
  const [
    shellJobId,
    setShellJobId
  ] =
    useState<
      string | null
    >(null);

  const [
    queryJobId,
    setQueryJobId
  ] =
    useState<
      string | null
    >(null);

  const [
    isOpen,
    setIsOpen
  ] =
    useState(false);

  const openFrameRef =
    useRef<
      number | null
    >(null);

  const contentTimerRef =
    useRef<
      ReturnType<
        typeof setTimeout
      > | null
    >(null);

  const exitTimerRef =
    useRef<
      ReturnType<
        typeof setTimeout
      > | null
    >(null);

  const prefetchJob =
    usePrefetchNormalizedJob();

  const query =
    useNormalizedJob(
      queryJobId
    );

  function clearAnimationTimers() {
    if (
      openFrameRef.current !==
      null
    ) {
      window.cancelAnimationFrame(
        openFrameRef.current
      );

      openFrameRef.current =
        null;
    }

    if (
      contentTimerRef.current
    ) {
      clearTimeout(
        contentTimerRef.current
      );

      contentTimerRef.current =
        null;
    }

    if (
      exitTimerRef.current
    ) {
      clearTimeout(
        exitTimerRef.current
      );

      exitTimerRef.current =
        null;
    }
  }

  /*
   * Quan trọng:
   *
   * Khi jobId xuất hiện:
   * 1. prefetch query chạy nền
   * 2. shell drawer được mở
   * 3. query UI CHƯA subscribe
   * 4. hết animation mới set queryJobId
   *
   * Vì vậy data query không thể mount giữa lúc drawer đang chạy.
   */
  useEffect(() => {
    clearAnimationTimers();

    if (
      jobId
    ) {
      const normalizedJobId =
        jobId.trim();

      if (
        !normalizedJobId
      ) {
        return;
      }

      prefetchJob(
        normalizedJobId
      );

      setShellJobId(
        normalizedJobId
      );

      setQueryJobId(
        null
      );

      setIsOpen(
        false
      );

      /*
       * Drawer luôn có layer transform sẵn.
       * Chỉ cần đợi browser nhận state đóng trước
       * khi chuyển translate-x-0.
       */
      openFrameRef.current =
        window.requestAnimationFrame(
          () => {
            setIsOpen(
              true
            );

            openFrameRef.current =
              null;
          }
        );

      contentTimerRef.current =
        setTimeout(
          () => {
            setQueryJobId(
              normalizedJobId
            );

            contentTimerRef.current =
              null;
          },
          CONTENT_DELAY
        );

      return;
    }

    /*
     * Đóng drawer trước.
     * Giữ nguyên DOM detail trong exit animation để
     * browser chỉ composite texture có sẵn.
     */
    setIsOpen(
      false
    );

    exitTimerRef.current =
      setTimeout(
        () => {
          setShellJobId(
            null
          );

          setQueryJobId(
            null
          );

          exitTimerRef.current =
            null;
        },
        DRAWER_EXIT_DURATION
      );
  }, [
    jobId,
    prefetchJob
  ]);

  useEffect(() => {
    return () => {
      clearAnimationTimers();
    };
  }, []);

  /*
   * Không lock body ngay frame click.
   *
   * Đợi drawer gần hoàn tất animation rồi mới thay đổi
   * overflow của body. Việc thay overflow có thể trigger
   * layout/reflow toàn page nên không để nó tranh frame
   * với transform animation.
   */
  useEffect(() => {
    if (
      !shellJobId
    ) {
      return;
    }

    let locked =
      false;

    const body =
      document.body;

    const oldOverflow =
      body.style.overflow;

    const oldPaddingRight =
      body.style.paddingRight;

    const lockTimer =
      setTimeout(
        () => {
          const scrollbarWidth =
            window.innerWidth -
            document
              .documentElement
              .clientWidth;

          body.style.overflow =
            "hidden";

          if (
            scrollbarWidth >
            0
          ) {
            body.style.paddingRight =
              `${scrollbarWidth}px`;
          }

          locked =
            true;
        },
        DRAWER_ENTER_DURATION
      );

    return () => {
      clearTimeout(
        lockTimer
      );

      if (
        locked
      ) {
        body.style.overflow =
          oldOverflow;

        body.style.paddingRight =
          oldPaddingRight;
      }
    };
  }, [
    shellJobId
  ]);

  useEffect(() => {
    if (
      !shellJobId
    ) {
      return;
    }

    function handleKeyDown(
      event: KeyboardEvent
    ) {
      if (
        event.key ===
        "Escape"
      ) {
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
    };
  }, [
    shellJobId,
    onClose
  ]);

  const job =
    query.data;

  const jobType =
    job &&
    job.jobType !==
      "UNKNOWN"
      ? t(
          `jobTypes.${job.jobType}`
        )
      : null;

  const seniority =
    job &&
    job.seniority !==
      "UNKNOWN"
      ? t(
          `seniority.${job.seniority}`
        )
      : null;

  const experience =
    job
      ? getJobExperience(
          job
        )
      : null;

  const experienceText =
    experience?.type ===
    "range"
      ? t(
          "experience.range",
          {
            min:
              experience.min,

            max:
              experience.max
          }
        )
      : experience?.type ===
          "minimum"
        ? t(
            "experience.minimum",
            {
              value:
                experience.value
            }
          )
        : experience?.type ===
            "maximum"
          ? t(
              "experience.maximum",
              {
                value:
                  experience.value
              }
            )
          : null;

  const locations =
    job?.locations
      .filter(Boolean)
      .join(" · ") ||
    null;

  const locationText =
    job?.locationText?.trim() &&
    job.locationText.trim() !==
      locations
      ? job.locationText.trim()
      : null;

  const structuredSalary =
    job
      ? getStructuredJobSalary(
          job,
          locale
        )
      : null;

  const salaryText =
    job?.salaryText?.trim() ||
    null;

  const postedAt =
    job
      ? formatJobDate(
          job.postedAt,
          locale
        )
      : null;

  const deadlineAt =
    job
      ? formatJobDate(
          job.deadlineAt,
          locale
        )
      : null;

  const detailUrl =
    job?.detailUrl?.trim() ||
    null;

  const applyUrl =
    job?.applyUrl?.trim() ||
    detailUrl;

  const hasLongContent =
    Boolean(
      job?.descriptionText?.trim() ||
      job?.requirementsText?.trim() ||
      job?.benefitsText?.trim()
    );

  /*
   * Root + aside luôn tồn tại.
   *
   * Khi đóng, aside chỉ nằm translate-x-full.
   * Browser không phải tạo compositing layer mới mỗi lần
   * user click View job.
   */
  return (
    <div
      aria-hidden={
        !shellJobId
      }
      className={`fixed inset-0 z-[200] overflow-hidden ${
        shellJobId &&
        isOpen
          ? "pointer-events-auto"
          : "pointer-events-none"
      }`}
    >
      <button
        type="button"
        disabled={
          !shellJobId
        }
        tabIndex={
          shellJobId
            ? 0
            : -1
        }
        aria-label={t(
          "detail.close"
        )}
        onClick={
          onClose
        }
        className={`absolute inset-0 bg-black/18 transition-opacity duration-150 ease-out motion-reduce:transition-none ${
          isOpen
            ? "opacity-100"
            : "opacity-0"
        }`}
      />

      <aside
        role="dialog"
        aria-modal={
          shellJobId
            ? "true"
            : undefined
        }
        aria-label={t(
          "detail.subtitle"
        )}
        className={`absolute inset-y-0 right-0 flex w-full max-w-[720px] transform-gpu flex-col border-l border-black/[0.055] bg-[#f7f7f4] transition-transform duration-[240ms] ease-[cubic-bezier(0.22,1,0.36,1)] [backface-visibility:hidden] [contain:layout_paint] will-change-transform motion-reduce:transition-none ${
          isOpen
            ? "translate-x-0"
            : "translate-x-full"
        }`}
      >
        {shellJobId ? (
          <>
            <header className="flex h-[76px] shrink-0 items-center justify-between border-b border-black/[0.055] bg-[#f7f7f4] px-5 sm:px-7">
              <div>
                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
                  {t(
                    "detail.eyebrow"
                  )}
                </p>

                <p className="mt-1 text-[11px] text-black/38">
                  {t(
                    "detail.subtitle"
                  )}
                </p>
              </div>

              <button
                type="button"
                onClick={
                  onClose
                }
                aria-label={t(
                  "detail.close"
                )}
                className="flex size-9 items-center justify-center rounded-full border border-black/[0.065] bg-white text-black/45 transition-colors duration-150 hover:text-black"
              >
                <CloseIcon />
              </button>
            </header>

            <div className="min-h-0 flex-1 overscroll-contain overflow-y-auto">
              {!queryJobId ||
              query.isLoading ? (
                <DrawerLoading />
              ) : null}

              {queryJobId &&
              query.isError ? (
                <div className="p-5 sm:p-7">
                  <div className="rounded-[18px] border border-red-950/10 bg-white p-5">
                    <p className="text-[12px] font-bold text-[#333]">
                      {t(
                        "detail.loadError"
                      )}
                    </p>

                    <p className="mt-2 text-[11px] leading-5 text-black/45">
                      {getApiErrorMessage(
                        query.error
                      )}
                    </p>

                    <button
                      type="button"
                      onClick={() =>
                        query.refetch()
                      }
                      className="mt-4 h-9 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white"
                    >
                      {t(
                        "tryAgain"
                      )}
                    </button>
                  </div>
                </div>
              ) : null}

              {job ? (
                <div className="px-5 py-7 sm:px-8 sm:py-9">
                  <div className="flex flex-wrap items-center gap-2">
                    {job.sourceCode?.trim() ? (
                      <span className="rounded-full bg-[#ecece7] px-2.5 py-1 font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/45">
                        {
                          job.sourceCode
                        }
                      </span>
                    ) : null}

                    {jobType ? (
                      <JobMetaPill>
                        {jobType}
                      </JobMetaPill>
                    ) : null}

                    {seniority ? (
                      <JobMetaPill>
                        {seniority}
                      </JobMetaPill>
                    ) : null}
                  </div>

                  <h1 className="mt-5 max-w-[620px] text-[30px] font-bold leading-[1.06] tracking-[-0.055em] text-[#20201e] sm:text-[38px]">
                    {job.title?.trim() ||
                      t(
                        "untitled"
                      )}
                  </h1>

                  {job.companyName?.trim() ? (
                    <p className="mt-3 text-[14px] font-semibold text-black/48">
                      {
                        job.companyName
                      }
                    </p>
                  ) : null}

                  <div className="mt-7 grid gap-3 sm:grid-cols-2">
                    <DetailItem
                      label={t(
                        "fields.locations"
                      )}
                      value={
                        locations
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.locationText"
                      )}
                      value={
                        locationText
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.jobType"
                      )}
                      value={
                        jobType
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.seniority"
                      )}
                      value={
                        seniority
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.experience"
                      )}
                      value={
                        experienceText
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.salary"
                      )}
                      value={
                        salaryText ||
                        structuredSalary ? (
                          <div className="space-y-1">
                            {salaryText ? (
                              <p>
                                {
                                  salaryText
                                }
                              </p>
                            ) : null}

                            {structuredSalary &&
                            structuredSalary !==
                              salaryText ? (
                              <p className="text-black/38">
                                {
                                  structuredSalary
                                }
                              </p>
                            ) : null}
                          </div>
                        ) : null
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.postedAt"
                      )}
                      value={
                        postedAt
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.deadlineAt"
                      )}
                      value={
                        deadlineAt
                      }
                    />

                    <DetailItem
                      label={t(
                        "fields.applyType"
                      )}
                      value={
                        job.applyType !==
                        "UNKNOWN"
                          ? t(
                              `applyTypes.${job.applyType}`
                            )
                          : null
                      }
                    />
                  </div>

                  {job.skills.filter(
                    Boolean
                  ).length >
                  0 ? (
                    <section className="mt-7">
                      <h3 className="text-[13px] font-bold tracking-[-0.025em] text-[#292927]">
                        {t(
                          "fields.skills"
                        )}
                      </h3>

                      <div className="mt-3 flex flex-wrap gap-2">
                        {job.skills
                          .filter(
                            Boolean
                          )
                          .map(
                            (
                              skill
                            ) => (
                              <span
                                key={
                                  skill
                                }
                                className="rounded-[9px] bg-[#ecece7] px-3 py-1.5 text-[10px] font-semibold text-[#565651]"
                              >
                                {
                                  skill
                                }
                              </span>
                            )
                          )}
                      </div>
                    </section>
                  ) : null}

                  <div className="mt-8 space-y-7">
                    <TextSection
                      title={t(
                        "fields.description"
                      )}
                      content={
                        job.descriptionText
                      }
                    />

                    <TextSection
                      title={t(
                        "fields.requirements"
                      )}
                      content={
                        job.requirementsText
                      }
                    />

                    <TextSection
                      title={t(
                        "fields.benefits"
                      )}
                      content={
                        job.benefitsText
                      }
                    />
                  </div>

                  {!hasLongContent ? (
                    <div className="mt-8 rounded-[16px] border border-black/[0.05] bg-white p-5 text-[11px] leading-5 text-black/42">
                      {t(
                        "detail.noLongContent"
                      )}
                    </div>
                  ) : null}

                  {detailUrl ? (
                    <div className="mt-8 border-t border-black/[0.055] pt-6">
                      <a
                        href={
                          detailUrl
                        }
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-2 text-[11px] font-semibold text-black/55 transition-colors hover:text-black"
                      >
                        {t(
                          "detail.viewSource"
                        )}

                        <ExternalLinkIcon />
                      </a>
                    </div>
                  ) : null}

                  <div className="h-24" />
                </div>
              ) : null}
            </div>

            {job ? (
              <footer className="shrink-0 border-t border-black/[0.055] bg-[#f7f7f4] p-4 sm:px-7 sm:py-5">
                <div className="flex items-center justify-between gap-4">
                  <div className="hidden min-w-0 sm:block">
                    <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
                      {t(
                        "detail.applicationSource"
                      )}
                    </p>

                    <p className="mt-1 truncate text-[10px] text-black/40">
                      {applyUrl
                        ? t(
                            "detail.applicationAvailable"
                          )
                        : t(
                            "detail.applicationUnavailable"
                          )}
                    </p>
                  </div>

                  {applyUrl ? (
                    <a
                      href={
                        applyUrl
                      }
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full bg-[#171717] px-5 text-[11px] font-semibold text-white transition-transform duration-150 hover:-translate-y-0.5 sm:w-auto"
                    >
                      {t(
                        "detail.apply"
                      )}

                      <ExternalLinkIcon />
                    </a>
                  ) : (
                    <button
                      type="button"
                      disabled
                      className="h-11 w-full cursor-not-allowed rounded-full bg-black/8 px-5 text-[11px] font-semibold text-black/30 sm:w-auto"
                    >
                      {t(
                        "detail.noApplyLink"
                      )}
                    </button>
                  )}
                </div>
              </footer>
            ) : null}
          </>
        ) : null}
      </aside>
    </div>
  );
}