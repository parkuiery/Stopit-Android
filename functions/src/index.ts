import { onNewFatalIssuePublished, onNewNonfatalIssuePublished, onNewAnrIssuePublished, onRegressionAlertPublished, onVelocityAlertPublished } from "firebase-functions/v2/alerts/crashlytics";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";

type CrashlyticsIssue = {
  id?: string;
  title?: string;
  subtitle?: string;
  appVersion?: string;
  issueType?: string;
};

const discordWebhookUrl = defineSecret("DISCORD_WEBHOOK_URL");

function issueSummary(event: any): CrashlyticsIssue {
  return event?.data?.payload?.issue ?? {};
}

function issueUrl(appId: string, issueId?: string) {
  if (!issueId) return null;
  return `https://console.firebase.google.com/project/stopit-be785/crashlytics/app/${encodeURIComponent(appId)}/issues/${encodeURIComponent(issueId)}`;
}

async function postToDiscord(username: string, content: string) {
  const webhook = discordWebhookUrl.value();
  const response = await fetch(webhook, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, content }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Discord webhook failed: ${response.status} ${body}`);
  }
}

async function sendCrashlyticsAlert(kind: string, emoji: string, event: any) {
  const appId = event.appId ?? "unknown-app";
  const issue = issueSummary(event);
  const url = issueUrl(appId, issue.id);
  const lines = [
    `${emoji} **Stopit Crashlytics Alert**`,
    `- 유형: ${kind}`,
    `- 앱: ${appId}`,
    `- 버전: ${issue.appVersion ?? "unknown"}`,
    `- 이슈: ${issue.title ?? "unknown issue"}`,
  ];

  if (issue.subtitle) lines.push(`- 요약: ${issue.subtitle}`);
  if (issue.id) lines.push(`- Issue ID: \`${issue.id}\``);
  if (url) lines.push(`- 링크: ${url}`);

  const content = lines.join("\n");
  logger.info("Sending Crashlytics alert to Discord", { kind, appId, issueId: issue.id });
  await postToDiscord("Stopit Crashlytics", content);
}

export const postNewFatalIssueToDiscord = onNewFatalIssuePublished(
  { secrets: [discordWebhookUrl], region: "asia-northeast3" },
  async (event) => {
    await sendCrashlyticsAlert("new_fatal_issue", "🚨", event);
  },
);

export const postNewNonfatalIssueToDiscord = onNewNonfatalIssuePublished(
  { secrets: [discordWebhookUrl], region: "asia-northeast3" },
  async (event) => {
    await sendCrashlyticsAlert("new_nonfatal_issue", "⚠️", event);
  },
);

export const postNewAnrIssueToDiscord = onNewAnrIssuePublished(
  { secrets: [discordWebhookUrl], region: "asia-northeast3" },
  async (event) => {
    await sendCrashlyticsAlert("new_anr_issue", "🧊", event);
  },
);

export const postRegressionAlertToDiscord = onRegressionAlertPublished(
  { secrets: [discordWebhookUrl], region: "asia-northeast3" },
  async (event) => {
    await sendCrashlyticsAlert("regression_alert", "📈", event);
  },
);

export const postVelocityAlertToDiscord = onVelocityAlertPublished(
  { secrets: [discordWebhookUrl], region: "asia-northeast3" },
  async (event) => {
    await sendCrashlyticsAlert("velocity_alert", "🔥", event);
  },
);
