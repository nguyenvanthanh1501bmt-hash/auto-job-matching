export const LIVE_CRAWLER_SOURCES = [
  "ITVIEC",
  "JOBOKO",
  "TOPDEV",
  "VIECLAM24H"
] as const;

export type LiveCrawlerSourceCode =
  (typeof LIVE_CRAWLER_SOURCES)[number];

export type CrawlerSourceCode =
  | "MOCK"
  | LiveCrawlerSourceCode;

export type LiveCrawlerRunOptions = {
  limit?: number;
};

export type CrawlRunResponse = {
  sourceCode: CrawlerSourceCode;

  requestedLimit: number | null;

  insertedCount: number;

  totalRawJobs: number;
};