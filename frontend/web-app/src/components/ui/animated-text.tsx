"use client";

type AnimatedTextSegment = {
  text: string;
  className?: string;
  breakBefore?: boolean;
};

type Props = {
  segments: AnimatedTextSegment[];
  active?: boolean;
};

export function AnimatedText({
  segments,
  active = true
}: Props) {
  const animatedCharacters = segments.reduce(
    (total, segment) =>
      total +
      Array.from(segment.text).filter(
        (character) => character !== " "
      ).length,
    0
  );

  const waveCharacterDelay = 105;
  const overlapCharacters = 0.1;
  const loopDelay = 1500;

  const waveStartDelay =
    animatedCharacters * 28 + 620;

  const waveCycleDuration = Math.max(
    900,
    (animatedCharacters - overlapCharacters) *
      waveCharacterDelay +
      loopDelay
  );

  let characterIndex = 0;

  return (
    <>
      {segments.map((segment, segmentIndex) => {
        const parts = segment.text.split(/(\s+)/);

        return (
          <span
            key={`${segment.text}-${segmentIndex}`}
            aria-hidden="true"
            className={`${segment.breakBefore ? "block" : "inline"} ${
              segment.className ?? ""
            }`}
          >
            {parts.map((part, partIndex) => {
              if (/^\s+$/.test(part)) {
                return (
                  <span
                    key={`space-${segmentIndex}-${partIndex}`}
                    aria-hidden="true"
                  >
                    {part}
                  </span>
                );
              }

              return (
                <span
                  key={`word-${segmentIndex}-${partIndex}`}
                  aria-hidden="true"
                  className="inline-block whitespace-nowrap"
                >
                  {Array.from(part).map((character, index) => {
                    const currentIndex = characterIndex++;

                    return (
                      <span
                        key={`${character}-${segmentIndex}-${partIndex}-${index}`}
                        className={`aj-home-title-char-enter inline-block ${
                          active
                            ? "aj-home-title-char-enter-active"
                            : ""
                        }`}
                        style={{
                          animationDelay: `${currentIndex * 28}ms`
                        }}
                      >
                        <span
                          className={`aj-home-title-char-wave inline-block ${
                            active
                              ? "aj-home-title-char-wave-active"
                              : ""
                          }`}
                          style={{
                            animationDelay: `${
                              waveStartDelay +
                              currentIndex * waveCharacterDelay
                            }ms`,
                            animationDuration: `${waveCycleDuration}ms`
                          }}
                        >
                          {character}
                        </span>
                      </span>
                    );
                  })}
                </span>
              );
            })}
          </span>
        );
      })}

      <style>{`
        @keyframes aj-home-title-char-enter {
          0% {
            opacity: 0;
            transform: translate3d(0, 16px, 0) scale(.97);
          }

          70% {
            opacity: 1;
            transform: translate3d(0, -1px, 0) scale(1.008);
          }

          100% {
            opacity: 1;
            transform: translate3d(0, 0, 0) scale(1);
          }
        }

        .aj-home-title-char-enter {
          opacity: 0;
          transform: translate3d(0, 16px, 0) scale(.97);
        }

        .aj-home-title-char-enter-active {
          animation:
            aj-home-title-char-enter
            560ms
            cubic-bezier(.22, 1, .36, 1)
            both;
        }

        @keyframes aj-home-title-char-wave {
          0% {
            transform: translate3d(0, 0, 0);
            animation-timing-function: cubic-bezier(.22, 1, .36, 1);
          }

          8% {
            transform: translate3d(0, -7px, 0);
            animation-timing-function: cubic-bezier(.22, 1, .36, 1);
          }

          14% {
            transform: translate3d(0, 1px, 0);
            animation-timing-function: cubic-bezier(.22, 1, .36, 1);
          }

          20%,
          100% {
            transform: translate3d(0, 0, 0);
          }
        }

        .aj-home-title-char-wave {
          transform-origin: center bottom;
          will-change: transform;
        }

        .aj-home-title-char-wave-active {
          animation-name: aj-home-title-char-wave;
          animation-timing-function: linear;
          animation-iteration-count: infinite;
          animation-fill-mode: both;
        }
      `}</style>
    </>
  );
}