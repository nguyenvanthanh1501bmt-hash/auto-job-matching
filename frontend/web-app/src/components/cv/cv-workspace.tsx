"use client";

import {
  useEffect,
  useState
} from "react";

import {
  useTranslations
} from "next-intl";

import {
  CandidateProfileView
} from "@/components/cv/candidate-profile-view";

import {
  CurrentCvCard
} from "@/components/cv/current-cv-card";

import {
  CvUploadCard
} from "@/components/cv/cv-upload-card";

import {
  WorkspacePageHeader
} from "@/components/user/workspace-page-header";

import {
  useCandidateProfile,
  useParseCv,
  useRawCv,
  useUploadCv
} from "@/hooks/use-cv";

import {
  getApiErrorMessage
} from "@/lib/api-error";

import {
  getAuthSession
} from "@/lib/auth-storage";

import {
  clearUserCvContext,
  getUserCvContext,
  setUserCvContext
} from "@/lib/user-cv-context";

import {
  CV_ALLOWED_EXTENSIONS,
  CV_MAX_FILE_SIZE_BYTES
} from "@/types/cv";

function EmptyProfile() {
  const t =
    useTranslations(
      "user.cv"
    );

  return (
    <div className="flex min-h-[520px] flex-col items-center justify-center rounded-[22px] border border-dashed border-black/[0.08] bg-white/45 px-7 text-center">
      <div className="flex size-12 items-center justify-center rounded-[15px] bg-white text-xl shadow-[0_5px_22px_rgba(0,0,0,0.035)]">
        ↗
      </div>

      <p className="mt-5 font-mono text-[8px] font-semibold uppercase tracking-[0.15em] text-black/28">
        {t(
          "empty.eyebrow"
        )}
      </p>

      <h2 className="mt-2 max-w-[420px] text-[22px] font-bold tracking-[-0.045em] text-[#292927]">
        {t(
          "empty.title"
        )}
      </h2>

      <p className="mt-3 max-w-[500px] text-[11px] leading-5 text-black/42">
        {t(
          "empty.description"
        )}
      </p>
    </div>
  );
}

export function CvWorkspace() {
  const t =
    useTranslations(
      "user.cv"
    );

  const [
    userId,
    setUserId
  ] =
    useState<
      string | null
    >(null);

  const [
    rawCvId,
    setRawCvId
  ] =
    useState<
      string | null
    >(null);

  const [
    candidateProfileId,
    setCandidateProfileId
  ] =
    useState<
      string | null
    >(null);

  const [
    uploadProgress,
    setUploadProgress
  ] =
    useState(0);

  const [
    clientError,
    setClientError
  ] =
    useState<
      string | null
    >(null);

  const uploadMutation =
    useUploadCv();

  const parseMutation =
    useParseCv();

  const rawCvQuery =
    useRawCv(
      rawCvId
    );

  const profileQuery =
    useCandidateProfile(
      rawCvQuery.data
        ?.status ===
        "PARSED"
        ? rawCvId
        : null
    );

  useEffect(() => {
    const session =
      getAuthSession();

    const nextUserId =
      session
        ?.user.id ??
      null;

    setUserId(
      nextUserId
    );

    if (
      !nextUserId
    ) {
      return;
    }

    const context =
      getUserCvContext(
        nextUserId
      );

    setRawCvId(
      context
        ?.rawCvId ??
        null
    );

    setCandidateProfileId(
      context
        ?.candidateProfileId ??
        null
    );
  }, []);

  const profile =
    parseMutation.data ??
    profileQuery.data ??
    null;

  const isUploading =
    uploadMutation.isPending;

  const isParsing =
    parseMutation.isPending;

  const isBusy =
    isUploading ||
    isParsing;

  const currentCv =
    rawCvQuery.data;

  const currentStatus =
    isParsing
      ? "PARSING" as const
      : currentCv
          ?.status ??
        null;

  const mutationError =
    uploadMutation.isError
      ? getApiErrorMessage(
          uploadMutation.error
        )
      : parseMutation.isError
        ? getApiErrorMessage(
            parseMutation.error
          )
        : null;

  function validateFile(
    file: File
  ): string | null {
    const extension =
      file.name
        .split(".")
        .pop()
        ?.toLowerCase();

    if (
      !extension ||
      !CV_ALLOWED_EXTENSIONS.includes(
        extension as (
          typeof CV_ALLOWED_EXTENSIONS
        )[number]
      )
    ) {
      return t(
        "errors.invalidType"
      );
    }

    if (
      file.size >
      CV_MAX_FILE_SIZE_BYTES
    ) {
      return t(
        "errors.tooLarge"
      );
    }

    return null;
  }

  function saveContext(
    nextRawCvId:
      string,

    nextCandidateProfileId:
      string | null
  ) {
    setRawCvId(
      nextRawCvId
    );

    setCandidateProfileId(
      nextCandidateProfileId
    );

    const ownerUserId =
      userId ??
      getAuthSession()
        ?.user.id ??
      null;

    if (
      ownerUserId
    ) {
      setUserCvContext(
        ownerUserId,
        {
          rawCvId:
            nextRawCvId,

          candidateProfileId:
            nextCandidateProfileId
        }
      );
    }
  }

  async function parseCv(
    nextRawCvId:
      string
  ) {
    try {
      const parsed =
        await parseMutation.mutateAsync(
          nextRawCvId
        );

      saveContext(
        nextRawCvId,
        parsed.candidateProfileId
      );
    } catch {
      /*
       * CV upload vẫn được giữ.
       * User có thể bấm parse lại.
       */
    }
  }

  async function processFile(
    file: File
  ) {
    const validationError =
      validateFile(
        file
      );

    setClientError(
      validationError
    );

    if (
      validationError
    ) {
      return;
    }

    uploadMutation.reset();
    parseMutation.reset();

    setUploadProgress(
      0
    );

    try {
      const uploaded =
        await uploadMutation.mutateAsync(
          {
            file,

            onProgress:
              setUploadProgress
          }
        );

      saveContext(
        uploaded.id,
        null
      );

      setUploadProgress(
        100
      );

      await parseCv(
        uploaded.id
      );
    } catch {
      /*
       * Mutation error được
       * render trong upload card.
       */
    }
  }

  function clearCurrentCv() {
    const ownerUserId =
      userId ??
      getAuthSession()
        ?.user.id ??
      null;

    if (
      ownerUserId
    ) {
      clearUserCvContext(
        ownerUserId
      );
    }

    setRawCvId(
      null
    );

    setCandidateProfileId(
      null
    );

    setUploadProgress(
      0
    );

    setClientError(
      null
    );

    uploadMutation.reset();
    parseMutation.reset();
  }

  const canRetryParse =
    Boolean(
      currentCv &&
        !isParsing &&
        (
          currentCv.status ===
            "FAILED" ||
          parseMutation.isError
        )
    );

  return (
    <>
      <WorkspacePageHeader
        eyebrow={t(
          "eyebrow"
        )}
        title={t(
          "title"
        )}
        description={t(
          "description"
        )}
      />

      <div className="mt-7 grid gap-6 xl:grid-cols-[330px_minmax(0,1fr)]">
        <aside className="min-w-0">
          <div className="space-y-4 xl:sticky xl:top-[110px]">
            <CvUploadCard
              hasCurrentCv={
                Boolean(
                  rawCvId
                )
              }
              isBusy={
                isBusy
              }
              isUploading={
                isUploading
              }
              isParsing={
                isParsing
              }
              uploadProgress={
                uploadProgress
              }
              error={
                clientError ||
                mutationError
              }
              onSelectFile={(
                file
              ) => {
                void processFile(
                  file
                );
              }}
            />

            {rawCvId ? (
              <CurrentCvCard
                cv={
                  currentCv
                }
                isLoading={
                  rawCvQuery.isLoading
                }
                isError={
                  rawCvQuery.isError
                }
                error={
                  rawCvQuery.error
                }
                status={
                  currentStatus
                }
                isBusy={
                  isBusy
                }
                canRetryParse={
                  canRetryParse
                }
                onRetryParse={() => {
                  if (
                    currentCv
                  ) {
                    void parseCv(
                      currentCv.id
                    );
                  }
                }}
                onClear={
                  clearCurrentCv
                }
              />
            ) : null}
          </div>
        </aside>

        <section className="min-w-0">
          {profile ? (
            <CandidateProfileView
              profile={
                profile
              }
            />
          ) : profileQuery.isLoading ||
            isParsing ? (
            <div className="flex min-h-[520px] items-center justify-center rounded-[22px] border border-black/[0.05] bg-white/50">
              <div className="flex items-center gap-3 text-[11px] font-medium text-black/40">
                <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/55" />

                {t(
                  "upload.analyzing"
                )}
              </div>
            </div>
          ) : profileQuery.isError ? (
            <div className="rounded-[20px] border border-black/[0.055] bg-white p-6">
              <h2 className="text-[15px] font-bold tracking-[-0.03em] text-[#333]">
                {t(
                  "errors.profileFailed"
                )}
              </h2>

              <p className="mt-2 text-[11px] leading-5 text-black/45">
                {getApiErrorMessage(
                  profileQuery.error
                )}
              </p>

              <button
                type="button"
                onClick={() =>
                  profileQuery.refetch()
                }
                className="mt-4 h-9 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white"
              >
                {t(
                  "upload.retryParse"
                )}
              </button>
            </div>
          ) : (
            <EmptyProfile />
          )}
        </section>
      </div>
    </>
  );
}