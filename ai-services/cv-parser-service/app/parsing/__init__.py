"""Deterministic, job-agnostic CV parsing pipeline."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from app.parsing.profile_parser import ProfileParser

__all__ = ["ProfileParser"]


def __getattr__(name: str) -> Any:
    if name != "ProfileParser":
        raise AttributeError(
            f"module {__name__!r} has no attribute {name!r}"
        )

    from app.parsing.profile_parser import ProfileParser

    return ProfileParser