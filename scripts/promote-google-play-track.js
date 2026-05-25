#!/usr/bin/env node
/*
 * Promote an existing Google Play release from one track to another.
 *
 * This intentionally avoids external npm dependencies so GitHub Actions can run
 * it on ubuntu-latest with only Node.js and a Play service-account JSON file.
 */

const crypto = require('crypto');
const fs = require('fs');
const https = require('https');

function env(name, defaultValue = '') {
  return (process.env[name] || defaultValue).trim();
}

function base64url(input) {
  return Buffer.from(input)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function requestJson(method, url, { headers = {}, body = undefined } = {}) {
  return new Promise((resolve, reject) => {
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const request = https.request(url, {
      method,
      headers: {
        ...headers,
        ...(payload ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } : {}),
      },
    }, (response) => {
      let data = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { data += chunk; });
      response.on('end', () => {
        const status = response.statusCode || 0;
        let parsed = {};
        if (data) {
          try {
            parsed = JSON.parse(data);
          } catch (_) {
            parsed = { raw: data };
          }
        }
        if (status < 200 || status >= 300) {
          reject(new Error(`${method} ${url} failed with HTTP ${status}: ${data}`));
          return;
        }
        resolve(parsed);
      });
    });
    request.on('error', reject);
    if (payload) request.write(payload);
    request.end();
  });
}

function requestForm(method, url, form) {
  return new Promise((resolve, reject) => {
    const payload = new URLSearchParams(form).toString();
    const request = https.request(url, {
      method,
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(payload),
      },
    }, (response) => {
      let data = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { data += chunk; });
      response.on('end', () => {
        const status = response.statusCode || 0;
        let parsed = {};
        if (data) {
          try {
            parsed = JSON.parse(data);
          } catch (_) {
            parsed = { raw: data };
          }
        }
        if (status < 200 || status >= 300) {
          reject(new Error(`${method} ${url} failed with HTTP ${status}: ${data}`));
          return;
        }
        resolve(parsed);
      });
    });
    request.on('error', reject);
    request.write(payload);
    request.end();
  });
}

async function accessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = {
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: serviceAccount.token_uri || 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now,
  };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const signature = crypto
    .createSign('RSA-SHA256')
    .update(signingInput)
    .sign(serviceAccount.private_key);
  const assertion = `${signingInput}.${base64url(signature)}`;
  const tokenResponse = await requestForm('POST', claims.aud, {
    grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    assertion,
  });
  if (!tokenResponse.access_token) {
    throw new Error('Google OAuth token response did not include access_token');
  }
  return tokenResponse.access_token;
}

function androidPublisherClient(token, packageName) {
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/edits`;
  const headers = { Authorization: `Bearer ${token}` };
  return {
    insertEdit: () => requestJson('POST', base, { headers, body: {} }),
    getTrack: (editId, track) => requestJson('GET', `${base}/${encodeURIComponent(editId)}/tracks/${encodeURIComponent(track)}`, { headers }),
    updateTrack: (editId, track, body) => requestJson('PUT', `${base}/${encodeURIComponent(editId)}/tracks/${encodeURIComponent(track)}`, { headers, body }),
    commitEdit: (editId) => requestJson('POST', `${base}/${encodeURIComponent(editId)}:commit`, { headers, body: {} }),
  };
}

function versionNumber(code) {
  const value = Number.parseInt(String(code), 10);
  return Number.isFinite(value) ? value : -1;
}

function selectRelease(sourceTrack, requestedVersionCode) {
  const releases = Array.isArray(sourceTrack.releases) ? sourceTrack.releases : [];
  if (releases.length === 0) {
    throw new Error(`Source track ${sourceTrack.track || 'unknown'} has no releases to promote`);
  }

  if (requestedVersionCode) {
    const match = releases.find((release) => (release.versionCodes || []).map(String).includes(String(requestedVersionCode)));
    if (!match) {
      throw new Error(`Version code ${requestedVersionCode} was not found on source track ${sourceTrack.track}`);
    }
    return match;
  }

  return releases
    .slice()
    .sort((left, right) => Math.max(...(right.versionCodes || []).map(versionNumber)) - Math.max(...(left.versionCodes || []).map(versionNumber)))[0];
}

async function main() {
  const serviceAccountPath = env('GOOGLE_PLAY_SERVICE_ACCOUNT_PATH');
  const packageName = env('PACKAGE_NAME');
  const sourceTrack = env('SOURCE_TRACK', 'internal');
  const targetTrack = env('DEPLOY_TRACK', 'production');
  const releaseStatus = env('RELEASE_STATUS', 'completed');
  const rolloutFraction = env('ROLLOUT_FRACTION');
  const versionCode = env('VERSION_CODE');

  if (!serviceAccountPath) throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_PATH is required');
  if (!packageName) throw new Error('PACKAGE_NAME is required');
  if (!targetTrack) throw new Error('DEPLOY_TRACK is required');
  if (sourceTrack === targetTrack) throw new Error('SOURCE_TRACK and DEPLOY_TRACK must be different for promotion');

  const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
  const token = await accessToken(serviceAccount);
  const client = androidPublisherClient(token, packageName);
  const edit = await client.insertEdit();
  const editId = edit.id;
  if (!editId) throw new Error('Google Play edit response did not include id');

  const source = await client.getTrack(editId, sourceTrack);
  const sourceRelease = selectRelease(source, versionCode);
  const release = {
    name: sourceRelease.name,
    versionCodes: sourceRelease.versionCodes,
    status: releaseStatus,
  };
  if (sourceRelease.releaseNotes) release.releaseNotes = sourceRelease.releaseNotes;
  if (releaseStatus === 'inProgress') {
    if (!rolloutFraction) throw new Error('ROLLOUT_FRACTION is required when RELEASE_STATUS is inProgress');
    release.userFraction = Number.parseFloat(rolloutFraction);
  }

  await client.updateTrack(editId, targetTrack, { track: targetTrack, releases: [release] });
  const committed = await client.commitEdit(editId);
  console.log(JSON.stringify({
    packageName,
    sourceTrack,
    targetTrack,
    status: releaseStatus,
    versionCodes: release.versionCodes,
    editId,
    committedEdit: committed.id || null,
  }, null, 2));
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
