# 개발 환경 검증 기록 — 2026-09-08

대상: CAMP Checklist 0.3.0, Minecraft 1.21.1, NeoForge 21.1.219,
LDLib2 2.2.38.a, Gradle 8.14.3, Windows / Eclipse Temurin JDK 21.0.11.

## 조사 및 변경 범위

- 프로젝트를 조사한 뒤 Gradle 개발 설정, wrapper, ignore 규칙과 문서를 정리했습니다.
- 소스/빌드 설정에서 개인 PrismLauncher 또는 기존 개발 폴더 경로를 요구하지 않습니다.
  생성된 Gradle 산출물과 실행 로그에 현재 머신의 절대경로가 기록되는 것은 정상입니다.
- 기존 `run/mods`의 수동 설치와 개발 실행을 분리하고, 선택한 모드는 Maven 의존성으로 가져옵니다.
- `gradle-local.properties`는 Git 제외 대상이며 profile/game directory/heap을 지정할 수 있습니다.
- Java 기능 코드, ViewModel, ProgressStore, Network, 연동 백엔드, 목표 데이터와 UI NBT는 변경하지 않았습니다.
- production 모드 의존성 메타데이터는 기존 조건을 유지합니다.
- 표준 `gradle/wrapper`만 사용하며 이전 루트 wrapper 두 파일은 Git 제외 경로
  `qa-backups/legacy-wrapper-20260908`로 이동해 보관했습니다.

## 실행 결과

| 검증 | 결과 / 확인 범위 |
| --- | --- |
| 기본 `gradlew.bat clean build` | 성공, JUnit 24개, 실패/오류 0 |
| `clean build -PcampProfile=all` | 성공, optional runtime 포함 상태에서 빌드/테스트 완료 |
| 소스만 새 폴더에 복사한 `clean build` | 성공, JUnit 24개, 실패/오류 0 |
| `runClient`, UI 최소 구성 | CAMP 0.3.0 + LDLib2 + Minecraft/NeoForge만 발견, 렌더링 초기화 완료 |
| Create 단독 | Create 6.0.10 및 지원 라이브러리 발견, 렌더링 초기화 완료 |
| AE2 단독 | AE2 19.2.17 발견, 렌더링 초기화 완료 |
| Mekanism 단독 | Mekanism 10.7.19 발견, 렌더링 초기화 완료 |
| PneumaticCraft 단독 | PneumaticCraft 8.2.23 발견, 렌더링 초기화 완료 |
| 네 가지 모두 | 네 모드 모두 실제 모드 목록에 포함, 렌더링 초기화 완료 |
| `all` + `withCreate=false` | runtimeClasspath에서 Create 제외, 다른 세 모드 포함 확인 |
| 최소 `runServer` | `Done` 도달, 정의 4탭/15목표/errors=0, UI prewarm loaded=true |

클라이언트 검증 로그는 Git 제외 경로 `run/final-{ui,create,ae2,mekanism,pneumaticcraft,all}/logs/latest.log`에 있습니다.
서버 로그는 `run/ui-server/logs/latest.log`입니다. 렌더링 초기화 완료는 texture atlas 및
`Loaded 0 entity animations` 로그까지의 확인이며, 화면 조작을 했다는 의미는 아닙니다.
검증 클라이언트는 창 닫기로 종료했습니다. 서버는 명령 입력 채널이 닫혀 있어 테스트 프로세스를 종료했습니다.

독립 폴더 검증에는 `src`, 표준 `gradle/wrapper`, wrapper 스크립트와 Gradle 설정만 복사했습니다.
기존 프로젝트의 `.gradle`, `build`, `run` 또는 개인 설정은 복사하지 않았습니다.
다만 같은 PC의 JDK와 사용자 Gradle 다운로드 캐시는 공유했습니다. 실제 노트북이나 빈 다운로드 캐시에서의 검증은 아닙니다.

## 경고와 남은 검증

- 최소 환경에서 없는 모드의 아이템/checker를 사용할 수 없다는 경고가 발생합니다. 정의 자체를 변경하거나 숨기지 않았습니다.
- 서버 UI 템플릿의 등록 탭 수 경고(등록 3 / 실제 4)가 발생한 뒤 기존 코드가 4개로 재구성하고 prewarm을 완료했습니다. 원본 UI는 수정하지 않았습니다.
- 전체 개발 구성에 외부 모드의 PonderWorld/JetBrains annotation mixin 클래스 경고, refmap 및 shader 경고가 있으나 이번 클라이언트 초기화를 중단하지 않았습니다.
- Gradle HTML Problems Report timeout은 해당 보고서 생성을 비활성화한 뒤 clean build가 통과했습니다.
- 중간 검증에서 JUnit 부팅의 UnionFileSystem NoSuchFileException이 관찰되었습니다. 이후 clean build는 통과했지만 정확한 저수준 원인을 확정하거나 영구 해결됐다고 판단하지 않았습니다.
- 실제 UI 탭 전환, 스크롤, 토글, 재오픈 및 게임 내 연동 목표 달성은 수동 검증이 남아 있습니다.
- `all`은 네 가지 optional integration의 개발 구성이지 기존 C.A.M.P 모드팩 전체의 복제본이 아닙니다. 기존 PrismLauncher 전체 모드팩/실제 월드의 회귀 검증은 미완료입니다.
- 초기 조사 시 로컬 `.git`과 원격 저장소의 커밋이 없었습니다. 이후 사용자 요청으로 소스, 개발 환경, 문서의 큰 단계별 초기 커밋 및 GitHub 업로드를 진행합니다. 업로드 여부는 현재 원격 Git 이력으로 확인합니다.
