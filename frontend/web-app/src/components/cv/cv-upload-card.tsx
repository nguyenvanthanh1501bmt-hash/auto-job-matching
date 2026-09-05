"use client";

import {
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent
} from "react";

import {
  useTranslations
} from "next-intl";

import {
  CV_ACCEPT
} from "@/types/cv";

import type {
  CvUploadCardProps
} from "@/types/cv-ui";

function UploadIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-5"
      aria-hidden="true"
    >
      <path
        d="M12 15V4m0 0L7.75 8.25M12 4l4.25 4.25"
        stroke="currentColor"
        strokeWidth="1.65"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M5 14.75v2.5A2.75 2.75 0 0 0 7.75 20h8.5A2.75 2.75 0 0 0 19 17.25v-2.5"
        stroke="currentColor"
        strokeWidth="1.65"
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
        d="M10 2.5c.55 3.65 2.35 5.45 6 6-3.65.55-5.45 2.35-6 6-.55-3.65-2.35-5.45-6-6 3.65-.55 5.45-2.35 6-6Z"
        stroke="currentColor"
        strokeWidth="1.35"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function CvUploadCard({
  hasCurrentCv,
  isBusy,
  isUploading,
  isParsing,
  uploadProgress,
  error,
  onSelectFile
}: CvUploadCardProps) {
  const t =
    useTranslations(
      "user.cv"
    );

  const inputRef =
    useRef<HTMLInputElement>(
      null
    );

  const [
    dragActive,
    setDragActive
  ] =
    useState(false);

  function handleInput(
    event:
      ChangeEvent<HTMLInputElement>
  ) {
    const file =
      event.target
        .files?.[0];

    if (file) {
      onSelectFile(
        file
      );
    }

    event.target.value =
      "";
  }

  function handleDrop(
    event:
      DragEvent<HTMLDivElement>
  ) {
    event.preventDefault();

    setDragActive(
      false
    );

    const file =
      event.dataTransfer
        .files?.[0];

    if (file) {
      onSelectFile(
        file
      );
    }
  }

  return (
    <section className="relative overflow-hidden rounded-[22px] border border-black/[0.055] bg-white p-5 shadow-[0_6px_24px_rgba(0,0,0,0.03)] sm:p-6">
      <div className="pointer-events-none absolute -right-16 -top-16 size-44 rounded-full bg-[#d9ff75]/35 blur-3xl" />

      <div className="pointer-events-none absolute -bottom-20 -left-16 size-40 rounded-full border border-black/[0.025]" />

      <div className="relative">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-[#93ad39]">
                <SparkIcon />
              </span>

              <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.15em] text-black/30">
                {t(
                  "profile.eyebrow"
                )}
              </span>
            </div>

            <h2 className="mt-3 text-[20px] font-bold tracking-[-0.045em] text-[#20201e]">
              {t(
                "upload.title"
              )}
            </h2>

            <p className="mt-1.5 max-w-[260px] text-[10px] leading-5 text-black/38">
              {t(
                "upload.description"
              )}
            </p>
          </div>

          <div className="flex size-10 shrink-0 items-center justify-center rounded-[13px] border border-black/[0.05] bg-[#f5f5f1] text-black/42">
            <UploadIcon />
          </div>
        </div>

        <input
          ref={
            inputRef
          }
          type="file"
          accept={
            CV_ACCEPT
          }
          className="hidden"
          onChange={
            handleInput
          }
        />

        <div
          onDragEnter={(
            event
          ) => {
            event.preventDefault();

            setDragActive(
              true
            );
          }}
          onDragOver={(
            event
          ) => {
            event.preventDefault();
          }}
          onDragLeave={(
            event
          ) => {
            event.preventDefault();

            setDragActive(
              false
            );
          }}
          onDrop={
            handleDrop
          }
          className={`relative mt-5 overflow-hidden rounded-[18px] border border-dashed px-4 py-7 text-center transition-all duration-200 ${
            dragActive
              ? "border-[#b7d752] bg-[#f5ffd8]"
              : "border-black/[0.09] bg-[#fafaf7] hover:border-[#b4ce61] hover:bg-[#fbfff0]"
          }`}
        >
          <div className="pointer-events-none absolute left-1/2 top-0 h-px w-28 -translate-x-1/2 bg-gradient-to-r from-transparent via-[#b7d752]/70 to-transparent" />

          <div className="mx-auto flex size-12 items-center justify-center rounded-[15px] border border-[#cfe47d] bg-[#efffc3] text-[#60721f] shadow-[0_4px_14px_rgba(137,164,48,0.08)]">
            <UploadIcon />
          </div>

          <p className="mt-4 text-[12px] font-bold tracking-[-0.025em] text-[#292927]">
            {t(
              "upload.dropTitle"
            )}
          </p>

          <p className="mt-1.5 text-[9px] leading-4 text-black/32">
            {t(
              "upload.dropDescription"
            )}
          </p>

          <button
            type="button"
            disabled={
              isBusy
            }
            onClick={() =>
              inputRef
                .current
                ?.click()
            }
            className="mt-5 inline-flex h-10 items-center justify-center rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)] transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-[0_7px_18px_rgba(0,0,0,0.11)] disabled:cursor-not-allowed disabled:opacity-40"
          >
            {hasCurrentCv
              ? t(
                  "upload.replace"
                )
              : t(
                  "upload.choose"
                )}
          </button>
        </div>

        {isUploading ? (
          <div className="mt-4 rounded-[14px] border border-black/[0.05] bg-[#fafaf7] p-3.5">
            <div className="flex justify-between gap-3 text-[9px] font-medium text-black/42">
              <span>
                {t(
                  "upload.uploading"
                )}
              </span>

              <span className="font-mono font-semibold text-[#718323]">
                {t(
                  "upload.progress",
                  {
                    progress:
                      uploadProgress
                  }
                )}
              </span>
            </div>

            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-black/[0.055]">
              <div
                className="h-full rounded-full bg-[#b5d354] transition-[width] duration-300"
                style={{
                  width:
                    `${uploadProgress}%`
                }}
              />
            </div>
          </div>
        ) : null}

        {isParsing ? (
          <div className="mt-4 flex items-center gap-3 rounded-[14px] border border-[#dce9aa] bg-[#f7fbdc] px-3.5 py-3 text-[10px] font-semibold text-[#596725]">
            <span className="size-3.5 animate-spin rounded-full border-2 border-[#a8c747]/20 border-t-[#8da72e]" />

            {t(
              "upload.analyzing"
            )}
          </div>
        ) : null}

        {error ? (
          <div className="mt-4 rounded-[14px] border border-red-100 bg-red-50 px-3.5 py-3 text-[10px] leading-5 text-red-700">
            {error}
          </div>
        ) : null}
      </div>
    </section>
  );
}