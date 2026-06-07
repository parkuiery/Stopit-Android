# Install Referrer / UTM Attribution Contract

Issue: #581

## 목적

최근 #65/#242 acquisition readback에서는 `newUsers`가 반등했지만 `Direct` 신규 사용자 비중이 계속 과다해 ASO 효과, 외부 링크, 캠페인, attribution 누락을 분리하기 어렵다. 이 문서는 Play Install Referrer와 UTM link helper를 도입할 때의 **제품/analytics/ops 계약**을 먼저 고정한다.

이 PR은 docs-lane 계약 산출물이다. Android 코드, SDK 의존성, GA4 Admin 등록, Play Console 확인, release/tag/Play deploy, 14일/30일 readback이 끝나기 전까지 #581 follow-up은 `Refs #581`을 사용한다.

## 현재 문제

- 2026-06-06T22:17:11Z live readback 기준 `newUsers = 553`이지만 `Direct = 332 / 553 = 60.0%`로 과다하다.
- `Organic Search = 221 / 553 = 40.0%`로 #65 기준선 178명을 넘었지만, Play Console Search/Explore와 external/campaign source가 확인되지 않아 ASO 회복으로 표현하지 않는다.
- `Paid Search`는 신규 0명인데 활성 18명·세션 170회가 남아 있어 신규 획득 성과가 아니라 과거 사용자/재방문/분류 잔상 가능성이 크다.
- 앱 코드는 아직 Play Install Referrer SDK를 사용하지 않으므로 GA4 `Direct`에는 진짜 direct와 referrer/UTM 손실이 섞일 수 있다.

## 범위

### 포함

- Play Install Referrer 조회 결과를 privacy-safe bucket으로 정규화하는 Android code-lane handoff.
- UTM/referrer parser 계약: raw URL이나 검색어를 보내지 않고 enum/bucket만 남긴다.
- Play Store 링크 생성 helper 또는 운영 명령 계약.
- GA4 이벤트/파라미터 계약과 Admin 등록/readback 절차.
- #65/#242 ASO 판정표에서 repo-internal 구현 경계와 Play Console/캠페인 수동 경계를 분리한다.

### 제외

- 이 docs-lane PR에서 Android SDK를 추가하거나 첫 실행 runtime path를 변경하지 않는다.
- attribution provider, MMP, server-side campaign warehouse 도입은 별도 결정 전까지 제외한다.
- `Direct`를 0에 가깝게 만드는 것을 목표로 삼지 않는다. 목표는 **Search/Explore, external/campaign, unknown/direct를 더 신뢰 가능하게 분리**하는 것이다.

## Product / PM decision contract

| 질문 | 기본 판단 |
| --- | --- |
| 성공 지표 | external/campaign tagged coverage 증가, unknown/direct 해석 confidence 개선, #65 ASO 판정의 Play Console/GA4 불일치 감소 |
| North Star 연결 | 획득 채널을 정확히 나눠야 첫 차단 활성화와 반복 사용 개선 실험의 실제 유입원을 알 수 있다 |
| 위험 | raw referrer URL/검색어/개인 식별 가능 문자열 저장, 앱 첫 실행 지연/크래시, ASO 성과 과대해석 |
| 기본 가드레일 | privacy-safe enum/bucket only, non-blocking lookup, 실패 시 앱 시작/차단 흐름 영향 없음 |

## Android code-lane handoff

권장 패키지 이름은 code-lane에서 조정할 수 있지만, 계약상 최소 seam은 아래를 만족해야 한다.

1. `InstallReferrerAttributionProvider`
   - Play Install Referrer Client 호출을 캡슐화한다.
   - timeout/failure/service-unavailable을 성공 경로와 분리한다.
   - 앱 시작, onboarding, lock/routine path를 block하지 않는다.
2. `AcquisitionAttributionParser`
   - referrer string / UTM query를 파싱하되 raw string을 analytics로 넘기지 않는다.
   - 필수 UTM 필드: `utm_source`, `utm_medium`, `utm_campaign`.
   - unknown/malformed/missing을 명시 enum으로 반환한다.
3. `CampaignLinkBuilder` 또는 운영 script
   - Play Store URL에 UTM 필수 필드를 일관되게 붙인다.
   - link surface와 campaign name을 slug/bucket으로 제한한다.
   - redirect/short-link를 쓰면 final URL에서 UTM 보존 여부를 확인하는 체크리스트를 제공한다.
4. 테스트 seam
   - Play service unavailable / developer error / timeout / success / missing UTM / malformed UTM / full UTM 케이스를 단위 테스트로 고정한다.
   - raw referrer URL, search term, arbitrary query 값이 analytics payload에 들어가지 않는 회귀 테스트를 둔다.

## Analytics event contract

후보 이벤트는 code-lane에서 실제 구현 시 event dictionary와 GA4 runbook에 동기화한다.

| 이벤트명 | 발생 시점 | 목적 |
| --- | --- | --- |
| `install_referrer_attribution_checked` | 첫 실행/설치 attribution lookup이 terminal status에 도달했을 때 1회 | referrer 조회 성공/실패/미지원/UTM coverage를 privacy-safe하게 확인 |
| `campaign_link_opened` (선택) | 앱 deep link/web redirect가 생긴 뒤에만 검토 | Play Store 밖 링크 표면 성과를 분리. 현재 MVP에서는 필수 아님 |

### `install_referrer_attribution_checked` parameters

| 파라미터 | 값 계약 | Required? | 금지 |
| --- | --- | --- | --- |
| `referrer_status` | `success`, `missing`, `unavailable`, `timeout`, `error`, `malformed` | Required | raw exception message |
| `utm_source_type` | `play_store`, `discord`, `web`, `qr`, `paid_search`, `community`, `unknown`, `none` | Required | raw source string |
| `utm_medium_type` | `organic`, `social`, `referral`, `paid`, `qr`, `owned`, `unknown`, `none` | Required | raw medium string |
| `campaign_bucket` | `aso_baseline`, `launch`, `review_push`, `routine_share`, `manual_test`, `other`, `none` | Required | raw campaign name if it can identify a person/small group |
| `link_surface` | `play_store_listing`, `discord`, `website`, `docs`, `qr`, `ad`, `unknown`, `none` | Recommended | raw URL/path |
| `lookup_latency_bucket` | `0_499ms`, `500_999ms`, `1000_1999ms`, `2000ms_plus`, `not_measured` | Recommended | raw latency if high-cardinality |

금지 payload/query 축:

- raw referrer URL
- 검색어/search term
- email, phone, account id, Discord user/channel name 같은 개인 식별 가능 문자열
- full campaign name이 개인/소규모 배포 대상을 드러내는 경우
- raw timestamp / raw URL path / arbitrary query key-value

## GA4 Admin registration boundary

이벤트를 코드에 추가하면 아래 custom dimensions를 `docs/GA4_CUSTOM_DIMENSION_REGISTRATION_RUNBOOK.md` ledger에 추가하고 metadata readback 전까지 live breakdown을 낮은 confidence로 둔다.

Required:

- `customEvent:referrer_status`
- `customEvent:utm_source_type`
- `customEvent:utm_medium_type`
- `customEvent:campaign_bucket`

Recommended:

- `customEvent:link_surface`
- `customEvent:lookup_latency_bucket`

해석 경계:

- code-lane 구현만으로 GA4 queryability가 완성되지 않는다.
- Admin 등록 후 metadata에서 `customEvent:*`가 보이고, 해당 코드가 포함된 release/tag/Play deploy 후 14일 이상 지난 뒤에야 coverage/Direct 변화 해석을 시작한다.
- `Direct`가 줄지 않아도 실패로 단정하지 않는다. tagged campaign coverage와 Play Console source 비교 confidence가 올라갔는지가 1차 목적이다.

## Campaign link operating contract

Play Store 링크 배포 시 아래 값을 운영 로그 또는 helper 출력으로 남긴다.

```md
## Campaign link record
- Link created at(KST):
- Surface: discord / website / docs / qr / ad / other
- Intended audience:
- Play Store URL:
- UTM:
  - utm_source:
  - utm_medium:
  - utm_campaign:
- Short link / redirect used: yes/no
- Final URL keeps UTM: pass/fail/not checked
- Expected interpretation window: 14 days / 30 days
- Related issue/PR:
```

UTM slug 원칙:

- lower kebab/snake case를 쓰고 공백/한글 원문/개인명은 피한다.
- `utm_source`는 표면 또는 채널 bucket, `utm_medium`은 traffic type, `utm_campaign`은 실험/운영명 bucket이다.
- 자동화가 Play Store 링크를 만들면 원본 URL과 final URL의 UTM 보존 여부를 함께 출력해야 한다.

## #65/#242 readback 연결

#581 구현 후에도 #65/#242 판정은 계속 아래 순서를 따른다.

1. Play Console Search/Explore와 external/campaign source를 수동 확인한다.
2. GA4 `firstUserDefaultChannelGroup`의 `Organic Search`, `Direct`, `Paid Search`를 같은 30일 창으로 비교한다.
3. `install_referrer_attribution_checked`가 배포/등록/14일 관측 경계를 넘었는지 확인한다.
4. tagged campaign coverage가 충분하면 external/campaign 증가분을 ASO 효과에서 분리한다.
5. Play Console Search/Explore와 GA4 Organic Search가 같은 방향일 때만 #65 ASO 회복 후보로 표현한다.

## Verification checklist for future PRs

Docs-lane/source-of-truth 검증:

```bash
python3 -m unittest scripts.tests.test_install_referrer_attribution_contract -v
python3 -m unittest scripts.tests.test_acquisition_attribution_docs_contract -v
git diff --check
```

Code-lane 추가 검증 후보:

```bash
./gradlew --console=plain :app:testDevDebugUnitTest --tests '*AcquisitionAttribution*' --tests '*InstallReferrer*'
./gradlew --console=plain :app:assembleProdDebug
```

릴리즈/측정 경계:

- implementation PR merge commit이 `origin/main` / SemVer tag / Play deploy에 포함됐는지 확인.
- GA4 Admin metadata에서 `customEvent:referrer_status`, `customEvent:utm_source_type`, `customEvent:utm_medium_type`, `customEvent:campaign_bucket` 확인.
- D+14/D+30 readback에서 Direct 신규 비중, tagged campaign coverage, Play Console Search/Explore vs external/campaign source를 같은 표에 기록.

## Completion boundary

#581은 아래가 모두 끝나야 closure를 검토한다.

- parser/provider/helper 코드와 테스트가 구현된다.
- raw referrer/URL/PII 금지 회귀 테스트가 있다.
- campaign link helper 또는 문서화된 운영 명령이 있다.
- event dictionary / GA4 runbook / ASO 런북 / metrics context가 동기화된다.
- release/tag/Play deploy 이후 14일/30일 readback 절차가 실행된다.
- #242/#65 외부 경계와 #581 repo-internal 구현 경계가 issue comment에 분리 기록된다.

이 docs-lane PR은 위 중 **계약/문서/정적 회귀 기준**만 완료한다. 남은 경계는 Android implementation, GA4 Admin, Play Console/campaign 수동 기록, release/readback이다.
