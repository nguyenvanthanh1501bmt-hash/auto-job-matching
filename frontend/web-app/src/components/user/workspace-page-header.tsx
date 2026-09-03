import type {ReactNode} from "react";

type Props = {
  eyebrow: string;
  title: string;
  description: string;

  statistic?: {
    label: string;
    value: ReactNode;
  };
};

export function WorkspacePageHeader({
  eyebrow,
  title,
  description,
  statistic
}: Props) {
  return (
    <div className="flex flex-col gap-6 border-b border-black/[0.05] pb-7 sm:flex-row sm:items-end sm:justify-between">
      <div className="max-w-[720px]">
        <div className="flex items-center gap-3">
          <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.16em] text-black/32">
            {eyebrow}
          </span>

          <span className="h-px w-8 bg-black/[0.08]" />

          <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />
        </div>

        <h1 className="mt-4 text-[32px] font-bold leading-none tracking-[-0.055em] text-[#20201e] sm:text-[40px] lg:text-[46px]">
          {title}
        </h1>

        <p className="mt-4 max-w-[650px] text-[12px] leading-6 text-black/45 sm:text-[13px]">
          {description}
        </p>
      </div>

      {statistic ? (
        <div className="shrink-0 rounded-[15px] border border-black/[0.055] bg-white px-4 py-3 text-right shadow-[0_2px_10px_rgba(0,0,0,0.02)]">
          <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
            {statistic.label}
          </p>

          <div className="mt-1 text-[20px] font-bold tracking-[-0.04em] text-[#252523]">
            {statistic.value}
          </div>
        </div>
      ) : null}
    </div>
  );
}