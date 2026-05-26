"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toPng } from "html-to-image";

// ---------- Design tokens (mirrors core/kds Color.kt Light) ----------
const TOKENS = {
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

const FONT =
  'var(--font-pretendard), -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif';

// ---------- Canvas sizes (Google Play recommended portrait) ----------
const PHONE_SIZES = [{ label: "Phone 1080×1920", w: 1080, h: 1920 }] as const;

// ---------- Slide registry ----------
type Slide = {
  id: string;
  filename: string;
  src: string; // expected screenshot path under /public
  placeholder: string; // shown when src missing
  label: string;
  l1: string;
  l2: string;
  sub: string; // short supporting line under the headline
  variant: "light" | "dark";
  accent: "orange" | "red";
  phoneWidthRatio: number; // 0.0 - 1.0 of canvas W
  phoneTranslateY: number; // 0.0 - 1.0 of canvas H (downward)
  tilt?: number; // degrees; optional accent rotation
};

// Uniform phone footprint across all slides for a consistent rhythm.
// Sized so the entire device + screenshot is visible above the canvas bottom.
// A small translateY crops only the bottom bezel for a grounded look.
const PHONE_W_RATIO = 0.62;
const PHONE_TY = 0.03;

type Locale = "ko" | "en";

const SLIDE_BASE: Omit<Slide, "label" | "l1" | "l2" | "sub" | "placeholder" | "src">[] = [
  {
    id: "select",
    filename: "01-select",
    variant: "light",
    accent: "orange",
    phoneWidthRatio: PHONE_W_RATIO,
    phoneTranslateY: PHONE_TY,
  },
  {
    id: "timer",
    filename: "02-timer",
    variant: "light",
    accent: "orange",
    phoneWidthRatio: PHONE_W_RATIO,
    phoneTranslateY: PHONE_TY,
  },
  {
    id: "routine",
    filename: "03-routine",
    variant: "light",
    accent: "orange",
    phoneWidthRatio: PHONE_W_RATIO,
    phoneTranslateY: PHONE_TY,
  },
  {
    id: "block",
    filename: "04-block",
    variant: "dark",
    accent: "orange",
    phoneWidthRatio: PHONE_W_RATIO,
    phoneTranslateY: PHONE_TY,
  },
  {
    id: "emergency",
    filename: "05-emergency",
    variant: "light",
    accent: "red",
    phoneWidthRatio: PHONE_W_RATIO,
    phoneTranslateY: PHONE_TY,
  },
  {
    id: "history",
    filename: "06-history",
    variant: "light",
    accent: "orange",
    phoneWidthRatio: PHONE_W_RATIO,
    phoneTranslateY: PHONE_TY,
  },
];

// Locale-aware screenshot path. EN captures live in /screenshots/en/.
function screenshotSrc(filename: string, locale: Locale): string {
  return locale === "en" ? `/screenshots/en/${filename}.png` : `/screenshots/${filename}.png`;
}

const COPY: Record<Locale, Record<string, { label: string; l1: string; l2: string; sub: string; placeholder: string }>> = {
  ko: {
    select: { label: "유혹 앱 차단", l1: "유혹 앱만", l2: "골라서 차단", sub: "방해되는 앱을 직접 선택해 막아요", placeholder: "앱 선택" },
    timer: { label: "타이머 잠금", l1: "지금 바로", l2: "타이머 잠금", sub: "한 번 누르고 몰입 시작", placeholder: "타이머 잠금" },
    routine: { label: "루틴 잠금", l1: "요일·시간대로", l2: "자동 차단", sub: "공부·업무 시간에 알아서 잠겨요", placeholder: "루틴" },
    block: { label: "실제 차단", l1: "앱을 열어도", l2: "바로 차단", sub: "흐름이 깨지기 전에 먼저 멈춥니다", placeholder: "차단 화면" },
    emergency: { label: "긴급 해제", l1: "꼭 필요할 때만", l2: "긴급 해제", sub: "제한된 시간으로 안전하게 열어요", placeholder: "긴급 해제" },
    history: { label: "잠금 기록", l1: "버틴 시간이", l2: "기록으로 남아요", sub: "집중을 이어온 증거를 한눈에", placeholder: "잠금 기록" },
  },
  en: {
    select: { label: "Block distractions", l1: "Pick the apps", l2: "that steal focus", sub: "Choose which apps to lock out completely", placeholder: "App picker" },
    timer: { label: "Timer lock", l1: "One tap.", l2: "Locked in.", sub: "Start a focus session in seconds", placeholder: "Timer lock" },
    routine: { label: "Routine lock", l1: "Schedule by day", l2: "and time", sub: "Auto-locks during study and work hours", placeholder: "Routine" },
    block: { label: "Real blocking", l1: "Open the app —", l2: "blocked instantly", sub: "Catches the slip before momentum breaks", placeholder: "Block screen" },
    emergency: { label: "Emergency unlock", l1: "When you truly", l2: "need it back", sub: "Safely unlock with a strict time limit", placeholder: "Emergency unlock" },
    history: { label: "Lock history", l1: "Every minute", l2: "you held the line", sub: "See the focus you've earned at a glance", placeholder: "Lock history" },
  },
};

const UI: Record<Locale, { title: string; meta: (n: number, w: number, h: number) => string; exportAll: string; exporting: string; footerStart: string; footerMid: string; footerEnd: string }> = {
  ko: {
    title: "StopIt — Play Store ASO Screenshots",
    meta: (n, w, h) => `${n} slides · ${w}×${h} · Pretendard`,
    exportAll: "전체 PNG 내보내기",
    exporting: "내보내는 중…",
    footerStart: "캡처본을 ",
    footerMid: " … ",
    footerEnd: " 순서로 추가하면 자동 반영됩니다.",
  },
  en: {
    title: "StopIt — Play Store ASO Screenshots",
    meta: (n, w, h) => `${n} slides · ${w}×${h} · Pretendard`,
    exportAll: "Export all PNG",
    exporting: "Exporting…",
    footerStart: "Drop captures into ",
    footerMid: " … ",
    footerEnd: " — they'll appear automatically.",
  },
};

function buildSlides(locale: Locale): Slide[] {
  return SLIDE_BASE.map((b) => ({
    ...b,
    ...COPY[locale][b.id],
    src: screenshotSrc(b.filename, locale),
  }));
}

// ---------- Feature Graphic (Play Store 1024×500 banner) ----------
const FEATURE_SIZE = { w: 1024, h: 500 } as const;

const FEATURE_COPY: Record<Locale, { label: string; l1: string; l2: string; sub: string }> = {
  ko: {
    label: "스크린 타임 관리",
    l1: "유혹은 차단하고",
    l2: "집중은 지킨다",
    sub: "정해진 시간, 정해진 앱, 흔들림 없는 몰입",
  },
  en: {
    label: "Screen time, owned",
    l1: "Block distractions.",
    l2: "Stay in focus.",
    sub: "Set the apps, set the hours, hold the line.",
  },
};

function FeatureGraphic({ locale }: { locale: Locale }) {
  const W = FEATURE_SIZE.w;
  const H = FEATURE_SIZE.h;
  const t = FEATURE_COPY[locale];
  return (
    <div
      style={{
        width: W,
        height: H,
        position: "relative",
        overflow: "hidden",
        background: `linear-gradient(135deg, ${TOKENS.bg} 0%, ${TOKENS.bgSoft} 100%)`,
        fontFamily: FONT,
      }}
    >
      {/* Warm halo behind the icon */}
      <div
        style={{
          position: "absolute",
          right: -120,
          top: -120,
          width: 720,
          height: 720,
          borderRadius: "50%",
          background: `radial-gradient(circle, ${TOKENS.accent}66 0%, transparent 65%)`,
          filter: "blur(20px)",
        }}
      />
      <div
        style={{
          position: "absolute",
          left: -80,
          bottom: -120,
          width: 360,
          height: 360,
          borderRadius: "50%",
          background: `radial-gradient(circle, ${TOKENS.accent}33 0%, transparent 70%)`,
          filter: "blur(20px)",
        }}
      />

      {/* Brand mark */}
      <div
        style={{
          position: "absolute",
          top: 56,
          left: 64,
          display: "flex",
          alignItems: "center",
          gap: 14,
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/app-icon.png"
          alt="StopIt"
          style={{ width: 52, height: 52, borderRadius: 13 }}
          draggable={false}
        />
        <span
          style={{
            fontSize: 28,
            fontWeight: 700,
            color: TOKENS.text,
            letterSpacing: "-0.01em",
          }}
        >
          StopIt
        </span>
      </div>

      {/* Copy */}
      <div style={{ position: "absolute", left: 64, top: 156, width: 560 }}>
        <div
          style={{
            fontSize: 16,
            fontWeight: 700,
            letterSpacing: "0.16em",
            textTransform: "uppercase",
            color: TOKENS.accentDeep,
            display: "flex",
            alignItems: "center",
            gap: 12,
            marginBottom: 22,
          }}
        >
          <span style={{ width: 28, height: 2, background: TOKENS.accentDeep }} />
          {t.label}
        </div>
        <div
          style={{
            fontSize: 62,
            fontWeight: 700,
            lineHeight: 1.04,
            letterSpacing: "-0.045em",
            color: TOKENS.text,
          }}
        >
          <div>{t.l1}</div>
          <div>{t.l2}</div>
        </div>
        <div
          style={{
            fontSize: 22,
            fontWeight: 500,
            color: TOKENS.textMuted,
            marginTop: 22,
            lineHeight: 1.45,
            letterSpacing: "-0.01em",
          }}
        >
          {t.sub}
        </div>
      </div>

      {/* Hero icon on the right */}
      <div
        style={{
          position: "absolute",
          right: 72,
          top: "50%",
          transform: "translateY(-50%)",
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/app-icon.png"
          alt="StopIt icon"
          style={{
            width: 300,
            height: 300,
            borderRadius: 64,
            boxShadow: "0 28px 60px rgba(20,20,30,0.18)",
          }}
          draggable={false}
        />
      </div>
    </div>
  );
}

function FeaturePreview({
  locale,
  onExport,
  busy,
}: {
  locale: Locale;
  onExport: () => void;
  busy: boolean;
}) {
  const cardRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.5);
  useEffect(() => {
    const el = cardRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const w = el.clientWidth;
      if (w > 0) setScale(w / FEATURE_SIZE.w);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return (
    <div style={{ marginBottom: 28 }}>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 10,
        }}
      >
        <div style={{ fontSize: 14, fontWeight: 600, color: TOKENS.text }}>
          Feature Graphic · {FEATURE_SIZE.w}×{FEATURE_SIZE.h}
        </div>
        <button
          onClick={onExport}
          disabled={busy}
          style={{
            padding: "6px 14px",
            borderRadius: 6,
            fontSize: 12,
            fontWeight: 600,
            border: `1px solid ${TOKENS.border}`,
            background: "#fff",
            cursor: busy ? "not-allowed" : "pointer",
            opacity: busy ? 0.5 : 1,
            color: TOKENS.text,
            fontFamily: FONT,
          }}
        >
          PNG
        </button>
      </div>
      <div
        ref={cardRef}
        style={{
          width: "100%",
          aspectRatio: `${FEATURE_SIZE.w} / ${FEATURE_SIZE.h}`,
          position: "relative",
          overflow: "hidden",
          borderRadius: 18,
          background: "#fff",
          boxShadow: "0 1px 3px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.05)",
        }}
      >
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            width: FEATURE_SIZE.w,
            height: FEATURE_SIZE.h,
            transform: `scale(${scale})`,
            transformOrigin: "top left",
          }}
        >
          <FeatureGraphic locale={locale} />
        </div>
      </div>
    </div>
  );
}

// ---------- CSS-only phone frame ----------
function Phone({
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

function PhonePlaceholder({ label }: { label: string }) {
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

// ---------- Caption ----------
function Caption({
  label,
  l1,
  l2,
  sub,
  dark,
  accent,
  canvasW,
}: {
  label: string;
  l1: string;
  l2: string;
  sub: string;
  dark?: boolean;
  accent: "orange" | "red";
  canvasW: number;
}) {
  const labelSize = canvasW * 0.026;
  const headSize = canvasW * 0.082;
  const subSize = canvasW * 0.028;
  // Deeper accent for label legibility on cream bg; brighter on dark bg.
  const labelColor = dark
    ? accent === "red"
      ? TOKENS.danger
      : TOKENS.accent
    : accent === "red"
      ? TOKENS.dangerDeep
      : TOKENS.accentDeep;
  const headColor = dark ? "#FFFFFF" : TOKENS.text;
  const subColor = dark ? "rgba(255,255,255,0.7)" : TOKENS.textMuted;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: canvasW * 0.018 }}>
      <div
        style={{
          fontFamily: FONT,
          fontSize: labelSize,
          fontWeight: 700,
          letterSpacing: "0.14em",
          textTransform: "uppercase",
          color: labelColor,
          display: "flex",
          alignItems: "center",
          gap: canvasW * 0.012,
        }}
      >
        <span
          style={{
            width: canvasW * 0.03,
            height: 2,
            background: labelColor,
            display: "inline-block",
          }}
        />
        {label}
      </div>
      <div
        style={{
          fontFamily: FONT,
          fontSize: headSize,
          fontWeight: 700,
          lineHeight: 1.0,
          letterSpacing: "-0.045em",
          color: headColor,
        }}
      >
        <div>{l1}</div>
        <div>{l2}</div>
      </div>
      <div
        style={{
          fontFamily: FONT,
          fontSize: subSize,
          fontWeight: 500,
          lineHeight: 1.35,
          letterSpacing: "-0.015em",
          color: subColor,
          marginTop: canvasW * 0.012,
        }}
      >
        {sub}
      </div>
    </div>
  );
}

// ---------- Slide background decoration ----------
function Decoration({
  id,
  canvasW,
  canvasH,
}: {
  id: string;
  canvasW: number;
  canvasH: number;
}) {
  // Large saturated halos behind the phone — Opal/Forest style.
  const halo = (style: React.CSSProperties) => (
    <div style={{ position: "absolute", borderRadius: "50%", filter: "blur(16px)", ...style }} />
  );
  // Stronger orange and red gradient stops with extended falloff.
  const orange = (alpha: string) => `radial-gradient(circle, ${TOKENS.accent}${alpha} 0%, transparent 65%)`;
  const red = (alpha: string) => `radial-gradient(circle, ${TOKENS.danger}${alpha} 0%, transparent 65%)`;
  switch (id) {
    case "select":
      return (
        <>
          {halo({
            top: canvasH * 0.18,
            left: "50%",
            transform: "translateX(-50%)",
            width: canvasW * 1.1,
            height: canvasW * 1.1,
            background: orange("66"),
          })}
          {halo({
            top: -canvasW * 0.12,
            right: -canvasW * 0.18,
            width: canvasW * 0.55,
            height: canvasW * 0.55,
            background: orange("44"),
          })}
        </>
      );
    case "timer":
      return (
        <>
          {halo({
            top: canvasH * 0.22,
            left: "50%",
            transform: "translateX(-50%)",
            width: canvasW * 1.15,
            height: canvasW * 1.15,
            background: orange("70"),
          })}
          {halo({
            bottom: -canvasH * 0.03,
            left: -canvasW * 0.2,
            width: canvasW * 0.6,
            height: canvasW * 0.6,
            background: orange("40"),
          })}
        </>
      );
    case "routine":
      return (
        <>
          {halo({
            top: canvasH * 0.2,
            left: "50%",
            transform: "translateX(-50%)",
            width: canvasW * 1.05,
            height: canvasW * 1.05,
            background: orange("5C"),
          })}
          {halo({
            top: canvasH * 0.45,
            right: -canvasW * 0.18,
            width: canvasW * 0.5,
            height: canvasW * 0.5,
            background: orange("3D"),
          })}
        </>
      );
    case "block":
      return (
        <>
          {halo({
            top: canvasH * 0.22,
            left: "50%",
            transform: "translateX(-50%)",
            width: canvasW * 1.2,
            height: canvasW * 1.2,
            background: red("66"),
          })}
          {halo({
            top: -canvasW * 0.1,
            right: -canvasW * 0.05,
            width: canvasW * 0.45,
            height: canvasW * 0.45,
            background: `radial-gradient(circle, ${TOKENS.accent}28 0%, transparent 70%)`,
          })}
        </>
      );
    case "emergency":
      return (
        <>
          {halo({
            top: canvasH * 0.22,
            left: "50%",
            transform: "translateX(-50%)",
            width: canvasW * 1.1,
            height: canvasW * 1.1,
            background: red("4D"),
          })}
          {halo({
            top: canvasH * 0.05,
            left: -canvasW * 0.15,
            width: canvasW * 0.5,
            height: canvasW * 0.5,
            background: red("33"),
          })}
        </>
      );
    case "history":
      return (
        <>
          {halo({
            top: canvasH * 0.2,
            left: "50%",
            transform: "translateX(-50%)",
            width: canvasW * 1.1,
            height: canvasW * 1.1,
            background: orange("66"),
          })}
          {halo({
            bottom: -canvasW * 0.08,
            right: -canvasW * 0.2,
            width: canvasW * 0.55,
            height: canvasW * 0.55,
            background: orange("3D"),
          })}
        </>
      );
    default:
      return null;
  }
}

// ---------- Per-slide brand mark (only on hero #1) ----------
function BrandMark({ canvasW }: { canvasW: number }) {
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
          color: TOKENS.text,
          letterSpacing: "-0.01em",
        }}
      >
        StopIt
      </div>
    </div>
  );
}

// ---------- A full slide rendered at the full canvas resolution ----------
function SlideShell({
  slide,
  canvasW,
  canvasH,
}: {
  slide: Slide;
  canvasW: number;
  canvasH: number;
}) {
  const isDark = slide.variant === "dark";
  const isHero = slide.id === "select";
  return (
    <div
      style={{
        position: "relative",
        width: canvasW,
        height: canvasH,
        overflow: "hidden",
        background: isDark ? TOKENS.bgDark : TOKENS.bg,
        fontFamily: FONT,
      }}
    >
      {/* Layered background */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          background: isDark
            ? `radial-gradient(ellipse 80% 60% at 50% 10%, ${TOKENS.bgDarkSoft} 0%, ${TOKENS.bgDark} 70%)`
            : `linear-gradient(180deg, ${TOKENS.bg} 0%, ${TOKENS.bgSoft} 100%)`,
        }}
      />
      <Decoration id={slide.id} canvasW={canvasW} canvasH={canvasH} />

      {/* Hero BrandMark anchored to the top-right corner so it doesn't
          eat caption space. */}
      {isHero && (
        <div
          style={{
            position: "absolute",
            top: canvasH * 0.045,
            right: canvasW * 0.075,
          }}
        >
          <BrandMark canvasW={canvasW} />
        </div>
      )}

      {/* Top caption block */}
      <div
        style={{
          position: "absolute",
          top: canvasH * 0.045,
          left: canvasW * 0.075,
          right: canvasW * 0.075,
        }}
      >
        <Caption
          label={slide.label}
          l1={slide.l1}
          l2={slide.l2}
          sub={slide.sub}
          dark={isDark}
          accent={slide.accent}
          canvasW={canvasW}
        />
      </div>

      {/* Phone */}
      <div
        style={{
          position: "absolute",
          bottom: 0,
          left: "50%",
          transform: `translate(-50%, ${canvasH * slide.phoneTranslateY}px) rotate(${slide.tilt ?? 0}deg)`,
          transformOrigin: "50% 50%",
          width: canvasW * slide.phoneWidthRatio,
        }}
      >
        <Phone
          src={slide.src}
          alt={slide.placeholder}
          placeholder={slide.placeholder}
          dark={isDark}
        />
      </div>
    </div>
  );
}

// ---------- Preview card (ResizeObserver scaling) ----------
function PreviewCard({
  slide,
  canvasW,
  canvasH,
  onExport,
  busy,
}: {
  slide: Slide;
  canvasW: number;
  canvasH: number;
  onExport: (id: string) => void;
  busy: boolean;
}) {
  const cardRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.2);

  useEffect(() => {
    const el = cardRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const w = el.clientWidth;
      if (w > 0) setScale(w / canvasW);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [canvasW]);

  return (
    <div>
      <div
        ref={cardRef}
        style={{
          width: "100%",
          aspectRatio: `${canvasW} / ${canvasH}`,
          position: "relative",
          overflow: "hidden",
          borderRadius: 18,
          background: "#fff",
          boxShadow: "0 1px 3px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.05)",
        }}
      >
        <div
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            width: canvasW,
            height: canvasH,
            transform: `scale(${scale})`,
            transformOrigin: "top left",
          }}
        >
          <SlideShell slide={slide} canvasW={canvasW} canvasH={canvasH} />
        </div>
      </div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginTop: 10,
        }}
      >
        <div style={{ fontSize: 13, color: TOKENS.textMuted }}>
          {slide.filename} · {slide.placeholder}
        </div>
        <button
          onClick={() => onExport(slide.id)}
          disabled={busy}
          style={{
            padding: "4px 12px",
            borderRadius: 6,
            fontSize: 12,
            fontWeight: 600,
            border: `1px solid ${TOKENS.border}`,
            background: "#fff",
            cursor: busy ? "not-allowed" : "pointer",
            opacity: busy ? 0.5 : 1,
            color: TOKENS.text,
            fontFamily: FONT,
          }}
        >
          PNG
        </button>
      </div>
    </div>
  );
}

// ---------- Page ----------
export default function Page() {
  const [sizeIdx, setSizeIdx] = useState(0);
  const W = PHONE_SIZES[sizeIdx].w;
  const H = PHONE_SIZES[sizeIdx].h;

  const [locale, setLocale] = useState<Locale>("ko");
  const slides = buildSlides(locale);
  const t = UI[locale];

  // ?solo=<id>&locale=<ko|en> renders a single slide at native 1080×1920 with no chrome.
  // Useful for headless screenshots of a single slide.
  const [solo, setSolo] = useState<Slide | null>(null);
  useEffect(() => {
    if (typeof window === "undefined") return;
    const params = new URLSearchParams(window.location.search);
    const localeParam = params.get("locale");
    const initialLocale: Locale = localeParam === "en" ? "en" : "ko";
    if (localeParam === "en") setLocale("en");
    const id = params.get("solo");
    if (id) {
      const s = buildSlides(initialLocale).find((x) => x.id === id);
      if (s) setSolo(s);
    }
  }, []);

  const exportRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const setExportRef = useCallback(
    (id: string) => (node: HTMLDivElement | null) => {
      exportRefs.current[id] = node;
    },
    [],
  );
  const [busy, setBusy] = useState(false);

  const exportSlide = useCallback(
    async (slide: Slide) => {
      const el = exportRefs.current[slide.id];
      if (!el) return null;
      // Move on-screen for capture
      el.style.left = "0px";
      el.style.opacity = "1";
      el.style.zIndex = "-1";
      const opts = {
        width: W,
        height: H,
        pixelRatio: 1,
        cacheBust: true,
        style: { fontFamily: FONT } as Record<string, string>,
      };
      try {
        // Double-call: first warms up fonts/images, second produces clean output.
        await toPng(el, opts);
        const dataUrl = await toPng(el, opts);
        return dataUrl;
      } finally {
        el.style.left = "-9999px";
        el.style.opacity = "";
        el.style.zIndex = "";
      }
    },
    [W, H],
  );

  const downloadDataUrl = (dataUrl: string, name: string) => {
    const a = document.createElement("a");
    a.href = dataUrl;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const exportOne = async (id: string) => {
    const slide = slides.find((s) => s.id === id);
    if (!slide) return;
    setBusy(true);
    try {
      const dataUrl = await exportSlide(slide);
      if (dataUrl) downloadDataUrl(dataUrl, `${slide.filename}-${locale}-${W}x${H}.png`);
    } finally {
      setBusy(false);
    }
  };

  const exportAll = async () => {
    setBusy(true);
    try {
      for (const slide of slides) {
        const dataUrl = await exportSlide(slide);
        if (dataUrl) downloadDataUrl(dataUrl, `${slide.filename}-${locale}-${W}x${H}.png`);
        await new Promise((r) => setTimeout(r, 300));
      }
    } finally {
      setBusy(false);
    }
  };

  const featureRef = useRef<HTMLDivElement>(null);
  const exportFeature = useCallback(async () => {
    const el = featureRef.current;
    if (!el) return;
    setBusy(true);
    try {
      el.style.left = "0px";
      el.style.opacity = "1";
      el.style.zIndex = "-1";
      const opts = {
        width: FEATURE_SIZE.w,
        height: FEATURE_SIZE.h,
        pixelRatio: 1,
        cacheBust: true,
        style: { fontFamily: FONT } as Record<string, string>,
      };
      try {
        await toPng(el, opts);
        const dataUrl = await toPng(el, opts);
        downloadDataUrl(
          dataUrl,
          `feature-graphic-${locale}-${FEATURE_SIZE.w}x${FEATURE_SIZE.h}.png`,
        );
      } finally {
        el.style.left = "-9999px";
        el.style.opacity = "";
        el.style.zIndex = "";
      }
    } finally {
      setBusy(false);
    }
  }, [locale]);

  if (solo) {
    return (
      <div style={{ width: W, height: H, overflow: "hidden", fontFamily: FONT }}>
        <SlideShell slide={solo} canvasW={W} canvasH={H} />
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100vh", padding: 24, fontFamily: FONT, position: "relative" }}>
      <div style={{ maxWidth: 1280, margin: "0 auto" }}>
        <header
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginBottom: 24,
            flexWrap: "wrap",
            gap: 12,
          }}
        >
          <div>
            <h1
              style={{
                fontSize: 22,
                fontWeight: 700,
                color: TOKENS.text,
                margin: 0,
                letterSpacing: "-0.02em",
              }}
            >
              {t.title}
            </h1>
            <p style={{ fontSize: 13, color: TOKENS.textMuted, margin: "4px 0 0" }}>
              {t.meta(slides.length, W, H)}
            </p>
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <div
              role="tablist"
              style={{
                display: "inline-flex",
                border: `1px solid ${TOKENS.border}`,
                borderRadius: 8,
                overflow: "hidden",
                background: "#fff",
              }}
            >
              {(["ko", "en"] as Locale[]).map((loc) => {
                const active = locale === loc;
                return (
                  <button
                    key={loc}
                    role="tab"
                    aria-selected={active}
                    onClick={() => setLocale(loc)}
                    style={{
                      padding: "8px 14px",
                      border: "none",
                      background: active ? TOKENS.text : "transparent",
                      color: active ? "#fff" : TOKENS.textMuted,
                      fontWeight: 600,
                      fontSize: 13,
                      cursor: "pointer",
                      fontFamily: FONT,
                      letterSpacing: "0.04em",
                    }}
                  >
                    {loc.toUpperCase()}
                  </button>
                );
              })}
            </div>
            <select
              value={sizeIdx}
              onChange={(e) => setSizeIdx(Number(e.target.value))}
              style={{
                padding: "8px 12px",
                borderRadius: 8,
                border: `1px solid ${TOKENS.border}`,
                background: "#fff",
                fontFamily: FONT,
                fontSize: 14,
                color: TOKENS.text,
              }}
            >
              {PHONE_SIZES.map((s, i) => (
                <option key={i} value={i}>
                  {s.label}
                </option>
              ))}
            </select>
            <button
              onClick={exportAll}
              disabled={busy}
              style={{
                padding: "8px 16px",
                borderRadius: 8,
                border: "none",
                background: TOKENS.text,
                color: "#fff",
                fontWeight: 600,
                cursor: busy ? "not-allowed" : "pointer",
                opacity: busy ? 0.5 : 1,
                fontFamily: FONT,
                fontSize: 14,
              }}
            >
              {busy ? t.exporting : t.exportAll}
            </button>
          </div>
        </header>

        <FeaturePreview locale={locale} onExport={exportFeature} busy={busy} />

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
            gap: 20,
          }}
        >
          {slides.map((slide) => (
            <PreviewCard
              key={slide.id}
              slide={slide}
              canvasW={W}
              canvasH={H}
              onExport={exportOne}
              busy={busy}
            />
          ))}
        </div>

        <p style={{ marginTop: 24, fontSize: 12, color: TOKENS.textSub, lineHeight: 1.6 }}>
          {t.footerStart}
          <code>public/screenshots/01-select.png</code>
          {t.footerMid}
          <code>06-history.png</code>
          {t.footerEnd}
        </p>
      </div>

      {/* Offscreen full-resolution export nodes */}
      {slides.map((slide) => (
        <div
          key={`${slide.id}-export`}
          ref={setExportRef(slide.id)}
          style={{
            position: "absolute",
            left: -9999,
            top: 0,
            width: W,
            height: H,
            fontFamily: FONT,
          }}
        >
          <SlideShell slide={slide} canvasW={W} canvasH={H} />
        </div>
      ))}
      <div
        ref={featureRef}
        style={{
          position: "absolute",
          left: -9999,
          top: 0,
          width: FEATURE_SIZE.w,
          height: FEATURE_SIZE.h,
          fontFamily: FONT,
        }}
      >
        <FeatureGraphic locale={locale} />
      </div>
    </div>
  );
}
