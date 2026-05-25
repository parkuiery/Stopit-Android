const firebaseProjectId = "stopit-be785";
const productionPromotionCustomIdPrefix = "stopit_promote_production";

export function issueUrl(appId: string, issueId?: string): string | null {
  if (!issueId) return null;
  return `https://console.firebase.google.com/project/${firebaseProjectId}/crashlytics/app/${encodeURIComponent(appId)}/issues/${encodeURIComponent(issueId)}`;
}

export function hexToBytes(hex: string): Uint8Array {
  if (!/^[0-9a-fA-F]+$/.test(hex) || hex.length % 2 !== 0) {
    throw new Error("Invalid hex string");
  }

  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = Number.parseInt(hex.slice(i, i + 2), 16);
  }
  return bytes;
}

export function csvSecretValues(raw: string): Set<string> {
  return new Set(
    raw
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
  );
}

export function promotionTagFromCustomId(customId: string | undefined): string | null {
  if (!customId) return null;

  const [prefix, tag] = customId.split(":");
  if (prefix !== productionPromotionCustomIdPrefix) return null;
  if (!/^v\d+\.\d+\.\d+$/.test(tag ?? "")) return null;
  return tag;
}
