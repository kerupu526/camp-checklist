# CAMP Checklist 작업 기록 및 인수인계

이 문서는 앞으로 이 프로젝트에서 진행하는 **모든 작업의 단일 진행 기록**입니다. 새 세션은 먼저 이 문서와 `AGENTS.md`, `MODEL-ROUTING.md`를 읽고, 아래 단계의 마지막 미완료 항목부터 시작합니다. 코드를 바꾸거나 QA 결과가 생길 때마다 해당 단계의 상태, 근거, 남은 일을 이 문서에 갱신합니다. 완료 근거가 없으면 `[x]`로 표시하지 않습니다.

상태 표기: `[ ]` 시작 전 · `[-]` 진행 중 · `[x]` 완료 · `[!]` 차단. 차단 시 원인, 이미 시도한 내용, 재개 조건을 바로 아래에 기록합니다. 각 단계의 완료 기준을 모두 만족해야 다음 단계로 이동합니다.

## 현재 작업 범위와 기준 상태

- 프로젝트: `D:\modding\camp-checklist`.
- 설계 협업 대화: **뉴매틱크래프트 목표 정리** (`chatgpt-conversation://6a996d7d-3a54-83e8-8741-fd094f639a5d`). 설계 결정이 필요한 경우 이 대화에 직접 문의합니다.
- 현재 작업 트리에는 C6 내부 무효화 스케줄러, C7 공개 무효화 API 및 그 이전 UI/프레임워크 변경을 포함한 미커밋 변경 사항이 있습니다. 새 세션은 먼저 `git status --short`를 확인하고 사용자 변경을 덮어쓰지 않습니다.
- 직전 구현 기준 버전은 `0.7.0`입니다. 이전 세션에서 JUnit 110개 통과, `clean build` 성공, all profile 전용 서버의 4탭·15목표·오류 0·UI prewarm 성공을 확인했습니다. 이는 **2026-09-10의 과거 검증**이며 Windows 11 환경에서 다시 실행한 결과로 취급하지 않습니다.
- 현재 승인된 작업 범위는 `0.7.1` UI correctness patch입니다. 작은 logical viewport에서의 좌측 클리핑과 resource reload 직후 첫 `K` 열기 race만 다루며, Phase D·카드 grab-drag·ViewModel/ProgressStore/Network 의미 변경은 시작하지 않습니다.
- Phase D는 설계 승인 전 시작하지 않습니다. 이번 후속 작업의 목표는 Windows 11 CUA 확인, 실제 게임 화면 QA, 결함 확인 시 최소 수정과 재검증입니다.

## 0. 인수인계와 환경 확인

- [x] 이 단일 작업 기록과 `MODEL-ROUTING.md`를 만들고 `AGENTS.md`에 두 경로를 연결.
- [x] 기존 `AGENTS.md`, 검증 문서 및 작업 트리 상태 확인. 미커밋 변경 다수 확인.
- [x] Windows 버전 확인. 2026-09-12 `Microsoft Windows 11 Pro`, 버전 `10.0.26200`, 빌드 `26200`.
- [x] 현재 `computer-use` 스킬과 필수 guidance/API/confirmation 문서 확인.
- [x] 공식 `@oai/sky` 초기화 및 `sky.list_apps()` 성공. Prism Launcher가 설치 앱으로 반환되고, 실행 중인 다른 Windows 앱의 창도 반환됨. 지난 세션의 `Trusted RPC service is not configured: sky` 오류는 이번 호출에서 발생하지 않음.
- [x] Prism Launcher 11.1.0을 공식 CUA로 실행·선택하고 `1021×586` screenshot과 접근성 트리를 캡처. 비파괴적인 `Tab` 입력 뒤 포커스가 목록에서 항목으로 이동한 것을 후속 캡처에서 확인.
- [x] CUA 관찰 결과를 아래 증거 기록에 기록.

완료 기준: 현재 세션에서 Windows 앱 창 하나를 공식 CUA로 선택하고 캡처하며, 비파괴적 입력 후 화면 또는 포커스 변화를 다시 관찰합니다. 앱 목록 조회만으로 게임 QA 가능 판정을 내리지 않습니다.

## 1. QA 준비와 개발 클라이언트

- [x] 새 QA 작업 `01a09499-e709-7243-9e42-7ab9001da8fd`의 실행 기록 `turn_context`(8행)에서 **Sol Medium** (`gpt-5.6-sol`, `medium`) 실제 선택 확인. 대량·정형 반복·최종 중요 QA에는 해당 행의 모델을 별도로 명시한다.
- [x] 새 Development 작업 `01a0949a-574f-74a0-9d72-038ec4db6bfe`를 **Luna Max** (`gpt-5.6-luna`, `max`)로 명시해 생성. QA 담당과 개발 담당이 동시에 같은 파일을 수정하지 않는다.
- [x] `runClient -PcampProfile=all`의 실제 Minecraft 개발 클라이언트에 접속하고 Creative 테스트 월드 `Checklist QA`에서 `K`로 Checklist를 엶. 로딩 로그에서 CAMP Checklist `0.7.0`, AE2, Create, Mekanism, PneumaticCraft, LDLib2 및 정의 `tabs=4, goals=15, errors=0` 확인.
- [x] Prism 창 상태를 새로 얻어 `1021×586` screenshot과 창 상대 좌표 경계를 확인. 다른 창에 가린 상태에서 캡처된 `1920×1032` 이미지는 입력 좌표 기준으로 사용하지 않고 창을 활성화한 뒤 재캡처함. Windows 화면 배율 수치는 미확인.
- [x] 활성화한 Prism 창의 새 `1021×586` screenshot 기준 `(942, 209)` 클릭이 정상 반영되어 C.A.M.P. 선택 강조를 후속 화면에서 확인. 이전 `(900, 98)` 사용자 입력 감지 거부와 `856×512` 경계 오류는 제품 UI 결함의 근거가 아님.

완료 기준: 실제 Minecraft 개발 클라이언트에 접속하고 Checklist를 열 수 있으며, CUA 입력과 캡처가 같은 창 좌표계에서 작동합니다.

## 2. 수동 CUA QA

항목마다 **통과 / 실패 / 미검증**과 관찰 근거를 아래 증거 기록에 남깁니다. 이전 사용자 QA 결과나 서버 prewarm을 이번 화면 검증으로 대체하지 않습니다.

- [x] Checklist 열기와 4개 탭 표시 통과. `1920×1032`로 창을 확대한 뒤 Create, Mekanism, AE2, PneumaticCraft 탭을 화면에서 확인.
- [x] Create→Mekanism→AE2→PneumaticCraft→AE2와 재오픈 후 Create→AE2 전환 통과. 각 탭 제목·카드가 교체되며 다른 탭 카드 혼입 없음.
- [x] 카드 수 Create 3, Mekanism 4, AE2 4, PneumaticCraft 4(합계 15), 일반 카드 이름·진행률 표시 확인. AE2 Spatial IO·Wireless Quantum 및 PneumaticCraft Aerial Interface 긴 설명의 원문 끝까지 두 줄로 표시 확인. 상세 행 아이콘은 AE2 Processor 3행에서 확인.
- [x] AE2 Processor 상세 펼치기·접기 통과: Logic, Calculation, Engineering Processor의 아이콘·이름·`0 / 128` 3행 확인. 탭 이동 후 펼침 유지, Checklist 닫고 다시 열면 기본 Create 탭/AE2 상세 접힘 상태로 초기화됨.
- [!] 탭 이동 후 펼침 유지, Escape→K 재오픈 후 펼침 초기화 통과. F3+T 리소스 새로고침 후 4탭·수동 완료 상태는 유지되나, 직후 첫 `K`에서 화면이 열리지 않고 서버 ERROR가 3회 중 2회 발생. 다음 `K`에서 열림. 세 번째 반복은 첫 `K` 성공. 수정 후 재검증 필요.
- [-] AE2 휠 스크롤과 PneumaticCraft·Mekanism·AE2 스크롤바 드래그 통과. 카드 영역 드래그는 CUA에서 275~380px 끌 때 내용이 0~29px만 움직였으며 입력 이벤트와 제품 반응 분리가 남아 미검증.
- [!] AE2 Processor 상세 3행과 다음 카드 간 겹침 없음, 긴 설명 원문 끝까지 표시 확인. 다만 최초 `856×512` 기본 게임 창에서 Checklist 왼쪽 가장자리와 첫 탭이 화면 밖으로 잘림. `1920×1032` 최대화 시 해소됨. 화면 배율/GUI scale 영향과 ScrollerView의 작은 창 높이 제약은 추가 조사 필요.
- [!] Creative 테스트 월드 `Checklist QA`의 Create 수동 목표 `케이크 공장 자동화 검토` 토글 ON·재오픈/F3+T 후 유지 검증 통과. 기존 ChatGPT 설계 답변의 QA 상태 원상복구 지시를 확인했으나, 복구를 위한 두 번째 클라이언트에서 사용자 입력 감지가 반복되어 CUA를 중단. 현재 테스트 월드 상태는 ON이며, 사용자 입력이 없는 안전한 시점의 최종 QA에서 OFF 복구 필요.
- [x] 종료 후 `run/ae2-create-mekanism-pneumaticcraft/logs/latest.log` 확인. 초기 정의 `tabs=4, goals=15, errors=0`, client/server TabView runtime `headers=4, contents=4, registered=4`. 새로고침 직후 첫 열기 실패 때 두 번의 서버 ERROR와 `camp_checklist:open` payload 실패 확인. 최하위 `ConcurrentModificationException`은 LDLib2 `LDFontManager.apply:210`에서 발생. 로딩 중 일부 optional 모드의 ClassNotFound 경고는 있었으나 Checklist 관련 클래스 로딩 오류는 확인되지 않음.

완료 기준: 각 항목에 실제 화면 관찰과 결과가 기록되어 있고, 실패 항목은 재현 절차·기대 동작·실제 동작·로그 여부가 분리되어 있습니다. 도구 좌표 오류는 제품 결함으로 단정하지 않습니다.

## 3. 결함 분류와 설계 확인

- [x] CUA 비대상 창 클릭 거부·과거 사용자 입력 감지와 Java TEMP 경로 오류는 게임 UI 결함에서 분리. 작은 창의 왼쪽 잘림과 F3+T 후 간헐적 첫 열기 실패는 실제 화면/로그 결함으로 분류. 카드 drag는 입력 이벤트 세부량을 확인하지 못해 미검증으로 유지.
- [x] 첫 열기 실패의 최하위 원인은 LDLib2 폰트 매니저의 `ConcurrentModificationException`으로 확인. UI 생성 중 서버 스레드의 NBT 역직렬화→Label 생성→폰트 접근 경로와 리소스 새로고침이 겹쳤고, client reload listener lifecycle에서 다음 client tick까지 열기 요청을 게이트하는 최소 수정으로 반영함. 작은 창 잘림은 Minecraft 1.21.1 Auto GUI scale의 `856×512 → scale 2, logical 428×256` 제약과 고정 폭 root가 겹친 원인으로 확인하고 UI Editor NBT의 root 폭을 `PERCENT 1`/`max-width 500`으로 수정함.
- [x] 지정된 ChatGPT 설계 대화에 QA 결과와 작은 창 지원 기준을 직접 질문하고 전체 답변 확인. `856×512 + guiScale:0(Auto)`를 공식 QA 범위에 포함, 실제 guiWidth/guiHeight/resolved scale 측정, NBT/LSS 소유권 유지, UI 결함과 F3+T race 분리, 0.7.1 patch 허용, drag 미확정, Phase D 미시작 결정. Development에 직접 전달.
- [x] 확인된 버그 수정은 SemVer에 따라 각 PATCH 증가가 필요하고, Development 완료 전 `gradle.properties`의 `mod_version` 확인이 필요함을 인수인계.

완료 기준: 개발 담당에게 넘길 재현 절차와 수정 경계가 명확합니다. 실제 결함이 없으면 수정 단계는 `해당 없음`으로 기록하고 검증 단계로 이동합니다.

## 4. 최소 수정과 자동 검증

- [x] 승인된 범위의 최소 수정. UI Editor NBT root 폭은 부모 제약에 맞게 `PERCENT 1`/`min-width 0`/`max-width 500`으로 유지하고, LDLib2 singleton font cache를 공유하는 integrated server/client UI construction을 앱 내부 lock으로 직렬화함. `ClientReloadOpenGate`는 실제 reload overlay 종료까지 열기·snapshot 적용을 막고, 서버 open과 클라이언트 snapshot에는 분류된 font CME에 한해 다음 tick 최대 1회 bounded retry와 latest snapshot coalescing을 추가함. `ViewModel`, `ProgressStore`, `Network`의 동작 계약과 완료된 연동 백엔드는 변경하지 않음.
- [x] 관련 회귀 테스트 추가 및 실패→수정→통과 확인. 고정 폭 NBT assertion이 먼저 실패한 뒤 새 NBT에서 통과했고, `ClientReloadOpenGateTest` 3개와 `UiRetryPolicyTest` 3개를 추가함. 전체 JUnit 결과는 116/116, failures/errors=0.
- [x] `gradle.properties`의 `mod_version`을 `0.7.1`로 증가.
- [x] 관련 테스트와 `TEMP=TMP=C:\jtmp .\gradlew.bat clean build` 실행. `BUILD SUCCESSFUL`, 10 actionable tasks.
- [x] all profile dedicated server 스모크 실행. `CAMP Checklist 0.7.1`, `tabs=4, goals=15, errors=0`, runtime `headers=4, contents=4, registered=4`, UI prewarm loaded=true 및 `Done` 확인.
- [x] `git diff --check`와 최종 변경 범위 확인. 기존 C6/C7 및 QA 미커밋 변경을 보존하고 이번 patch의 신규 Java/test/NBT/version/docs만 확인함.

완료 기준: 실제 결함 재현 테스트가 통과하고 전체 빌드가 성공하며, 버전과 변경 범위가 확인됩니다. 자동 검증 결과만으로 수동 QA를 완료 처리하지 않습니다.

## 5. 최종 중요 QA와 종료

- [x] QA 작업 `01a09499-e709-7243-9e42-7ab9001da8fd`의 **Sol High** (`gpt-5.6-sol`, `high`) 선택을 확인했고, 사용자가 Minecraft 창 조작 가능 시점을 알려준 뒤 `0.7.1` all profile client를 실행해 실제 화면을 재검증. 기본 `856×512`, `guiScale:0(Auto)`에서 Checklist 좌측 경계/첫 탭/카드가 잘리지 않음. 최대화 `1920×1032`에서도 같은 구조 확인.
- [x] 0.7.1 화면의 Create/Mekanism/AE2/PneumaticCraft 네 탭과 탭별 3/4/4/4 목표, 긴 설명의 두 줄 원문 끝, AE2 Processor 펼침 상세 Logic/Calculation/Engineering 3행 각각 `0 / 128`, 휠 스크롤과 스크롤바 이동, Escape→K 재오픈을 확인. 카드 grab-drag는 원래 범위 밖이라 미검증.
- [x] QA 월드 `Checklist QA`의 Create 수동 목표 `케이크 공장 자동화 검토`를 ON→OFF로 원상복구. 화면에서 Create `0 / 3` 및 빈 체크를 확인하고 `Save and Quit to Title`로 저장한 뒤 월드 재입장 후에도 `0 / 3`과 빈 체크가 유지됨을 확인.
- [x] **추가 보정 전 실패 기록:** F3+T 직후 첫 K 세 번 모두 당시 `latest.log`에 `ConcurrentModificationException` (`HashMap.computeIfAbsent`→LDLib2 `LDFontManager.apply:210`)이 발생. 1회차 `00:30:16` Server thread `camp_checklist:open` 실패, 첫 K 미표시·두 번째 K 표시. 2회차 `00:31:02`와 3회차 `00:31:21` Render thread `camp_checklist:snapshot` 실패. 이 근거를 Development와 지정된 ChatGPT 설계 대화에 전달했고, 이후 추가 보정과 아래 재QA를 진행함.
- [x] 지정된 ChatGPT 설계 대화의 이번 실패에 대한 답변 전문 확인 후 Development에 전달. 작은 창 이슈와 QA 월드 상태 복구는 CLOSED. 서버 open과 클라이언트 snapshot 양쪽의 font-dependent 작업 경계를 조사하고, gate의 임의 지연 증가 대신 실제 동시성 확인 및 필요한 경우 분류된 transient CME에만 다음 tick 최대 1회 bounded retry를 우선. 수정 후 F3+T→첫 K **최소 10회** 모두 열리고 최신 로그의 `camp_checklist:open`/`camp_checklist:snapshot` ERROR가 각각 0이어야 최종 QA 통과. `0.7.1` 미배포 상태면 버전 유지 가능, Phase D·grab-drag는 계속 제외.
- [x] 남은 미검증/차단 항목을 명시하고 지정된 설계 대화에 0.7.1 범위와 수정 결과를 보고.
- [x] 월드를 게임 UI로 저장하고 `Quit Game`으로 종료. `runClient` exit 0, `BUILD SUCCESSFUL in 7m 17s`. 최신 로그 `run/ae2-create-mekanism-pneumaticcraft/logs/latest.log`(269694바이트, 2026-09-13 00:33:57)에 재로드 예외가 남았고, 다음 단계는 Development의 근본 원인 수정·자동 검증 후 F3+T 직후 첫 K 재QA임.
- [x] **추가 보정 후 Sol High 수동 재QA 통과:** `TEMP=TMP=C:\jtmp` all profile `runClient`에서 CAMP Checklist 0.7.1/AE2/Create/Mekanism/PneumaticCraft/LDLib2 로딩. `856×512`, `guiScale:0(Auto)`에서 좌측 클리핑 없음, 네 탭, 탭별 3/4/4/4 목표, 긴 설명 감김, AE2 Processor 상세 Logic/Calculation/Engineering 3행 `0 / 128`, 휠 및 스크롤바 하단 접근, Escape→K 재오픈 확인. Create 케이크 목표는 OFF/`0 / 3` 유지. 평상시 K 열기 3/3.
- [x] F3+T overlay가 사라진 뒤 첫 K를 유효한 **10회** 반복했고 모두 한 번에 Checklist가 열림(10/10). 총 `[Debug]: Reloaded resource packs` 12건 중 1건은 일시정지 메뉴가 나타나 즉시 K 판정에서 제외, 다른 1건은 사용자 입력 감지로 K가 도구에서 거부되어 제외하고 사용자가 `재개`를 지시한 뒤 별도 새로고침으로 마지막 유효 회차를 완료. 각 유효 회차에서 단일 Checklist와 Create `0 / 3`이 표시되어 중복 UI·오래된 상태 징후 없음. 카드 grab-drag는 계속 미검증.
- [x] 월드를 `Save and Quit to Title`로 저장하고 `Quit Game`으로 종료. `runClient` exit 0/`BUILD SUCCESSFUL in 13m 34s`; `latest.log` 116612바이트(2026-09-13 01:12:03). 해당 실행에서 `camp_checklist:open` ERROR 0, `camp_checklist:snapshot` ERROR 0, `ConcurrentModificationException` 0, `LDFontManager.apply` 0, `Error executing task on Server/Client` 각각 0. 전체 `/ERROR` 1건은 시작 시 `[EARLYDISPLAY] WARNING: glfwInit took 7.783... seconds`로 Checklist 경로와 무관. Minecraft ModLauncher/runClient/runServer 프로세스 0.
- [x] 지정된 ChatGPT 설계 대화에서 최종 QA 보고에 대한 답변 전문 확인. 위 10/10 화면·로그 근거를 받아 **0.7.1 UI correctness patch와 최종 수동 QA 종료 승인**. `mod_version=0.7.1`, storage format 1, network protocol 2, API major 1을 final baseline으로 기록. grab-drag는 별도 미검증이며 이번 종료 차단 원인은 아님. ChatGPT는 Phase D 진행 가능하다고 했으나, 사용자의 "현재 진행 중인 작업까지만" 범위 지시에 따라 이 QA 작업에서는 Phase D를 시작하지 않음.
- [x] 사용자 요청으로 Phase D가 원래 CAMP 모드팩 계획에 포함되는지 지정된 ChatGPT 설계 대화에 재확인. Phase D의 command-controlled condition, 관리자 명령어, 외부 Java control API는 CAMP 원래 요구사항이 아니라 범용 Condition Framework의 선택적 후속 확장으로 확인됨. 구현·사전 조사를 중단하고 `0.7.1` baseline을 유지. C4~C7, UI 안정화, 기존 모드 연동은 되돌리지 않음. Phase D는 **Optional/Deferred External Control Extension**으로 재분류하며, 사용자의 후속 제품 결정 전에는 시작하지 않음.
- [x] 사용자 배포 준비: 지정된 ChatGPT 설계 대화가 첫 안정 공개 버전 `1.0.0` 승격을 승인. 코드 변경 없이 `mod_version`과 사용자 문서·변경 기록을 1.0.0으로 올렸고, storage format=1/network protocol=2/API major=1을 유지한다. `TEMP=TMP=C:\\jtmp .\\gradlew.bat clean build` 성공 및 JUnit 116/116(failures/errors=0)을 확인했다. 생성 artifact `build/libs/camp_checklist-1.0.0.jar`(SHA-256 `B1D04B483FF8723DC130A41622C81E359D17AFC1FA5179661BEC6C0755BD521B`)의 NeoForge metadata에서 `camp_checklist`/`CAMP Checklist`/`1.0.0`, Minecraft `1.21.1`, NeoForge `[21.1.219,)`, LDLib2 `[2.2.38.a,)`를 확인했다. all-profile dedicated smoke는 0.7.1→1.0.0 version difference를 인지한 기존 데이터에서 정상 기동했으며 definitions `tabs=4, goals=15, errors=0`, runtime `registered=4`, UI prewarm, `Done`을 확인했다. 이번 버전 전용 client 재실행은 Windows 그래픽의 `glfwGetPrimaryMonitor failed`와 CUA `GetCursorPos access denied`로 불가했고, RDP `/admin` 콘솔 연결도 세션에 표시되지 않아 복구되지 않았다. 코드가 없는 release 승격이므로 직전 0.7.1의 `856×512`·F3+T 후 첫 K 10/10·4탭/15목표·저장 상태·로그 ERROR 0 수동 회귀 증거를 적용한다. grab-drag는 별도 미검증이며 Phase D는 보류 상태로 유지.

완료 기준: 최종 QA의 관찰 근거와 자동 검증이 함께 있고, 다음 세션이 추측 없이 이어갈 수 있습니다.

## 증거 및 결정 기록

| 날짜 | 단계 | 관찰/명령 | 결과와 의미 | 다음 행동 |
| --- | --- | --- | --- | --- |
| 2026-09-10 | 이전 C7 | `test --rerun-tasks`, `clean build`, dedicated server all profile | JUnit 110/110, 빌드 성공, 4탭·15목표·오류 0. 과거 Windows 10 기준. | Windows 11에서 화면 QA 필요 |
| 2026-09-12 | 0 | 작업 기록·모델 규칙 문서 생성, `AGENTS.md` 경로 추가 | 단계별 완료 기준과 QA/개발 모델 분담을 저장소에서 반복 확인 가능. | 새 세션은 세 문서를 먼저 읽기 |
| 2026-09-12 | 0 | OS 조회 | Windows 11 Pro, 빌드 26200 | CUA 실제 창 테스트 |
| 2026-09-12 | 0 | `@oai/sky` 초기화, `sky.list_apps()` | 성공. Prism Launcher 설치 앱과 실행 중인 Windows 창 반환. | 창 캡처와 입력 검증 |
| 2026-09-12 | 0 | Prism Launcher 11.1.0 `launch_app` → `get_window_state` → `Tab` → `get_window_state` | 창 하나를 선택해 `1021×586` screenshot과 접근성 트리를 캡처했고, 포커스가 목록에서 항목으로 바뀜. Windows 11의 native CUA 캡처와 키 입력은 실제 동작 확인. | Minecraft 클라이언트/마우스 QA는 별도 |
| 2026-09-12 | 1 | Prism 창 좌표 `(900, 98)` 입력 시도 | 도구가 동시 사용자 입력을 감지하고 해당 입력을 거부. 후속 창 상태는 다시 조회함. 좌표계와 창 크기 변경 검증은 미완료이며 제품 결함 증거가 아님. | 새 세션에서 사용자 입력이 없는 시점에 fresh window/screenshot으로 재시도 |
| 2026-09-12 | 1 | 새 Prism 창 캡처→좌표 클릭→후속 캡처 | 처음 `1920×1032` 캡처에서 `(59,589)` 클릭은 Discord 창이 위에 있어 도구가 거부. Prism을 활성화하자 실제 창 `1021×586`이 캡처됨. 이 새 화면의 `(942,209)` 클릭 후 C.A.M.P. 선택 강조 확인. 창 상태/좌표 갱신 절차 통과. 화면 배율 수치 미확인. | all profile 개발 클라이언트 실행 |
| 2026-09-12 | 1 | 모델 선택 확인 | `SESSION-LISTS.md`의 지정과 함께 현재 QA 세션 실행 기록의 첫 `turn_context`(8행)에서 `model=gpt-5.6-sol`, `effort=medium`을 직접 확인. | Sol Medium QA 진행 |
| 2026-09-12 | 1 | `runClient -PcampProfile=all` 첫 시도, `gradlew help --stacktrace`, Java `Selector.open()` | 기본 `%TEMP%` (`C:\Users\KERUPU~1\AppData\Local\Temp`)에서 Java NIO AF_UNIX 연결이 `Invalid argument: connect`로 실패하여 Gradle 데몬 연결 중단. 프로젝트 코드/게임 UI 단계 이전의 실행 환경 문제. | 짧은 임시 경로로 단일 프로세스 환경을 바꿔 재시도 |
| 2026-09-12 | 1 | `TEMP=TMP=C:\jtmp`에서 Java `Selector.open()`과 `runClient -PcampProfile=all` | Selector 성공, Gradle `runClient` 진입. 로딩 로그에 CAMP Checklist 0.7.0, AE2 19.2.17, Create 6.0.10, Mekanism 10.7.19, PneumaticCraft 8.2.23, LDLib2 2.2.38.a 포함. 클라이언트 프로세스 실행 중이며 화면 QA는 아직 미검증. | 게임 창 로딩과 Checklist 열기 |
| 2026-09-12 | 2 | Creative 월드 `Checklist QA` 생성 후 `K` | Checklist 열기 통과. 최초 Minecraft 창/screenshot `856×512`; UI 왼쪽 가장자리와 첫 탭이 잘려 보여 창 확대 후 재확인 필요. 통합 서버 로그 `tabs=4, goals=15, errors=0`; 템플릿 TabView의 미등록 자식 경고 후 runtime `headers=4, contents=4, registered=4` 복구 및 UI prewarm 성공. | 창 확대 후 UI 전체와 카드 검사 |
| 2026-09-12 | 2 | Minecraft 창을 `1920×1032`로 확대하고 새 screenshot 기준 탭 클릭 | 4개 탭 모두 표시. Create→Mekanism→AE2에서 각 탭 제목·목표 카드가 교체되어 탭별 카드 격리 확인. 작은 `856×512` 창의 왼쪽 잘림은 창 크기 영향이며 큰 창에서는 해당 잘림 없음. | PneumaticCraft와 역방향 탭 검증 |
| 2026-09-12 | 2 | AE2 Processor 카드 클릭·휠 스크롤 | 상세 3행 Logic/Calculation/Engineering Processor, 각각 아이콘·`0 / 128` 확인. 휠 스크롤로 카드 하단과 다음 카드 시작이 순서대로 나타나며 겹침은 관찰되지 않음. | 접기, 상태 유지, 스크롤바·드래그 확인 |
| 2026-09-12 | 2 | PneumaticCraft 탭과 스크롤바 | 네 카드가 순서대로 분리됨. 스크롤바 thumb를 아래로 드래그해 하단 Aerial Interface Supply System 카드까지 접근. 긴 설명은 두 줄로 감기며 원문 마지막 `구축합니다.`가 보임. | 카드 drag와 다른 긴 설명 확인 |
| 2026-09-12 | 2 | AE2 재선택→상세 접기/펼치기→Escape→K→AE2 | 탭 이동 후 Processor 3행 펼침 유지. 클릭 접기 시 행 제거와 다음 카드 재배치 확인. Escape 후 `K`로 재오픈하면 Create 기본 탭, AE2 재선택 시 Processor 상세 접힘과 목록 상단으로 초기화됨. | refresh 및 수동 토글 확인 |
| 2026-09-12 | 2 | 카드 영역 `sky.drag` 두 차례 | PneumaticCraft 하단 카드에서 `y855→504` 드래그 후 화면 위치 변화 관찰되지 않음. AE2 Spatial 카드 `y873→493` 드래그 후 카드 위치가 약 29px만 이동. 도구가 게임에 전달한 실제 drag event 세부량과 제품 코드의 반응은 아직 구분되지 않음. | 다른 방향/거리로 재현하고 로그 확보 |
| 2026-09-12 | 2 | Create 수동 카드 토글→재오픈→F3+T 새로고침→재오픈 | `케이크 공장 자동화 검토` 초록 체크, `1 / 1`, 탭 `1 / 3` 유지. F3+T 후 게임 채팅 `[Debug]: Reloaded resource packs` 확인, 4탭 재표시. | 서버 오류 로그 분류 |
| 2026-09-12 | 2 | 새로고침 후 터미널 로그 검토 | 리소스 재로드 이후 `17:05:46` 클라이언트 UI `registered=4` 복구 로그 다음 `Server thread/ERROR`와 LDLib2 `UIElement.deserializeNBT`/`UITemplate.createUI` 스택이 발생. 출력량이 많아 상단 예외 메시지는 아직 확보 못함. 실제 화면은 이후 열림. 실행 디렉터리의 `latest.log`·`debug.log`는 현재 0바이트라 터미널 출력 재현 필요. | 오류 메시지와 영향 재현 후 Development에 전달 |
| 2026-09-12 | 2·3 | F3+T→완료 후 첫 `K` 3회 반복, 종료 후 최신 로그 확인 | 2회는 첫 `K`에서 화면 미표시와 서버 ERROR, 뒤이은 `K`로 열림. 세 번째는 첫 `K` 성공. `run/ae2-create-mekanism-pneumaticcraft/logs/latest.log:212,762` 서버 `Error executing task on Server`, `:461,1011` `camp_checklist:open` payload 실패. 최하위 `:372,922` `ConcurrentModificationException` at `HashMap.computeIfAbsent`→LDLib2 `LDFontManager.apply:210`→`Label.<init>`→NBT 역직렬화→`ChecklistUi$Instance.<init>:223`. 간헐적 타이밍 결함으로 Development에 직접 전달. | 동시성 원인 조사·최소 수정 후 재검증 |
| 2026-09-12 | 2·3 | 작은 창/최대화 창 비교 | 기본 `856×512` Minecraft 창에서 Checklist 첫 탭 왼쪽과 카드 왼쪽이 화면 밖으로 잘림. 최대화 `1920×1032`에서는 4탭과 카드 정상 표시. CUA 좌표/포커스 오류와 분리해 Development에 전달. | UI Editor NBT·부모 제약·GUI scale 확인 |
| 2026-09-12 | 2 | 추가 카드·스크롤·드래그 | 탭별 3/4/4/4 카드 합계 15. AE2 Wireless Quantum 설명은 마지막 `연결합니다.`, Spatial IO 설명은 `확인합니다.`, PneumaticCraft Aerial 설명은 `구축합니다.`까지 표시. Mekanism 스크롤바로 네 번째 Fusion Reactor 접근. Create 카드 영역 `y680→405` CUA 드래그는 약 29px만 스크롤되어 입력 이벤트 상세 미검증. | drag 이벤트 분리 필요 |
| 2026-09-12 | 1·2 | 테스트 월드 저장→메인 메뉴 Quit Game | `runClient` 종료 코드 0, `BUILD SUCCESSFUL in 14m 56s`. 종료 후 `latest.log`·`debug.log`가 198382바이트로 기록됨. Creative 월드 `Checklist QA`는 저장되어 있으며 수동 목표 1개가 ON. Development에 클라이언트 종료 및 clean/build 가능 통보. | Development 수정·자동 검증 대기 |
| 2026-09-12 | 3 | 지정된 ChatGPT 설계 대화 최신 답변 전체 확인 | `856×512+Auto` 지원, logical viewport 실측 후 NBT/LSS 반응형 수정. F3+T reload race와 분리하여 0.7.1 UI correctness patch에 함께 포함 가능. Grab-drag는 미확정으로 별도. Phase D 미시작. 기존 설계 답변에 QA용 수동 토글 원상복구 지시 확인. | Development에 답변 전달, QA 상태 복구 |
| 2026-09-12 | 1·2 | 실행 환경·배율 설정 보충 | `run/ae2-create-mekanism-pneumaticcraft/options.txt`는 `guiScale:0`, `fullscreen:false`, overrideWidth/Height=0. 화면 캡처는 기본 `856×512`, 최대화 `1920×1032`. Windows OS DPI 수치는 레지스트리 조회로 확정되지 않았고 Minecraft logical guiWidth/guiHeight 및 resolved scale은 미측정. | Development가 작은 창 기준 실측 |
| 2026-09-12 | 2·3 | 수동 목표 OFF 복구를 위한 두 번째 `runClient` | 클라이언트 로딩 중 다른 앱의 내용이 게임 창 캡처에 나타나고 `user input was detected in this window`가 발생. 지침에 따라 게임 입력 중단·창 상태 갱신. 사용자 입력이 계속되는 동안 테스트 월드에는 입장하지 않고 실행 세션을 중단했으며 Minecraft 창이 사라진 것을 확인. 테스트 월드의 케이크 목표는 ON으로 남음. Development에 clean/build 재개 가능과 최종 QA 복구 필요를 통보. | 사용자 입력이 없는 시점에 OFF 원상복구 |
| 2026-09-12 | 5 | Sol High 실제 선택, `0.7.1` all profile runClient | 현재 QA 세션 후속 turn_context에서 `model=gpt-5.6-sol`, `effort=high` 확인. `TEMP=TMP=C:\jtmp`에서 client 실행, Mod List에 CAMP Checklist 0.7.1 및 AE2/Create/Mekanism/PneumaticCraft/LDLib2 포함. 현재 Minecraft 창의 856×512 캡처에 다른 앱 화면이 나타나 UI 좌표 입력을 보류. 사용자에게 안전한 포커스 시점을 비동기로 문의. | 사용자 입력이 멈추고 Minecraft 창을 새로 확인한 뒤 수동 QA |
| 2026-09-12 | 5 | 사용자 입력 시점 답변 및 클라이언트 상태 | 사용자가 Minecraft 포커스 이동 가능 시점을 나중에 알려주겠다고 답함. 게임 입력 없이 대기하던 중 `runClient`가 `BUILD SUCCESSFUL in 1m 56s`(exit 0)로 종료됐고 `sky.list_windows()`에 Minecraft 창 없음. 최신 `run/ae2-create-mekanism-pneumaticcraft/logs/latest.log`는 13333바이트, CAMP Checklist 0.7.1 로딩만 확인. 월드 미진입이므로 작은 창·F3+T·4탭·토글 복구는 모두 미검증이며 이 실행에서 ERROR가 없다는 사실을 화면 QA 통과로 해석하지 않음. | 안전한 입력 시점에 Sol High 최종 QA 재개 |
| 2026-09-12 | 3·4 | UI template test-first 수정 | 기존 고정 폭 `500` assertion을 먼저 실행해 실패한 뒤, UI Editor NBT root를 `width=PERCENT 1`, `min-width=LENGTH 0`, `max-width=LENGTH 500`으로 바꾸고 테스트 통과. 높이와 카드/탭 구조는 유지함. | 작은 창 Sol High 화면 회귀 대기 |
| 2026-09-12 | 3·4 | reload open gate 및 자동 회귀 | `ClientReloadOpenGateTest` 3개를 추가하고 전체 `gradlew test --rerun-tasks` 결과 113/113, failures/errors=0. gate는 reload callback과 client tick 간 상태를 synchronized로 보호하고 한 tick 뒤 pending open을 1회 coalesce함. | F3+T 직후 첫 K 수동 재확인 대기 |
| 2026-09-12 | 4 | 버전·전체 빌드 | `gradle.properties:mod_version=0.7.1`; `TEMP=TMP=C:\jtmp .\gradlew.bat clean build`가 `BUILD SUCCESSFUL`로 완료. | 최종 diff 범위 확인 완료 |
| 2026-09-12 | 4 | all profile dedicated server | `runServer -PcampProfile=all`에서 CAMP Checklist 0.7.1, definitions `tabs=4, goals=15, errors=0`, runtime TabView `headers=4, contents=4, registered=4`, UI prewarm `loaded=true`, `Done` 확인 후 서버 프로세스 종료. | client 화면 회귀만 남음 |
| 2026-09-12 | 인수인계 | 사용자 요청에 따라 `SESSION-LISTS.md` 작성, `AGENTS.md`에서 링크 | 대화 맥락이 비어 있는 Development·QA 새 작업을 각각 만들고 모델/effort를 명시할 준비 완료. | 작업 ID 발급 후 목록 갱신 |
| 2026-09-12 | 인수인계 | 새 Codex 작업 2개 생성, `SESSION-LISTS.md`에 ID 기재 | QA는 Sol Medium, Development는 Luna Max로 모델과 effort를 요청에 명시. 둘 다 이전 대화 fork가 아닌 새 작업이며 현재 저장된 프로젝트를 공유. | 각 작업의 첫 실행 상태 확인 |
| 2026-09-13 | 5 | 사용자 안전 시점 통보→`TEMP=TMP=C:\jtmp .\gradlew.bat runClient -PcampProfile=all`→공식 CUA | CAMP Checklist 0.7.1, AE2/Create/Mekanism/PneumaticCraft/LDLib2 로딩. `856×512` Auto 창에서 좌측 잘림 해소, 네 탭과 각 카드 표시. 최대화 `1920×1032`도 확인. | F3+T 회귀 및 상태 복구 |
| 2026-09-13 | 5 | 작은 창 탭/상세/스크롤, 큰 창 긴 설명/스크롤바 | Create/Mekanism/AE2/PneumaticCraft 3/4/4/4 목표, 탭별 내용 격리, AE2 Processor 3 상세 행과 `0 / 128`, AE2 Spatial IO·Wireless Quantum의 설명 끝, 휠 및 스크롤바로 하단 카드 접근 확인. 카드 grab-drag는 미검증. | 새로고침 직후 첫 K 검사 |
| 2026-09-13 | 5 | F3+T→즉시 첫 K 3회, 종료 후 `latest.log` 검색 | 1회차 첫 K 화면 미표시·두 번째 K에서 표시. 2·3회차는 화면 표시. 그러나 세 번 모두 `ConcurrentModificationException` 발생: `latest.log:178,427` 서버 `camp_checklist:open`, `:719,961,1247,1489` 클라이언트 `camp_checklist:snapshot`. 최하위 `LDFontManager.apply:210` (`:337,586,878,1120,1406,1648`). | Development 수정과 재검증 필요 |
| 2026-09-13 | 5 | Create 수동 토글 OFF→월드 저장→재입장→K | 초록 체크가 빈 체크로 바뀌고 Create `0 / 3`. 재입장 후 `0 / 3`과 빈 체크가 유지되어 QA 상태 원상복구 확인. | 게임 종료 및 로그 점검 |
| 2026-09-13 | 5 | `Save and Quit to Title`→`Quit Game`→runClient 종료 | 게임 UI로 정상 종료, exit 0, `BUILD SUCCESSFUL in 7m 17s`. `latest.log` 269694바이트. Development와 지정 ChatGPT 설계 대화에 실패 근거 직접 전달. | 0.7.1 reload race 해결 전 최종 QA 완료로 표시하지 않음 |
| 2026-09-13 | 3·5 | 지정 ChatGPT 설계 대화의 실패 후속 답변 전문 확인 | 작은 창과 QA 수동 토글 복구는 CLOSED. 서버 open 및 클라이언트 snapshot 경계의 실제 동시성 조사, 필요한 경우 font CME로 한정한 다음 tick bounded retry, pending open/snapshot 분리·중복/최신값 coalescing 지시. 정상 경로 지연·LDLib2 monkey patch 금지. 수정 후 자동 검증과 F3+T 첫 K 10/10·두 payload ERROR 0이 합격 기준. Development에 전문 열람과 핵심 결정을 전달. | Development 수정·자동 검증 후 Sol High 수동 재QA |
| 2026-09-13 | 3·4 | LDLib2 구현·로그 원인 조사 | 로컬 LDLib2 2.2.38.a `LDFontManager.INSTANCE`는 instance-owned 일반 `HashMap fontSets`를 사용하고 `apply()`는 `computeIfAbsent`, `onResourceManagerReload()`는 같은 상태를 invalidate/clear함. Integrated server와 render thread의 실제 로그 stack에서 `Font.split`/`UITemplate.createUI`가 같은 singleton font cache에 접근하는 경로를 확인함. | UI construction 경계 containment 구현 |
| 2026-09-13 | 3·4 | open/snapshot containment 구현 | `ChecklistUi`의 server/client construction·refresh를 앱 lock으로 직렬화하고, server open은 snapshot을 중복 전송하지 않는 다음 server tick 1회 retry/coalesce, client snapshot은 decoded latest DTO를 보류해 다음 client tick 1회 retry/coalesce. unrelated exception과 font CME를 구분하며 retry bound를 초과하지 않음. | targeted/full test 및 smoke |
| 2026-09-13 | 4 | 추가 자동 검증 | `ClientReloadOpenGateTest`, `UiRetryPolicyTest`, 기존 UI 테스트 targeted 통과. fresh `TEMP=TMP=C:\jtmp .\gradlew.bat test --rerun-tasks` 및 `clean build` 모두 `BUILD SUCCESSFUL`; JUnit 116/116, failures/errors=0. | dedicated smoke 및 Sol High 10회 QA |
| 2026-09-13 | 5 | 추가 보정 후 all profile `runClient`, `Checklist QA` 월드, `856×512` Auto 화면 | CAMP Checklist 0.7.1, 4탭·15목표와 각 탭 내용, 긴 설명 감김, AE2 Processor 3 상세 행, 휠·스크롤바, Create 케이크 목표 OFF/0/3 확인. 평상시 K 3/3 즉시 열림. | F3+T 10회와 종료 로그 대조 |
| 2026-09-13 | 5 | F3+T 후 overlay 종료→첫 K 유효 10회 | 10/10 첫 K에서 단일 Checklist 열림. 리로드 메시지 총 12건 중 1건은 일시정지 화면, 1건은 사용자 입력 감지로 K 거부되어 유효 횟수에서 제외. 마지막 회차는 사용자 `재개` 후 새 F3+T로 수행. 각 화면에서 Create 0/3 유지, 중복 UI·오래된 snapshot 징후 없음. | 월드 저장·클라이언트 종료·로그 검사 |
| 2026-09-13 | 5 | 게임 UI 정상 종료→`latest.log` 검사 | `runClient` exit 0, `BUILD SUCCESSFUL in 13m 34s`, 로그 116612바이트. `camp_checklist:open`/`camp_checklist:snapshot` ERROR 각각 0, CME 0, `LDFontManager.apply` 0, server/client task ERROR 0. `/ERROR` 1건은 시작 시 GLFW 초기화 지연 경고. ModLauncher 게임 프로세스 0. | Development에 결과 전달 후 0.7.1 최종 QA 종료 판단 |
| 2026-09-13 | 3·5 | 지정 ChatGPT 설계 대화에 QA 결과 보고 후 답변 전문 확인 | 0.7.1 UI correctness patch와 최종 수동 QA 종료 승인. final baseline: mod 0.7.1, storage 1, network 2, API major 1. grab-drag 별도 미검증 허용. Phase D는 사용자 지정 현재 작업 범위를 넘으므로 이번 QA에서는 미시작. | Development에 승인 결과 전달하고 현재 작업 종료 |
| 2026-09-13 | 운영 규칙 | 사용자 요청으로 `MODEL-ROUTING.md`와 `SESSION-LISTS.md` 갱신 | 새 기본 QA는 Terra Medium, 높은 추론이 필요한 QA는 Terra High. Sol은 Terra·Luna에서 합계 세 차례의 독립 실패가 확인되는 등 매우 높은 추론이 필요한 예외에만 사용. 기존 Sol QA 세션은 과거 기록으로 보존. | 다음 새 QA/작업 생성부터 적용 |
| 2026-09-13 | 범위 확인 | 사용자 요청으로 지정 ChatGPT 설계 대화에 Phase D와 원래 CAMP 계획의 관계를 질의 | Phase D external control은 CAMP 원래 요구사항이 아니라 선택적 framework 확장으로 확인. 코드·저장소·네트워크·명령어 변경 없이 중단하고, 0.7.1 stable baseline 및 C4~C7은 유지. | 사용자의 후속 제품 방향 결정 대기 |

현재 Minecraft 개발 클라이언트·개발 서버는 종료되어 있습니다. `0.7.1`의 작은 창 레이아웃과 핵심 화면 회귀가 통과했고, Creative 월드의 케이크 수동 토글도 OFF로 복구되어 저장 후 유지됩니다. 추가 보정 후 F3+T→첫 K의 유효한 10/10회가 한 번에 열렸고 해당 실행의 server `camp_checklist:open` 및 client `camp_checklist:snapshot` ERROR는 모두 0입니다. 지정된 ChatGPT 설계 대화가 이를 받아 0.7.1을 최종 baseline으로 승인했습니다(mod 0.7.1/storage 1/network 2/API major 1). Phase D는 CAMP 원래 요구사항이 아닌 선택적 외부 제어 확장으로 확인되어 deferred 상태입니다. 카드 grab-drag는 별도 미검증이며, 다음 제품 방향을 사용자가 결정할 때까지 신규 framework 기능은 시작하지 않습니다.

## 다음 세션 시작 지점

1. `AGENTS.md`, 이 문서, `MODEL-ROUTING.md`, `SESSION-LISTS.md`를 읽습니다.
2. 이 문서의 첫 `[-]`, `[!]`, `[ ]` 항목과 최신 증거 기록을 확인합니다.
3. 같은 단계의 미완료 검증부터 진행하고, 결과를 즉시 이 문서에 기록합니다.
4. 새 작업 생성 시 **기본 모델을 그대로 쓰지 말고** 역할에 맞는 모델·reasoning effort를 명시합니다. 사용자 승인 없는 Astra 선택을 금지합니다.
