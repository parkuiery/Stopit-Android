const test = require('node:test');
const assert = require('node:assert/strict');

const {
  resolvePromotionVersionCode,
  selectRelease,
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
