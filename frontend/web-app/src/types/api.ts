export type ApiErrorResponse = {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  details?: string[];
  retryAfterSeconds?: number;
};

export type ApiError = {
  status: number | null;
  error: string;
  message: string;
  path: string | null;
  details: string[];
  retryAfterSeconds: number | null;
  isNetworkError: boolean;
};