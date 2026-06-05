const test = require('node:test');
const assert = require('node:assert/strict');

const {
  resolvePromotionVersionCode,
  selectRelease,
  validateReleaseStatusAndRolloutFraction,
} = require('../promote-google-play-track.js');

test('resolvePromotionVersionCode requires VERSION_CODE for production promotions', () => {
  assert.throws(
    () => resolvePromotionVersionCode('production', ''),
    /VERSION_CODE is required when DEPLOY_TRACK=production/,
  );
  assert.equal(resolvePromotionVersionCode('production', '123'), '123');
  assert.equal(resolvePromotionVersionCode('internal', ''), '');
});

test('selectRelease chooses the explicitly requested version code', () => {
  const sourceTrack = {
    track: 'internal',
    releases: [
      { name: 'v1.7.4', versionCodes: ['174'] },
      { name: 'v1.7.5', versionCodes: ['175'] },
    ],
  };

  assert.deepEqual(selectRelease(sourceTrack, '174'), sourceTrack.releases[0]);
  assert.throws(
    () => selectRelease(sourceTrack, '999'),
    /Version code 999 was not found on source track internal/,
  );
});

test('validateReleaseStatusAndRolloutFraction accepts production staged rollout contract', () => {
  assert.equal(validateReleaseStatusAndRolloutFraction('inProgress', '0.05'), 0.05);
  assert.equal(validateReleaseStatusAndRolloutFraction('inProgress', '1'), 1);
  assert.equal(validateReleaseStatusAndRolloutFraction('completed', ''), null);
  assert.equal(validateReleaseStatusAndRolloutFraction('draft', ''), null);
  assert.equal(validateReleaseStatusAndRolloutFraction('halted', ''), null);
});

test('validateReleaseStatusAndRolloutFraction rejects invalid production staged rollout contract', () => {
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('inProgress', ''),
    /RELEASE_STATUS=inProgress requires rollout_fraction/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('inProgress', '0'),
    /0 < rollout_fraction <= 1/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('inProgress', '-0.1'),
    /0 < rollout_fraction <= 1/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('inProgress', '1.5'),
    /0 < rollout_fraction <= 1/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('inProgress', 'not-a-number'),
    /0 < rollout_fraction <= 1/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('completed', '0.2'),
    /Release statuses other than inProgress must leave rollout_fraction empty/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('draft', '0.2'),
    /Release statuses other than inProgress must leave rollout_fraction empty/,
  );
  assert.throws(
    () => validateReleaseStatusAndRolloutFraction('halted', '0.2'),
    /Release statuses other than inProgress must leave rollout_fraction empty/,
  );
});
