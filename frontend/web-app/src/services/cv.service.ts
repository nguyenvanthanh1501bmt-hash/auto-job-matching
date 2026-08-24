import {apiClient} from "@/lib/api-client";

import type {
  CandidateProfileResponse,
  RawCvResponse
} from "@/types/cv";

const CV_BASE_PATH = "/api/cvs";

export type CvUploadProgressHandler = (
  percentage: number
) => void;

async function upload(
  file: File,
  onProgress?: CvUploadProgressHandler
): Promise<RawCvResponse> {
  // Browser gửi file qua multipart; file người dùng chọn không phải filesystem path.
  const formData = new FormData();

  /**
   * QUAN TRỌNG:
   *
   * Backend dùng:
   *
   * @RequestPart("file")
   *
   * nên key bắt buộc phải là "file".
   */
  formData.append("file", file);

  const response =
    await apiClient.post<RawCvResponse>(
      CV_BASE_PATH,
      formData,
      {
        /**
         * Không tự set:
         *
         * Content-Type: multipart/form-data
         *
         * Axios/browser sẽ tự tạo boundary đúng.
         */
        onUploadProgress: (event) => {
          // Một số adapter không cung cấp total nên không thể tính phần trăm tin cậy.
          if (
            !onProgress ||
            !event.total ||
            event.total <= 0
          ) {
            return;
          }

          const percentage = Math.min(
            100,
            Math.round(
              (event.loaded / event.total) *
                100
            )
          );

          onProgress(percentage);
        }
      }
    );

  return response.data;
}

async function getById(
  rawCvId: string
): Promise<RawCvResponse> {
  const response =
    await apiClient.get<RawCvResponse>(
      `${CV_BASE_PATH}/${encodeURIComponent(
        rawCvId
      )}`
    );

  return response.data;
}

async function parse(
  rawCvId: string
): Promise<CandidateProfileResponse> {
  // Parse là request riêng sau upload, định danh bằng rawCvId backend đã trả về.
  const response =
    await apiClient.post<CandidateProfileResponse>(
      `${CV_BASE_PATH}/${encodeURIComponent(
        rawCvId
      )}/parse`
    );

  return response.data;
}

async function getProfile(
  rawCvId: string
): Promise<CandidateProfileResponse> {
  const response =
    await apiClient.get<CandidateProfileResponse>(
      `${CV_BASE_PATH}/${encodeURIComponent(
        rawCvId
      )}/profile`
    );

  return response.data;
}

export const cvService = {
  upload,
  getById,
  parse,
  getProfile
};