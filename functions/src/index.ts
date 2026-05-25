import { onRequest } from "firebase-functions/v2/https";
import { onNewFatalIssuePublished, onNewNonfatalIssuePublished, onNewAnrIssuePublished, onRegressionAlertPublished, onVelocityAlertPublished } from "firebase-functions/v2/alerts/crashlytics";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import nacl from "tweetnacl";

const githubOwner = "parkuiery";
const githubRepo = "Stopit-Android";
const playDeployWorkflow = "play-deploy.yml";
const productionPromotionCustomIdPrefix = "stopit_promote_production";

type CrashlyticsIssue = {
  id?: string;
  title?: string;
  subtitle?: string;
  appVersion?: string;
  issueType?: string;
};

type DiscordInteraction = {
  type?: number;
  id?: string;
  token?: string;
  application_id?: string;
  channel_id?: string;
  member?: {
    user?: { id?: string; username?: string };
    roles?: string[];
  };
  user?: { id?: string; username?: string };
  data?: {
    custom_id?: string;
  };
};

const discordWebhookUrl = defineSecret("DISCORD_WEBHOOK_URL");
const discordPublicKey = defineSecret("DISCORD_PUBLIC_KEY");
const discordDeployChannelId = defineSecret("DISCORD_DEPLOY_CHANNEL_ID");
const discordDeployAllowedRoleIds = defineSecret("DISCORD_DEPLOY_ALLOWED_ROLE_IDS");
const discordDeployAllowedUserIds = defineSecret("DISCORD_DEPLOY_ALLOWED_USER_IDS");
const githubActionsDispatchToken = defineSecret("GITHUB_ACTIONS_DISPATCH_TOKEN");

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

function hexToBytes(hex: string): Uint8Array {
  if (!/^[0-9a-fA-F]+$/.test(hex) || hex.length % 2 !== 0) {
    throw new Error("Invalid hex string");
  }

  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = Number.parseInt(hex.slice(i, i + 2), 16);
  }
  return bytes;
}

function csvSecretValues(raw: string): Set<string> {
  return new Set(
    raw
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
  );
}

function verifyDiscordRequest(signature: string | undefined, timestamp: string | undefined, rawBody: Buffer): boolean {
  if (!signature || !timestamp) return false;

  const publicKey = hexToBytes(discordPublicKey.value());
  const signatureBytes = hexToBytes(signature);
  const timestampBytes = Buffer.from(timestamp, "utf8");
  const message = Buffer.concat([timestampBytes, rawBody]);

  return nacl.sign.detached.verify(new Uint8Array(message), signatureBytes, publicKey);
}

function promotionTagFromCustomId(customId: string | undefined): string | null {
  if (!customId) return null;

  const [prefix, tag] = customId.split(":");
  if (prefix !== productionPromotionCustomIdPrefix) return null;
  if (!/^v\d+\.\d+\.\d+$/.test(tag ?? "")) return null;
  return tag;
}

function userCanPromote(interaction: DiscordInteraction): boolean {
  const configuredChannelId = discordDeployChannelId.value().trim();
  if (configuredChannelId && interaction.channel_id !== configuredChannelId) {
    return false;
  }

  const userId = interaction.member?.user?.id ?? interaction.user?.id ?? "";
  const userIds = csvSecretValues(discordDeployAllowedUserIds.value());
  if (userId && userIds.has(userId)) {
    return true;
  }

  const roleIds = csvSecretValues(discordDeployAllowedRoleIds.value());
  const memberRoles = interaction.member?.roles ?? [];
  if (memberRoles.some((roleId) => roleIds.has(roleId))) {
    return true;
  }

  return false;
}

async function dispatchProductionPromotion(tag: string) {
  const response = await fetch(
    `https://api.github.com/repos/${githubOwner}/${githubRepo}/actions/workflows/${playDeployWorkflow}/dispatches`,
    {
      method: "POST",
      headers: {
        "Accept": "application/vnd.github+json",
        "Authorization": `Bearer ${githubActionsDispatchToken.value()}`,
        "Content-Type": "application/json",
        "User-Agent": "stopit-discord-deploy-bot",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      body: JSON.stringify({
        ref: tag,
        inputs: {
          track: "production",
          release_status: "completed",
          rollout_fraction: "",
        },
      }),
    },
  );

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub workflow dispatch failed: ${response.status} ${body}`);
  }
}

async function updateDiscordInteractionResponse(interaction: DiscordInteraction, content: string) {
  if (!interaction.application_id || !interaction.token) {
    logger.warn("Cannot update Discord interaction response without application_id/token", {
      interactionId: interaction.id,
    });
    return;
  }

  const response = await fetch(
    `https://discord.com/api/v10/webhooks/${interaction.application_id}/${interaction.token}/messages/@original`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ content }),
    },
  );

  if (!response.ok) {
    const body = await response.text();
    logger.warn("Failed to update Discord interaction response", {
      status: response.status,
      body,
      interactionId: interaction.id,
    });
  }
}

function interactionResponse(content: string, status = 200) {
  return {
    status,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      type: 4,
      data: {
        content,
        flags: 64,
      },
    }),
  };
}

export const promoteProductionFromDiscord = onRequest(
  {
    region: "asia-northeast3",
    secrets: [
      discordPublicKey,
      discordDeployChannelId,
      discordDeployAllowedRoleIds,
      discordDeployAllowedUserIds,
      githubActionsDispatchToken,
    ],
  },
  async (request, response) => {
    if (request.method !== "POST") {
      response.status(405).send("Method Not Allowed");
      return;
    }

    const rawBody = (request as any).rawBody as Buffer | undefined;
    if (!rawBody) {
      response.status(400).send("Missing raw body");
      return;
    }

    const signature = request.get("x-signature-ed25519");
    const timestamp = request.get("x-signature-timestamp");
    if (!verifyDiscordRequest(signature, timestamp, rawBody)) {
      logger.warn("Rejected Discord interaction with invalid signature");
      response.status(401).send("invalid request signature");
      return;
    }

    const interaction = JSON.parse(rawBody.toString("utf8")) as DiscordInteraction;

    if (interaction.type === 1) {
      response.status(200).json({ type: 1 });
      return;
    }

    if (interaction.type !== 3) {
      response.status(200).json({ type: 4, data: { content: "지원하지 않는 Discord interaction입니다.", flags: 64 } });
      return;
    }

    const tag = promotionTagFromCustomId(interaction.data?.custom_id);
    if (!tag) {
      response.status(200).json({ type: 4, data: { content: "알 수 없는 배포 버튼입니다.", flags: 64 } });
      return;
    }

    if (!userCanPromote(interaction)) {
      logger.warn("Rejected unauthorized production promotion", {
        tag,
        channelId: interaction.channel_id,
        userId: interaction.member?.user?.id ?? interaction.user?.id,
      });
      response.status(200).json({ type: 4, data: { content: "프로덕션 배포 권한이 없습니다.", flags: 64 } });
      return;
    }

    try {
      const deferredResponse = {
        type: 5,
        data: {
          content: `🚀 \`${tag}\` 프로덕션 배포 workflow를 시작하는 중입니다...`,
          flags: 64,
        },
      };
      response.status(200).json(deferredResponse);

      await dispatchProductionPromotion(tag);
      logger.info("Dispatched production promotion", {
        tag,
        channelId: interaction.channel_id,
        userId: interaction.member?.user?.id ?? interaction.user?.id,
      });
      await updateDiscordInteractionResponse(
        interaction,
        `🚀 \`${tag}\` 프로덕션 배포 workflow를 시작했습니다. GitHub Actions에서 완료 상태를 확인해 주세요.`,
      );
    } catch (error) {
      logger.error("Failed to dispatch production promotion", { tag, error });
      await updateDiscordInteractionResponse(
        interaction,
        `프로덕션 배포 시작에 실패했습니다: ${(error as Error).message}`,
      );
    }
  },
);

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
