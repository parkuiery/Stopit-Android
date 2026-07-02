#!/usr/bin/env node

const releaseStatus = (process.env.RELEASE_STATUS || 'completed').trim();
const rolloutFraction = (process.env.ROLLOUT_FRACTION || '').trim();

function fail(message) {
  console.error(message);
  process.exit(1);
}

if (releaseStatus === 'inProgress') {
  if (!rolloutFraction) {
    fail('RELEASE_STATUS=inProgress requires rollout_fraction. Set rollout_fraction to a numeric value with 0 < rollout_fraction <= 1.');
  }

  const fraction = Number(process.env.ROLLOUT_FRACTION);
  if (!Number.isFinite(fraction) || fraction <= 0 || fraction > 1) {
    fail(`Invalid rollout_fraction=${rolloutFraction}. RELEASE_STATUS=inProgress requires 0 < rollout_fraction <= 1.`);
  }

  console.log(`Validated staged rollout input: release_status=${releaseStatus}, rollout_fraction=${rolloutFraction}`);
  process.exit(0);
}

if (rolloutFraction) {
  fail(`Release statuses other than inProgress must leave rollout_fraction empty. Got release_status=${releaseStatus}, rollout_fraction=${rolloutFraction}.`);
}

console.log(`Validated Play deploy input: release_status=${releaseStatus}, rollout_fraction is empty.`);
