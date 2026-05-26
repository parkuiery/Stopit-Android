import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";

const pretendard = localFont({
  src: [
    { path: "./fonts/pretendard_regular.otf", weight: "400", style: "normal" },
    { path: "./fonts/pretendard_medium.otf", weight: "500", style: "normal" },
    { path: "./fonts/pretendard_semibold.otf", weight: "600", style: "normal" },
    { path: "./fonts/pretendard_bold.otf", weight: "700", style: "normal" },
  ],
  variable: "--font-pretendard",
  display: "swap",
});

export const metadata: Metadata = {
  title: "StopIt — Play Store ASO Screenshots",
  description: "Play Store screenshot generator for the StopIt Android app.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" className={`${pretendard.variable} h-full antialiased`}>
      <body className="min-h-full">{children}</body>
    </html>
  );
}
