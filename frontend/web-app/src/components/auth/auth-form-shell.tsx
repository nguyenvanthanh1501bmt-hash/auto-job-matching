import type {ReactNode} from "react";

type Props = {
  index: string;
  eyebrow: string;
  title: string;
  description: string;

  children: ReactNode;

  footerText: string;
  footerAction: string;

  onSwitch: () => void;
};

export function AuthFormShell({
  index,
  eyebrow,
  title,
  description,
  children,
  footerText,
  footerAction,
  onSwitch
}: Props) {
  return (
    <>
      <div className="mb-7">
        <div className="mb-5 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <span className="font-mono text-[9px] text-[#b0b0aa]">
              {index}
            </span>

            <span className="h-px w-4 bg-black/10" />

            <span className="text-[9px] font-bold uppercase tracking-[0.17em] text-[#9b9b95]">
              {eyebrow}
            </span>
          </div>

          <span className="size-1.5 rounded-full bg-[#171717]" />
        </div>

        <h2 className="text-[31px] font-semibold leading-none tracking-[-0.055em] text-[#171717]">
          {title}
        </h2>

        <p className="mt-3 max-w-[390px] text-[12px] leading-[20px] text-[#7b7b76]">
          {description}
        </p>
      </div>

      {children}

      <div className="mt-6 flex items-center justify-center gap-1.5 text-[11px] text-[#90908a]">
        <span>
          {footerText}
        </span>

        <button
          type="button"
          onClick={onSwitch}
          className="font-semibold text-[#242422] underline decoration-black/15 underline-offset-4 transition hover:decoration-black/60"
        >
          {footerAction}
        </button>
      </div>
    </>
  );
}