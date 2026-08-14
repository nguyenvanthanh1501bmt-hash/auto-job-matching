from __future__ import annotations

from pathlib import Path


REPO_ROOT = (
    Path(__file__)
    .resolve()
    .parent
    .parent
)

TARGET = (
        REPO_ROOT
        / "backend"
        / "modules"
        / "job-normalizer"
        / "src"
        / "main"
        / "java"
        / "com"
        / "autojob"
        / "modules"
        / "jobnormalizer"
        / "normalization"
        / "JobEmbeddingTextBuilder.java"
)


OLD = """@Component
@RequiredArgsConstructor
public class JobEmbeddingTextBuilder {
"""

NEW = """@Component("normalizedJobEmbeddingTextBuilder")
@RequiredArgsConstructor
public class JobEmbeddingTextBuilder {
"""


def main() -> None:
    if not TARGET.is_file():
        raise FileNotFoundError(
            TARGET
        )

    text = TARGET.read_text(
        encoding="utf-8"
    )

    if NEW in text:
        print(
            "Already fixed: "
            "normalizedJobEmbeddingTextBuilder"
        )
        return

    count = text.count(
        OLD
    )

    if count != 1:
        raise RuntimeError(
            "Expected exactly one legacy "
            "@Component declaration, "
            f"found {count}. "
            "No file was modified."
        )

    updated = text.replace(
        OLD,
        NEW,
        1,
    )

    TARGET.write_text(
        updated,
        encoding="utf-8",
        newline="\n",
    )

    print(
        "Updated: "
        "backend/modules/job-normalizer/"
        "src/main/java/com/autojob/modules/"
        "jobnormalizer/normalization/"
        "JobEmbeddingTextBuilder.java"
    )

    print(
        "Legacy bean name: "
        "normalizedJobEmbeddingTextBuilder"
    )

    print(
        "Canonical job-embedding bean remains: "
        "jobEmbeddingTextBuilder"
    )


if __name__ == "__main__":
    main()