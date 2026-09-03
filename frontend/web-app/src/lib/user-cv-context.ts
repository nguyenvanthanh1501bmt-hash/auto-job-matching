export type UserCvContext = {
  rawCvId: string;
  candidateProfileId: string | null;
  updatedAt: string;
};

const STORAGE_PREFIX =
  "autojob.user.cv-context";

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

function getStorageKey(
  userId: string
): string {
  return `${STORAGE_PREFIX}:${userId.trim()}`;
}

function isUserCvContext(
  value: unknown
): value is UserCvContext {
  if (
    typeof value !== "object" ||
    value === null
  ) {
    return false;
  }

  const candidate =
    value as Partial<UserCvContext>;

  return (
    typeof candidate.rawCvId === "string" &&
    candidate.rawCvId.trim().length > 0 &&
    (
      candidate.candidateProfileId === null ||
      typeof candidate.candidateProfileId ===
        "string"
    ) &&
    typeof candidate.updatedAt === "string"
  );
}

export function getUserCvContext(
  userId: string | null | undefined
): UserCvContext | null {
  if (
    !isBrowser() ||
    !userId?.trim()
  ) {
    return null;
  }

  const key =
    getStorageKey(userId);

  const raw =
    window.localStorage.getItem(key);

  if (!raw) {
    return null;
  }

  try {
    const parsed: unknown =
      JSON.parse(raw);

    if (
      !isUserCvContext(parsed)
    ) {
      window.localStorage.removeItem(
        key
      );

      return null;
    }

    return {
      rawCvId:
        parsed.rawCvId.trim(),

      candidateProfileId:
        parsed.candidateProfileId?.trim() ||
        null,

      updatedAt:
        parsed.updatedAt
    };
  } catch {
    window.localStorage.removeItem(
      key
    );

    return null;
  }
}

export function setUserCvContext(
  userId: string,
  context: Pick<
    UserCvContext,
    | "rawCvId"
    | "candidateProfileId"
  >
): UserCvContext {
  const normalizedUserId =
    userId.trim();

  const rawCvId =
    context.rawCvId.trim();

  if (!normalizedUserId) {
    throw new Error(
      "User id is required"
    );
  }

  if (!rawCvId) {
    throw new Error(
      "Raw CV id is required"
    );
  }

  const nextContext: UserCvContext = {
    rawCvId,

    candidateProfileId:
      context.candidateProfileId?.trim() ||
      null,

    updatedAt:
      new Date().toISOString()
  };

  if (isBrowser()) {
    window.localStorage.setItem(
      getStorageKey(
        normalizedUserId
      ),
      JSON.stringify(
        nextContext
      )
    );
  }

  return nextContext;
}

export function clearUserCvContext(
  userId: string | null | undefined
): void {
  if (
    !isBrowser() ||
    !userId?.trim()
  ) {
    return;
  }

  window.localStorage.removeItem(
    getStorageKey(userId)
  );
}