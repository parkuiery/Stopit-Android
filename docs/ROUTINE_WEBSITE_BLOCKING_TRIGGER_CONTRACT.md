# 루틴 웹사이트 차단 트리거 계약

Issue: 미할당 (branch `feature/website-blocking-spike`)

이 문서는 **루틴 시간대에 웹사이트 차단이 실제로 서 있는 구간**을 고정하는 source of truth다.
앱 차단은 접근성 서비스가 foreground 앱이 바뀔 때마다 다시 판정하지만, 웹 차단은 DNS VPN이
그 시간대 내내 떠 있어야 성립한다. 즉 "언제 켜지는가"가 곧 "언제 지켜지는가"이며, 지금은 그
계기가 하나뿐이다.

`docs/ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md`가 "루틴 시간대에는 앱이 약속을 실제로 지켜준다"를
앱 차단 관점에서 고정했다면, 이 문서는 같은 약속의 웹 차단 쪽 경계와 아직 지키지 못하는 구간을
적는다.

## 현재 동작

| 항목 | 동작 | 근거 |
| --- | --- | --- |
| 켜는 계기 | 루틴 **시작 알람** 수신 1회 | `RoutineAlarmReceiver.applyRoutineWebsiteBlocking` |
| 차단 대상 | 그 시점에 활성인 **모든** 루틴의 도메인 합집합 | `RoutineWebsiteBlockingPolicy.resolveSession` |
| 유지 구간 | 켜진 뒤 창이 끝날 때까지 연속 | 서비스가 `stopAtEpochMillis`를 들고 스스로 종료 |
| 마감 값 | 활성 루틴 중 **가장 늦게 끝나는** 시각 | `RoutineWebsiteBlockingPolicy` — 먼저 끝나는 루틴에 맞추면 진행 중인 다른 루틴의 약속이 깨진다 |
| 필요 권한 | VPN 동의 + 정확 알람. **접근성 권한은 불필요** | 웹 전용 루틴이 쓰지도 않을 권한을 요구하지 않기 위한 설계 |
| 동의 요청 시점 | 루틴 편집에서 웹사이트를 고를 때 | 수신자에서는 시스템 동의창을 띄울 수 없다 |
| 수동/타이머 잠금 | 별도 경로. Home 화면이 살아 있는 동안만 판정 | `WebsiteBlockingVpnController` |

**알람이 울리는 순간에만 막히는 것이 아니다.** 한 번 켜지면 창이 끝날 때까지 유지된다.
문제는 그 "한 번"이 실패했을 때 다시 시도할 계기가 없다는 점이다.

## 실기기 근거

Galaxy S21 (`SM-G991N`), Android 15, 2026-08-05. 접근성 권한을 끄고 앱을 백그라운드로 둔 상태.

```
13:05:00.076 KeepRoutineWeb  routine_web total=5 active=3 withSites=4 session=true consent=true
13:05:00.103 KeepDnsVpnSpike upstream_rebind network=133 dns_count=2
13:05:01.745 KeepDnsVpnSpike upstream_attempt=failed address_family=4 error_type=SocketTimeoutException
13:05:03.252 KeepDnsVpnSpike stop_reason=UpstreamUnavailable
```

- 알람 → 정책 판정 → 동의 확인 → TUN 수립까지 화면 없이 동작한다.
- 이어서 업스트림 DNS가 1.5초 타임아웃에 걸려 fail-open으로 종료했다. 당시 회선은 `1.1.1.1`
  왕복이 약 1.7초였다. 차단 동작 자체(도메인 NXDOMAIN)는 이 회선에서 확인하지 못했다.
- 같은 날 12:45 회차는 알람이 **49초 지연**되어 도착했고, 그 시점에 기기 네트워크가 없어
  서비스가 떴다가 즉시 fail-open으로 내려갔다.

## 열려 있는 구멍

모두 "창 안인데 차단이 서 있지 않다"로 수렴한다.

| # | 조건 | 사용자에게 보이는 결과 | 현재 상태 |
| --- | --- | --- | --- |
| 1 | 창이 이미 진행 중일 때 루틴을 새로 만들거나 켬 | 다음 시작 시각까지 웹은 열려 있음 | 미해결 |
| 2 | 창 도중 재부팅 | 알람은 다시 예약되지만(`BootReceiver.restoreRoutinesForBoot`) 차단은 켜지지 않음 | 미해결 |
| 3 | fail-open으로 물러난 뒤 네트워크가 회복됨 | 스스로 복귀하지 않음. 창이 끝날 때까지 열린 채 유지 | 미해결. 상태는 `NetworkUnavailable` 배너로 노출됨 |
| 4 | 정확 알람이 지연·누락되거나 권한이 없음 | 그 회차 전체가 누락 | 미해결. 앱 차단 쪽은 `#609` 계약이 별도로 다룸 |
| 5 | VPN 동의가 없는 상태로 루틴이 시작됨 | 앱만 막히고 웹은 열림 | 완화됨. 웹사이트 선택 시점에 미리 동의를 받고, 실패 시 `ConsentDenied` 배너로 고지 |

### 반대 방향

창 도중 루틴을 끄거나 삭제해도 서비스는 마감까지 계속 돈다. 다만 `#609` 계약이 실행 중 루틴의
수정·삭제·OFF를 이미 막고 있으므로 실사용에서 이 경로는 좁다. 남는 것은 긴급 해제이며,
**긴급 해제는 현재 앱만 풀고 웹 차단은 그대로 둔다**(`EmergencyUnlockState`는 `unlockedApps`만
보유). 이 불일치는 별도 결정 대상이다.

## 닫는 방안

같은 판정(`RoutineWebsiteBlockingPolicy.resolveSession`)을 세 지점에서 더 돌리면 1~4가 닫힌다.
접근성 권한을 새로 요구하지 않는다.

| 지점 | 하는 일 | 닫히는 구멍 |
| --- | --- | --- |
| 앱 진입 시 | Home 컨트롤러가 수동/타이머 잠금뿐 아니라 루틴 창까지 합쳐 판정 | 1, 2, 4 (앱을 열었을 때 한정) |
| 부팅 직후 | `BootReceiver`가 알람 재예약과 함께 같은 판정 1회 | 2 |
| 네트워크 회복 시 | fail-open으로 물러난 세션을 업스트림이 살아나면 재시도 | 3 |

앱 진입 판정은 반대 방향(루틴 OFF/삭제 반영)도 함께 정리한다.

## 판단이 필요한 것

1. **긴급 해제가 웹 차단에도 적용되어야 하는가.** 지금은 앱만 풀린다. "잠깐 숨통"이 약속의
   의미라면 웹도 함께 풀어야 일관되고, 그렇지 않다면 긴급 해제 화면이 그 범위를 명시해야 한다.
2. **약한 네트워크에서의 1.5초 업스트림 타임아웃.** 이 값에서는 느린 회선이 상시 fail-open으로
   수렴한다. 스파이크의 500/0 신뢰성 근거는 양호한 Wi-Fi에서만 얻었다
   (`docs/WEBSITE_BLOCKING_VPN_SPIKE.md`).
3. **접근성 서비스 안전망을 추가할 것인가.** 위 세 지점으로 부족하다고 판단되면 접근성 서비스의
   시간 경계 재평가에 같은 판정을 얹을 수 있다. 대신 웹 전용 루틴도 접근성 권한을 요구하게 된다.

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
adb logcat -d | grep -E "KeepRoutineWeb|KeepDnsVpnSpike"
adb shell dumpsys activity services com.uiery.keep.dev | grep -c KeepDnsVpnService
adb shell "ping -c 1 <blocked-domain>"    # unknown host 여야 한다
adb shell "ping -c 1 www.cloudflare.com"  # 정상 해석되어야 한다
```

구멍 1·2를 재현하려면 창이 진행 중인 상태에서 각각 루틴을 새로 켜거나 기기를 재부팅한 뒤,
서비스가 뜨지 않는 것을 위 명령으로 확인한다.

## 관련 문서

- `docs/WEBSITE_BLOCKING_VPN_SPIKE.md` — DNS VPN 타당성 근거, 미해결 게이트, 우회 한계
- `docs/ACTIVE_ROUTINE_ENFORCEMENT_CONTRACT.md` — 활성 루틴 보호(앱 차단) 계약
- `docs/MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md` — 수동/타이머 잠금 마감 계약
