export type ApiError = {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
};
