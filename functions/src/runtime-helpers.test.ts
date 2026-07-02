import assert from "node:assert/strict";
import test from "node:test";

import {
  csvSecretValues,
  formatCrashlyticsAlert,
  hexToBytes,
  issueUrl,
  promotionTagFromCustomId,
} from "./runtime-helpers.js";

test("issueUrl encodes appId and issueId for Crashlytics console links", () => {
  assert.equal(
    issueUrl("1:560920556829:android:ae388fa6c7555b32299df4", "issue/with spaces"),
    "https://console.firebase.google.com/project/stopit-be785/crashlytics/app/1%3A560920556829%3Aandroid%3Aae388fa6c7555b32299df4/issues/issue%2Fwith%20spaces",
  );
  assert.equal(issueUrl("app-id"), null);
});

test("csvSecretValues trims entries and drops blanks", () => {
  assert.deepEqual(
    [...csvSecretValues(" user-1, role-1 , , role-2 ,,user-1 ")].sort(),
    ["role-1", "role-2", "user-1"],
  );
});

test("formatCrashlyticsAlert includes triage evidence and duplicate-search link", () => {
  const content = formatCrashlyticsAlert({
    kind: "new_anr_issue",
    emoji: "🧊",
    appId: "1:560920556829:android:ae388fa6c7555b32299df4",
    issue: {
      id: "anr issue/id",
      title: "Main thread blocked in RoutineAlarmReceiver",
      subtitle: "Application Not Responding for 5s",
      appVersion: "1.7.4 (26)",
      issueType: "ANR",
    },
  });

  assert.match(content, /🧊 \*\*Stopit Crashlytics Alert\*\*/);
  assert.match(content, /- 유형: new_anr_issue \(ANR\)/);
  assert.match(content, /- 버전: 1\.7\.4 \(26\)/);
  assert.match(content, /- 이슈: Main thread blocked in RoutineAlarmReceiver/);
  assert.match(content, /- 요약: Application Not Responding for 5s/);
  assert.match(content, /- Issue ID: `anr issue\/id`/);
  assert.match(content, /console\.firebase\.google\.com\/project\/stopit-be785\/crashlytics/);
  assert.match(content, /github\.com\/parkuiery\/Stopit-Android\/issues\?q=is%3Aissue\+%22anr\+issue%2Fid%22/);
  assert.match(content, /- QA 다음 단계: Crashlytics stack trace·영향 버전·affected users를 GitHub 이슈\/PR에 복사하고, 같은 Issue ID 중복 여부를 먼저 확인/);
});

test("promotionTagFromCustomId accepts only Stopit production SemVer tags", () => {
  assert.equal(
    promotionTagFromCustomId("stopit_promote_production:v1.2.3"),
    "v1.2.3",
  );
  assert.equal(promotionTagFromCustomId("stopit_promote_production:1.2.3"), null);
  assert.equal(promotionTagFromCustomId("wrong-prefix:v1.2.3"), null);
  assert.equal(promotionTagFromCustomId(undefined), null);
});

test("hexToBytes rejects malformed input and decodes valid hex", () => {
  assert.deepEqual([...hexToBytes("00ff7f")], [0, 255, 127]);
  assert.throws(() => hexToBytes("xyz"), /Invalid hex string/);
  assert.throws(() => hexToBytes("abc"), /Invalid hex string/);
});
