"use client";

import {useState} from "react";
import type {FormEvent} from "react";
import {useTranslations} from "next-intl";

import {useParseDetailFile, useParseListFile} from "@/hooks/use-admin-tools";
import {PARSER_SOURCE_CODES} from "@/types/admin-parser";
import type {ParserSourceCode} from "@/types/admin-parser";

import {
  ErrorMessage,
  Field,
  inputClassName,
  PageHeading,
  PrimaryButton,
  ResultBox,
  selectClassName
} from "./admin-ui";

export function ParserSection() {
  const t = useTranslations("admin.parser");

  const listParser = useParseListFile();
  const detailParser = useParseDetailFile();

  const [sourceCode, setSourceCode] = useState<ParserSourceCode>("MOCK");

  const [listFilePath, setListFilePath] = useState("");
  const [baseUrl, setBaseUrl] = useState("");

  const [detailFilePath, setDetailFilePath] = useState("");
  const [detailUrl, setDetailUrl] = useState("");
  const [listUrl, setListUrl] = useState("");

  function changeSource(value: string) {
    setSourceCode(value as ParserSourceCode);

    // Kết quả parse thuộc source trước đó không nên tiếp tục hiển thị
    // sau khi admin chuyển sang một parser source khác.
    listParser.reset();
    detailParser.reset();
  }

  function parseList(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const filePath = listFilePath.trim();
    const normalizedBaseUrl = baseUrl.trim();

    if (!filePath || !normalizedBaseUrl) {
      return;
    }

    listParser.mutate({
      sourceCode,
      request: {
        filePath,
        baseUrl: normalizedBaseUrl
      }
    });
  }

  function parseDetail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const filePath = detailFilePath.trim();
    const normalizedDetailUrl = detailUrl.trim();

    if (!filePath || !normalizedDetailUrl) {
      return;
    }

    detailParser.mutate({
      sourceCode,
      request: {
        filePath,
        detailUrl: normalizedDetailUrl,
        listUrl: listUrl.trim() || null
      }
    });
  }

  function useDetailUrl(url: string) {
    setDetailUrl(url);

    document
      .getElementById("parser-detail-step")
      ?.scrollIntoView({
        behavior: "smooth",
        block: "start"
      });
  }

  return (
    <div className="space-y-6">
      <PageHeading
        eyebrow={t("eyebrow")}
        title={t("title")}
        description={t("description")}
      />

      {/* Parser là công cụ kiểm tra pipeline, nên phần đầu cần giải thích
          rõ thứ tự xử lý thay vì đưa admin thẳng vào một nhóm input kỹ thuật. */}
      <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_10px_35px_rgba(0,0,0,0.022)]">
        <div className="grid lg:grid-cols-[minmax(0,1fr)_310px]">
          <div className="px-5 py-5 sm:px-6">
            <div className="flex items-start gap-4">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#f8ffe7]">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  className="size-[18px] text-[#697149]"
                  aria-hidden="true"
                >
                  <path
                    d="M5 7.5h4l2 3h8M5 16.5h4l2-3h8"
                    stroke="currentColor"
                    strokeWidth="1.7"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                  <circle
                    cx="4"
                    cy="7.5"
                    r="1.5"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  />
                  <circle
                    cx="20"
                    cy="10.5"
                    r="1.5"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  />
                  <circle
                    cx="4"
                    cy="16.5"
                    r="1.5"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  />
                  <circle
                    cx="20"
                    cy="13.5"
                    r="1.5"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  />
                </svg>
              </div>

              <div className="min-w-0">
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
                    {t("flow.label")}
                  </span>

                  <span className="h-px w-8 bg-black/[0.08]" />
                </div>

                <h2 className="mt-2.5 text-[18px] font-semibold tracking-[-0.03em] text-[#292927]">
                  {t("flow.title")}
                </h2>

                <p className="mt-1.5 max-w-[680px] text-[12px] leading-5 text-[#8f8f89]">
                  {t("flow.description")}
                </p>

                <div className="mt-5 grid max-w-[680px] grid-cols-[1fr_auto_1fr] items-center gap-3">
                  <div className="rounded-[13px] border border-black/[0.05] bg-[#fafaf8] px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <span className="font-mono text-[9px] font-semibold text-[#aaa]">
                        01
                      </span>

                      <span className="text-[12px] font-semibold text-[#555]">
                        {t("flow.list")}
                      </span>
                    </div>

                    <p className="mt-1.5 text-[10px] leading-[16px] text-[#999]">
                      {t("flow.listDescription")}
                    </p>
                  </div>

                  <span className="text-[15px] text-black/20">
                    →
                  </span>

                  <div className="rounded-[13px] border border-black/[0.055] bg-[#f8ffe7] px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <span className="font-mono text-[9px] font-semibold text-[#929c65]">
                        02
                      </span>

                      <span className="text-[12px] font-semibold text-[#5f6546]">
                        {t("flow.detail")}
                      </span>
                    </div>

                    <p className="mt-1.5 text-[10px] leading-[16px] text-[#919a70]">
                      {t("flow.detailDescription")}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="border-t border-black/[0.045] bg-[#fafaf8]/70 px-5 py-5 lg:border-t-0 lg:border-l">
            <p className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#aaa]">
              {t("source.label")}
            </p>

            <p className="mt-2 text-[11px] leading-[18px] text-[#999]">
              {t("source.description")}
            </p>

            <div className="mt-4">
              <select
                value={sourceCode}
                onChange={(event) => changeSource(event.target.value)}
                className={selectClassName}
              >
                {PARSER_SOURCE_CODES.map((source) => (
                  <option
                    key={source}
                    value={source}
                  >
                    {source}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
        <div className="border-b border-black/[0.045] px-5 py-5 sm:px-6">
          <div className="flex items-start gap-4">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] font-mono text-[11px] font-semibold text-[#777]">
              01
            </div>

            <div>
              <p className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#aaa]">
                {t("list.label")}
              </p>

              <h2 className="mt-2 text-[19px] font-semibold tracking-[-0.035em] text-[#252523]">
                {t("list.title")}
              </h2>

              <p className="mt-1.5 max-w-[700px] text-[12px] leading-5 text-[#92928c]">
                {t("list.description")}
              </p>
            </div>
          </div>
        </div>

        <div className="grid xl:grid-cols-[minmax(0,1fr)_340px]">
          <form
            onSubmit={parseList}
            className="space-y-5 px-5 py-5 sm:px-6"
          >
            <Field
              label={t("list.filePath")}
              hint={t("common.filePathHint")}
            >
              <input
                value={listFilePath}
                onChange={(event) => setListFilePath(event.target.value)}
                placeholder={t("list.filePlaceholder")}
                className={inputClassName}
              />
            </Field>

            <Field
              label={t("list.baseUrl")}
              hint={t("list.baseUrlHint")}
            >
              <input
                value={baseUrl}
                onChange={(event) => setBaseUrl(event.target.value)}
                placeholder={t("list.baseUrlPlaceholder")}
                className={inputClassName}
              />
            </Field>

            <div className="flex items-center gap-4 pt-1">
              <PrimaryButton
                type="submit"
                disabled={
                  !listFilePath.trim() ||
                  !baseUrl.trim() ||
                  listParser.isPending
                }
              >
                {listParser.isPending
                  ? t("list.parsing")
                  : t("list.submit")}
              </PrimaryButton>

              <p className="text-[10px] leading-[16px] text-[#aaa]">
                {t("list.actionHint", {source: sourceCode})}
              </p>
            </div>

            {listParser.isError ? (
              <ErrorMessage error={listParser.error} />
            ) : null}
          </form>

          <aside className="border-t border-black/[0.045] bg-[#fafaf8]/60 p-5 xl:border-t-0 xl:border-l">
            <div className="flex items-start gap-3">
              <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.05] bg-white">
                <svg
                  viewBox="0 0 20 20"
                  fill="none"
                  className="size-4 text-[#888]"
                  aria-hidden="true"
                >
                  <path
                    d="M10 5.5v5M10 14.5h.01"
                    stroke="currentColor"
                    strokeWidth="1.7"
                    strokeLinecap="round"
                  />
                  <circle
                    cx="10"
                    cy="10"
                    r="7"
                    stroke="currentColor"
                    strokeWidth="1.4"
                  />
                </svg>
              </div>

              <div>
                <p className="text-[11px] font-semibold text-[#666]">
                  {t("common.filesystemTitle")}
                </p>

                <p className="mt-1.5 text-[10px] leading-[17px] text-[#999]">
                  {t("common.filesystemDescription")}
                </p>
              </div>
            </div>

            <div className="mt-5 rounded-[13px] border border-black/[0.045] bg-white px-4 py-3">
              <p className="font-mono text-[8px] uppercase tracking-[0.12em] text-[#aaa]">
                {t("common.currentSource")}
              </p>

              <div className="mt-2 flex items-center gap-2">
                <span className="size-1.5 rounded-full bg-[#d9ff75]" />

                <span className="font-mono text-[11px] font-semibold text-[#555]">
                  {sourceCode}
                </span>
              </div>
            </div>
          </aside>
        </div>

        {listParser.data ? (
          <div className="border-t border-black/[0.045] bg-[#fcfcfa] px-5 py-5 sm:px-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#929c65]">
                  {t("list.resultLabel")}
                </p>

                <h3 className="mt-1.5 text-[16px] font-semibold tracking-[-0.025em] text-[#3d3d39]">
                  {t("list.resultTitle")}
                </h3>
              </div>

              <div className="flex items-baseline gap-2 rounded-full border border-black/[0.05] bg-white px-4 py-2">
                <span className="text-[17px] font-semibold tracking-[-0.03em] text-[#333]">
                  {listParser.data.detailUrlCount}
                </span>

                <span className="text-[10px] font-medium text-[#999]">
                  {t("list.urlsFound")}
                </span>
              </div>
            </div>

            {listParser.data.detailUrls.length > 0 ? (
              <div className="mt-4 overflow-hidden rounded-[14px] border border-black/[0.05] bg-white">
                <div className="flex items-center justify-between border-b border-black/[0.045] px-4 py-3">
                  <p className="text-[11px] font-semibold text-[#666]">
                    {t("list.detailUrls")}
                  </p>

                  <p className="text-[9px] text-[#aaa]">
                    {t("list.chooseHint")}
                  </p>
                </div>

                <div className="max-h-[320px] divide-y divide-black/[0.045] overflow-y-auto">
                  {listParser.data.detailUrls.map((url, index) => (
                    <div
                      key={`${url}-${index}`}
                      className="flex items-center gap-4 px-4 py-3 transition hover:bg-[#fafaf8]"
                    >
                      <span className="w-7 shrink-0 font-mono text-[9px] text-[#bbb]">
                        {String(index + 1).padStart(2, "0")}
                      </span>

                      <p className="min-w-0 flex-1 truncate font-mono text-[10px] text-[#686863]">
                        {url}
                      </p>

                      <button
                        type="button"
                        onClick={() => useDetailUrl(url)}
                        className="shrink-0 rounded-full border border-black/[0.06] bg-white px-3 py-1.5 text-[9px] font-semibold text-[#666] shadow-[0_2px_8px_rgba(0,0,0,0.025)] transition hover:border-black/[0.12] hover:text-[#222]"
                      >
                        {t("list.useUrl")}
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="mt-4 rounded-[14px] border border-dashed border-black/[0.08] bg-white px-4 py-5 text-center text-[11px] text-[#999]">
                {t("list.noUrls")}
              </div>
            )}

            <details className="group mt-4 overflow-hidden rounded-[13px] border border-black/[0.05] bg-white">
              <summary className="flex cursor-pointer list-none items-center justify-between px-4 py-3 text-[10px] font-semibold text-[#85857f]">
                {t("common.technicalDetails")}

                <svg
                  viewBox="0 0 20 20"
                  fill="none"
                  className="size-4 transition-transform group-open:rotate-180"
                  aria-hidden="true"
                >
                  <path
                    d="m6 8 4 4 4-4"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </summary>

              <div className="border-t border-black/[0.045] p-3">
                <ResultBox
                  title={t("list.resultTitle")}
                  value={listParser.data}
                />
              </div>
            </details>
          </div>
        ) : null}
      </section>

      <section
        id="parser-detail-step"
        className="scroll-mt-24 overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]"
      >
        <div className="border-b border-black/[0.045] px-5 py-5 sm:px-6">
          <div className="flex items-start gap-4">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#f8ffe7] font-mono text-[11px] font-semibold text-[#697149]">
              02
            </div>

            <div>
              <p className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#929c65]">
                {t("detail.label")}
              </p>

              <h2 className="mt-2 text-[19px] font-semibold tracking-[-0.035em] text-[#252523]">
                {t("detail.title")}
              </h2>

              <p className="mt-1.5 max-w-[700px] text-[12px] leading-5 text-[#92928c]">
                {t("detail.description")}
              </p>
            </div>
          </div>
        </div>

        <div className="grid xl:grid-cols-[minmax(0,1fr)_340px]">
          <form
            onSubmit={parseDetail}
            className="space-y-5 px-5 py-5 sm:px-6"
          >
            <Field
              label={t("detail.filePath")}
              hint={t("common.filePathHint")}
            >
              <input
                value={detailFilePath}
                onChange={(event) => setDetailFilePath(event.target.value)}
                placeholder={t("detail.filePlaceholder")}
                className={inputClassName}
              />
            </Field>

            <Field
              label={t("detail.detailUrl")}
              hint={t("detail.detailUrlHint")}
            >
              <input
                value={detailUrl}
                onChange={(event) => setDetailUrl(event.target.value)}
                placeholder={t("detail.detailUrlPlaceholder")}
                className={inputClassName}
              />
            </Field>

            <Field
              label={t("detail.listUrl")}
              hint={t("detail.listUrlHint")}
            >
              <input
                value={listUrl}
                onChange={(event) => setListUrl(event.target.value)}
                placeholder={t("detail.listUrlPlaceholder")}
                className={inputClassName}
              />
            </Field>

            <div className="flex items-center gap-4 pt-1">
              <PrimaryButton
                type="submit"
                disabled={
                  !detailFilePath.trim() ||
                  !detailUrl.trim() ||
                  detailParser.isPending
                }
              >
                {detailParser.isPending
                  ? t("detail.parsing")
                  : t("detail.submit")}
              </PrimaryButton>

              <p className="text-[10px] leading-[16px] text-[#aaa]">
                {t("detail.actionHint")}
              </p>
            </div>

            {detailParser.isError ? (
              <ErrorMessage error={detailParser.error} />
            ) : null}
          </form>

          <aside className="border-t border-black/[0.045] bg-[#fafaf8]/60 p-5 xl:border-t-0 xl:border-l">
            <p className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#aaa]">
              {t("detail.aboutLabel")}
            </p>

            <h3 className="mt-2.5 text-[14px] font-semibold tracking-[-0.02em] text-[#555]">
              {t("detail.aboutTitle")}
            </h3>

            <p className="mt-1.5 text-[10px] leading-[17px] text-[#999]">
              {t("detail.aboutDescription")}
            </p>

            <div className="mt-5 space-y-2.5">
              <div className="flex items-center gap-3 rounded-[12px] border border-black/[0.045] bg-white px-3.5 py-3">
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-[#fafaf8] font-mono text-[8px] text-[#999]">
                  1
                </span>

                <span className="text-[10px] font-medium text-[#777]">
                  {t("detail.steps.read")}
                </span>
              </div>

              <div className="flex items-center gap-3 rounded-[12px] border border-black/[0.045] bg-white px-3.5 py-3">
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-[#fafaf8] font-mono text-[8px] text-[#999]">
                  2
                </span>

                <span className="text-[10px] font-medium text-[#777]">
                  {t("detail.steps.parse")}
                </span>
              </div>

              <div className="flex items-center gap-3 rounded-[12px] border border-black/[0.045] bg-[#f8ffe7] px-3.5 py-3">
                <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-white font-mono text-[8px] text-[#879159]">
                  3
                </span>

                <span className="text-[10px] font-semibold text-[#697149]">
                  {t("detail.steps.rawJob")}
                </span>
              </div>
            </div>
          </aside>
        </div>

        {detailParser.data ? (
          <div className="border-t border-black/[0.045] bg-[#fcfcfa] px-5 py-5 sm:px-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

                  <p className="font-mono text-[9px] font-medium uppercase tracking-[0.14em] text-[#929c65]">
                    {t("detail.resultLabel")}
                  </p>
                </div>

                <h3 className="mt-2 text-[17px] font-semibold tracking-[-0.03em] text-[#353532]">
                  {detailParser.data.title || t("detail.untitled")}
                </h3>

                <p className="mt-1 text-[11px] text-[#8f8f89]">
                  {detailParser.data.companyName || t("detail.unknownCompany")}
                </p>
              </div>

              <span className="rounded-full border border-black/[0.05] bg-white px-3 py-1.5 font-mono text-[9px] font-semibold text-[#777]">
                {detailParser.data.sourceCode || sourceCode}
              </span>
            </div>

            <div className="mt-5 grid gap-px overflow-hidden rounded-[14px] border border-black/[0.05] bg-black/[0.05] sm:grid-cols-2 lg:grid-cols-4">
              <div className="bg-white px-4 py-3.5">
                <p className="font-mono text-[8px] uppercase tracking-[0.12em] text-[#aaa]">
                  {t("detail.metrics.rawJobId")}
                </p>

                <p className="mt-2 truncate font-mono text-[10px] font-medium text-[#666]">
                  {detailParser.data.id}
                </p>
              </div>

              <div className="bg-white px-4 py-3.5">
                <p className="font-mono text-[8px] uppercase tracking-[0.12em] text-[#aaa]">
                  {t("detail.metrics.location")}
                </p>

                <p className="mt-2 truncate text-[10px] font-medium text-[#666]">
                  {detailParser.data.locationText || "—"}
                </p>
              </div>

              <div className="bg-white px-4 py-3.5">
                <p className="font-mono text-[8px] uppercase tracking-[0.12em] text-[#aaa]">
                  {t("detail.metrics.html")}
                </p>

                <p className="mt-2 text-[10px] font-semibold text-[#666]">
                  {detailParser.data.rawHtmlStored
                    ? t("common.yes")
                    : t("common.no")}
                </p>
              </div>

              <div className="bg-white px-4 py-3.5">
                <p className="font-mono text-[8px] uppercase tracking-[0.12em] text-[#aaa]">
                  {t("detail.metrics.text")}
                </p>

                <p className="mt-2 text-[10px] font-semibold text-[#666]">
                  {detailParser.data.rawTextStored
                    ? t("common.yes")
                    : t("common.no")}
                </p>
              </div>
            </div>

            <details className="group mt-4 overflow-hidden rounded-[13px] border border-black/[0.05] bg-white">
              <summary className="flex cursor-pointer list-none items-center justify-between px-4 py-3 text-[10px] font-semibold text-[#85857f]">
                {t("common.technicalDetails")}

                <svg
                  viewBox="0 0 20 20"
                  fill="none"
                  className="size-4 transition-transform group-open:rotate-180"
                  aria-hidden="true"
                >
                  <path
                    d="m6 8 4 4 4-4"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </summary>

              <div className="border-t border-black/[0.045] p-3">
                <ResultBox
                  title={t("detail.resultTitle")}
                  value={detailParser.data}
                />
              </div>
            </details>
          </div>
        ) : null}
      </section>
    </div>
  );
}