# 루틴 웹사이트 차단 트리거 계약

Issue: 미할당 (branch `feature/website-blocking-spike`)

이 문서는 **루틴 시간대에 웹사이트 차단이 실제로 서 있는 구간**을 고정하는 source of truth다.
앱 차단은 접근성 서비스가 foreground 앱이 바뀔 때마다 다시 판정하지만, 웹 차단은 DNS VPN이
그 시간대 내내 떠 있어야 성립한다. 즉 "언제 켜지는가"가 곧 "언제 지켜지는가"다.

`docs/ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md`가 "루틴 시간대에는 앱이 약속을 실제로 지켜준다"를
앱 차단 관점에서 고정했다면, 이 문서는 같은 약속의 웹 차단 쪽 경계와 아직 지키지 못하는 구간을
적는다.

## 현재 동작

| 항목 | 동작 | 근거 |
| --- | --- | --- |
| 켜는 계기 | 네 곳이 **같은 판정**을 돌린다 (아래 표) | `RoutineWebsiteBlockingLauncher` / `WebsiteBlockingRuntimePolicy` |
| 차단 대상 | 그 시점에 활성인 **모든** 루틴의 도메인 합집합 | `RoutineWebsiteBlockingPolicy.resolveSession` |
| 유지 구간 | 켜진 뒤 창이 끝날 때까지 연속 | 서비스가 `stopAtEpochMillis`를 들고 스스로 종료 |
| 마감 값 | 활성 루틴 중 **가장 늦게 끝나는** 시각 | `RoutineWebsiteBlockingPolicy` — 먼저 끝나는 루틴에 맞추면 진행 중인 다른 루틴의 약속이 깨진다 |
| 필요 권한 | VPN 동의 + 정확 알람. **접근성 권한은 불필요** | 웹 전용 루틴이 쓰지도 않을 권한을 요구하지 않기 위한 설계 |
| 동의 요청 시점 | 루틴 편집에서 웹사이트를 고를 때 | 수신자에서는 시스템 동의창을 띄울 수 없다 |
| 수동/타이머 잠금 | Home 판정에 루틴 창과 **함께** 합성된다 | `WebsiteBlockingRuntimePolicy.decide` |

### 켜는 계기

| 계기 | 판정 주체 | 동의창 |
| --- | --- | --- |
| 루틴 **시작 알람** | `RoutineAlarmReceiver` → `RoutineWebsiteBlockingLauncher` | 불가 (`ConsentDenied` 고지) |
| 루틴 생성·수정·삭제·ON/OFF | `RoutineViewModel` 목록 흐름 → 같은 launcher | 웹사이트 선택 시점에 이미 받음 |
| 부팅 직후 | `BootReceiver` → 같은 launcher | 불가 (동의는 재부팅에도 유지됨) |
| 앱 진입 / 홈 복귀 | `HomeViewModel.refreshRoutineWebsiteSession` → `WebsiteBlockingVpnController` | **가능** |

**알람이 울리는 순간에만 막히는 것이 아니다.** 한 번 켜지면 창이 끝날 때까지 유지되고,
그 "한 번"이 실패해도 위 네 계기 중 아무거나 하나가 같은 창을 다시 세운다.

마감 합성 규칙: 무기한 소스(수동 잠금)가 하나라도 있으면 마감 없음, 아니면 관여하는
마감들 중 **가장 늦은** 값. 먼저 끝나는 쪽에 맞추면 아직 진행 중인 약속이 조용히 깨진다.

## 실기기 근거

Galaxy S21 (`SM-G991N`), Android 15, 2026-08-05. 접근성 권한을 끄고 앱을 백그라운드로 둔 상태.

```
13:05:00.076 KeepRoutineWeb  routine_web total=5 active=3 withSites=4 session=true consent=true
13:05:00.103 KeepWebsiteBlocking upstream_rebind network=133 dns_count=2
13:05:01.745 KeepWebsiteBlocking upstream_attempt=failed address_family=4 error_type=SocketTimeoutException
13:05:03.252 KeepWebsiteBlocking stop_reason=UpstreamUnavailable
```

- 알람 → 정책 판정 → 동의 확인 → TUN 수립까지 화면 없이 동작한다.
- 이어서 업스트림 DNS가 1.5초 타임아웃에 걸려 fail-open으로 종료했다. 당시 회선은 `1.1.1.1`
  왕복이 약 1.7초였다. 차단 동작 자체(도메인 NXDOMAIN)는 이 회선에서 확인하지 못했다.
- 같은 날 12:45 회차는 알람이 **49초 지연**되어 도착했고, 그 시점에 기기 네트워크가 없어
  서비스가 떴다가 즉시 fail-open으로 내려갔다.

### 손실이 큰 회선에서의 재측정 (2026-08-05 15:33~15:40)

같은 기기, Wi-Fi RSSI −75 / 링크 8Mbps. **업스트림 DNS(`168.126.63.1`)로의 ICMP 손실률이
85%**(20패킷 중 3수신)인 회선이다. 앞선 기록이 "확인하지 못했다"고 남긴 것들을 여기서 확인했다.

| 확인 항목 | 결과 |
| --- | --- |
| 차단 도메인 NXDOMAIN | `youtube.com` → `unknown host` |
| 하위 도메인 | `m.youtube.com` → `unknown host` |
| 유사 접미사 통과 | `notyoutube.com` → `46.8.8.226` 정상 해석 |
| 통과 도메인 | `example.com`, `www.cloudflare.com` 정상 해석 |
| **손실 회선에서의 차단 유지** | `youtube.com` 20회 시도 → **20회 차단, 누수 0** |
| fail-open 후 서비스 생존 | `stop_reason=UpstreamUnavailable` 이후에도 FGS 유지, 스스로 재바인딩 |
| 정지 후 복구 | 서비스 0개로 정리, `youtube.com` 즉시 정상 해석 |

**단, "누수 0"은 그 20회가 fail-open 구간에 걸리지 않았다는 뜻이지 누수가 불가능하다는 뜻이
아니다.** 같은 회선에서 이어진 관측에서는 `stop_reason=UpstreamUnavailable` 직후 한 차례
`youtube.com`이 해석됐다. 차단 판정은 TUN이 서 있는 동안 로컬에서 내려지므로 업스트림
지연에는 영향받지 않지만, 필터가 **완전히 물러나 TUN을 내린 구간**에서는 차단 대상도 함께
열린다. 그것이 fail-open의 정의다. 그 구간은 250ms 재시도로 짧게 유지된다.

이 회선에서 관측된 재시도는 전부 250ms 전환용 재시도였고 백오프 단계(5s/15s/60s)는 걸리지
않았다. **간헐적 손실은 지속적 장애가 아니기 때문이다** — 중간에 성공한 왕복이 예산을
정당하게 되돌린다. 백오프는 업스트림이 계속 죽어 있을 때를 위한 경로이며, 이 회선에서는
아직 관측되지 않았다.

> 위 표는 **손실이 큰 회선에서의 차단 정확도** 근거이지 신뢰성 게이트 근거가 아니다.
> 500/0 신뢰성과 p95 지연 게이트는 `docs/WEBSITE_BLOCKING_VPN_SPIKE.md`에 있다. 최초 측정은
> 업스트림 타임아웃이 1.5초이던 시절의 것이었으나, 5초로 올린 뒤 2026-08-05 에 같은 계측
> 테스트를 양호한 Wi-Fi(Galaxy S21, RSSI `-42`, ICMP 손실 `0%`)에서 다시 돌렸다. 3회 연속
> `500/500` · 실패 `0`, 로컬 blocked-query p95 `0ms`/`1ms`/`0ms`. 두 게이트 모두 5초 기준으로
> 유효하다.

### 계기별 실기기 확인 (2026-08-05 16:42~16:55)

앱 0개 · 웹사이트 1개(`youtube.com`)인 **웹 전용 루틴**, 창 16:39~17:39 수요일.

| 계기 | 결과 | 근거 |
| --- | --- | --- |
| 루틴 생성 (창 진행 중) | **확인.** 알람 없이 저장 즉시 차단이 섰다 | `16:42:36 routine_web ... session=true` → `upstream_rebind` |
| 부팅 | **확인.** 앱을 열지 않고도 차단이 다시 섰다 | `16:54:27 routine_web ... session=true` (부팅 16:54:19) |
| 접근성 권한 회수 후 | **확인.** 웹 전용 루틴은 접근성 없이 계속 막았다 | `enabled_accessibility_services=null` 상태에서 차단 유지 |
| 앱 진입 | 콜드 스타트에서 복구는 관측됨. 다만 **홈 경로 단독 기여는 미검증**(아래) | `routine_web` 로그가 함께 떠, 루틴 화면 경로와 분리되지 않았다 |

**부팅 경로의 실제 타이밍**: Keep은 Direct Boot 대응이 아니므로 `BOOT_COMPLETED`는 부팅
직후가 아니라 **사용자가 기기 잠금을 푼 뒤에** 전달된다. 위 로그의 16:54:27도 잠금 해제
시점이다. 잠긴 화면에서는 브라우징도 불가능하므로 약속이 깨지는 구간은 아니지만, "부팅
직후"라는 표현은 정확히는 "부팅 후 첫 잠금 해제"다.

### 돌아왔을 때의 재확인

`WebsiteBlockingVpnController`의 판정 효과는 **판정값이 바뀔 때만** 실행된다. 그래서 아래가
재현됐다.

1. 창 안에서 차단이 서 있다 (판정 = `Running`)
2. 앱 프로세스는 살아 있는 채 서비스만 죽는다 (시스템이 회수했거나 강제 종료)
3. 홈으로 돌아온다 → 판정은 여전히 `Running`이라 효과가 다시 돌지 않는다 → **차단이 서지 않는다**

그래서 재개 신호(`resumeCount`)를 키로 삼는 **재확인 전용 효과**를 따로 뒀다. 기존 효과는
그대로 두었으므로 대상이 바뀐 잠금을 다시 세우는 경로는 영향받지 않는다.

무엇을 다시 세울지는 `WebsiteBlockingReassertPolicy`가 정하며, **아무것도 서 있지 않을 때
(`Inactive`)만** 다시 세운다. 런타임 상태를 그대로 효과 키에 넣으면 두 가지가 깨지기
때문이다 — `NetworkUnavailable`로 물러난 세션을 홈이 즉시 다시 세워 **서비스의 백오프와
싸우고**, 동의가 없는 경우 **홈에 돌아올 때마다 시스템 동의창이 다시 뜬다**. 재확인 경로는
동의창을 아예 띄우지 않는다.

실기기 확인 (18:14, 프로세스 pid 유지 · 서비스만 종료):

```
resume_reassert count=2 decision=Running status=Inactive consent=true
upstream_rebind network=100 dns_count=2      → 서비스 복구
```

같은 로그가 콜드 스타트에서는 `status=Active`로 찍히고 아무것도 다시 세우지 않는다.

## 구멍 현황

모두 "창 안인데 차단이 서 있지 않다"로 수렴하던 것들이다.

| # | 조건 | 현재 상태 |
| --- | --- | --- |
| 1 | 창이 이미 진행 중일 때 루틴을 새로 만들거나 켬 | **닫힘.** 루틴을 바꾸는 사람은 그 순간 앱 안에 있다. 목록 흐름이 그 자리에서 판정한다 |
| 2 | 창 도중 재부팅 | **닫힘.** `BootReceiver`가 알람 재예약 루프보다 먼저 같은 판정을 돌린다 |
| 3 | fail-open으로 물러난 뒤 네트워크가 회복됨 | **닫힘.** 서비스가 죽지 않고 창이 끝날 때까지 5s→15s→60s 백오프로 다시 시도한다 |
| 4 | 정확 알람이 지연·누락되거나 권한이 없음 | **부분.** 앱을 열거나 홈으로 돌아오면 그 회차가 되살아난다(실측). **앱을 열지 않으면 여전히 누락** |
| 5 | VPN 동의가 없는 상태로 루틴이 시작됨 | 완화됨. 웹사이트 선택 시점에 미리 동의를 받고, 실패 시 `ConsentDenied` 배너로 고지 |

구멍 4가 완전히 닫히지 않는 이유는 신뢰할 수 있는 계기가 알람·부팅·포그라운드뿐이기
때문이다. WorkManager는 대안이 못 된다 — 주기 최소 간격보다도, **워커가 백그라운드
포그라운드-서비스 시작 제한의 면제 목록에 없다**는 것이 결정적이다. 남은 선택지는 접근성
서비스뿐이며 아래 판단 2번으로 남긴다.

### 반대 방향

창 도중 루틴을 끄거나 삭제하면 이제 그 자리에서 차단이 내려간다(`StopBlocking`). 앱 진입
판정도 루틴이 서 있지 않으면 `Stopped`를 낸다. 다만 `#609` 계약이 실행 중 루틴의
수정·삭제·OFF를 이미 막고 있으므로 실사용에서 이 경로는 좁다. 남는 것은 긴급 해제이며,
**긴급 해제는 현재 앱만 풀고 웹 차단은 그대로 둔다**(`EmergencyUnlockState`는 `unlockedApps`만
보유). 이 불일치는 아래 판단 1번이다.

## 복구 동작 (구멍 3)

업스트림 DNS가 응답하지 않으면 필터는 여전히 물러난다(통과 도메인을 막지 않기 위해서다).
달라진 것은 **되돌아올 수 있다**는 점이다.

| 단계 | 동작 |
| --- | --- |
| 전환용 빠른 재시도 | 같은 네트워크에서 250ms 뒤 1회 (`UPSTREAM_TRANSITION_RETRY_LIMIT`) |
| 복구 재시도 | 5s → 15s → 60s(고정) 백오프. 창이 끝날 때까지 반복 |
| 포기 | 다음 시도가 창 마감을 넘길 때만. 그때 서비스가 종료된다 |

핵심은 복구를 기다리는 동안 **서비스와 네트워크 콜백을 살려두는 것**이다. 이전에는 물러나는
순간 `stopFromWorker`가 콜백을 해제하고 서비스를 끝내, 되살릴 주체가 함께 사라졌다.
느린 회선은 네트워크가 계속 붙어 있어 연결성 콜백이 오지 않으므로 자체 타이머가 필요하다.
다시 서면 배너는 `Active`로 돌아가고 백오프 단계도 0으로 돌아간다.

## 판단이 필요한 것

1. **긴급 해제가 웹 차단에도 적용되어야 하는가.** 지금은 앱만 풀린다. "잠깐 숨통"이 약속의
   의미라면 웹도 함께 풀어야 일관되고, 그렇지 않다면 긴급 해제 화면이 그 범위를 명시해야 한다.
   범위를 넓히려면 해제 만료 시 차단을 *되살리는* 경로(앱이 닫혀 있을 수 있으므로 알람)가
   함께 필요하다.
2. **접근성 서비스 안전망을 추가할 것인가.** 구멍 4의 잔여(앱을 열지 않는 사용자)를 닫는
   유일한 선택지다. 대신 웹 전용 루틴도 접근성 권한을 요구하게 된다. 네 계기로 부족한지는
   실사용 로그를 본 뒤 판단한다.

### 해소된 판단

- **업스트림 타임아웃 1.5초** → 5초로 올렸다. 측정된 회선의 1.1.1.1 왕복이 약 1.7초여서 모든
  질의가 시간 초과로 떨어지고 있었다. 차단 정확도에는 영향이 없다 — 차단 대상은
  `DnsVpnDatagramProcessor`가 로컬에서 NXDOMAIN을 만들고, 업스트림은 통과 도메인에만 관여한다.
  느려지는 것은 죽은 업스트림에서의 통과 질의뿐이고, 그마저도 이제는 영구 fail-open이 아니다.
  `DnsUpstreamEndpointPool`은 이미 **모든 서버를 소진한 뒤에만** 실패로 판정한다.

## 재현 / 검증 절차

dev 플레이버에서만 웹사이트 탭이 열린다(`BuildConfig.WEBSITE_BLOCKING_ENABLED`).

```bash
./gradlew :app:assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM allow
```

1. 루틴을 만들고 웹사이트 탭에서 도메인을 추가한다(앱은 0개여도 저장된다). VPN 동의를 허용한다.
2. 시작 시각을 몇 분 뒤로 두고 저장한 다음, 홈 키로 앱을 백그라운드로 보낸다.
3. 알람 시각 이후 확인한다.

```bash
adb logcat -d | grep -E "KeepRoutineWeb|KeepWebsiteBlocking"
adb shell dumpsys activity services com.uiery.keep.dev | grep -c KeepDnsVpnService
adb shell "ping -c 1 <blocked-domain>"    # unknown host 여야 한다
adb shell "ping -c 1 www.cloudflare.com"  # 정상 해석되어야 한다
```

### 계기별 확인

각 계기는 창이 **이미 진행 중인 상태**에서 확인해야 의미가 있다. 위 `dumpsys` 카운트가
1이 되고 `KeepRoutineWeb`에 `session=true`가 남으면 그 계기가 선 것이다.

| 계기 | 재현 |
| --- | --- |
| 루틴 생성/ON | 창 안의 시간대로 루틴을 새로 만들거나 켠다. 화면을 나가지 않아도 서야 한다 |
| 부팅 | 창 도중 `adb reboot`. 잠금 해제 없이도 서야 한다 |
| 앱 진입 | 서비스를 강제 종료(`adb shell am force-stop`)한 뒤 앱을 열고 홈으로 돌아온다 |
| 루틴 OFF/삭제 | 창 도중 끄면 서비스가 내려가야 한다(`#609`가 막지 않는 경로에서) |

복구(구멍 3)는 창 도중 기내 모드를 켰다 끄면 확인된다. 로그에서 다음 순서가 보여야 한다.

```
KeepWebsiteBlocking stop_reason=UpstreamUnavailable
KeepWebsiteBlocking upstream_recovery_scheduled ... attempt=1 delay_ms=5000
KeepWebsiteBlocking upstream_recovery_retry ...
```

이전 동작과의 차이는 첫 줄 뒤에 서비스가 **사라지지 않는다**는 점이다. 회복 전까지 배너는
`NetworkUnavailable`로 남고, 다시 서면 `Active`로 돌아간다.

## 관련 문서

- `docs/WEBSITE_BLOCKING_VPN_SPIKE.md` — DNS VPN 타당성 근거, 게이트 판정(2026-08-06 기준 전부 통과), 우회 한계
- `docs/ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md` — 활성 루틴 보호(앱 차단) 계약
- `docs/MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md` — 수동/타이머 잠금 마감 계약
