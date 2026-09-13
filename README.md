# CAMP Checklist

Condition Framework는 native condition 실행, addon API, native opaque node storage와 C5 재귀
condition presentation snapshot까지 구현되어 있습니다. 저장 호환성·현재 제한·단계별 설계는
[Condition Framework 기록](CONDITION-FRAMEWORK.md)을 참고하세요.

실행 검증 결과와 남은 수동 확인 항목은 [개발 환경 검증 기록](DEVELOPMENT-VERIFICATION.md)을 참고하세요.

현재 milestone은 0.7.1입니다. native condition, addon API, native node state, protocol 2의 typed
recursive presentation snapshot과 내부 의존성 기반 무효화 스케줄러까지 구현되었습니다. 실제 Checklist
화면의 작은 창 표시와 resource reload 직후 첫 열기 안정성을 보강했으며, 최종 화면 회귀 QA는 별도 확인 항목입니다.

CAMP Checklist is a NeoForge 1.21.1 mod. The project keeps its Minecraft,
NeoForge, LDLib2, and optional integration versions in
[`gradle.properties`](gradle.properties), with the Java 21 toolchain in
[`build.gradle`](build.gradle), so a second development machine does
not need the original launcher instance or a pre-existing game directory.

## New-machine setup

Use a normal Git checkout. 기존 소스 폴더에서는 `java -version`부터 실행합니다:

```powershell
git clone https://github.com/kerupu526/camp-checklist.git
cd camp-checklist
java -version
.\gradlew.bat --version
.\gradlew.bat clean build
```

Use a JDK 21 installation. The Gradle wrapper downloads Gradle 8.14.3, and the
NeoForge userdev setup downloads the pinned Minecraft/NeoForge artifacts on the
first sync or build. Internet access is required for that first dependency
resolution.

IDE에서는 이 폴더를 Gradle 프로젝트로 열고 Gradle JVM을 JDK 21로 선택한 뒤
Sync/Reload Gradle Project를 실행합니다. 전역 Gradle 설치는 필요하지 않습니다.
`JAVA_HOME`을 JDK 21에 맞추거나 사용자 Gradle 홈의 `gradle.properties`에
`org.gradle.java.home`을 지정할 수 있습니다. 이 JDK 설정은 프로젝트의
`gradle-local.properties`가 아니라 Gradle이 직접 읽는 사용자 설정에 둡니다.
macOS/Linux에서는 `bash gradlew clean build`, `bash gradlew runClient`를 사용합니다.

저장소에는 프로젝트 소스와 함께
`gradlew`, `gradlew.bat`, `gradle/wrapper`의 JAR와 properties를 모두 포함합니다.

## Lightweight UI run

`runClient` defaults to the `ui` profile and a project-local `run/ui` game
directory. This keeps UI work independent from a large modpack and does not
load Create, AE2, Mekanism, or PneumaticCraft:

```powershell
.\gradlew.bat runClient
```

기본 클라이언트 힙은 2GB이고 optional 모드를 켜면 4GB입니다.
`'-PcampHeap=3G'` 또는 `CAMP_HEAP`으로 조절할 수 있습니다.
컴파일 시에는 통합 API를 확인하기 위해 compileOnly 의존성도 다운로드합니다.
경량 구성은 다운로드를 없애는 기능이 아니라 대형 모드의 실행을 생략하는 구성입니다.

The UI template and checklist data remain the real project resources. Missing
optional gameplay mods may make their item icons or integration-backed goals
unavailable, but they do not prevent the lightweight client from checking the
LDLib2 UI topology and binding behavior.

## Optional runtime profiles

Enable exactly the integration needed for a run with `campProfile`:

```powershell
.\gradlew.bat runClient -PcampProfile=create
.\gradlew.bat runClient -PcampProfile=ae2
.\gradlew.bat runClient -PcampProfile=mekanism
.\gradlew.bat runClient -PcampProfile=pneumaticcraft
.\gradlew.bat runClient -PcampProfile=all
```

전체 optional integration을 포함한 수동 QA는 별도 실행 폴더를 사용합니다.

```powershell
.\gradlew.bat runClient '-PcampProfile=all' '-PcampGameDir=run/c5-full-qa'
```

Multiple profiles can be comma-separated, for example
`-PcampProfile=create,ae2`. The older `-PwithCreate`, `-PwithMekanism`,
`-PwithPneumaticCraft`, and `-PwithAE2` switches remain supported for existing
local scripts. These switches only change the development runtime classpath;
they do not change the mod's production logic or checklist definitions.

PowerShell에서는 복합 인자를 따옴표로 감싸 주세요:
`'-PcampProfile=create,ae2'`, `'-PcampGameDir=run/test-v0.3'`.
`'-PcampProfile=all' '-PwithCreate=false'`처럼 개별 모드를 끌 수 있습니다.
실행 폴더는 최종 활성 모드 조합으로 결정되므로 기존 `run/mods`를 읽지 않습니다.
로컬 properties에서 Windows 경로는 `C:/...`처럼 슬래시를 사용합니다.

For a personal default, copy
[`gradle-local.properties.example`](gradle-local.properties.example) to
`gradle-local.properties` and edit it. The copied file is ignored by Git. The
same settings can be supplied through `CAMP_PROFILE` and `CAMP_GAME_DIR`.
Command-line `-PcampProfile`/`-PcampGameDir` values have highest precedence.

To keep a development run outside the repository, set a local path without
editing the build files:

```powershell
.\gradlew.bat runClient -PcampGameDir=C:\path\to\camp-checklist-run
```

테스트 월드만 사용하고 실제 모드팩의 월드 폴더를 game directory로 지정하지 마세요.
`runServer`도 같은 profile을 사용하며 클라이언트 폴더 이름에 `-server`를 붙여
분리합니다. 최초 실행 후 Minecraft EULA를 직접 확인하고 동의한 경우에만
해당 실행 폴더의 `eula.txt`를 수정합니다.
개발 서버는 인증이 비활성화된 상태로 실행될 수 있으므로 외부에 포트를 공개하지 마세요.

The path above is only an example; no developer-specific absolute path is
required by the project.

## Useful verification commands

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat runClient -PcampProfile=all
```

The runtime profile is selected when Gradle configures the run, so use a fresh
run directory when switching between profiles if a previous run has copied
mod files or configuration into it.

The UI layout and styling are authored by the LDLib2 template. Java binds the
existing template to the ViewModel; this development setup does not redesign
the UI or alter goal definitions.

UI 수동 점검: 테스트 월드를 만들고 Checklist 키(키 설정에서 확인)를 누른 뒤
네 탭의 카드, 긴 설명, 상세 행, 스크롤, 수동 토글, 닫기/재오픈을 확인합니다.
Editor에서 수정한 NBT는 `src/main/resources/assets/camp_checklist/resources/ui/checklist_ui.ui.nbt`로
반영해야 다음 clone/build에 포함됩니다. 개인 런처의 Editor 파일은 자동 복사하지 않습니다.

검증 중 Gradle 8.14.3의 HTML Problems Report 생성 timeout이 관찰되어
`org.gradle.problems.report=false`를 사용합니다. 필요 시 `--problems-report`로
재활성화할 수 있습니다. JUnit 결과는 `build/reports/tests/test/index.html`에 남습니다.
실행 중인 개발 클라이언트를 종료한 후 `clean build`를 실행합니다.
JUnit 부팅 시 `UnionFileSystem$NoSuchFileException`이 나오면 실행 프로세스 종료와
`clean build`로 산출물 재생성을 먼저 확인합니다. 정확한 저수준 원인은 확정하지 않았습니다.
