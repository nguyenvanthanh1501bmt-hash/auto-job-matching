"use client";

import {
  useState,
  type FormEvent
} from "react";

import {useTranslations} from "next-intl";

import {useRouter} from "@/i18n/navigation";
import {toApiError} from "@/lib/api-error";
import {authService} from "@/services/auth.service";

import {
  AuthPasswordField,
  AuthServerError,
  AuthSubmitButton,
  AuthTextField
} from "./auth-fields";
import {AuthFormShell} from "./auth-form-shell";
import {RegisterFormErrors} from "@/types/auth";

type Props = {
  active: boolean;
  onSwitch: () => void;
};

const EMAIL_REGEX =
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function RegisterForm({
  active,
  onSwitch
}: Props) {
  const t = useTranslations(
    "auth"
  );

  const router = useRouter();

  const [
    displayName,
    setDisplayName
  ] = useState("");

  const [email, setEmail] =
    useState("");

  const [password, setPassword] =
    useState("");

  const [
    confirmPassword,
    setConfirmPassword
  ] = useState("");

  const [errors, setErrors] =
    useState<RegisterFormErrors>({});

  const [
    serverError,
    setServerError
  ] = useState<string | null>(
    null
  );

  const [loading, setLoading] =
    useState(false);

  function validate() {
    const next: RegisterFormErrors = {};

    const cleanName =
      displayName.trim();

    const cleanEmail =
      email.trim();

    if (!cleanName) {
      next.displayName = t(
        "validation.displayNameRequired"
      );
    } else if (
      cleanName.length > 100
    ) {
      next.displayName = t(
        "validation.displayNameMax"
      );
    }

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
    } else if (
      password.length < 8
    ) {
      next.password = t(
        "validation.passwordMin"
      );
    } else if (
      password.length > 128
    ) {
      next.password = t(
        "validation.passwordMax"
      );
    }

    if (!confirmPassword) {
      next.confirmPassword =
        t(
          "validation.confirmPasswordRequired"
        );
    } else if (
      confirmPassword !==
      password
    ) {
      next.confirmPassword =
        t(
          "validation.passwordMismatch"
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
      "EMAIL_ALREADY_EXISTS"
    ) {
      return t(
        "errors.emailExists"
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
        await authService.register({
          displayName:
            displayName.trim(),
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
      index="02"
      eyebrow={t(
        "register.eyebrow"
      )}
      title={t(
        "register.title"
      )}
      description={t(
        "register.description"
      )}
      footerText={t(
        "register.hasAccount"
      )}
      footerAction={t(
        "register.signIn"
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
        {/* Form ẩn vẫn giữ state, nhưng không được phép tương tác trong lúc chuyển mode. */}
        <fieldset
          disabled={
            !active || loading
          }
          className="space-y-[11px]"
        >
          <AuthTextField
            id="register-name"
            label={t(
              "fields.displayName"
            )}
            value={displayName}
            autoComplete="name"
            maxLength={100}
            placeholder={t(
              "placeholders.displayName"
            )}
            error={
              errors.displayName
            }
            onChange={(value) => {
              setDisplayName(value);

              if (
                errors.displayName
              ) {
                setErrors(
                  (current) => ({
                    ...current,
                    displayName:
                      undefined
                  })
                );
              }
            }}
          />

          <AuthTextField
            id="register-email"
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
            id="register-password"
            label={t(
              "fields.password"
            )}
            value={password}
            placeholder={t(
              "placeholders.newPassword"
            )}
            autoComplete="new-password"
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

          <AuthPasswordField
            id="register-confirm-password"
            label={t(
              "fields.confirmPassword"
            )}
            value={
              confirmPassword
            }
            placeholder={t(
              "placeholders.confirmPassword"
            )}
            autoComplete="new-password"
            error={
              errors.confirmPassword
            }
            onChange={(value) => {
              setConfirmPassword(
                value
              );

              if (
                errors.confirmPassword
              ) {
                setErrors(
                  (current) => ({
                    ...current,
                    confirmPassword:
                      undefined
                  })
                );
              }
            }}
          />

          <div className="pt-1">
            <AuthSubmitButton
              loading={loading}
              loadingText={t(
                "register.submitting"
              )}
            >
              {t(
                "register.submit"
              )}
            </AuthSubmitButton>
          </div>
        </fieldset>
      </form>
    </AuthFormShell>
  );
}