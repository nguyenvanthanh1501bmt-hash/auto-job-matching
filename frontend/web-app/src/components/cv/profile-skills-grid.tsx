"use client";

import {
  ProfileSkillCard
} from "@/components/cv/profile-skill-card";

import type {
  ProfileSkillsGridProps
} from "@/types/cv-ui";

export function ProfileSkillsGrid({
  skills
}: ProfileSkillsGridProps) {
  const visibleSkills =
    skills.filter(
      (skill) =>
        Boolean(
          skill.name?.trim() ||
          skill.normalizedName?.trim()
        )
    );

  if (
    visibleSkills.length ===
    0
  ) {
    return null;
  }

  return (
    <div>
      <div className="mb-3 flex items-center gap-2.5">
        <span className="font-mono text-[7px] font-bold text-[#6d7f28]">
          {String(
            visibleSkills.length
          ).padStart(
            2,
            "0"
          )}
        </span>

        <span className="size-1 rounded-full bg-[#b5d354]" />

        <span className="h-px flex-1 bg-black/[0.045]" />
      </div>

      <div className="grid gap-2 md:grid-cols-2 2xl:grid-cols-3">
        {visibleSkills.map(
          (
            skill,
            index
          ) => (
            <ProfileSkillCard
              key={`${skill.normalizedName ?? skill.name ?? "skill"}-${index}`}
              skill={
                skill
              }
              index={
                index
              }
            />
          )
        )}
      </div>
    </div>
  );
}