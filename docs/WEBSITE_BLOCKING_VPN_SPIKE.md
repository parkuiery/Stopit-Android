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

Run the explicit physical-device DNS path check after VPN consent has been granted:

```bash
./gradlew :app:assembleDevDebug :app:assembleDevDebugAndroidTest
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb shell am instrument -w -r \
  -e runVpnDeviceTest true \
  -e class com.uiery.keep.websiteblocking.WebsiteBlockingSpikeDeviceTest \
  com.uiery.keep.dev.test/androidx.test.runner.AndroidJUnitRunner
```

The test sends DNS packets through the virtual IPv4 and IPv6 DNS endpoints and verifies exact-domain NXDOMAIN, subdomain NXDOMAIN, and an allowed upstream response on both paths. In the same VPN session it also runs 500 alternating IPv4/IPv6 allowed queries at 50ms intervals and 200 local blocked-query latency samples after 20 warmups. The pacing avoids treating a resolver-rate-limit stress test as browser reliability evidence. It is skipped unless `runVpnDeviceTest=true` is supplied because it requires a physical device, network access, and pre-granted VPN consent.

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
- The service forwards allowed UDP DNS payloads to the captured active network's DNS servers through protected sockets explicitly bound to that underlying network. It reuses one socket per upstream server for the VPN session, discards a socket after an I/O failure, and retries through the next configured server.
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
| Chrome | Automatic | Automatic/current provider | PASS (supported row) | Android 16 emulator and Galaxy S21 Android 15: exact and subdomain checks showed `DNS_PROBE_FINISHED_NXDOMAIN`; `www.cloudflare.com` loaded while the VPN service remained active. |
| Chrome | Off | Automatic/current provider | PASS (supported row) | Galaxy S21: exact and subdomain checks returned NXDOMAIN; Cloudflare loaded; the service remained active. |
| Chrome | Automatic | Off | PASS (supported row) | Galaxy S21: the setting was visibly off; exact and subdomain checks returned NXDOMAIN; Cloudflare loaded; the service remained active. The setting was restored to Automatic. |
| Chrome | Automatic | Explicit Cloudflare DoH | EXPECTED BYPASS | Galaxy S21: the Cloudflare provider was visibly selected and the blocked exact domain loaded while the VPN service remained active. The setting was restored to Automatic. |
| Chrome | Strict hostname (`dns.google`) | Automatic/current provider | EXPECTED BYPASS | Galaxy S21: exact and subdomain pages loaded while the service remained active. Android Private DNS was restored to its original Automatic state. |
| Samsung Internet | Automatic | System / no separate setting found | PASS (supported row) | Galaxy S21: exact and subdomain pages showed the browser connection error; Cloudflare loaded; the service remained active. |
| Samsung Internet | Off | System / no separate setting found | PASS (supported row) | Galaxy S21: exact and subdomain pages showed the browser connection error; Cloudflare loaded; the service remained active. |
| Samsung Internet | Strict hostname (`dns.google`) | System / no separate setting found | EXPECTED BYPASS | Galaxy S21: exact and subdomain pages loaded while the service remained active. |
| Firefox | — | — | BLOCKED | Not installed on the test device. |
| Edge | — | — | BLOCKED | Not installed on the test device. |

## Lifecycle Matrix

| Scenario | Steps | Pass condition | Status | Evidence |
| --- | --- | --- | --- | --- |
| Start after fresh install | Install dev APK, start spike, approve VPN consent | Foreground notification appears and browsing still works for allowed sites | PASS | Galaxy S21 Android 15: consent was approved, the service remained active, direct DNS checks passed, and Chrome/Samsung Internet loaded allowed pages. |
| Stop command | Run the stop command | VPN notification disappears and internet remains available within 3s | PASS | Galaxy S21: service stopped and allowed DNS/internet was available on the first probe. Repeated start/stop commands were also verified without force-stopping the app. |
| VPN consent denied | Revoke/clear consent, start spike, deny system prompt | Service does not start and blocking is not counted as passed | PASS | Galaxy S21: tapping Cancel left the service absent and the activity reported that consent was not granted. |
| VPN revoked while active | Start spike, revoke Keep VPN in Android settings | Service stops; internet recovers within 3s | BLOCKED | ADB AppOps changes do not revoke an already-established VPN. Samsung VPN settings required configuring a device screen lock before changing VPN ownership, so the active-revoke path was not changed on the user's device. |
| Other VPN active before start | Activate another VPN, start Keep spike | Keep does not become active or displace expected VPN behavior silently | BLOCKED | A third-party VPN profile is installed, but activating or reconfiguring it would change that app's privacy/network state and the system requires a screen lock. |
| Other VPN selected while active | Start Keep spike, select another VPN | Keep website blocking stops/degrades; app does not claim success | BLOCKED | Same device-policy and third-party state boundary as the previous row. |
| Reboot while active | Start spike, reboot device | Spike does not auto-enable; no stale active success state | PASS | Galaxy S21: service was active before reboot, absent after boot, and general internet succeeded immediately. |
| Wi-Fi to mobile | Start on Wi-Fi, switch to mobile data | Allowed DNS/browser traffic recovers within 3s | BLOCKED | Mobile data was disabled and telephony reported out-of-service/data registration denied. It was not enabled due charge and service-state risk. |
| Mobile to Wi-Fi | Start on mobile data, switch to Wi-Fi | Allowed DNS/browser traffic recovers within 3s | BLOCKED | No usable mobile starting network. Re-enabling Wi-Fi after the attempted observation restored validated internet in about 5.1s. |
| Airplane mode recovery | Start spike, enable/disable airplane mode | Service fails open or resumes without breaking allowed browsing | PASS (fail open) | Galaxy S21: airplane mode stopped the spike service; after disabling airplane mode, IP and DNS browsing recovered in 2s. The spike did not auto-resume. |

## Results Summary

| Gate | Status | Evidence |
| --- | --- | --- |
| Exact/subdomain blocking | PASS FOR SUPPORTED SYSTEM-DNS ROWS | Galaxy S21 Android 15: direct IPv4/IPv6 checks, Chrome, and Samsung Internet passed exact/subdomain blocking when DNS used the supported system path. Strict Private DNS and explicit Chrome DoH bypassed as expected. |
| Similar-domain allow behavior | PASS (automated) | `DomainNamePolicyTest` and `DnsMessageCodecTest` verify that `notexample.com` and `example.com.evil.test` do not match a blocked `example.com`. |
| Allowed-site 500/0 reliability | PASS (repeated) | Galaxy S21 completed three consecutive 500/0 runs after upstream sockets were reused per VPN session and failed endpoints were discarded before fallback. The runs observed 2, 3, and 6 transient IPv4 upstream timeouts respectively, but all completed with zero client-visible failures. |
| Local p95 latency `<=20ms` | PASS | Galaxy S21: repeated runs with 20 warmups plus 200 local blocked DNS samples produced p95 between `0ms` and `2ms`. |
| Recovery `<=3s` | PASS FOR EXECUTABLE PATHS | Stop recovered immediately; airplane-mode fail-open restored IP and DNS access in 2s. Active settings revoke was blocked by device security policy. |
| Other VPN conflict behavior | BLOCKED | Installed third-party VPN state was not changed; Samsung required a screen lock for the relevant VPN settings path. |
| Wi-Fi/mobile coverage | BLOCKED FOR MOBILE | Wi-Fi passed. Mobile was disabled and out of service, so mobile transition rows could not be executed safely. |
| IPv4/IPv6 coverage | PASS FOR VIRTUAL DNS PATHS | Direct DNS query/response assertions passed through both virtual IP paths. Underlying carrier IPv6 coverage was not available. |

### 2026-07-24 Galaxy S21 checkpoint

- Device: Samsung Galaxy S21 (`SM-G991N`), Android 15 / API 35.
- Initial failure: the VPN stopped immediately on a valid IPv6 Hop-by-Hop Options packet carrying ICMPv6.
- Second failure: the TUN client address and virtual DNS endpoint used the same address, so DNS traffic was locally consumed instead of reaching the packet processor.
- Fix checkpoint: ICMP/ICMPv6 control traffic is ignored, unsupported TCP connections to the virtual DNS endpoints receive an immediate RST instead of being blackholed, the IPv6 Hop-by-Hop header is parsed safely, and client/DNS addresses are distinct.
- Automated physical-device result: exact block, subdomain block, and p95 `2ms` local blocking passed through the virtual IPv4 and IPv6 DNS paths. One 500/0 allowed-query run passed, but two final repetitions failed after upstream resolver timeouts, so reliability remains a failed gate.
- Browser follow-up after unlock: Android Private DNS and Chrome Secure DNS were both `Automatic`; exact `example.org` and subdomain `www.example.org` showed `DNS_PROBE_FINISHED_NXDOMAIN`; allowed `www.cloudflare.com` loaded; the VPN service remained active after all three checks.
- Chrome follow-up: Secure DNS Off passed; explicit Cloudflare DoH bypassed; the original Automatic setting was restored.
- Samsung Internet follow-up: Automatic and Private DNS Off system-resolver rows passed; strict Private DNS bypassed. The browser exposed no separate Secure DNS setting in settings search.
- Lifecycle follow-up: consent denial, repeated start/stop, reboot without stale restart, and airplane-mode fail-open passed. Mobile transitions, active settings revoke, and third-party VPN conflict remain blocked by device/service-state constraints.
- Firefox and Edge were not installed on the device.

### 2026-07-25 Galaxy S21 reliability follow-up

- Opal was removed before the final rerun, eliminating its accessibility overlay as a source of unrelated device-test interference.
- Forwarding weakness identified: every allowed DNS query opened, protected, bound, connected, and closed a new UDP socket. Earlier repeated runs accumulated upstream timeouts and eventually exhausted the configured DNS servers for a query.
- Fix checkpoint: the VPN session now reuses one protected and underlying-network-bound UDP endpoint per configured DNS server. Any endpoint that throws an I/O error is closed and removed, the query falls back to the next server, and a later query recreates the failed endpoint.
- Automated physical-device result, run 1: exact/subdomain IPv4 and IPv6 checks passed, local blocked-query p95 was `0ms`, and allowed DNS completed `500/500`. Two transient IPv4 upstream socket timeouts were recovered without a client-visible failure.
- Automated physical-device result, run 2: exact/subdomain IPv4 and IPv6 checks passed, local blocked-query p95 was `0ms`, and allowed DNS completed `500/500`. Three transient IPv4 upstream socket timeouts were recovered without a client-visible failure.
- Automated physical-device result, run 3: exact/subdomain IPv4 and IPv6 checks passed, local blocked-query p95 was `1ms`, and allowed DNS completed `500/500`. Six transient IPv4 upstream socket timeouts were recovered without a client-visible failure; fallback delays increased total test time from about 43s to 73s.
- The reliability gate is now repeatable on the tested Wi-Fi device. Mobile-network transitions, active settings revoke, and another-VPN ownership changes remain unverified constraints and are not promoted to passes.
- The 1.5s timeout before fallback can still surface as intermittent allowed-site lookup latency. Production work should measure allowed-query tail latency and consider adaptive upstream ordering or a shorter evidence-based timeout before treating UX latency as closed.

### 2026-07-24 Android 16 emulator Chrome checkpoint

- Device: `sdk_gphone64_arm64`, Android 16 / API 36, emulator Wi-Fi.
- Settings inspected in UI: Android Private DNS `Automatic`; Chrome `Use secure DNS` `Automatic`.
- Initial allowed-site failure: a protected UDP socket was not pinned to the active underlying network. The emulator DNS server timed out, the fail-open policy stopped the VPN, and the requested page then loaded outside the filter.
- Fix checkpoint: the service captures the active `Network`, declares it through `Builder.setUnderlyingNetworks`, and binds every protected upstream DNS socket to it before connecting.
- Browser result: exact `example.net` and subdomain `www.example.net` both showed `DNS_PROBE_FINISHED_NXDOMAIN`; allowed `www.cloudflare.com` loaded; the VPN service remained active after all three checks.
- The physical-device-only DNS instrumentation test is not counted as emulator evidence. Its same-UID first query timed out on this emulator, while the separate Chrome process exercised the user-facing browser path successfully.

## Expected Limitations

- DNS-only: the TUN route targets DNS, not arbitrary web traffic.
- UDP-only forwarding: the spike parses and forwards UDP DNS. TCP DNS fallback is not implemented; TCP attempts to the virtual DNS endpoints are explicitly rejected with RST.
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

- Core system-DNS blocking mechanics, local latency, and repeatable 500/0 allowed-DNS reliability are supported on the tested Galaxy S21 Wi-Fi path, but the production gate is not complete.
- The socket-churn reliability blocker is resolved for the tested path by session-scoped endpoint reuse plus failure invalidation and fallback.
- Do not call the production gate complete until active-revoke and other-VPN conflict behavior are verified on a QA device with an appropriate screen lock, and mobile transitions are verified on an active test SIM/network.
- If browser Secure DNS or strict Private DNS bypasses the spike, product UX must disclose that limitation or the plan must move to a different enforcement design.
- If allowed-site reliability, latency, or recovery gates fail, stop v1 implementation and replan the VPN/DNS architecture before adding product UI.
- If other-VPN conflicts are common in target QA devices, add explicit degraded-state UX to the v1 plan before implementation.
