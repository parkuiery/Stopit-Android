"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toPng } from "html-to-image";
import { TOKENS, FONT, Phone, BrandMark } from "../_shared/brand";

// The shared Phone frame is drawn at 9:19.5, but the captures under
// public/screenshots are 1080×1920. Letting `contain` fit a 9:16 image into a
// 9:19.5 screen leaves white bands above and below the UI. Sizing the frame so
// its inner screen area (94% × 96.4% of the box) matches 9:16 removes them.
const PHONE_ASPECT = 0.94 / (0.964 * (1080 / 1920));

/**
 * Google Ads App campaign image assets: landscape (1.91:1), square (1:1) and
 * portrait (4:5).
 *
 * Geometry is declared per size rather than derived, because the copy block and
 * the device must never overlap. `copy.top`/`phone.top` are canvas-height
 * fractions and are chosen so the device always starts below the last line of
 * body copy (or beside it, on the landscape canvas).
 */
const SIZES = [
  {
    id: "landscape",
    label: "가로 1200×628",
    w: 1200,
    h: 628,
    ratio: "16x9",
    head: 0.07,
    sub: 0.027,
    copy: { top: 0.2, width: 0.44 },
    phone: { w: 0.34, top: 0.14, anchor: "right" },
  },
  {
    id: "square",
    label: "정사각 1200×1200",
    w: 1200,
    h: 1200,
    ratio: "1x1",
    head: 0.08,
    sub: 0.03,
    copy: { top: 0.11, width: 0.8 },
    phone: { w: 0.46, top: 0.45, anchor: "center" },
  },
  {
    id: "portrait",
    label: "세로 1200×1500",
    w: 1200,
    h: 1500,
    ratio: "4x5",
    head: 0.082,
    sub: 0.031,
    copy: { top: 0.11, width: 0.8 },
    phone: { w: 0.52, top: 0.38, anchor: "center" },
  },
] as const;

type Size = (typeof SIZES)[number];

/**
 * Each concept pairs a generated scene (no text, no UI) with a real device
 * capture from public/screenshots. The app UI is never AI-generated: showing a
 * screen the app does not have would breach Google Ads misrepresentation policy
 * and Play listing policy alike.
 */
type Concept = {
  id: string;
  scene: string; // basename under /ads/scenes; the ratio suffix is appended per size
  l1: string;
  l2: string;
  sub: string;
  shot: string;
  shotDark: boolean;
};

const CONCEPTS: Concept[] = [
  {
    id: "focus",
    scene: "a-dawn",
    l1: "공부할 때만",
    l2: "폰이 잠겨요",
    sub: "타이머 한 번이면 정해둔 시간 동안\n유혹 앱이 열리지 않습니다",
    shot: "/screenshots/02-timer.png",
    shotDark: false,
  },
  {
    id: "block",
    scene: "b-facedown",
    l1: "열어도",
    l2: "바로 막힙니다",
    sub: "차단한 앱을 열면\n잠금 화면이 대신 뜹니다",
    shot: "/screenshots/04-block.png",
    shotDark: true,
  },
  {
    id: "record",
    scene: "c-reward",
    l1: "버틴 시간이",
    l2: "남습니다",
    sub: "오늘 몇 시간을 지켰는지\n기록으로 쌓입니다",
    shot: "/screenshots/06-history.png",
    shotDark: false,
  },
];

const CREAM = "251, 248, 242"; // TOKENS.bg as an rgb triplet for scrim gradients

/**
 * Copy sits on a cream scrim rather than on bare photography. Scenes are
 * generated separately per ratio, so their bright areas move around; the scrim
 * keeps the headline legible without hand-tuning text against every render.
 */
function Scrim({ size }: { size: Size }) {
  const horizontal = size.id === "landscape";
  const gradient = horizontal
    ? `linear-gradient(90deg, rgba(${CREAM},0.96) 0%, rgba(${CREAM},0.90) 34%, rgba(${CREAM},0.52) 52%, rgba(${CREAM},0) 68%)`
    : `linear-gradient(180deg, rgba(${CREAM},0.96) 0%, rgba(${CREAM},0.90) 30%, rgba(${CREAM},0.52) 46%, rgba(${CREAM},0) 64%)`;
  return <div style={{ position: "absolute", inset: 0, background: gradient }} />;
}

// ---------- One artboard at native export resolution ----------
function Artboard({ concept, size }: { concept: Concept; size: Size }) {
  const { w: W, h: H } = size;
  const [sceneFailed, setSceneFailed] = useState(false);
  // Scenes are photographic, so they are stored as JPEG; PNG cost 40x the bytes
  // for no visible gain at the 1200px canvas widths these are consumed at.
  const sceneSrc = `/ads/scenes/${concept.scene}-${size.ratio}.jpg`;
  const phoneW = W * size.phone.w;

  return (
    <div
      style={{
        position: "relative",
        width: W,
        height: H,
        overflow: "hidden",
        background: TOKENS.bg,
        fontFamily: FONT,
      }}
    >
      {sceneFailed ? (
        <div
          style={{
            position: "absolute",
            inset: 0,
            background: `linear-gradient(135deg, ${TOKENS.bgSoft} 0%, ${TOKENS.bg} 100%)`,
          }}
        />
      ) : (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={sceneSrc}
          alt=""
          onError={() => setSceneFailed(true)}
          style={{
            position: "absolute",
            inset: 0,
            width: "100%",
            height: "100%",
            objectFit: "cover",
            objectPosition: "center",
          }}
          draggable={false}
        />
      )}

      <Scrim size={size} />

      <div style={{ position: "absolute", top: H * 0.05, left: W * 0.062 }}>
        <BrandMark canvasW={W} />
      </div>

      {/* Copy */}
      <div
        style={{
          position: "absolute",
          top: H * size.copy.top,
          left: W * 0.062,
          width: W * size.copy.width,
          display: "flex",
          flexDirection: "column",
          gap: W * 0.022,
        }}
      >
        <div
          style={{
            fontSize: W * size.head,
            fontWeight: 700,
            lineHeight: 1.18,
            letterSpacing: "-0.035em",
            color: TOKENS.text,
          }}
        >
          {concept.l1}
          <br />
          <span style={{ color: TOKENS.accentDeep }}>{concept.l2}</span>
        </div>
        <div
          style={{
            fontSize: W * size.sub,
            fontWeight: 500,
            lineHeight: 1.5,
            letterSpacing: "-0.015em",
            color: TOKENS.textMuted,
            whiteSpace: "pre-line",
          }}
        >
          {concept.sub}
        </div>
      </div>

      {/* Device — overflows the bottom edge so it reads as grounded */}
      <div
        style={{
          position: "absolute",
          top: H * size.phone.top,
          width: phoneW,
          height: phoneW * PHONE_ASPECT,
          ...(size.phone.anchor === "right"
            ? { right: W * 0.06 }
            : { left: "50%", transform: "translateX(-50%)" }),
        }}
      >
        <Phone
          src={concept.shot}
          alt={concept.id}
          placeholder="캡처 없음"
          dark={concept.shotDark}
          style={{ width: "100%", height: "100%", aspectRatio: undefined }}
        />
      </div>
    </div>
  );
}

// ---------- Scaled preview card ----------
function PreviewCard({
  concept,
  size,
  onExport,
  busy,
}: {
  concept: Concept;
  size: Size;
  onExport: () => void;
  busy: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.3);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const w = el.clientWidth;
      if (w > 0) setScale(w / size.w);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [size.w]);

  return (
    <div
      style={{
        background: "#fff",
        borderRadius: 16,
        border: `1px solid ${TOKENS.border}`,
        overflow: "hidden",
      }}
    >
      <div ref={ref} style={{ width: "100%", height: size.h * scale, position: "relative" }}>
        <div style={{ transform: `scale(${scale})`, transformOrigin: "top left" }}>
          <Artboard concept={concept} size={size} />
        </div>
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "10px 14px",
          borderTop: `1px solid ${TOKENS.border}`,
          fontSize: 13,
          color: TOKENS.textMuted,
        }}
      >
        <span>
          {concept.id} · {size.label}
        </span>
        <button
          onClick={onExport}
          disabled={busy}
          style={{
            padding: "6px 12px",
            borderRadius: 8,
            border: "none",
            background: busy ? TOKENS.border : TOKENS.text,
            color: "#fff",
            fontSize: 13,
            fontWeight: 600,
            cursor: busy ? "default" : "pointer",
          }}
        >
          PNG
        </button>
      </div>
    </div>
  );
}

export default function AdsPage() {
  const [busy, setBusy] = useState(false);
  const [solo, setSolo] = useState<{ concept: Concept; size: Size } | null>(null);

  // ?solo=<conceptId>-<sizeId> renders one artboard at native size with no
  // chrome, so a headless browser can screenshot it directly.
  useEffect(() => {
    if (typeof window === "undefined") return;
    const raw = new URLSearchParams(window.location.search).get("solo");
    if (!raw) return;
    const size = SIZES.find((s) => raw.endsWith(`-${s.id}`));
    if (!size) return;
    const concept = CONCEPTS.find((c) => c.id === raw.slice(0, raw.length - size.id.length - 1));
    if (concept) setSolo({ concept, size });
  }, []);

  const refs = useRef<Record<string, HTMLDivElement | null>>({});
  const setRef = useCallback(
    (key: string) => (node: HTMLDivElement | null) => {
      refs.current[key] = node;
    },
    [],
  );

  const exportOne = useCallback(async (concept: Concept, size: Size) => {
    const el = refs.current[`${concept.id}-${size.id}`];
    if (!el) return;
    el.style.left = "0px";
    el.style.opacity = "1";
    el.style.zIndex = "-1";
    const opts = {
      width: size.w,
      height: size.h,
      pixelRatio: 1,
      cacheBust: true,
      style: { fontFamily: FONT } as Record<string, string>,
    };
    try {
      // First pass warms fonts and decodes images; the second yields clean output.
      await toPng(el, opts);
      const dataUrl = await toPng(el, opts);
      const a = document.createElement("a");
      a.href = dataUrl;
      a.download = `ads-${concept.id}-${size.w}x${size.h}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } finally {
      el.style.left = "-9999px";
      el.style.opacity = "";
      el.style.zIndex = "";
    }
  }, []);

  const exportAll = async () => {
    setBusy(true);
    try {
      for (const concept of CONCEPTS) {
        for (const size of SIZES) {
          await exportOne(concept, size);
          await new Promise((r) => setTimeout(r, 300));
        }
      }
    } finally {
      setBusy(false);
    }
  };

  if (solo) {
    return (
      <div style={{ width: solo.size.w, height: solo.size.h, overflow: "hidden", fontFamily: FONT }}>
        <Artboard concept={solo.concept} size={solo.size} />
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
            marginBottom: 20,
          }}
        >
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 700, color: TOKENS.text }}>
              StopIt — Google Ads 앱 캠페인 이미지 소재
            </h1>
            <p style={{ fontSize: 13, color: TOKENS.textSub, marginTop: 4 }}>
              수험생·취준생 타깃 · {CONCEPTS.length}컨셉 × {SIZES.length}규격 ={" "}
              {CONCEPTS.length * SIZES.length}장
            </p>
          </div>
          <button
            onClick={exportAll}
            disabled={busy}
            style={{
              padding: "10px 18px",
              borderRadius: 10,
              border: "none",
              background: busy ? TOKENS.border : TOKENS.accentDeep,
              color: "#fff",
              fontSize: 14,
              fontWeight: 700,
              cursor: busy ? "default" : "pointer",
            }}
          >
            {busy ? "내보내는 중…" : "전체 PNG 내보내기"}
          </button>
        </header>

        {CONCEPTS.map((concept) => (
          <section key={concept.id} style={{ marginBottom: 32 }}>
            <h2 style={{ fontSize: 15, fontWeight: 600, color: TOKENS.textMid, marginBottom: 10 }}>
              {concept.l1} {concept.l2}
            </h2>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 }}>
              {SIZES.map((size) => (
                <PreviewCard
                  key={size.id}
                  concept={concept}
                  size={size}
                  busy={busy}
                  onExport={async () => {
                    setBusy(true);
                    try {
                      await exportOne(concept, size);
                    } finally {
                      setBusy(false);
                    }
                  }}
                />
              ))}
            </div>
          </section>
        ))}
      </div>

      {/* Off-screen native-resolution artboards used as export sources */}
      {CONCEPTS.map((concept) =>
        SIZES.map((size) => (
          <div
            key={`${concept.id}-${size.id}`}
            ref={setRef(`${concept.id}-${size.id}`)}
            style={{ position: "absolute", top: 0, left: -9999, opacity: 0, pointerEvents: "none" }}
          >
            <Artboard concept={concept} size={size} />
          </div>
        )),
      )}
    </div>
  );
}
