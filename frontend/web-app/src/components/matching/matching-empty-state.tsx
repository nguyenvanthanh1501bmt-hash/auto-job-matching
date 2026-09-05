import type {
  MatchingEmptyStateProps
} from "@/types/matching-ui";

export function MatchingEmptyState({
  eyebrow,
  title,
  description,
  action
}: MatchingEmptyStateProps) {
  return (
    <div className="mt-7 flex min-h-[410px] flex-col items-center justify-center rounded-[22px] border border-dashed border-black/[0.08] bg-white/45 px-6 text-center">
      <div className="flex size-12 items-center justify-center rounded-[15px] bg-white shadow-[0_5px_22px_rgba(0,0,0,0.035)]">
        <span className="size-2 rounded-full bg-[#d9ff75] ring-4 ring-[#d9ff75]/20" />
      </div>

      <p className="mt-5 font-mono text-[8px] font-semibold uppercase tracking-[0.15em] text-black/28">
        {eyebrow}
      </p>

      <h2 className="mt-2 max-w-[520px] text-[22px] font-bold tracking-[-0.045em] text-[#292927]">
        {title}
      </h2>

      <p className="mt-3 max-w-[560px] text-[11px] leading-5 text-black/42">
        {description}
      </p>

      {action ? (
        <div className="mt-6">
          {action}
        </div>
      ) : null}
    </div>
  );
}