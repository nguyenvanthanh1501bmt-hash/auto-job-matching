import type {Metadata} from "next";

import {AppIntlProvider} from "@/components/providers/app-intl-provider";
import {QueryProvider} from "@/components/providers/query-provider";

import "../globals.css";

export const metadata: Metadata = {
  title: "AutoJob",
  description: "AI-powered job matching platform"
};

type Props = {
  children: React.ReactNode;

  params: Promise<{
    locale: string;
  }>;
};

export default async function LocaleLayout({
  children,
  params
}: Props) {
  const {locale} = await params;

  return (
    <html lang={locale}>
      <body>
        <AppIntlProvider
          initialLocale={locale}
        >
          <QueryProvider>
            {children}
          </QueryProvider>
        </AppIntlProvider>
      </body>
    </html>
  );
}