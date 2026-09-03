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

type Props = {
  hasCurrentCv: boolean;

  isBusy: boolean;
  isUploading: boolean;
  isParsing: boolean;

  uploadProgress: number;

  error:
    | string
    | null;

  onSelectFile:
    (file: File) =>
      void;
};

export function CvUploadCard({
  hasCurrentCv,
  isBusy,
  isUploading,
  isParsing,
  uploadProgress,
  error,
  onSelectFile
}: Props) {
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
    <section className="rounded-[20px] border border-black/[0.055] bg-white p-5 shadow-[0_4px_18px_rgba(0,0,0,0.025)]">
      <h2 className="text-[14px] font-bold tracking-[-0.03em] text-[#30302e]">
        {t(
          "upload.title"
        )}
      </h2>

      <p className="mt-1 text-[10px] text-black/35">
        {t(
          "upload.description"
        )}
      </p>

      <input
        ref={inputRef}
        type="file"
        accept={CV_ACCEPT}
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
        className={`mt-4 rounded-[16px] border border-dashed px-4 py-7 text-center transition-colors ${
          dragActive
            ? "border-black/20 bg-[#f3f3ef]"
            : "border-black/[0.09] bg-[#fafaf7]"
        }`}
      >
        <div className="mx-auto flex size-10 items-center justify-center rounded-[13px] bg-white text-lg text-black/35 shadow-[0_3px_14px_rgba(0,0,0,0.035)]">
          ↑
        </div>

        <p className="mt-3 text-[11px] font-bold text-[#4a4a46]">
          {t(
            "upload.dropTitle"
          )}
        </p>

        <p className="mt-1 text-[9px] text-black/32">
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
          className="mt-4 h-9 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.07)] disabled:cursor-not-allowed disabled:opacity-40"
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
        <div className="mt-4">
          <div className="flex justify-between text-[9px] font-medium text-black/40">
            <span>
              {t(
                "upload.uploading"
              )}
            </span>

            <span>
              {t(
                "upload.progress",
                {
                  progress:
                    uploadProgress
                }
              )}
            </span>
          </div>

          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-black/[0.05]">
            <div
              className="h-full rounded-full bg-[#171717] transition-[width]"
              style={{
                width:
                  `${uploadProgress}%`
              }}
            />
          </div>
        </div>
      ) : null}

      {isParsing ? (
        <div className="mt-4 flex items-center gap-2 rounded-[12px] bg-[#f7f7f4] px-3 py-3 text-[10px] font-semibold text-black/45">
          <span className="size-3.5 animate-spin rounded-full border-2 border-black/10 border-t-black/55" />

          {t(
            "upload.analyzing"
          )}
        </div>
      ) : null}

      {error ? (
        <div className="mt-4 rounded-[12px] border border-red-950/10 bg-red-50/60 px-3 py-3 text-[10px] leading-5 text-red-700">
          {error}
        </div>
      ) : null}
    </section>
  );
}