const firebaseProjectId = "stopit-be785";
const productionPromotionCustomIdPrefix = "stopit_promote_production";
const githubIssueSearchBaseUrl = "https://github.com/parkuiery/Stopit-Android/issues";

export type CrashlyticsIssueSummary = {
  id?: string;
  title?: string;
  subtitle?: string;
  appVersion?: string;
  issueType?: string;
};

export type CrashlyticsAlertSummary = {
  kind: string;
  emoji: string;
  appId: string;
  issue: CrashlyticsIssueSummary;
};

export function issueUrl(appId: string, issueId?: string): string | null {
  if (!issueId) return null;
  return `https://console.firebase.google.com/project/${firebaseProjectId}/crashlytics/app/${encodeURIComponent(appId)}/issues/${encodeURIComponent(issueId)}`;
}

export function githubIssueSearchUrl(issueId?: string): string | null {
  if (!issueId) return null;
  const params = new URLSearchParams({ q: `is:issue "${issueId}"` });
  return `${githubIssueSearchBaseUrl}?${params.toString()}`;
}

export function formatCrashlyticsAlert({ kind, emoji, appId, issue }: CrashlyticsAlertSummary): string {
  const consoleUrl = issueUrl(appId, issue.id);
  const duplicateSearchUrl = githubIssueSearchUrl(issue.id);
  const issueTypeSuffix = issue.issueType ? ` (${issue.issueType})` : "";
  const lines = [
    `${emoji} **Stopit Crashlytics Alert**`,
    `- 유형: ${kind}${issueTypeSuffix}`,
    `- 앱: ${appId}`,
    `- 버전: ${issue.appVersion ?? "unknown"}`,
    `- 이슈: ${issue.title ?? "unknown issue"}`,
  ];

  if (issue.subtitle) lines.push(`- 요약: ${issue.subtitle}`);
  if (issue.id) lines.push(`- Issue ID: \`${issue.id}\``);
  if (consoleUrl) lines.push(`- Crashlytics: ${consoleUrl}`);
  if (duplicateSearchUrl) lines.push(`- GitHub 중복 검색: ${duplicateSearchUrl}`);
  lines.push("- QA 다음 단계: Crashlytics stack trace·영향 버전·affected users를 GitHub 이슈/PR에 복사하고, 같은 Issue ID 중복 여부를 먼저 확인");

  return lines.join("\n");
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
