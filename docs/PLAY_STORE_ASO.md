# Play Store ASO 실행 런북

이 문서는 GitHub issue #65 `[성장] Play Console ASO 시안 반영 및 14·30일 유입 회복 검증`의 저장소 기준 실행 문서다.

이전 closed issue #15는 **카피/스크린샷/측정 초안 정리** 단계였고, 현재 문서는 그 초안을 실제 Play Console 반영과 14일·30일 검증까지 이어가기 위한 운영 기준을 담는다.

## 현재 상태 요약

- 상태: 대표님 수동 작업으로 실제 Play Console ASO copy/스크린샷 반영 완료
- 저장소 기준 남은 작업:
  - Play Console 반영 일시/범위 기록
  - 반영 직전/직후 baseline 보강
  - 14일/30일 성과 검증 루프 기록
- 완료된 외부 수동 작업:
  - Play Console listing copy 반영
  - Play Console 스크린샷 업로드
- 후속 수동 기록 작업:
  - listing 전환율 / 평점 / 리뷰 수 기록
  - 반영 당시 실제 노출값과 저장소 문서 일치 여부 재확인

> 운영 메모: 이 이슈의 실제 Play Console 반영은 저장소 CI가 아니라 대표님 수동 배포로 처리될 수 있다. 따라서 repo/CI 자동화 흔적이 없다는 이유만으로 미반영으로 판단하지 않는다.

## 현재 외부 경계

이번 run 기준으로 저장소 안에서 더 밀 수 있는 문서 계약은 정리했다. 남은 항목은 모두 **Play Console 수동 확인 또는 시간 경과 후 측정**이 필요한 외부 경계다.

- Play Console 현재 노출값을 열어 정확한 반영 시각/범위/스크린샷 버전명을 기록해야 함
- listing 전환율 / 평점 / 리뷰 수 / 최근 리뷰 톤은 저장소에서 자동 조회할 수 없어 수동 확인이 필요함
- 14일·30일 성과 비교는 실제 시간이 지나야 채울 수 있음

따라서 이 문서는 이제 "반영 여부 의심" 문서가 아니라, **이미 반영된 수동 작업의 사후 복원 기록 + 후속 측정 런북**으로 해석한다.

### 2026-06-01 repo-observable 중간 스냅샷

- 확인 시각: `2026-06-01 06:08 KST`
- 명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
- 해석 주의: Play Console 반영이 `2026-05-27 01:18 KST 이전`으로만 복원되어 있고, 아래 `30daysAgo..yesterday` 창은 ASO 반영 전/후가 섞인 중간 관측이다. 따라서 이 값은 **14일/30일 성과 판정이 아니라 다음 측정의 기준선 보강**으로만 쓴다.

| 지표 | 2026-05-24 issue #65 기준 | 2026-06-01 GA4 30일 창 | 변화 | 해석 |
| --- | ---: | ---: | ---: | --- |
| `newUsers` | 203 | 274 | +35.0% | 직전 스냅샷보다 회복됐지만, 같은 창의 직전 30일 373 대비는 -26.5%라 완전 회복 아님 |
| `Organic Search` 신규 사용자 | 178 | 167 | -6.2% | 신규 유저 증가는 `Direct` 비중 증가와 함께 봐야 하며, ASO organic 회복으로 단정 금지 |
| `Organic Search` 신규 사용자 비중 | 87.7% | 60.9% | -26.8pp | acquisition mix가 바뀌었으므로 listing 효과 판단에는 Play Console listing conversion이 필요 |
| `activeUsers` | 457 | 523 | +14.4% | 활성 사용자는 반등했지만 직전 30일 614 대비 -14.8% |
| `sessions` | 4,636 | 4,484 | -3.3% | 세션은 아직 기준선보다 낮고 직전 30일 6,430 대비 -30.3% |

다음 결론은 보류한다.

- `Organic Search`만 보면 아직 #65 기준선보다 낮다.
- 전체 `newUsers` 반등은 긍정 신호지만, Play listing copy/screenshot 효과인지 다른 유입/버전/노출 요인인지는 Play Console의 listing conversion과 store acquisition breakdown 없이는 분리할 수 없다.
- 14일 체크는 `2026-06-10 KST 이후`, 30일 체크는 `2026-06-26 KST 이후`에 같은 분자/분모로 다시 기록한다.

### 2026-06-01 acquisition attribution gate (#242)

#65의 14일/30일 ASO 판정 전에 #242의 획득 채널 기준을 먼저 고정한다. 2026-06-01 GA4 스냅샷에서는 전체 신규 유저가 회복된 것처럼 보이지만 `Direct` 비중이 크게 늘었고 `Paid Search`는 신규 유저 없이 활성/세션만 남아 있어, 이 상태로 ASO 효과를 `Organic Search` 변화만으로 판정하면 오판 가능성이 크다.

2026-06-07 repo-observable 스냅샷에서도 같은 문제가 유지됐다. 최신 재조회(`2026-06-08T23:09:54Z`) 기준 전체 `newUsers`는 571명으로 직전 30일 346명 대비 `+65.0%`지만, `Direct` 신규 사용자가 336명으로 전체 신규의 `58.8%`로 과다 상태를 유지했다. `Organic Search` 신규 사용자는 236명으로 #65 기준선 178명을 넘었지만 Direct 과다/Play Console 미확인 상태라 ASO 회복으로 승격하지 않는다. `sessions`는 5,244회로 직전 6,149회 대비 `-14.7%`이며, `Paid Search`는 활성 18명·세션 156회가 남아 있으면서 신규 사용자는 계속 0명이다. 따라서 현재 신규 유입 반등은 **ASO 회복 후보가 아니라 attribution 확인 없이는 판정 보류**로 둔다.

| 항목 | 2026-06-01 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 274 | 직전 373 대비 -26.5% | 신규 유입은 아직 완전 회복 전 |
| 전체 `activeUsers` | 523 | 직전 614 대비 -14.8% | 활성 사용자도 직전 기간보다 낮음 |
| 전체 `sessions` | 4,484 | 직전 6,430 대비 -30.3% | 세션 회복은 더 약함 |
| `Organic Search` 신규 사용자 | 167 | 167 / 274 = 60.9% | #65 기준선 178보다 낮아 ASO 회복 단정 금지 |
| `Direct` 신규 사용자 | 107 | 107 / 274 = 39.1% | 실제 direct 유입인지, 링크/캠페인 attribution 누락인지 확인 필요 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 19명·세션 225회와 함께 보면 과거 유저/재방문/분류 잔상 가능성 확인 필요 |

#### 2026-06-02 추가 스냅샷

명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
확인 시각: `2026-06-02T22:16:16Z`

| 항목 | 2026-06-02 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 432 | 직전 366 대비 +18.0% | 신규 유저는 반등했지만 channel mix가 ASO 효과로 보기 어려움 |
| 전체 `activeUsers` | 688 | 직전 613 대비 +12.2% | 활성 사용자도 반등했지만 세션은 여전히 약함 |
| 전체 `sessions` | 4,721 | 직전 6,401 대비 -26.3% | 유입 반등이 참여 회복으로 충분히 이어졌다고 보기 어려움 |
| `Organic Search` 신규 사용자 | 169 | 169 / 432 = 39.1% | #65 기준선 178보다 낮고 비중도 2026-06-01보다 더 낮아짐 |
| `Direct` 신규 사용자 | 263 | 263 / 432 = 60.9% | attribution 누락/외부 링크/캠페인 유입 가능성을 먼저 확인해야 함 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 19명·세션 215회는 신규 획득 성과로 계산하지 않음 |

#### 2026-06-03 live readback

명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
확인 시각: `2026-06-03T06:12:47Z`

| 항목 | 2026-06-03 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 432 | 직전 366 대비 +18.0% | 전일 최신값과 동일하게 신규 유저 반등은 유지 |
| 전체 `activeUsers` | 688 | 직전 613 대비 +12.2% | 활성 사용자 반등은 유지되지만 세션 회복은 약함 |
| 전체 `sessions` | 4,733 | 직전 6,401 대비 -26.1% | 세션은 여전히 직전 30일보다 크게 낮음 |
| `Organic Search` 신규 사용자 | 169 | 169 / 432 = 39.1% | #65 기준선 178보다 낮아 ASO 회복으로 승격 불가 |
| `Direct` 신규 사용자 | 263 | 263 / 432 = 60.9% | Direct 과다 상태가 유지되어 Play Console/external/campaign 확인이 선행 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 19명·세션 215회는 신규 획득 성과로 계산하지 않음 |

#### 2026-06-03 second live readback

명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
확인 시각: `2026-06-03T15:18:34Z`

| 항목 | 2026-06-03 두 번째 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 457 | 직전 360 대비 +26.9% | 신규 유저 반등 폭은 더 커졌지만 channel mix가 더 불안정함 |
| 전체 `activeUsers` | 707 | 직전 608 대비 +16.3% | 활성 사용자도 반등했지만 신규 반등 원천은 아직 attribution 확인 필요 |
| 전체 `sessions` | 4,744 | 직전 6,350 대비 -25.3% | 세션은 여전히 직전 30일보다 크게 낮아 참여 회복으로 단정 불가 |
| `Organic Search` 신규 사용자 | 176 | 176 / 457 = 38.5% | #65 기준선 178보다 아직 낮아 ASO 회복으로 승격 불가 |
| `Direct` 신규 사용자 | 281 | 281 / 457 = 61.5% | Direct 과다 상태가 더 커져 Play Console/external/campaign 확인이 선행 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 19명·세션 200회는 신규 획득 성과로 계산하지 않음 |

#### 2026-06-04 late live readback

명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
확인 시각: `2026-06-04T20:14:53Z`

| 항목 | 2026-06-04 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 506 | 직전 349 대비 +45.0% | 신규 유저 반등 폭은 더 커졌지만 channel mix 불안정은 유지 |
| 전체 `activeUsers` | 757 | 직전 601 대비 +26.0% | 활성 사용자도 반등했지만 신규 반등 원천은 아직 attribution 확인 필요 |
| 전체 `sessions` | 4,897 | 직전 6,269 대비 -21.9% | 세션은 여전히 직전 30일보다 낮아 참여 회복으로 단정 불가 |
| `Organic Search` 신규 사용자 | 179 | 179 / 506 = 35.4% | #65 기준선 178을 간신히 넘었지만 Play Console 확인 전까지 ASO 회복으로 승격 불가 |
| `Direct` 신규 사용자 | 327 | 327 / 506 = 64.6% | Direct 과다 상태가 더 커져 Play Console/external/campaign 확인이 선행 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 18명·세션 190회는 신규 획득 성과로 계산하지 않음 |

#### 2026-06-05 live readback

명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
확인 시각: `2026-06-05T21:24:54Z`

| 항목 | 2026-06-05 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 520 | 직전 346 대비 +50.3% | 신규 유저 반등 폭은 더 커졌지만 channel mix 불안정은 유지 |
| 전체 `activeUsers` | 766 | 직전 594 대비 +29.0% | 활성 사용자도 반등했지만 신규 반등 원천은 아직 attribution 확인 필요 |
| 전체 `sessions` | 4,924 | 직전 6,297 대비 -21.8% | 세션은 여전히 직전 30일보다 낮아 참여 회복으로 단정 불가 |
| `Organic Search` 신규 사용자 | 187 | 187 / 520 = 36.0% | #65 기준선 178을 넘었지만 Direct 과다/Play Console 미확인 전까지 ASO 회복으로 승격 불가 |
| `Direct` 신규 사용자 | 333 | 333 / 520 = 64.0% | Direct 과다 상태가 유지되어 Play Console/external/campaign 확인이 선행 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 18명·세션 180회는 신규 획득 성과로 계산하지 않음 |

#### 2026-06-08 live readback

명령: `python3 /Users/uiel/.hermes/scripts/stopit_metrics_snapshot.py`
확인 시각: `2026-06-08T23:09:54Z`

| 항목 | 2026-06-08 GA4 30일 창 | 직전 30일 대비/비중 | 현재 해석 |
| --- | ---: | ---: | --- |
| 전체 `newUsers` | 571 | 직전 346 대비 +65.0% | 신규 유저 반등은 유지됐지만 channel mix 불안정은 계속됨 |
| 전체 `activeUsers` | 806 | 직전 564 대비 +42.9% | 활성 사용자도 반등했지만 신규 반등 원천은 아직 attribution 확인 필요 |
| 전체 `sessions` | 5,244 | 직전 6,149 대비 -14.7% | 세션은 여전히 직전 30일보다 낮아 참여 회복으로 단정 불가 |
| `Organic Search` 신규 사용자 | 236 | 236 / 571 = 41.3% | #65 기준선 178을 넘었지만 Direct 과다/Play Console 미확인 전까지 ASO 회복으로 승격 불가 |
| `Direct` 신규 사용자 | 336 | 336 / 571 = 58.8% | Direct 과다 상태가 유지되어 Play Console/external/campaign 확인이 선행 |
| `Paid Search` 신규 사용자 | 0 | 신규 비중 0% | 활성 18명·세션 156회는 신규 획득 성과로 계산하지 않음 |

#### ASO 성과 판정 전 attribution 확인 순서

1. Play Console `Store performance` / acquisition report에서 같은 최근 30일 창의 검색·탐색·외부/캠페인 유입을 확인한다.
2. GA4 `firstUserDefaultChannelGroup`의 `Organic Search`, `Direct`, `Paid Search`와 Play Console acquisition source가 같은 방향인지 표로 비교한다.
3. 실제 Paid Search 캠페인이 집행 중인지 확인한다. 집행 중이 아니라면 최신 스냅샷의 `Paid Search` 활성 18명·세션 156회는 신규 유입 성과가 아니라 과거 사용자/재방문/분류 잔상으로 분리한다.
4. Discord, 웹, 문서, QR, 캠페인 링크가 Play Store로 유입을 만들고 있다면 UTM 또는 Play Install Referrer 적용 여부를 점검한다.
5. #65의 14일/30일 판정은 아래 `획득 채널 판정 표`가 채워진 뒤에만 “ASO 효과”로 표현한다. 표가 비어 있으면 `newUsers`/`Organic Search` 변화는 중간 신호로만 둔다.

#### 획득 채널 판정 표

| 시점 | GA4 `newUsers` | GA4 `Organic Search` 신규 | GA4 `Direct` 신규 | GA4 `Paid Search` 신규 | Play Console Search/Explore | Play Console external/campaign | Paid campaign 집행 여부 | 판단 |
| --- | ---: | ---: | ---: | ---: | --- | --- | --- | --- |
| 2026-06-01 중간 스냅샷 | 274 | 167 | 107 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 39.1%와 Paid Search 신규 0명 때문에 ASO 회복 판정 보류 |
| 2026-06-02 중간 스냅샷 | 432 | 169 | 263 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 60.9%까지 상승. 신규 유입 반등을 ASO 효과로 표현 금지 |
| 2026-06-03 live readback | 432 | 169 | 263 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 60.9% 유지. Play Console/external 확인 전까지 ASO 회복 판정 보류 |
| 2026-06-03 second live readback | 457 | 176 | 281 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 61.5%까지 상승. 신규 유입 반등을 ASO 효과로 표현 금지 |
| 2026-06-04 late live readback | 506 | 179 | 327 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 64.6%까지 상승. Organic Search는 기준선을 간신히 넘었지만 Play Console 확인 전까지 신규 유입 반등을 ASO 효과로 표현 금지 |
| 2026-06-05 live readback | 520 | 187 | 333 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 64.0% 과다 상태 유지. Organic Search는 기준선을 넘었지만 Play Console 확인 전까지 신규 유입 반등을 ASO 효과로 표현 금지 |
| 2026-06-08 live readback | 571 | 236 | 336 | 0 | `TODO: Play Console 수동 확인` | `TODO: Play Console 수동 확인` | `TODO: 캠페인 운영 확인` | Direct 58.8% 과다 상태 유지. Organic Search는 기준선을 넘었지만 Play Console 확인 전까지 신규 유입 반등을 ASO 효과로 표현 금지 |
| +14일 (`2026-06-10 KST 이후`) | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` |
| +30일 (`2026-06-26 KST 이후`) | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` |

#### 판정 규칙

- `Organic Search` 신규 사용자와 Play Console Search/Explore가 함께 개선되고, `Direct` 비중이 과도하게 늘지 않으면 ASO copy/screenshot 반영 효과 후보로 본다.
- `newUsers`가 늘었지만 `Direct` 또는 external/campaign만 늘면 ASO 효과가 아니라 링크/캠페인/어트리뷰션 변화로 분리한다.
- `Paid Search` 신규 사용자가 0인데 활성/세션만 남으면 신규 획득 성과로 계산하지 않는다. 실제 캠페인 집행이 확인될 때만 유료 획득 실험으로 해석한다.
- Play Console source와 GA4 channel group이 크게 어긋나면 #65 성과 판정 전에 UTM/Install Referrer 운영 규칙을 먼저 보강한다.

#### Link / campaign attribution 운영 규칙

#242가 닫히기 전까지 ASO/캠페인 효과를 판단할 때는 아래 규칙을 함께 적용한다. 이 섹션의 목적은 `Direct` 급증을 무조건 제품 성장으로 읽거나, `Organic Search` 변화만으로 #65 ASO 효과를 단정하는 일을 막는 것이다.

| 유입 표면 | 기본 분류 | 필요한 태깅/기록 | ASO 판정 시 처리 |
| --- | --- | --- | --- |
| Play Store 검색/탐색 | Play Console Search/Explore | Play Console source, listing visitors, conversion rate | `Organic Search`와 같은 방향이면 ASO 후보 |
| Discord/커뮤니티/문서 링크 | external/campaign 후보 | 링크 URL, 게시 시각, `utm_source`, `utm_medium`, `utm_campaign` | GA4 `Direct` 증가와 겹치면 ASO 효과에서 분리 |
| 유료 검색/광고 | Paid campaign | 집행 여부, 기간, 예산, campaign name | 신규 `Paid Search`가 0이면 신규 획득 성과로 계산하지 않음 |
| QR/오프라인 공유 | external/unknown | 배포 위치, 날짜, 가능하면 전용 링크 또는 UTM | `Direct` 급증 원인 후보로 별도 메모 |
| 앱 내/웹 redirect 또는 짧은 링크 | attribution risk | redirect 보존 여부, final Play URL, referrer 보존 여부 | Play Console/GA4 불일치 원인으로 먼저 점검 |

운영 계약:

1. 대표님이나 자동화가 Play Store 링크를 새로 배포할 때는 가능한 한 `utm_source`, `utm_medium`, `utm_campaign`을 붙인 URL을 기록한다.
2. Play Store로 redirect되는 짧은 링크/문서 링크를 쓰면 final URL에서 UTM이 보존되는지 확인한다.
3. PR #586으로 parser/helper/analytics foothold가, 이번 QA package로 Play Install Referrer SDK provider와 첫 실행 one-shot lookup path가 연결됐다. 단, 이 코드가 포함된 release/tag/Play deploy와 GA4 Admin metadata 확인 전까지 GA4 `Direct`를 “출처 없음/어트리뷰션 누락 가능성”으로 보수적으로 해석한다.
4. #65의 +14일/+30일 ASO 판정 표에는 GA4 채널뿐 아니라 Play Console Search/Explore와 external/campaign source를 같이 적는다.
5. campaign 집행이 없었다는 운영 확인이 있으면 `Paid Search` 활성/세션 잔상은 신규 획득 성과에서 제외한다.
6. 외부 링크/캠페인 운영 규칙이 실제로 필요해진 뒤에는 #581 `docs/INSTALL_REFERRER_ATTRIBUTION_CONTRACT.md`를 source of truth로 본다. 현재 상태는 parser/helper/analytics foothold와 SDK provider/첫 실행 one-shot lookup wiring 완료, GA4 Admin 등록, release/tag/Play deploy, 14일/30일 readback 대기이므로 Direct 감소나 ASO 회복을 주장하지 않는다.
7. #242/#65 acquisition snapshot 또는 #581 Install Referrer/UTM 계약을 고칠 때는 downstream 문서 drift를 막기 위해 `python3 -m unittest scripts.tests.test_acquisition_attribution_docs_contract -v`와 `python3 -m unittest scripts.tests.test_install_referrer_attribution_contract -v`를 함께 실행한다.

#### Play Console 수동 확인 템플릿

Play Console 접근은 저장소에서 자동 확정할 수 없는 외부 경계다. 확인자가 아래 값을 채운 뒤 #242 또는 #65에 코멘트로 남긴다.

```md
## Play Console acquisition 확인

- 확인 시각(KST):
- 확인 기간: 최근 30일 / +14일 / +30일 중 선택
- Play Console source:
  - Search:
  - Explore:
  - External/campaign:
  - Other/unknown:
- Store listing:
  - visitors:
  - acquisitions/conversions:
  - conversion rate:
- 캠페인 집행 여부:
  - Paid Search 집행: yes/no/unknown
  - 링크/Discord/웹/QR 배포: yes/no/unknown
  - 사용한 UTM/campaign name:
- GA4와 비교:
  - GA4 newUsers:
  - GA4 Organic Search 신규:
  - GA4 Direct 신규:
  - GA4 Paid Search 신규:
- 판정:
  - ASO 효과 후보 / attribution 불명 / campaign 효과 / 판정 보류 중 선택
- 후속 작업:
```

## 지표/근거

### 실행 트리거

- 분석 기간: 최근 30일 (`30daysAgo..yesterday`)
- 비교 기간: 직전 30일 (`60daysAgo..31daysAgo`)
- 핵심 수치:
  - `newUsers`: 203 / 이전 377 = `-46.2%`
  - `activeUsers`: 457 / 이전 612 = `-25.3%`
  - `sessions`: 4,636 / 이전 6,214 = `-25.4%`
  - `Organic Search` 신규 사용자: 178 / 전체 신규 사용자 203 = `87.7%`
  - `Direct` 신규 사용자: 25 / 전체 신규 사용자 203 = `12.3%`
- 확인 소스:
  - 2026-05-24 GA4 스냅샷
  - GitHub issue #65
  - 과거 docs 준비 이슈 #15 / PR #22

### 해석

- 사용성/참여율 급락보다 **신규 유입 병목**이 더 크다.
- 신규 유입의 대부분이 `Organic Search`에 쏠려 있어, 회복을 위해서는 앱 내부 퍼널만이 아니라 **Play Store listing copy / screenshot / social proof**를 같이 손봐야 한다.
- 따라서 이 이슈의 저장소 산출물은 “문구 초안”에 머무르면 안 되고, **실제 반영 체크리스트 + baseline + 검증 로그**까지 포함해야 한다.

## 메시지 원칙

1. 사용자 노출 앱명은 `StopIt / 스탑잇` 기준으로 통일한다.
2. 핵심 가치는 `유혹 앱 차단`, `타이머 잠금`, `루틴 잠금`, `긴급 해제`, `잠금 기록` 순으로 보여준다.
3. Android 앱의 실제 강점 기준으로 쓴다. iOS/막연한 자기계발 문구는 줄인다.
4. 과장보다 신뢰를 택한다. 접근성 권한, 루틴 보호, 긴급 해제의 제한성을 명확히 쓴다.
5. KR listing을 기준 문서로 두고, EN은 의미를 유지하는 범위에서 간결하게 번역한다.

## 타겟 사용 상황

### 핵심 대상
- 공부/업무 중 자꾸 특정 앱을 열어 집중이 깨지는 사용자
- 특정 시간대에 앱 차단 루틴이 필요한 사용자
- 강한 차단은 원하지만 완전 비가역 잠금은 부담스러운 사용자

### JTBD
- "공부하거나 일할 때 유혹 앱을 바로 막고 싶다."
- "원하는 시간 동안만 확실하게 잠그고 싶다."
- "정말 필요할 때만 제한적으로 풀 수 있어야 안심된다."

## 최종 반영 권장안

### 앱 제목

- KR: **스탑잇 - 집중 앱 차단, 루틴 잠금**
- EN: **StopIt - App Blocker for Focus**

대체 후보:
1. 스탑잇 - 앱 차단 타이머 & 집중 루틴
2. 스탑잇 - 유혹 앱 잠금, 공부 집중 도우미

### 짧은 설명

- KR: **유혹 앱을 선택하고, 타이머·루틴으로 잠그고, 필요할 때만 긴급 해제하세요.**
- EN: **Block distracting apps with timers, routines, and limited emergency unlock.**

### 긴 설명

#### KR long description

스탑잇은 공부, 업무, 수면 전처럼 집중이 필요한 시간에 유혹 앱 사용을 바로 막아주는 Android 앱입니다.

스탑잇은 사용자가 직접 선택한 앱이 실행될 때 이를 감지하고, 수동 잠금·타이머 잠금·루틴 잠금이 활성화된 동안 즉시 차단 화면을 표시하기 위해 접근성 권한을 사용합니다. 또한 잠금 상태 우회를 줄이기 위해 잠금 중 앱 삭제 시도를 감지할 수 있습니다. 이 권한은 앱 차단 및 잠금 유지라는 핵심 기능에만 사용되며, 광고, 프로파일링, 판매 또는 제3자 공유 목적으로 사용되지 않습니다.

원하는 앱을 선택한 뒤 직접 켜서 잠글 수도 있고, 타이머나 반복 루틴으로 자동 잠금을 설정할 수도 있습니다. 실제로 앱을 열었을 때 차단 화면이 동작해 "설정만 해두고 안 막히는" 앱이 아니라, 집중이 깨지는 순간을 바로 끊어주는 데 초점을 맞췄습니다.

### 이런 분에게 맞아요
- 공부를 시작하면 SNS·영상 앱부터 열게 되는 분
- 업무 시간에 메신저·커뮤니티·쇼핑 앱 사용을 줄이고 싶은 분
- 수면 전 특정 앱 사용을 제한하고 싶은 분
- 완전 삭제 대신, 필요한 시간에만 확실하게 차단하고 싶은 분

### 핵심 기능
- **유혹 앱 선택 차단**: 방해되는 앱만 골라서 잠글 수 있어요.
- **타이머 잠금**: 지금부터 몇 분/몇 시간 동안 앱 사용을 막을 수 있어요.
- **루틴 잠금**: 요일과 시간대를 정해 자동으로 차단할 수 있어요.
- **긴급 해제**: 정말 필요할 때만 제한된 횟수와 시간으로 임시 해제가 가능해요.
- **잠금 기록 확인**: 얼마나 자주, 얼마나 오래 버텼는지 기록으로 볼 수 있어요.
- **루틴 보호/삭제 방지**: 잠금 습관이 쉽게 무너지지 않도록 보호 장치를 둘 수 있어요.

### 스탑잇이 다른 점
- 단순 알림 앱이 아니라 실제 차단 동작에 집중합니다.
- "무조건 막기"보다 긴급 해제 같은 안전장치를 함께 제공합니다.
- 루틴, 기록, 차단 성공 경험을 통해 집중 습관을 쌓도록 설계했습니다.

### 이런 흐름으로 시작하세요
1. 자주 열어버리는 앱을 선택합니다.
2. 타이머 잠금 또는 루틴 잠금을 설정합니다.
3. 차단이 실제로 동작하는지 확인합니다.
4. 잠금 기록을 보며 집중 시간을 늘립니다.

스탑잇은 의지력에만 기대지 않고, 유혹이 생기는 순간 앱 사용을 끊을 수 있도록 돕습니다.

#### EN long description

StopIt helps you block distracting apps when you need to focus.

StopIt uses the Accessibility API to detect when user-selected distracting apps are opened and to immediately show a blocking screen while a manual lock, timer lock, or routine lock is active. It may also detect uninstall attempts during an active lock to reduce lock bypass. This access is used only for core app-blocking and lock-maintenance features, and is not used for advertising, profiling, selling data, or sharing data with third parties.

Select the apps that break your concentration, then lock them instantly, by timer, or on recurring routines. Instead of only reminding you, StopIt is built to block access when you actually open the app.

### Best for
- students who open social apps while studying
- workers who want fewer distractions during deep work
- people who want a routine-based app blocker at night or during work hours

### Key features
- block selected distracting apps
- start a focus timer lock instantly
- automate blocking with recurring routines
- allow emergency unlock only when truly needed
- review lock history and focus progress
- protect routines and prevent easy backtracking

StopIt is designed for practical focus: real blocking, routine support, and a safer fallback than all-or-nothing locking.

in-app Accessibility permission copy는 `docs/ACCESSIBILITY_PERMISSION_COPY_CONTRACT.md`를 source of truth로 본다. Store listing / Accessibility declaration은 `Accessibility API`를, 온보딩 권한 화면은 `Accessibility permission` 또는 locale별 Android 접근성 권한 표현을 사용해야 하며, `Screen Time permission`을 Android 권한명처럼 쓰지 않는다.

## Accessibility 정책 대응 제출 문안

### Play Console Accessibility declaration

#### Why does your app need Accessibility API?

StopIt is a self-control and app-blocking app. Users explicitly choose distracting apps they want to restrict. The app uses the Accessibility API to detect when one of those selected apps is opened and to immediately show a blocking screen while a manual lock, timer lock, or routine lock is active. It may also detect uninstall attempts during an active lock to reduce lock bypass. This is a core feature described in the store listing.

#### What data is accessed?

Foreground app/package context and active window content only as needed to determine whether a selected app or uninstall flow is currently open.

#### How is the data used?

The data is used only to trigger the blocking flow, enforce active locks, and reduce bypass during uninstall attempts. It is not used for advertising, profiling, sale of data, or sharing data with third parties.

### 심사 메모 권장 문안

This update does not introduce a new Accessibility permission scope. StopIt continues to use Accessibility only for its core app-blocking feature described in the store listing: detecting user-selected distracting apps, showing a blocking screen during active lock sessions, and reducing bypass through uninstall attempts during an active lock.

## 스크린샷 구성안

스크린샷은 `설정 → 즉시 잠금 → 루틴 → 실제 차단 → 긴급 해제 → 기록` 흐름으로 보여준다.

| 순서 | 화면 | 핵심 메시지 | 캡션 초안 |
| --- | --- | --- | --- |
| 1 | 앱 선택 | 유혹 앱만 골라서 차단 | 자꾸 열게 되는 앱만 골라서 차단하세요 |
| 2 | 홈 타이머 잠금 | 바로 시작 가능한 집중 잠금 | 지금 바로 타이머로 집중 시간을 시작하세요 |
| 3 | 루틴 화면 | 반복되는 집중 시간을 자동화 | 요일과 시간대로 앱 차단 루틴을 만드세요 |
| 4 | 차단 화면 | 실제 사용 순간에 차단 동작 | 앱을 열면 바로 차단되어 흐름이 끊기지 않아요 |
| 5 | 긴급 해제 | 완전 강제 대신 안전한 예외 처리 | 정말 필요할 때만 제한적으로 긴급 해제 |
| 6 | 잠금 기록 | 집중 성과를 확인 | 잠금 기록으로 내가 버틴 시간을 확인하세요 |

### 스크린샷 제작 메모
- 1~2장은 "왜 필요한지"보다 "무엇을 바로 할 수 있는지"를 보여준다.
- 긴 텍스트보다 1문장 가치 제안을 사용한다.
- 긴급 해제는 소개하되, 차단 강도를 약하게 보이게 만들지 않는다.
- 기록 화면은 단순 통계보다 "집중을 이어온 증거"로 표현한다.

### 스크린샷 생성기 / CI 검증

스크린샷 편집 source는 `tools/aso-screenshots`의 Next/Bun 생성기다. 생성기 코드, 템플릿, 캡처 asset, `package.json`, `bun.lock`을 바꾼 PR은 Play Console 반영과 별개로 로컬/CI build 증적을 남긴다.

```bash
cd tools/aso-screenshots
bun install --frozen-lockfile
bun run build
```

Ops CI의 `ASO screenshots build` job이 같은 명령을 실행한다. 이 gate는 Android 앱 빌드와 분리된 스크린샷 생성기 검증이며, Gradle/Firebase signing/Play deploy secret을 요구하지 않는다.

### 스크린샷 자산 체크리스트

| 슬롯 | 필요 화면 | 캡션 확정 | 실제 캡처 | 최종 편집본 | Play Console 반영 |
| --- | --- | --- | --- | --- | --- |
| 1 | 앱 선택 | [ ] | [ ] | [ ] | [ ] |
| 2 | 홈 타이머 잠금 | [ ] | [ ] | [ ] | [ ] |
| 3 | 루틴 화면 | [ ] | [ ] | [ ] | [ ] |
| 4 | 차단 화면 | [ ] | [ ] | [ ] | [ ] |
| 5 | 긴급 해제 | [ ] | [ ] | [ ] | [ ] |
| 6 | 잠금 기록 | [ ] | [ ] | [ ] | [ ] |

## baseline / 사후 복원 기록

원래는 실제 Play Console 반영 직전에 아래 표를 채우는 절차였다. 현재는 대표님 수동 배포가 먼저 완료된 상태이므로, 확인 가능한 항목은 사후라도 최대한 복원해 기록한다. `listing 전환율`, `평점`, `리뷰 수`, `현재 listing copy`는 저장소에서 자동 조회할 수 없으므로 수동 기록이 필요하다.

### 수동 복원 우선순위

1. **반영 사실 고정**: KR/EN copy, 스크린샷 반영 여부, 반영자, 대략적인 반영 시점
2. **현재 노출값 복원**: 현재 Play Console에 보이는 KR/EN 제목·짧은 설명·스크린샷 버전 메모
3. **비교 baseline 복원**: listing 전환율, 평점, 리뷰 수, 최근 리뷰 톤
4. **사후 측정 루프**: +14일 / +30일 같은 분자·분모 기준 비교

### 수동 복원 입력 체크리스트

- [ ] Play Console `Main store listing` 현재 KR 제목/짧은 설명/긴 설명을 확인해 아래 표에 채움
- [ ] Play Console `Main store listing` 현재 EN 제목/짧은 설명/긴 설명을 확인해 아래 표에 채움
- [ ] 스크린샷 6장 순서와 실제 노출 자산이 이 문서 구성안과 일치하는지 확인
- [ ] `Store listing performance`에서 listing 전환율을 기록
- [ ] 현재 평점/리뷰 수와 최근 리뷰 5~10개 톤 요약을 기록
- [ ] 가능하면 대표님 메모/히스토리 기준으로 정확한 반영 날짜·시각을 보강

| 항목 | 값 | 기록 방법 |
| --- | --- | --- |
| baseline 기록일 | `TODO` | 수동 입력 |
| 현재 KR 제목 | `TODO` | Play Console 수동 확인 |
| 현재 KR 짧은 설명 | `TODO` | Play Console 수동 확인 |
| 현재 KR 긴 설명 버전명 | `TODO` | Play Console 수동 확인 |
| 현재 EN 제목 | `TODO` | Play Console 수동 확인 |
| 현재 EN 짧은 설명 | `TODO` | Play Console 수동 확인 |
| 현재 EN 긴 설명 버전명 | `TODO` | Play Console 수동 확인 |
| 현재 스크린샷 버전/메모 | `TODO` | Play Console 수동 확인 |
| 최근 30일 `newUsers` | `203` | issue #65 기준 |
| 최근 30일 `Organic Search` 신규 사용자 | `178` | issue #65 기준 |
| 최근 30일 `activeUsers` | `457` | issue #65 기준 |
| 최근 30일 `sessions` | `4,636` | issue #65 기준 |
| listing 전환율 | `TODO` | Play Console 수동 확인 |
| rating count | `TODO` | Play Console 수동 확인 |
| 평균 평점 | `TODO` | Play Console 수동 확인 |
| 최근 리뷰 톤 메모 | `TODO` | 최근 리뷰 5~10개 수동 요약 |

## Play Console 반영 절차

### 1. 반영 직전 준비

- [ ] baseline 표를 사후 복원 포함 기준으로 최대한 채운다.
- [x] KR/EN 제목·짧은 설명·긴 설명 최종안을 이 문서 기준으로 정리했다.
- [ ] 스크린샷 6장 최종본 파일명/원본 경로를 정리한다.
- [ ] 반영 담당자와 정확한 반영 시간을 보강한다.

### 2. 실제 반영

- [x] KR listing copy 반영
- [x] EN listing copy 반영
- [x] 스크린샷 6장 업로드/정렬 반영
- [ ] 저장 후 실제 노출값 재확인

### 3. 반영 직후 기록

- [x] 반영 사실 기록
- [x] 반영자 기록
- [x] 적용 범위 기록 (copy only / screenshots only / both)
- [ ] 정확한 반영 시각 / 당시 Play Console 노출값 보강
- [ ] 반영 후 저장소 문서와 Play Console 값이 일치하는지 재확인

## 실행 로그

| 단계 | 상태 | 일시 | 담당 | 메모 |
| --- | --- | --- | --- | --- |
| baseline 기록 | 보강 필요 | `TODO` | `TODO` | 반영 전 수치/노출값을 사후 복원해야 함 |
| KR listing 반영 | 완료 | `2026-05-27 01:18 KST 이전` | 대표님 | issue #65 코멘트 기준 이미 수동 반영 완료 상태 확인 |
| EN listing 반영 | 완료 | `2026-05-27 01:18 KST 이전` | 대표님 | issue #65 코멘트 기준 이미 수동 반영 완료 상태 확인 |
| 스크린샷 반영 | 완료 | `2026-05-27 01:18 KST 이전` | 대표님 | issue #65 코멘트 기준 Play Console 실제 반영 완료 확인 |
| 14일 점검 | 예정 | `2026-06-10 KST 이후` | `TODO` | Play Console 반영이 `2026-05-27 01:18 KST 이전`인 점을 기준으로 한 최소 14일 후 체크 |
| 30일 점검 | 예정 | `2026-06-26 KST 이후` | `TODO` | 같은 분자/분모로 `Organic Search`, 전체 `newUsers`, listing conversion, 평점/리뷰 수 비교 |

## 14일 / 30일 검증 포맷

### 14일 후 확인
- `Organic Search` 신규 사용자 변화
- 전체 `newUsers` 변화
- GA4 `Direct` 신규 사용자 비중과 Play Console external/campaign 유입 변화
- GA4 `Paid Search` 신규/활성/세션과 실제 캠페인 집행 여부
- Play Console Search/Explore와 GA4 `Organic Search` 방향 일치 여부
- listing 전환율 변화
- 리뷰 수 증가 여부
- 평점 변화 여부
- `activeUsers` 동반 회복 조짐

### 30일 후 확인
- 신규 사용자 회복 여부
- `activeUsers` / `sessions` 동반 회복 여부
- 획득 채널 믹스가 ASO 효과로 해석 가능한지 여부
- ASO 카피 유지 vs 2차 수정 결정
- 스크린샷 교체 필요 여부

### 기록 표

| 시점 | newUsers | Organic Search 신규 사용자 | Direct 신규 사용자 | Paid Search 신규 사용자 | activeUsers | sessions | listing 전환율 | rating count | 평균 평점 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 203 | 178 | 25 | `TODO` | 457 | 4,636 | `TODO` | `TODO` | `TODO` | 반영 전 |
| 2026-06-01 중간 스냅샷 | 274 | 167 | 107 | 0 | 523 | 4,484 | `TODO` | `TODO` | `TODO` | 반영 전/후 혼합 30일 창. 전체 신규 유저는 반등했지만 Organic Search는 아직 기준선보다 낮고 Direct가 39.1%까지 커져 성과 판정 보류 |
| 2026-06-02 중간 스냅샷 | 432 | 169 | 263 | 0 | 688 | 4,721 | `TODO` | `TODO` | `TODO` | 전체 신규/활성은 반등했지만 Organic Search는 기준선 178보다 낮고 Direct가 60.9%까지 커져 ASO 효과 판정 보류 |
| 2026-06-03 live readback | 432 | 169 | 263 | 0 | 688 | 4,733 | `TODO` | `TODO` | `TODO` | Direct 60.9% 과다 상태가 유지되고 세션은 직전 30일 대비 -26.1%라 Play Console/external/campaign 확인 전까지 ASO 효과 판정 보류 |
| 2026-06-03 second live readback | 457 | 176 | 281 | 0 | 707 | 4,744 | `TODO` | `TODO` | `TODO` | 신규/활성 반등은 더 커졌지만 Direct 61.5% 과다와 Organic Search 기준선 미회복 때문에 ASO 효과 판정 보류 |
| 2026-06-04 late live readback | 506 | 179 | 327 | 0 | 757 | 4,897 | `TODO` | `TODO` | `TODO` | 신규/활성 반등은 더 커졌고 Organic Search는 기준선 178을 넘었지만 Direct 64.6% 과다 때문에 Play Console/external/campaign 확인 전까지 ASO 효과 판정 보류 |
| 2026-06-05 live readback | 520 | 187 | 333 | 0 | 766 | 4,924 | `TODO` | `TODO` | `TODO` | 신규/활성 반등은 더 커졌고 Organic Search는 기준선 178을 넘었지만 Direct 64.0% 과다 때문에 Play Console/external/campaign 확인 전까지 ASO 효과 판정 보류 |
| 2026-06-08 live readback | 571 | 236 | 336 | 0 | 806 | 5,244 | `TODO` | `TODO` | `TODO` | 신규/활성 반등은 유지됐고 Organic Search는 기준선 178을 넘었지만 Direct 58.8% 과다 때문에 Play Console/external/campaign 확인 전까지 ASO 효과 판정 보류 |
| +14일 (`2026-06-10 KST 이후`) | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` |
| +30일 (`2026-06-26 KST 이후`) | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` |

## 브랜딩 점검 메모

현재 repo에는 내부 코드명/리소스 key의 `Keep` 흔적과 사용자 노출 브랜드명이 함께 남아 있다. #510 기준으로 사용자에게 보이는 표기는 아래처럼 분리한다.

- Play listing copy와 영문/다국어 locale은 `StopIt` 기준을 유지한다.
- 인앱 한국어 사용자 노출 문자열은 `values-ko/strings.xml`에서 `스탑잇` 기준으로 통일한다.
- 내부 코드명, resource key, theme/class/package name의 `Keep`/`StopIt`은 사용자 노출 copy가 아니므로 #510 범위에서 제외한다.
- 회귀 방지: `python3 -m unittest scripts.tests.test_korean_brand_copy_contract -v`로 `values-ko/strings.xml`의 사용자 노출 문자열에 `StopIt`/`Keep`이 재유입되지 않는지 확인한다.

ASO 반영 후 후속 체크:

- Play listing copy는 전부 `StopIt` 기준으로 통일
- 인앱 한국어 사용자 노출 문자열은 `스탑잇` 기준으로 유지

## 키워드/카피 가드

### 유지할 표현
- 앱 차단
- 집중
- 타이머 잠금
- 루틴 잠금
- 긴급 해제
- 잠금 기록

### 피할 표현
- 막연한 동기부여 문구만 강조하는 표현
- iPhone/iOS 중심 문구
- 실제 기능보다 과장된 금욕/중독 치료 표현
- 광고성 과장 문구 (`최고`, `완벽`, `100%`) 남발

## 저장소 기준 완료 범위

이 문서와 연결된 저장소 작업이 완료되었다고 볼 수 있는 조건:

- [x] KR/EN listing copy 권장안이 문서화되어 있다.
- [x] 스크린샷 6장 구성과 캡션 방향이 문서화되어 있다.
- [x] baseline / 반영 / 14일 / 30일 기록 표가 준비되어 있다.
- [x] #15 준비 단계와 #65 실행 단계의 차이가 문서에 명시되어 있다.

이 이슈 자체(#65)가 닫히려면 추가로 필요한 조건:

- [x] Play Console에 실제 copy/스크린샷이 반영되었다.
- [ ] 반영 일시와 범위가 실행 로그에 기록된다.
- [ ] 14일 또는 30일 후 전후 비교 결과가 남는다.

## 운영 메모

- 실제 Play Console 반영은 수동 작업이지만, 저장소 기준 source of truth는 이 문서로 유지한다.
- 스크린샷 최종본이 정리되면 파일 저장 위치나 디자인 원본 경로를 이 문서에 추가한다.
- ASO 실험 2차 수정이 생기면 baseline과 변경 이력을 같은 문서에 누적한다.
- 저장소 자동화는 Play Console 값을 직접 읽지 못하므로, 외부 반영/측정 값은 수동 기록을 전제로 한다.
