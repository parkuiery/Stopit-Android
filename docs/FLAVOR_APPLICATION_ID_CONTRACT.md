# Stopit Flavor Application ID Contract

Issue: [#314](https://github.com/parkuiery/Stopit-Android/issues/314)

이 문서는 `dev` / `prod` flavor의 앱 identity를 분리하거나, 분리하지 않는 결정을 유지할 때 반드시 같이 확인해야 하는 운영 계약이다. Stopit은 접근성 서비스, 알림 권한, exact alarm, 루틴 알람, Room/DataStore, backup/restore 정책을 모두 쓰기 때문에 `applicationId` 변경은 단순 Gradle suffix 변경이 아니라 QA/runtime/release identity 변경이다.

## 현재 상태

`app/build.gradle.kts` 기준 현재 계약:

- `namespace = "com.uiery.keep"`
- `defaultConfig.applicationId = "com.uiery.keep"`
- `flavorDimensions += "server"`
- `dev` flavor에는 `applicationIdSuffix = ".dev"`가 주석 처리되어 있다.
- `prod` flavor는 production package id `com.uiery.keep`을 사용한다.

따라서 현재 `devDebug`와 `prodDebug` / `prodRelease`는 같은 앱 package identity를 공유할 수 있다. 이 상태에서는 로컬 dev 설치, CI smoke, release QA, production 설치본이 같은 권한·데이터·알람·접근성 component identity를 공유하거나 덮어쓸 위험이 있다.

## 목표 계약

### Production identity

`prod` / release / Play deploy 경로는 계속 아래 identity만 사용한다.

```text
com.uiery.keep
```

이 값은 Play Console, 기존 사용자 설치본, 권한 상태, backup/restore identity, Firebase/Crashlytics/Analytics 운영 데이터와 연결되어 있으므로 release 경로에서 바꾸면 안 된다.

### Dev identity 후보

`dev` flavor를 분리할 경우 기본 후보는 아래와 같다.

```text
com.uiery.keep.dev
```

이 후보는 `applicationIdSuffix = ".dev"`를 활성화했을 때 `defaultConfig.applicationId`에서 파생되는 값이다. 실제 적용 PR에서는 Gradle 설정뿐 아니라 Firebase client package, CI restore, runtime QA evidence가 모두 일치해야 한다.

## 왜 단순 suffix 변경으로 끝내면 안 되는가

`applicationId`는 Kotlin package / Android `namespace`와 다르다. `namespace`는 코드/R class/manifest package 생성에 관여하지만, runtime 설치 identity와 Android framework 권한·데이터 경계는 `applicationId`가 결정한다.

`dev` applicationId를 바꾸면 최소한 아래 표면이 함께 바뀐다.

| 표면 | 확인해야 하는 계약 |
| --- | --- |
| Firebase `google-services.json` | `app/src/dev/google-services.json` 안에 `com.uiery.keep.dev` client가 있어야 `processDevDebugGoogleServices`가 성공한다. `prod` source set은 계속 `com.uiery.keep` client를 사용한다. |
| GitHub Actions restore matrix | Android CI / Release QA는 dev+prod source set을 모두 복원한다. Release Build / Play Deploy는 prod-only 복원이다. Source of truth는 `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`다. |
| Android CI smoke | `:app:testDevDebugUnitTest`, `:app:lintDevDebug`, `:app:assembleProdDebug`, focused runtime smoke가 dev/prod identity 차이를 전제로 해석되어야 한다. |
| Runtime permissions | AccessibilityService, `POST_NOTIFICATION`, `SCHEDULE_EXACT_ALARM`, appops, notification channel state가 dev/prod 별개 package로 분리되는지 확인해야 한다. |
| DataStore / Room | dev 설치본이 production 설치본의 DataStore/Room/backup identity를 덮어쓰거나 공유하지 않는지 확인해야 한다. |
| Alarms / receivers | `PendingIntent`, boot/package-replaced receiver, routine alarm 재예약 evidence가 dev package 기준으로 남는지 확인해야 한다. |
| Backup/restore | dev package가 production backup/restore identity와 섞이지 않아야 하며, policy 문서는 production package 기준을 유지해야 한다. |
| Play deploy | `bundleProdRelease` / `play-deploy.yml`은 오직 `com.uiery.keep` artifact만 업로드해야 한다. |
| Test commands | host-side `adb shell appops set ...` 명령은 대상 package를 명시하므로 dev split 이후 `com.uiery.keep.dev`와 `com.uiery.keep`를 혼동하면 QA evidence가 무효가 된다. |

## 적용 전 체크리스트

Dev/prod identity split PR은 다음을 하나의 패키지로 다룬다.

- [ ] `app/build.gradle.kts`의 dev flavor identity가 명시되어 있고, prod/release identity가 `com.uiery.keep`으로 유지된다.
- [ ] dev Firebase config가 `com.uiery.keep.dev` client를 포함한다는 증거가 있다. secret 값은 출력하지 않는다.
- [ ] `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`의 restore matrix와 실제 workflow가 일치한다.
- [ ] Android CI / Release QA에서 dev package 대상 appops 명령과 prod package 대상 release/Play 계약이 섞이지 않는다.
- [ ] QA docs가 dev package와 prod package를 명확히 구분한다.
- [ ] `:app:testDevDebugUnitTest`와 flavor/build contract verification이 통과한다.
- [ ] `:app:assembleProdDebug` 또는 release dry-run으로 prod package 경로가 유지됨을 확인한다.
- [ ] Play deploy / Release Build가 prod-only Firebase config와 `com.uiery.keep` package만 사용한다는 guardrail이 유지된다.

## 적용 후 QA evidence

Dev split을 실제로 적용한 PR은 최소 아래 evidence를 PR body에 적는다.

```bash
./gradlew :app:testDevDebugUnitTest
./gradlew :app:assembleProdDebug
./gradlew -q help --task :app:bundleProdRelease
```

Runtime smoke 또는 release QA에서 host-side appops를 쓰는 경우, package identity를 분리해서 적는다.

```bash
# dev runtime smoke / CI 대상
adb shell appops set com.uiery.keep.dev POST_NOTIFICATION ignore
adb shell appops set com.uiery.keep.dev SCHEDULE_EXACT_ALARM deny

# production/release package 대상. Play/release 경로에서만 사용한다.
adb shell appops set com.uiery.keep POST_NOTIFICATION ignore
adb shell appops set com.uiery.keep SCHEDULE_EXACT_ALARM deny
```

실제 device/emulator evidence가 없으면 완료로 과대보고하지 않는다. 그 경우 PR은 `Refs #314`로 두고, 남은 외부 경계를 `디바이스/에뮬레이터 runtime evidence 대기` 또는 `Firebase dev client 등록 대기`처럼 명확히 적는다.

## 분리하지 않는 경우의 안전 계약

만약 당장 dev/prod applicationId를 분리하지 않는다면, PR/issue comment에 아래 이유와 안전 계약을 남겨야 한다.

- 왜 dev와 prod가 같은 `com.uiery.keep` identity를 공유해야 하는지.
- 로컬 dev 설치가 production 설치본을 덮어쓸 수 있음을 operator가 알고 있는지.
- dev smoke 전에 production 설치본/데이터/권한을 보호하거나 별도 기기를 쓰는 운영 절차.
- Release Build / Play Deploy는 계속 production package만 사용한다는 guardrail.

이 선택은 기본값이 아니라 임시 예외로 취급한다.

## 문서 소스 관계

- Secret restore / Firebase source-set matrix: `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md`
- Release / Play deploy flow: `docs/PLAY_DEPLOYMENT.md`, `docs/RELEASE_CHECKLIST.md`
- Runtime permission / receiver / accessibility QA: `docs/QA_RUNTIME_CHECKLIST.md`
- Release lane context pack: `docs/ops/stopit/release-context.md`
- Engineering context pack: `docs/ops/stopit/engineering-context.md`
