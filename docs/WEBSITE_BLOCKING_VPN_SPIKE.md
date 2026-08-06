# Website Blocking VPN Feasibility Spike Runbook

This runbook records the DNS VPN feasibility spike and the product integration that followed on
branch `feature/website-blocking-spike`.

## Scope

- Validate DNS-only local VPN feasibility and preserve its physical-device evidence.
- Test one normalized blocked domain at a time through the dev-only activity and service.
- Measure browser behavior, DNS-only coverage, allowed-site reliability, local filter latency, recovery, and lifecycle failure modes.
- Keep all results local unless summarized without raw browsing history or DNS query logs.

## Non-goals

- Per-routine web lists, emergency-unlock UI, analytics, or release policy.
- URL path, search term, content category, page, IP, or full HTTPS traffic blocking.
- HTTPS interception, certificate installation, MITM proxying, remote VPN, or remote DNS logging.
- Bypassing every DoH, Android Private DNS, ECH, or browser-private resolver configuration.
- Running alongside another VPN. Android allows one active VPN owner at a time.

## Source Contract

| Item | Value |
| --- | --- |
| Dev application ID | `com.uiery.keep.dev` |
| Activity component | `com.uiery.keep.dev/com.uiery.keep.websiteblocking.KeepDnsVpnSpikeActivity` |
| Service class | `com.uiery.keep.websiteblocking.KeepDnsVpnService` |
| Service exported | `false` |
| VPN service permission | `android.permission.BIND_VPN_SERVICE` |
| Always-on VPN support | `false` |
| Start action | `com.uiery.keep.websiteblocking.START_DNS_VPN_SPIKE` |
| Stop action | `com.uiery.keep.websiteblocking.STOP_DNS_VPN_SPIKE` |
| Domain extra | `domain` |
| Product domain-set extra | `domains` |
| Stop extra | `stop` |
| Default blocked domain | `example.com` |

The service is non-exported, so start and stop the spike through the exported activity. The activity requests `VpnService.prepare()` consent, then starts the foreground VPN service inside the app process.

## Product Integration Status

- Home manages app and website targets as separate tabs and persists them under separate DataStore keys.
  The website tab is gated by `BuildConfig.WEBSITE_BLOCKING_ENABLED`: `true` in the `dev` flavor,
  `false` in `prod` until the runtime wiring below exists.
- Selecting a supported app can produce a curated website recommendation; the recommendation state is
  computed and stored, but the confirmation UI that surfaces it is not implemented yet.
- Manual Keep mode and timed locks accept app targets, website targets, or both.
- Home requests system VPN consent when a lock with website targets becomes active, then starts
  `KeepDnsVpnService` with the normalized domain set. A timed lock also passes its deadline, so the
  service stops on time even when no Keep screen is composed.
- Declining consent does not cancel the lock. The app-blocking side of the lock keeps running and the
  user is told that websites are not blocked (snackbar plus a persistent banner on Home and the lock
  screen). The consent prompt is not raised again until that lock ends.
- `WebsiteBlockingRuntimeState` carries the difference between "lock is on" and "websites are actually
  blocked". The service publishes `Unavailable` when the TUN cannot be established (another VPN owns
  the slot) or when consent is revoked mid-lock, and the same banner explains it.
- The website selection sheet discloses that browser or device secure DNS settings can bypass blocking.
- Routines carry their own website list. Four triggers run the same judgement — the start alarm,
  saving or toggling a routine, boot, and opening Home — so a missed alarm no longer costs the whole
  window. Opening the app is still required for the alarm-missed case; a user who never opens Keep
  during that window still gets nothing. See `docs/ROUTINE_WEBSITE_BLOCKING_TRIGGER_CONTRACT.md`.
- When upstream DNS stops answering the filter still steps aside, but the service now stays alive and
  retries on a `5s`/`15s`/`60s` backoff until the window ends, so a recovered network is picked up
  without waiting for the next trigger.
- The service uses `START_REDELIVER_INTENT` so Android can restore the active domain set after a
  process restart.
- Routines and goal locks still retain their existing app-only target models.

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
- The service follows Android's best matching non-VPN internet network and re-establishes the DNS-only TUN when the physical underlying network changes. It forwards allowed UDP DNS payloads to that network's DNS servers through protected sockets explicitly bound to the same network.
- The service reuses one socket per upstream server for the VPN session, discards a socket after an I/O failure, and retries through the next configured server. A successful fallback becomes preferred, while a failed server is deprioritized for 30s but remains available as a last resort if every ready server fails. One bounded 250ms session retry covers a transient upstream failure immediately after a physical-network transition.
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
| VPN revoked while active | Start spike, revoke Keep VPN (settings, or let another VPN take the slot) | Service stops; internet recovers within 3s | FAIL (in-flight DNS tail) | Galaxy S20+ Android 13: system disconnect began at `16:31:33.685`, the service unregistered its network callback at `16:31:33.702`, and the VPN network closed by `16:31:33.753` (68ms). A DNS lookup started concurrently still waited for the resolver timeout; the second probe succeeded, but end-to-end recovery measured `3.88s`. Re-measured on Galaxy S21 Android 15 on 2026-08-06 after the upstream timeout moved to 5s: the slot moved at `02:16:37.871`, exactly one probe failed (the in-flight one, hanging `3.03s`), a lookup started after the revoke answered in `280ms`, and first success completed at `+3.13s`. Still above the 3s gate, and the timeout change did not lengthen it. |
| Other VPN active before start | Activate another VPN, start a website lock | Keep does not become active or displace expected VPN behavior silently | PASS AFTER FIX | Galaxy S21 Android 15 with `유니콘 HTTPS` (`kr.co.lylstudio.httpsguard`) owning the slot. Before the fix Android transferred VPN ownership to Keep with no prompt at all (Keep already held consent), blocking worked, and the third-party VPN did not return when the lock ended. Keep now asks before displacing: declining keeps the other VPN (`dumpsys connectivity` still showed `VPN:kr.co.lylstudio.httpsguard`, `KeepDnsVpnService` absent, `example.org` still resolving) and the lock continues with the "다른 VPN이 켜져 있어" banner. |
| Other VPN selected while active | Start Keep spike, select another VPN | Keep website blocking stops/degrades; app does not claim success | BLOCKED | Same device-policy and third-party state boundary as the previous row. |
| Reboot while active | Start spike, reboot device | Spike does not auto-enable; no stale active success state | PASS | Galaxy S21: service was active before reboot, absent after boot, and general internet succeeded immediately. |
| Wi-Fi to mobile | Start on Wi-Fi, switch to mobile data | Allowed DNS/browser traffic recovers within 3s | PASS | Galaxy S20+ Android 13: the callback suspended Wi-Fi network `633`, and the replacement VPN over LTE network `613` was created `0.36s` later. Exact blocking and allowed DNS both remained functional. The host command took `3.60s` because Samsung's `svc wifi disable` blocked before the actual network-loss event; this command latency is recorded separately from app recovery. |
| Mobile to Wi-Fi | Start on mobile data, switch to Wi-Fi | Allowed DNS/browser traffic recovers within 3s | PASS | Galaxy S20+ Android 13: allowed traffic continued over LTE while Wi-Fi connected, then the VPN moved from LTE network `613` to Wi-Fi network `633`. The resulting VPN reported `WIFI|VPN` with underlying network `633`; exact blocking and allowed DNS both passed after the handoff. |
| Airplane mode recovery | Start spike, enable/disable airplane mode | Service fails open or resumes without breaking allowed browsing | PASS (fail open) | Galaxy S21: airplane mode stopped the spike service; after disabling airplane mode, IP and DNS browsing recovered in 2s. The spike did not auto-resume. |

## Results Summary

| Gate | Status | Evidence |
| --- | --- | --- |
| Exact/subdomain blocking | PASS FOR SUPPORTED SYSTEM-DNS ROWS | Galaxy S21 Android 15: direct IPv4/IPv6 checks, Chrome, and Samsung Internet passed exact/subdomain blocking when DNS used the supported system path. Strict Private DNS and explicit Chrome DoH bypassed as expected. |
| Similar-domain allow behavior | PASS (automated) | `DomainNamePolicyTest` and `DnsMessageCodecTest` verify that `notexample.com` and `example.com.evil.test` do not match a blocked `example.com`. |
| Allowed-site 500/0 reliability | PASS (repeated) | Galaxy S21 completed three consecutive 500/0 runs after upstream sockets were reused per VPN session and failed endpoints were discarded before fallback. The runs observed 2, 3, and 6 transient IPv4 upstream timeouts respectively, but all completed with zero client-visible failures. |
| Local p95 latency `<=20ms` | PASS | Galaxy S21: repeated runs with 20 warmups plus 200 local blocked DNS samples produced p95 between `0ms` and `2ms`. |
| Recovery `<=3s` | PARTIAL / ACTIVE-REVOKE FAIL | Wi-Fi/mobile rebinding completed within 0.36s of the actual network-loss callback. Active revoke tears down in ~20-68ms and a lookup started after it answers in `280ms`, but the lookup already in flight hangs across the switch: end-to-end `3.88s` (S20+, 2026-07-27) and `3.13s` (S21, 2026-08-06, re-measured after the 5s upstream timeout). The tail is the in-flight lookup, not the teardown or the replacement path. |
| Other VPN conflict behavior | PASS AFTER FIX | Android hands the VPN slot to whoever asks last and does not prompt when consent already exists, so the failure mode is not "Keep cannot block" but "Keep silently disconnects the VPN the user was relying on, permanently". Keep now asks first and can run the lock without web blocking. Confirmed on a Galaxy S21 against `유니콘 HTTPS`. |
| Wi-Fi/mobile coverage | PASS ON GALAXY S20+ | Wi-Fi and LTE each passed 500/0 allowed DNS reliability, exact/subdomain blocking, and local latency. Both transition directions moved the VPN to the expected underlying network. |
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
- The 1.5s timeout before the first fallback can still surface as intermittent allowed-site lookup latency. Adaptive upstream ordering and a 30s failure cooldown are implemented; the current Galaxy S21 Wi-Fi checkpoint has low p95/p99 latency, while cross-network tail-latency evidence is still required before treating UX latency as closed.

### 2026-07-27 adaptive upstream checkpoint

- Unit coverage verifies that a failed endpoint is closed, a successful fallback is reused first, cooling endpoints are skipped while another ready endpoint succeeds, expired endpoints can be retried, and a cooling endpoint remains a last-resort recovery path.
- The physical-device test now records allowed-query p95, p99, and maximum latency alongside the 500/0 reliability count.
- After Wi-Fi was connected, three consecutive Galaxy S21 runs completed `500/500` allowed queries with no upstream timeout diagnostics. Allowed-query p95 was `13ms`, `14ms`, and `13ms`; p99 was `15ms`, `16ms`, and `15ms`; maximum was `16ms`, `128ms`, and `17ms`.
- Local blocked-query p95 was `0ms`, `1ms`, and `1ms`. Total instrumentation time was about `31.3s`, `31.6s`, and `31.4s`.
- The earlier `no_upstream_dns` and `ENETUNREACH` runs occurred while both Wi-Fi and mobile upstreams were absent and are not counted as product evidence.
- The 2026-07-25 runs used a different Wi-Fi network, so their 43s/43s/73s runtimes and 2/3/6 upstream timeouts are contextual comparison only, not a controlled before/after result.

### 2026-07-27 Galaxy S20+ mobile and transition checkpoint

- Device: Samsung Galaxy S20+ (`SM-G986N`), Android 13 / API 33, active SK Telecom LTE and Wi-Fi.
- Baseline Wi-Fi result before transition handling: exact/subdomain checks passed, local block p95 was `0ms`, and allowed DNS completed `500/500` with p95 `9ms`, p99 `11ms`, and maximum `13ms`.
- Baseline LTE result before transition handling: exact/subdomain checks passed, local block p95 was `0ms`, and allowed DNS completed `500/500` with p95 `39ms`, p99 `51ms`, and maximum `116ms`.
- Initial transition failure: the service captured one underlying `Network` at startup and never listened for changes. Wi-Fi loss caused an upstream timeout and fail-open stop, while LTE-to-Wi-Fi left the VPN pinned to LTE.
- Fix checkpoint: the API 33 best-matching callback now tracks `INTERNET + NOT_VPN`, waits for callback-provided `LinkProperties`, and replaces the session when the selected physical network changes. `VALIDATED` is intentionally not part of the request because Samsung Android 13 rejects it as a non-requestable mutable capability.
- Post-fix Wi-Fi regression: exact/subdomain checks passed, local block p95 was `0ms`, and allowed DNS completed `500/500` with p95 `9ms`, p99 `13ms`, and maximum `34ms`.
- Post-fix LTE regression: exact/subdomain checks passed, local block p95 was `0ms`, and allowed DNS completed `500/500` with p95 `39ms`, p99 `50ms`, and maximum `60ms`.
- LTE-to-Wi-Fi moved the VPN from LTE `613` to Wi-Fi `633`; the resulting VPN exposed `WIFI|VPN` and underlying network `633`.
- Wi-Fi-to-LTE suspended Wi-Fi `633` and created the replacement LTE VPN over `613` in about `0.36s`. The whole host-side `svc wifi disable` command plus first probe measured `3.60s`, mostly before Android emitted the network-loss callback.
- Active settings revoke removed the VPN network in `68ms`, but a DNS query launched concurrently with the disconnect waited for the resolver timeout. The second probe succeeded; total first-success measurement was `3.88s`, so the strict recovery gate remains failed for this edge case.
- A third-party `유니콘 HTTPS` profile is installed but was not activated. Other-VPN ownership behavior remains unverified because changing that profile would alter third-party privacy/network state.

### 2026-08-05 Galaxy S21 re-run after the 5s upstream timeout

The upstream timeout moved from `1.5s` to `5s`, so the reliability and latency gates were
re-measured against the new value rather than cited from the 1.5s runs.

- Device: Galaxy S21 (`SM-G991N`), Android 15. Wi-Fi `U+Net09B3`, RSSI `-42`, link `144Mbps`,
  upstream DNS `1.214.68.2` / `61.41.153.2`, `0%` ICMP loss over 20 packets.
- Three consecutive runs completed `500/500` allowed queries with `0` failures. Local blocked-query
  p95 was `0ms`, `1ms`, `0ms`. Instrumentation time was `42.3s`, `38.4s`, `38.8s`.
- Allowed-query p95 was `95ms`, `92ms`, `131ms`; p99 `120ms`, `195ms`, `196ms`; maximum `191ms`,
  `203ms`, `277ms`.
- **`upstream_attempt=failed` and `stop_reason` diagnostics were zero across all three runs.** No
  query reached even the old 1.5s timeout, let alone the new 5s one, so the timeout change did not
  participate in these numbers.
- The allowed-query latency is roughly 7x the `13ms`/`14ms`/`13ms` recorded on 2026-07-27. That run
  used a different Wi-Fi network and a different upstream resolver, so this is contextual comparison
  only, not a controlled before/after — the same caveat already applied to the 2026-07-25 runs. The
  gate itself is local filter latency, which stayed at `0-1ms`.

### 2026-08-06 active-revoke re-measured — still FAIL, tail did not grow

The row failed at `3.88s` because a concurrently started DNS lookup waited for the resolver. Raising
the upstream timeout to `5s` looked like it could make that worse. It did not, but the gate is still
missed.

Revoke was driven by connecting a second VPN (`유니콘 HTTPS`), which is how Android takes the slot
away from an app that already holds consent. System log at `02:16:37.871`:
`Vpn: setting state=DISCONNECTED, reason=prepare`, Keep's VPN network gone by `.887`, the other app
established by `.892`. `KeepDnsVpnService` was absent afterwards.

A DNS probe ran continuously across the event — one fresh wildcard hostname per probe so nothing is
served from cache, timed from `/proc/uptime`. Relative to the revoke:

| probe | start | end | result |
| --- | --- | --- | --- |
| #156 | `-0.36s` | `-0.19s` | ok (`170ms`) |
| #157 | `-0.19s` | `+2.84s` | **fail** (`3030ms`) — the in-flight lookup |
| #158 | `+2.85s` | `+3.13s` | ok (`280ms`) |

- **Exactly one probe failed**: the lookup that was already in flight when the slot moved. It hung
  until `+2.84s`.
- A lookup *started* after the revoke succeeded in `280ms`. Nothing about the new path is slow.
- First successful resolution completed at `+3.13s`, so the `<=3s` gate is missed — by a hair, and
  for the same reason as before: the in-flight lookup, not the recovery itself.
- `3.03s` against the earlier `3.88s` is consistent with the 5s upstream timeout being irrelevant
  here, as predicted: that timeout bounds a socket inside the service, and the service is gone.
  The two runs used different devices and networks, so this is not a controlled comparison either.

Closing the gate means shortening the in-flight lookup, not the teardown. The teardown is already
`~20ms` and the replacement path answers in `280ms`.

- Same Galaxy S21 and clean Wi-Fi as the run above.
- The **abrupt-teardown proxy** — a DNS lookup in flight, then the VPN removed without an orderly
  shutdown (`am force-stop`) — recovered on the **first probe after teardown** in all four attempts.
  ~~`381ms`, `411ms`, `290ms`, `298ms`~~ **Those millisecond figures were wrong and are withdrawn.**
  Android's `/system/bin/sh` evaluates `$(( ))` in 32 bits (`9223372036854775807` evaluates to `-1`),
  so subtracting nanosecond `date +%s%N` values overflowed and the printed durations were artifacts.
  What survives is the ordering, not the number: the probe immediately following the teardown
  resolved successfully every time.
- Harness note for anyone repeating this: time from `/proc/uptime` (centiseconds, small enough for
  32-bit shell arithmetic), and do not use `ping` exit status as a DNS check — a name that resolves
  but does not answer ICMP exits non-zero and reads as a DNS failure.
- Why the timeout is unlikely to be implicated: `DNS_TIMEOUT_MILLIS` bounds *our* upstream socket
  inside the service. Once the VPN is gone that socket is gone too, so it cannot extend the tail. It
  can only extend how long a query sits parked inside the service *before* teardown, and on a clean
  line the observed upstream round trip was well under `300ms`.

Constraint: revoking an app-based VPN cannot be driven from adb on this device, which is why the
second-VPN route was used. Samsung's VPN settings screen lists configured profiles only and does not
show `KeepDnsVpnService`; `appops set ACTIVATE_VPN deny|default` removes consent for the *next*
`prepare()` but leaves a running VPN established (`dumpsys vpn_management` still showed the active
session and no `onRevoke` reached the service); and there is no `cmd vpn_management` shell
interface. Connecting a second VPN needs a human tap, so this row is not automatable today.

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

- Core system-DNS blocking mechanics, local latency, repeatable 500/0 allowed-DNS reliability, and Wi-Fi/LTE rebinding are supported on the tested Galaxy devices, but the production gate is not complete.
- The socket-churn reliability blocker is resolved for the tested path by session-scoped endpoint reuse plus failure invalidation and fallback.
- Adaptive upstream ordering has three low-tail-latency Galaxy S21 Wi-Fi results plus post-fix Wi-Fi and LTE results on the Galaxy S20+.
- Do not call the production gate complete until the active-revoke in-flight DNS tail is resolved or explicitly accepted, and other-VPN conflict behavior is verified with an authorized test VPN profile.
- If browser Secure DNS or strict Private DNS bypasses the spike, product UX must disclose that limitation or the plan must move to a different enforcement design.
- If allowed-site reliability, latency, or recovery gates fail, stop v1 implementation and replan the VPN/DNS architecture before adding product UI.
- If other-VPN conflicts are common in target QA devices, add explicit degraded-state UX to the v1 plan before implementation.
