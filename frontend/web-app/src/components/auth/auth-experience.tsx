"use client";

import {
  useEffect,
  useState
} from "react";

import {useRouter} from "@/i18n/navigation";
import {getAuthSession} from "@/lib/auth-storage";

import {AuthLayout} from "./auth-layout";
import {LoginForm} from "./login-form";
import {RegisterForm} from "./register-form";

import type {AuthMode} from "@/types/auth";

type Props = {
  initialMode: AuthMode;
};

export function AuthExperience({
  initialMode
}: Props) {
  const router = useRouter();

  const [mode, setMode] =
    useState<AuthMode>(
      initialMode
    );

  useEffect(() => {
    const session =
      getAuthSession();

    if (!session) {
      return;
    }

    router.replace(
      session.user.roles.includes(
        "ADMIN"
      )
        ? "/admin"
        : "/jobs"
    );
  }, [router]);

  function changeMode(
    nextMode: AuthMode
  ) {
    if (nextMode === mode) {
      return;
    }

    setMode(nextMode);

    // Chỉ sync URL bằng History API để component không bị remount giữa animation.
    try {
      const url = new URL(
        window.location.href
      );

      const nextPath =
        url.pathname.replace(
          /\/(login|register)\/?$/,
          `/${nextMode}`
        );

      window.history.replaceState(
        window.history.state,
        "",
        `${nextPath}${url.search}${url.hash}`
      );
    } catch {
      // URL không ảnh hưởng tới flow auth nên không chặn chuyển mode nếu sync thất bại.
    }
  }

  return (
    <AuthLayout
      mode={mode}
      onModeChange={changeMode}
      loginForm={
        <LoginForm
          active={
            mode === "login"
          }
          onSwitch={() =>
            changeMode(
              "register"
            )
          }
        />
      }
      registerForm={
        <RegisterForm
          active={
            mode === "register"
          }
          onSwitch={() =>
            changeMode(
              "login"
            )
          }
        />
      }
    />
  );
}