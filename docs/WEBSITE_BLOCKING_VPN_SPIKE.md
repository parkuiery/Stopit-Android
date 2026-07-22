# Website Blocking VPN Feasibility Spike Runbook

This runbook is for the dev-only DNS VPN spike on branch `feature/website-blocking-spike`.
Use it to decide whether website blocking v1 can continue with Android `VpnService` DNS filtering.

## Scope

- Validate DNS-only local VPN feasibility before product implementation.
- Test one normalized blocked domain at a time through the dev-only activity and service.
- Measure browser behavior, DNS-only coverage, allowed-site reliability, local filter latency, recovery, and lifecycle failure modes.
- Keep all results local unless summarized without raw browsing history or DNS query logs.

## Non-goals

- Production UX, routine integration, emergency-unlock UI, analytics, or release policy.
- URL path, search term, content category, page, IP, or full HTTPS traffic blocking.
- HTTPS interception, certificate installation, MITM proxying, remote VPN, or remote DNS logging.
- Bypassing every DoH, Android Private DNS, ECH, or browser-private resolver configuration.
- Running alongside another VPN. Android allows one active VPN owner at a time.

## Source Contract

| Item | Value |
| --- | --- |
| Dev application ID | `com.uiery.keep.dev` |
| Activity component | `com.uiery.keep.dev/com.uiery.keep.websiteblocking.KeepDnsVpnSpikeActivity` |
| Service class | `com.uiery.keep.websiteblocking.KeepDnsVpnSpikeService` |
| Service exported | `false` |
| VPN service permission | `android.permission.BIND_VPN_SERVICE` |
| Always-on VPN support | `false` |
| Start action | `com.uiery.keep.websiteblocking.START_DNS_VPN_SPIKE` |
| Stop action | `com.uiery.keep.websiteblocking.STOP_DNS_VPN_SPIKE` |
| Domain extra | `domain` |
| Stop extra | `stop` |
| Default blocked domain | `example.com` |

The service is non-exported, so start and stop the spike through the exported activity. The activity requests `VpnService.prepare()` consent, then starts the foreground VPN service inside the app process.

## Build, Install, Start, Stop

Build and install the dev APK:

```bash
cd <repo-root>
./gradlew :app:assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

Start the spike with an explicit blocked domain:

```bash
adb shell am start \
  -n com.uiery.keep.dev/com.uiery.keep.websiteblocking.KeepDnsVpnSpikeActivity \
  -a com.uiery.keep.websiteblocking.START_DNS_VPN_SPIKE \
  --es domain youtube.com
```

Stop the spike:

```bash
adb shell am start \
  -n com.uiery.keep.dev/com.uiery.keep.websiteblocking.KeepDnsVpnSpikeActivity \
  -a com.uiery.keep.websiteblocking.STOP_DNS_VPN_SPIKE \
  --ez stop true
```

Optional diagnostics:

```bash
adb shell dumpsys connectivity | rg -i 'vpn|keep|com.uiery.keep.dev'
adb shell dumpsys notification --noredact | rg -i 'website blocking spike|keep'
adb logcat -c
adb logcat | rg 'KeepDnsVpnSpike|Vpn'
```

## VPN Consent Steps

1. Run the start command.
2. If Android shows VPN consent, approve the request for Keep.
3. Confirm the foreground notification title is `Website blocking spike active`.
4. If consent is denied, record `Consent denied` and do not mark any blocking row as passed.
5. If no consent appears, verify whether Keep already owns VPN consent from an earlier run.
6. To revoke consent, use Android VPN settings and remove/disable Keep as the VPN owner, then rerun the lifecycle tests.

## Expected Spike Behavior

- The activity normalizes the `domain` extra and rejects invalid domains.
- The VPN TUN captures DNS only by installing virtual DNS addresses/routes.
- The service forwards allowed UDP DNS payloads to the active network DNS servers through protected sockets.
- Exact domain and true subdomain matches return NXDOMAIN.
- Similar suffixes must remain allowed. Example: blocking `youtube.com` blocks `youtube.com` and `m.youtube.com`, but not `notyoutube.com` or `youtube.com.evil.test`.
- Parser, upstream, and response-build failures stop the VPN to fail open.

## Gate Criteria

Continue to website blocking v1 only if all gates pass:

| Gate | Pass threshold |
| --- | --- |
| Exact/subdomain blocking | 100% pass for supported system-DNS combinations |
| Similar-domain allow behavior | `not<domain>` and `<domain>.evil.test` are allowed |
| Allowed-site reliability | 0 failures in 500 allowed DNS/page-load attempts |
| Local filter latency | p95 added processing latency `<=20ms` |
| Recovery after service failure/stop/revoke | General internet recovers within `<=3s` |
| Other VPN conflict handling | Keep does not report website blocking as active when another VPN owns the slot |

Allowed failure boundary: DoH, strict Android Private DNS, TCP DNS fallback, fragmented DNS, and another active VPN may fail this DNS-only spike. Record them as limitations, not product success.

## Browser and Resolver Matrix

Run every row for the same blocked domain, then repeat the row across these network cells:

- Wi-Fi + IPv4
- Wi-Fi + IPv6
- Mobile + IPv4
- Mobile + IPv6

For every network cell, test:

- Blocked exact domain
- Blocked subdomain
- Similar allowed domain
- Normal allowed site
- Allowed-site reliability sample toward the 500/0 gate
- Local filter latency sample toward the p95 gate

| Browser | Android Private DNS | Browser Secure DNS | Status | Evidence |
| --- | --- | --- | --- | --- |
| Chrome | Off | Off | NOT YET EXECUTED | |
| Chrome | Off | On | NOT YET EXECUTED | |
| Chrome | Automatic | Off | NOT YET EXECUTED | |
| Chrome | Automatic | On | NOT YET EXECUTED | |
| Chrome | Strict hostname | Off | NOT YET EXECUTED | |
| Chrome | Strict hostname | On | NOT YET EXECUTED | |
| Samsung Internet | Off | Off | NOT YET EXECUTED | |
| Samsung Internet | Off | On | NOT YET EXECUTED | |
| Samsung Internet | Automatic | Off | NOT YET EXECUTED | |
| Samsung Internet | Automatic | On | NOT YET EXECUTED | |
| Samsung Internet | Strict hostname | Off | NOT YET EXECUTED | |
| Samsung Internet | Strict hostname | On | NOT YET EXECUTED | |
| Firefox | Off | Off | NOT YET EXECUTED | |
| Firefox | Off | On | NOT YET EXECUTED | |
| Firefox | Automatic | Off | NOT YET EXECUTED | |
| Firefox | Automatic | On | NOT YET EXECUTED | |
| Firefox | Strict hostname | Off | NOT YET EXECUTED | |
| Firefox | Strict hostname | On | NOT YET EXECUTED | |
| Edge | Off | Off | NOT YET EXECUTED | |
| Edge | Off | On | NOT YET EXECUTED | |
| Edge | Automatic | Off | NOT YET EXECUTED | |
| Edge | Automatic | On | NOT YET EXECUTED | |
| Edge | Strict hostname | Off | NOT YET EXECUTED | |
| Edge | Strict hostname | On | NOT YET EXECUTED | |

## Lifecycle Matrix

| Scenario | Steps | Pass condition | Status | Evidence |
| --- | --- | --- | --- | --- |
| Start after fresh install | Install dev APK, start spike, approve VPN consent | Foreground notification appears and browsing still works for allowed sites | NOT YET EXECUTED | |
| Stop command | Run the stop command | VPN notification disappears and internet remains available within 3s | NOT YET EXECUTED | |
| VPN consent denied | Revoke/clear consent, start spike, deny system prompt | Service does not start and blocking is not counted as passed | NOT YET EXECUTED | |
| VPN revoked while active | Start spike, revoke Keep VPN in Android settings | Service stops; internet recovers within 3s | NOT YET EXECUTED | |
| Other VPN active before start | Activate another VPN, start Keep spike | Keep does not become active or displace expected VPN behavior silently | NOT YET EXECUTED | |
| Other VPN selected while active | Start Keep spike, select another VPN | Keep website blocking stops/degrades; app does not claim success | NOT YET EXECUTED | |
| Reboot while active | Start spike, reboot device | Spike does not auto-enable; no stale active success state | NOT YET EXECUTED | |
| Wi-Fi to mobile | Start on Wi-Fi, switch to mobile data | Allowed DNS/browser traffic recovers within 3s | NOT YET EXECUTED | |
| Mobile to Wi-Fi | Start on mobile data, switch to Wi-Fi | Allowed DNS/browser traffic recovers within 3s | NOT YET EXECUTED | |
| Airplane mode recovery | Start spike, enable/disable airplane mode | Service fails open or resumes without breaking allowed browsing | NOT YET EXECUTED | |

## Results Summary

Do not fill this table until device testing has been executed.

| Gate | Status | Evidence |
| --- | --- | --- |
| Exact/subdomain blocking | NOT YET EXECUTED | |
| Similar-domain allow behavior | NOT YET EXECUTED | |
| Allowed-site 500/0 reliability | NOT YET EXECUTED | |
| Local p95 latency `<=20ms` | NOT YET EXECUTED | |
| Recovery `<=3s` | NOT YET EXECUTED | |
| Other VPN conflict behavior | NOT YET EXECUTED | |
| Wi-Fi/mobile coverage | NOT YET EXECUTED | |
| IPv4/IPv6 coverage | NOT YET EXECUTED | |

## Expected Limitations

- DNS-only: the TUN route targets DNS, not arbitrary web traffic.
- UDP-only: the spike parses and forwards UDP DNS. TCP DNS fallback is not implemented.
- Private DNS and browser Secure DNS may bypass the local DNS path.
- Fragmented DNS packets fail open.
- Only one Android VPN can be active at a time.
- The spike blocks by DNS name, so already-cached browser connections may need browser restart, tab close, or DNS/cache expiration before behavior is visible.
- The blocked response is NXDOMAIN. Browsers show their own connection error, not a Keep block screen.

## Privacy Rules

- Do not collect raw DNS logs for shared evidence.
- Do not paste full browsing history, DNS query streams, or personal domains into PRs, tickets, analytics, or Crashlytics.
- Use test domains in shared evidence when possible.
- If a real domain must be mentioned, record only the configured blocked domain and browser/settings outcome.
- Keep raw packet captures local and delete them after extracting aggregate pass/fail, latency, and failure-count evidence.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Activity says invalid domain | Use a host-like value such as `youtube.com`; IP literals, wildcards, single labels, and malformed labels are rejected. |
| No VPN consent prompt | Keep may already have VPN consent. Confirm Android VPN settings before rerunning. |
| No notification | Confirm the dev APK is installed as `com.uiery.keep.dev` and Android notifications/foreground service are not blocked for the app. |
| Start command works but sites are not blocked | Disable browser Secure DNS and Android Private DNS, then retest system-DNS rows. |
| Internet stops for allowed sites | Stop the spike; recovery must happen within 3s. Record the row as failed if it does not. |
| Other VPN is active | Stop the other VPN or record the conflict row. Keep cannot own the VPN slot at the same time. |
| Block persists after stop | Close/reopen the browser tab or browser, clear browser DNS/cache if available, then confirm device internet outside the browser. |

## Next Decision Gate

- Proceed with v1 DNS-filter implementation only if all gate criteria pass in the supported system-DNS rows.
- If browser Secure DNS or strict Private DNS bypasses the spike, product UX must disclose that limitation or the plan must move to a different enforcement design.
- If allowed-site reliability, latency, or recovery gates fail, stop v1 implementation and replan the VPN/DNS architecture before adding product UI.
- If other-VPN conflicts are common in target QA devices, add explicit degraded-state UX to the v1 plan before implementation.
