# Git Workflow

## Branch Strategy

```
main              ← 프로덕션 릴리즈 (태그 붙음)
  └── release/*   ← 릴리즈 준비 (버전 bump, 최종 수정)
       └── develop      ← 개발 통합 브랜치
            └── feature/* ← 기능 개발
```

## Branch Naming

| 브랜치 | 네이밍 | 예시 |
|--------|--------|------|
| 기능 개발 | `feature/<기능명-kebab-case>` | `feature/emergency-unlock` |
| 버그 수정 | `fix/<버그명>` | `fix/countdown-crash` |
| 릴리즈 준비 | `release/<버전>` | `release/1.6.0` |
| 긴급 수정 | `hotfix/<설명>` | `hotfix/block-screen-crash` |

## Development Flow

```
1. develop에서 feature/* 브랜치 생성
2. feature 브랜치에서 개발
3. 완료 시 develop으로 PR 생성
4. PR 리뷰 후 머지
5. 릴리즈 시 develop에서 release/* 생성
6. release 브랜치에서 버전 bump + 최종 수정
7. release → main 직접 머지 + 태그
8. main → develop 역머지 (동기화)
```

### feature → develop (PR)

- feature 브랜치 작업 완료 후 develop으로 PR 생성
- PR에 변경 사항 요약 포함
- 코드 리뷰 후 머지

### release → main (직접 머지)

- release 브랜치에서 최종 확인 후 main으로 직접 머지
- main에 semver 태그 생성 (예: `v1.6.0`)
- Play Store 수동 배포

## Versioning (Semantic Versioning)

`MAJOR.MINOR.PATCH` 형식:

| 자리 | 올릴 때 | 예시 |
|------|---------|------|
| **MAJOR** | 대규모 변경, 기존 기능 깨짐 | 1.x.x → 2.0.0 |
| **MINOR** | 새 기능 추가, 기존 기능 유지 | 1.5.x → 1.6.0 |
| **PATCH** | 버그 수정, 작은 개선 | 1.5.8 → 1.5.9 |

- 태그 형식: `v{MAJOR}.{MINOR}.{PATCH}` (예: `v1.6.0`)
- 변경 파일: `app/build.gradle.kts`의 `versionName`, `versionCode`

## Commit Convention

Conventional Commits 스타일, 한국어/영어 혼용:

| 접두사 | 용도 | 예시 |
|--------|------|------|
| `feat:` | 새 기능 | `feat: add emergency unlock feature` |
| `fix:` | 버그 수정 | `fix: countdown timer 오류 수정` |
| `style:` | UI/스타일 변경 | `style: improve emergency unlock UI` |
| `refactor:` | 코드 리팩토링 | `refactor: extract notification helper` |
| `docs:` | 문서 변경 | `docs: update CLAUDE.md` |
| `chore:` | 빌드/설정 변경 | `chore: bump version to 1.6.0` |
| `test:` | 테스트 | `test: add ViewModel unit tests` |

## Release Checklist

1. `develop`에서 `release/{version}` 브랜치 생성
2. `versionName`, `versionCode` 업데이트
3. 빌드 확인: `./gradlew bundleProd`
4. `release/{version}` → `main` 머지
5. `main`에 태그 생성: `git tag v{version}`
6. 태그 푸시: `git push origin v{version}`
7. `main` → `develop` 역머지
8. GitHub Actions가 태그 push를 감지해 Play Store internal track에 자동 업로드
9. 필요 시 Play Console에서 승격하거나, GitHub Actions `Deploy Android to Google Play` 워크플로를 수동 실행해 `production` track 배포

## Automated Play Deployment

자동 배포 설정과 필요한 GitHub Secrets는 [`PLAY_DEPLOYMENT.md`](PLAY_DEPLOYMENT.md)를 기준으로 관리한다.
