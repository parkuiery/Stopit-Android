import assert from "node:assert/strict";
import test from "node:test";

import {
  csvSecretValues,
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
