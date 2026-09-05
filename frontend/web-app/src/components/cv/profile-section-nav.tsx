"use client";

import {
  useEffect,
  useState
} from "react";

import type {
  ProfileSectionNavProps
} from "@/types/cv-ui";

export function ProfileSectionNav({
  items
}: ProfileSectionNavProps) {
  const [
    activeId,
    setActiveId
  ] = useState(
    items[0]?.id ?? ""
  );

  useEffect(() => {
    if (
      items.length === 0
    ) {
      return;
    }

    const elements =
      items
        .map(
          (item) =>
            document.getElementById(
              item.id
            )
        )
        .filter(
          (
            element
          ): element is HTMLElement =>
            Boolean(
              element
            )
        );

    if (
      elements.length === 0
    ) {
      return;
    }

    function updateActiveSection() {
      const offset = 170;

      let current =
        elements[0];

      for (
        const element
        of elements
      ) {
        const rect =
          element.getBoundingClientRect();

        if (
          rect.top <= offset
        ) {
          current =
            element;
        } else {
          break;
        }
      }

      if (current) {
        setActiveId(
          current.id
        );
      }
    }

    updateActiveSection();

    window.addEventListener(
      "scroll",
      updateActiveSection,
      {
        passive: true
      }
    );

    window.addEventListener(
      "resize",
      updateActiveSection
    );

    return () => {
      window.removeEventListener(
        "scroll",
        updateActiveSection
      );

      window.removeEventListener(
        "resize",
        updateActiveSection
      );
    };
  }, [
    items
  ]);

  if (
    items.length <= 1
  ) {
    return null;
  }

  return (
    <aside className="hidden self-stretch xl:block">
      <div className="relative h-full">
        <nav className="sticky top-[110px]">
          <div className="space-y-0.5">
            {items.map(
              (item) => {
                const active =
                  activeId ===
                  item.id;

                return (
                  <a
                    key={
                      item.id
                    }
                    href={`#${item.id}`}
                    title={
                      item.label
                    }
                    className={`group flex h-7 w-[108px] items-center gap-2 rounded-[8px] px-2 transition-colors ${
                      active
                        ? "bg-[#f2ffd1]"
                        : "hover:bg-black/[0.025]"
                    }`}
                  >
                    <span
                      className={`size-1.5 shrink-0 rounded-full transition-all ${
                        active
                          ? "bg-[#9fba3d] ring-[3px] ring-[#d9ff75]/30"
                          : "bg-black/[0.11] group-hover:bg-black/20"
                      }`}
                    />

                    <span
                      className={`min-w-0 flex-1 truncate text-[7.5px] font-semibold transition-colors ${
                        active
                          ? "text-[#56651f]"
                          : "text-black/30 group-hover:text-black/52"
                      }`}
                    >
                      {item.label}
                    </span>
                  </a>
                );
              }
            )}
          </div>
        </nav>
      </div>
    </aside>
  );
}