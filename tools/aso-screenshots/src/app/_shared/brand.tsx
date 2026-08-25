"use client";

import { useState } from "react";

// ---------- Design tokens (mirrors core/kds Color.kt Light) ----------
export const TOKENS = {
  bg: "#FBF8F2", // warm cream — replaces pure white for Opal/Forest-like warmth
  bgSoft: "#F3ECDD",
  bgSofter: "#F2F4F6",
  bgDark: "#17171C",
  bgDarkSoft: "#202027",
  text: "#191F28",
  textMid: "#333D4B",
  textMuted: "#4E5968",
  textSub: "#8B95A1",
  border: "#E5E8EB",
  accent: "#FFA927", // orange400
  accentDeep: "#E08A00", // darker orange for label legibility on cream
  danger: "#F04452", // red500
  dangerDeep: "#C42F3C",
} as const;

export const FONT =
  'var(--font-pretendard), -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif';

// ---------- Device frame wrapping a real app screenshot ----------
export function Phone({
  src,
  alt,
  placeholder,
  dark,
  style,
  className = "",
}: {
  src: string;
  alt: string;
  placeholder: string;
  dark?: boolean;
  style?: React.CSSProperties;
  className?: string;
}) {
  const [failed, setFailed] = useState(false);
  return (
    <div className={`relative ${className}`} style={{ aspectRatio: "9 / 19.5", ...style }}>
      <div
        style={{
          width: "100%",
          height: "100%",
          borderRadius: "5% / 2.4%",
          background: "linear-gradient(180deg, #1A1A1A 0%, #0A0A0F 100%)",
          position: "relative",
          overflow: "hidden",
          boxShadow: dark
            ? "inset 0 0 0 1px rgba(255,255,255,0.16), 0 28px 80px rgba(0,0,0,0.55)"
            : "inset 0 0 0 1px rgba(255,255,255,0.08), 0 28px 80px rgba(20,20,30,0.22)",
        }}
      >
        {/* Camera punch-hole */}
        <div
          style={{
            position: "absolute",
            top: "1.4%",
            left: "50%",
            transform: "translateX(-50%)",
            width: "2.4%",
            height: "0.55%",
            borderRadius: "999px",
            background: "#0A0A0A",
            border: "1px solid rgba(255,255,255,0.08)",
            zIndex: 20,
          }}
        />
        {/* Screen */}
        <div
          style={{
            position: "absolute",
            left: "3%",
            top: "1.8%",
            width: "94%",
            height: "96.4%",
            borderRadius: "4% / 2%",
            overflow: "hidden",
            background: "#000",
          }}
        >
          {failed ? (
            <PhonePlaceholder label={placeholder} />
          ) : (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={src}
              alt={alt}
              onError={() => setFailed(true)}
              style={{
                display: "block",
                width: "100%",
                height: "100%",
                objectFit: "contain",
                objectPosition: "center",
                background: dark ? "#000" : "#FFFFFF",
              }}
              draggable={false}
            />
          )}
        </div>
      </div>
    </div>
  );
}

export function PhonePlaceholder({ label }: { label: string }) {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        background: "linear-gradient(180deg, #F9FAFB 0%, #E5E8EB 100%)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexDirection: "column",
        gap: 24,
        color: TOKENS.textMuted,
        fontFamily: FONT,
        padding: 32,
        textAlign: "center",
      }}
    >
      <div
        style={{
          width: 84,
          height: 84,
          borderRadius: 18,
          background: TOKENS.bgSofter,
          border: `2px dashed ${TOKENS.border}`,
        }}
      />
      <div style={{ fontSize: 36, fontWeight: 700, color: TOKENS.text }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 500, color: TOKENS.textSub, lineHeight: 1.4 }}>
        실제 캡처 PNG를
        <br />
        <code style={{ background: TOKENS.bgSofter, padding: "2px 6px", borderRadius: 4, fontSize: 20 }}>
          public/screenshots/
        </code>
        에 추가하세요
      </div>
    </div>
  );
}

// ---------- App icon + wordmark ----------
export function BrandMark({ canvasW, dark }: { canvasW: number; dark?: boolean }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: canvasW * 0.012 }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src="/app-icon.png"
        alt="StopIt"
        style={{
          width: canvasW * 0.05,
          height: canvasW * 0.05,
          borderRadius: canvasW * 0.013,
        }}
        draggable={false}
      />
      <div
        style={{
          fontFamily: FONT,
          fontSize: canvasW * 0.022,
          fontWeight: 700,
          color: dark ? "#FFFFFF" : TOKENS.text,
          letterSpacing: "-0.01em",
        }}
      >
        StopIt
      </div>
    </div>
  );
}
