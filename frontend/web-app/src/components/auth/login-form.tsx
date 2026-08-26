"use client";

import {
  useState,
  type FormEvent
} from "react";

import {useTranslations} from "next-intl";

import {useRouter} from "@/i18n/navigation";
import {toApiError} from "@/lib/api-error";
import {authService} from "@/services/auth.service";

import {LoginFormErrors} from "@/types/auth";

import {
  AuthPasswordField,
  AuthServerError,
  AuthSubmitButton,
  AuthTextField
} from "./auth-fields";
import {AuthFormShell} from "./auth-form-shell";

type Props = {
  active: boolean;
  onSwitch: () => void;
};

const EMAIL_REGEX =
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginForm({
  active,
  onSwitch
}: Props) {
  const t = useTranslations(
    "auth"
  );

  const router = useRouter();

  const [email, setEmail] =
    useState("");

  const [password, setPassword] =
    useState("");

  const [errors, setErrors] =
    useState<LoginFormErrors>({});

  const [
    serverError,
    setServerError
  ] = useState<string | null>(
    null
  );

  const [loading, setLoading] =
    useState(false);

  function validate() {
    const next: LoginFormErrors = {};

    const cleanEmail =
      email.trim();

    if (!cleanEmail) {
      next.email = t(
        "validation.emailRequired"
      );
    } else if (
      cleanEmail.length > 254
    ) {
      next.email = t(
        "validation.emailMax"
      );
    } else if (
      !EMAIL_REGEX.test(
        cleanEmail
      )
    ) {
      next.email = t(
        "validation.emailInvalid"
      );
    }

    if (!password) {
      next.password = t(
        "validation.passwordRequired"
      );
    }

    return next;
  }

  function getErrorMessage(
    error: unknown
  ) {
    const apiError =
      toApiError(error);

    if (
      apiError.error ===
      "INVALID_CREDENTIALS"
    ) {
      return t(
        "errors.invalidCredentials"
      );
    }

    if (
      apiError.error ===
      "VALIDATION_FAILED"
    ) {
      return t(
        "errors.validationFailed"
      );
    }

    if (
      apiError.error ===
      "RATE_LIMIT_EXCEEDED"
    ) {
      return t(
        "errors.rateLimit"
      );
    }

    if (
      apiError.isNetworkError
    ) {
      return t(
        "errors.network"
      );
    }

    return t(
      "errors.generic"
    );
  }

  async function submit(
    event: FormEvent<HTMLFormElement>
  ) {
    event.preventDefault();

    const next =
      validate();

    setErrors(next);
    setServerError(null);

    if (
      Object.keys(next).length >
      0
    ) {
      return;
    }

    try {
      setLoading(true);

      const session =
        await authService.login({
          email: email.trim(),
          password
        });

      router.replace(
        session.user.roles.includes(
          "ADMIN"
        )
          ? "/admin"
          : "/jobs"
      );

      router.refresh();
    } catch (error) {
      setServerError(
        getErrorMessage(error)
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFormShell
      index="01"
      eyebrow={t(
        "login.eyebrow"
      )}
      title={t(
        "login.title"
      )}
      description={t(
        "login.description"
      )}
      footerText={t(
        "login.noAccount"
      )}
      footerAction={t(
        "login.createAccount"
      )}
      onSwitch={onSwitch}
    >
      <AuthServerError
        message={serverError}
      />

      <form
        onSubmit={submit}
        noValidate
      >
        {/* Form inactive vẫn mounted nhưng fieldset bị khóa để không nhận tab/submit ngoài ý muốn. */}
        <fieldset
          disabled={
            !active || loading
          }
          className="space-y-4"
        >
          <AuthTextField
            id="login-email"
            label={t(
              "fields.email"
            )}
            value={email}
            type="email"
            inputMode="email"
            autoComplete="email"
            maxLength={254}
            placeholder={t(
              "placeholders.email"
            )}
            error={errors.email}
            onChange={(value) => {
              setEmail(value);

              if (errors.email) {
                setErrors(
                  (current) => ({
                    ...current,
                    email: undefined
                  })
                );
              }
            }}
          />

          <AuthPasswordField
            id="login-password"
            label={t(
              "fields.password"
            )}
            value={password}
            placeholder={t(
              "placeholders.password"
            )}
            autoComplete="current-password"
            error={
              errors.password
            }
            onChange={(value) => {
              setPassword(value);

              if (
                errors.password
              ) {
                setErrors(
                  (current) => ({
                    ...current,
                    password:
                      undefined
                  })
                );
              }
            }}
          />

          <div className="pt-1.5">
            <AuthSubmitButton
              loading={loading}
              loadingText={t(
                "login.submitting"
              )}
            >
              {t(
                "login.submit"
              )}
            </AuthSubmitButton>
          </div>
        </fieldset>
      </form>
    </AuthFormShell>
  );
}