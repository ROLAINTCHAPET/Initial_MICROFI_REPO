import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import "./globals.css";

// DESIGN.md specifies Inter exclusively ("tall x-height and exceptional legibility on
// low-resolution mobile displays"). next/font self-hosts the font files at build time and
// serves them from this app's own origin — no runtime request to Google.
const inter = Inter({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-inter",
  display: "swap",
});

export const metadata: Metadata = {
  title: "MICROFI Back-Office",
  description: "MICROFI Back-Office — branch, agent and field-operations console",
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const locale = await getLocale();
  const dict = getDictionary(locale);

  return (
    <html lang={locale} className={`h-full antialiased ${inter.variable}`}>
      <body
        className="min-h-full flex flex-col bg-surface-grey-100"
        style={{ backgroundImage: "url('/backgrounds/pattern.jpg')", backgroundRepeat: "repeat", backgroundAttachment: "fixed" }}
      >
        <I18nProvider locale={locale} dict={dict}>
          {children}
        </I18nProvider>
      </body>
    </html>
  );
}
