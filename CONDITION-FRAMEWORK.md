# Condition Framework — Phase C6/C7

현재 개발 버전은 0.7.1입니다. 0.7.1은 Phase C6/C7의 저장·네트워크·공개 API 계약을 유지하면서
UI 템플릿의 작은 창 폭 제약과 resource reload 직후 첫 열기 게이트를 보강한 patch입니다. Phase C3는 기존 CAMP legacy 상태를 새 ConditionResult
세계로 읽기 전용 변환하는 compatibility bridge를 추가합니다. 기존 event/checker/storage
권한은 그대로 유지하며, legacy 자동 migration·command·UI recursive DTO 연결은
후속 단계입니다. Phase C4는 이 경계를 유지한 채 native condition의 opaque per-node state만
기존 ProgressStore에 additive로 저장합니다.

## 공개 무효화 API (Phase C7)

공개 무효화 표면은 C6 스케줄러 위의 얇은 façade입니다. 애드온은
`com.kelp.campchecklist.api.invalidation.ConditionDependency` 불변 값 객체를 만들고
`ChecklistInvalidation.invalidate(server, dependency)`를 호출합니다. 호출은 일치하는 native
목표를 큐에 넣기만 하며 즉시 평가하지 않습니다. 역인덱스와 flush 시점은 공개되지 않습니다.
서버 인스턴스는 반드시 명시하고 논리 서버 스레드에서 호출해야 합니다. 활성 runtime에서
일치하는 키가 없으면 정상적인 no-op이고, 활성 runtime이 없는 서버는 수명 주기 사용 오류입니다.

조건 타입은 추가된 기본 메서드 `ChecklistConditionType.dependencies(config)`로 정확한 의존성을
선언할 수 있습니다. core는 정의 및 인덱스 재구성 시 반환값을 복사하고 null 검증·중복 제거·
결정적 정렬을 수행합니다. null, 잘못된 값 또는 예외가 발생한 선언은 빈 의존성으로 취급하며
조건 평가, 저장 상태, 완료 처리, 네트워크 의미를 바꾸지 않습니다. 복합 조건은 기존처럼
자식 의존성을 내부에서 합칩니다.

공개 API는 exact dependency 무효화만 지원합니다. 목표 ID 무효화, wildcard, namespace 무효화,
전체 무효화, 비동기 호출, scheduler/service 노출, legacy checker migration은 제공하지 않습니다.
저장 형식은 1, 네트워크 프로토콜은 2, API major는 1을 유지합니다.

## 내부 경계

`Definitions.Goal.normalizedCondition()`은 기존 정의를
`internal.condition.ConditionNode(type, config, children)`으로 변환합니다.
config는 방어 복사하고 children은 불변 목록으로 보존합니다. UI·서버·저장소 객체를
노드에 넣지 않습니다. 이 패키지는 public addon API가 아닙니다.

`ConditionNormalizer.nativeCondition()`은 Loader와 Codec/registry 연결에 사용하는 정규화
경로입니다.
AND/OR는 비어 있지 않은 `children`, NOT은 단일 `child`를 가집니다. 알 수 없는 type
ID/config도 보존합니다. 실제 registry가 없는 type은 Engine에서 `UNAVAILABLE`로
처리되며 datapack reload를 crash하지 않습니다.

Goal은 `type`을 가진 legacy 형식과 `condition` object를 가진 native 형식 중 정확히
하나만 사용할 수 있습니다. 둘 다 있거나 둘 다 없거나 native condition 구조가 malformed인
리소스는 해당 리소스의 definition error로 격리됩니다. 정상적인 unknown condition ID는
raw node로 보존되므로 optional addon이 없어도 전체 reload를 실패시키지 않습니다.
native tree는 최대 depth 32, node 1024개로 제한됩니다.

Phase B public API는 [api 패키지](src/main/java/com/kelp/campchecklist/api)에 있습니다.
`ChecklistConditionType<C>`은 Codec, 동기 evaluator, tracking material, state layout
version, persistent-state capability, negation capability, static `ConditionDisplay`를 제공합니다.
`usesPersistentState(config)`가 false인 타입은 state를 열 수 없고, mutation 시 evaluation이
UNAVAILABLE로 격리됩니다. `ConditionStateHandle`
은 node별 방어 복사 state만 공개하며 ProgressStore·Runtime·LDLib2 타입을 노출하지 않습니다.
API_MAJOR=1은 public addon contract major이며 wire/storage version이 아닙니다.

| Legacy type | Normalized type | 데이터 |
| --- | --- | --- |
| manual | camp_checklist:manual | 루트 legacy 토글용; nested manual은 지원하지 않을 예정 |
| acquire_item | camp_checklist:item_acquired | item/tag, count=1 |
| craft_item | camp_checklist:item_crafted | item/tag, count=1 |
| craft_count | camp_checklist:item_crafted | item/tag, count=target |
| place_block | camp_checklist:block_placed | block/tag, count=1 |
| advancement | camp_checklist:advancement | advancement ID |
| custom | camp_checklist:legacy_custom | checker ID, target, opaque parameters |

기존 unit과 parameters도 보존합니다. custom의 실제 checker와 Create의 상태 mutation
경로는 변경하지 않았습니다. 이 매핑은 아직 이벤트·평가 실행을 대체하지 않습니다.
Native goal도 동일한 `normalizedCondition()` 경계를 사용합니다. C2에서는 native tree를
서버 스레드에서 평가하고 SATISFIED 결과만 기존 goal-level completed/toast에 반영합니다.
native node state는 C4 dual layout 전까지 backend가 없으므로 stateful leaf는 UNAVAILABLE로
처리하고 stateless leaf는 state를 열지 않습니다. 평가 trigger는 reload, player join,
advancement completion으로 제한하며 per-tick 전체 polling을 하지 않습니다.
`camp_checklist:advancement`는 현재 online-player snapshot의 부정이 서버 무인 시점에
잘못 완료되는 것을 막기 위해 negation을 지원하지 않습니다.

현재 Runtime의 `goalEvaluations`는 native ConditionEngine 결과와 legacy
`LegacyConditionBridge` 결과를 모두 보관하는 ephemeral snapshot입니다. Legacy bridge는
Counter/completed/toast/checker state를 변경하지 않으며, 기존 runtime만 legacy completion
authority로 남습니다. `completed`/`toastShown`은 sticky progression 이력이고
`ConditionResult.status`는 현재 authoritative 상태이므로 둘은 의도적으로 분리됩니다.
따라서 automatic legacy counter/custom은 완료 이력이 있어도 현재 raw progress가 내려가면
UNSATISFIED가 될 수 있고, checker가 unavailable이면 UNAVAILABLE을 유지합니다. Legacy
advancement는 기존 저장 완료 상태를 historical bridge 결과로 사용합니다. custom checker의
기존 numeric/detail은 neutral ConditionResult와 ConditionDetail로 변환되어 C5 ViewModel의
입력이 됩니다. validation 실패와 bridge 평가 중 발생한 UNAVAILABLE은 별도 경계로 유지하면서
goal-level ViewModel unavailable 상태와 ConditionResult를 함께 갱신합니다.

## Tracking signature와 저장 호환성

- 기존 `Goal.signature()` 문자열과 SavedData version=1/파일 이름은 그대로 유지합니다.
  기존 target 변경 시 numeric/custom state를 재사용하는 테스트도 유지합니다.
- 새 `TrackingSignatures`는 타입 ID, 해당 타입의 tracking material, 순서가 있는 child
  tree를 canonical JSON으로 직렬화하고 `condition-state-v1:` 접두사를 붙입니다.
  object key를 정렬하고 숫자 표기를 정규화합니다. child 순서/연산자가 바뀌면 signature가 바뀝니다.
- 내부 `Material` 확장 지점은 기본적으로 config 전체를 사용하며, 향후 등록된 타입의
  tracking 정책에 연결할 수 있습니다. item_acquired/item_crafted/block_placed는
  완료 임계값인 count를 제외하고, legacy_custom은 기존 target만 제외합니다.
  opaque parameters 안의 target 등은 임의로 제거하지 않습니다.
- Goal의 title/description/icon/order/display_unit은 normalized condition에 넣지 않습니다.
- 같은 goal ID의 completed/toastShown은 signature 변경으로 해제하지 않습니다.
  새로운 signature는 독립 카운터를 사용하며, 기존처럼 A→B→A 변경 시 과거 A의 numeric
  state를 자동 복구하지 않습니다. 사용하지 않는 다른 signature 기록은 저장소에 유지합니다.
- 최초 activeSignature 설정도 SavedData dirty 변경으로 처리합니다.

동일한 legacy/native 정의는 내부에서 동일한 normalized node/signature를 만들 수 있습니다.
그러나 **기존 저장 키와 새 normalized signature 사이의 자동 이전은 아직 없습니다.**
실제 state reuse는 향후 엔진 연결에서 signature뿐 아니라 state layout도 정확히 일치할 때만
허용해야 합니다. 휴리스틱 migration은 추가하지 않습니다.

## Phase C4 native state backend

기존 `ProgressStore`의 `goals`/legacy counter 영역은 그대로 유지하고, 저장 루트에
`nativeFramework` 영역을 추가합니다. 이 영역은 `formatVersion=1`, goal별 node 목록,
legacy normalization marker를 가집니다. Native node는 내부 structural address(`root`,
`root/0` 등), type ID, tracking signature, stateVersion, opaque `CompoundTag`를 저장합니다.
이 address는 public API나 ConditionDetail key가 아닙니다.

`NativeConditionStateProvider`는 server-thread 전용 `ConditionStateProvider` 구현입니다.
읽기 전용 평가의 `readCopy()`는 빈 태그를 반환할 수 있고 저장 entry를 만들지 않습니다.
새 node도 evaluator가 실제로 non-empty `replace()`를 커밋할 때만 materialize되며, 기존 node의
동일 값 replace는 dirty를 만들지 않습니다. `clear()`는 기존 entry를 제거합니다.
Evaluator 실행은 transaction 범위로 감싸며 정상 종료만 commit하고 예외는 rollback한 뒤 해당
node를 UNAVAILABLE로 격리합니다. StateVersion, type, tracking signature가 달라지면 해당
node만 빈 state로 재조정하고 sibling state는 유지합니다.

Native goal을 정상적으로 평가할 때 현재 tree의 address 집합과 metadata를 reconciliation하여
삭제된 node만 prune합니다. loader에서 제외된 malformed goal이나 제거된 goal은 runtime이
reconcile하지 않으므로 저장 bucket을 보존합니다. Registry에 없는 addon type, decode 실패,
tracking material 오류도 destructive reset 없이 기존 state를 보존합니다. NATIVE→LEGACY
schema 전환은 native bucket을 dormant 상태로 보존하고, LEGACY→NATIVE는 기존 native
bucket만 per-node metadata 기준으로 reconcile합니다. Legacy Counter/checker state를 native
state로 추측 복사하는 migration은 구현하지 않았습니다.

Legacy goal마다 `legacyMarkers`에 marker format, legacy type/signature, normalized signature를
기록하지만 이것은 migration이나 state bootstrap이 아닙니다. 동일 marker는 reload에서 다시
dirty 처리하지 않습니다. `ConditionResult` numeric/detail/status는 여전히 저장하지 않는
ephemeral evaluation snapshot입니다.

## 이후 단계와 UI 제한

### C4 state preservation correction (0.4.1)

한 evaluation cycle은 `PreparedConditionNode`에서 codec decode, non-null tracking material,
stateVersion, usesPersistentState를 각각 한 번 준비한 뒤 같은 snapshot으로 reconcile과
state open을 수행합니다. 준비 실패는 UNAVAILABLE이며 evaluator/state open을 실행하지 않습니다.
진단용 fallback signature는 persistence authority가 아닙니다.

Reconcile은 raw type 변경을 먼저 reset하고, 같은 type의 compatibilityKnown=false는
opaque state와 저장 metadata 전체를 보존합니다. 같은 type의 known metadata만 signature/version을
비교합니다. Unknown node도 parsed tree에 존재하는 한 live이며, 실제 removed address만 prune합니다.
Storage formatVersion=1과 public API major=1은 유지합니다. NOT capability callback 예외는 child
state evaluation 전에 격리하며 display 실패는 compatibility reset의 근거로 사용하지 않습니다.

### C5 recursive presentation snapshot (0.5.0)

서버는 cached `ConditionResult`를 `ViewModel.Evaluation`의 immutable recursive copy로 투영합니다.
이는 public API 객체와 별개이며 status, raw numeric progress, optional `progressText`, unavailable
Component, Component label, ItemStack icon, child details와 truncation을 보존합니다. `completed`는
goal history이며 raw evaluation은 변경하지 않습니다. Main card는 완료된 goal을 100%로 표시할 수
있지만 wire DTO의 raw evaluation은 그대로입니다.

`ConditionPresentation`은 network/presentation copy에만 depth 16, goal당 256 nodes, snapshot당
2048 nodes 상한을 적용하고 초과한 evaluation에 `detailsTruncated=true`를 기록합니다. semantic
`ConditionResult`와 goal availability는 상한 때문에 변경하지 않습니다. client는 received tree를
보존하고 final Ore UI binding에서만 deterministic preorder compact rows로 투영합니다. expansion은
기존 UI instance의 goal ID map에만 유지되어 refresh/tab switch에는 남고 SavedData에는 저장되지 않습니다.

Network protocol은 `2`이며 flat Gson snapshot 대신 registry-aware `StreamCodec` payload를 사용합니다.
Component와 ItemStack은 Minecraft codec으로 전달하며, snapshot count/string/detail bounds를 decode 시
검증합니다. protocol mismatch는 NeoForge registrar version negotiation으로 거절됩니다. 구버전 flat
payload compatibility layer는 없습니다.

Detail row reconciliation은 goal별 desired row identity 순서를 비교하여 순서가 바뀔 때 기존 행을
한 번 재구성하는 수준입니다. 같은 key의 UI element instance를 개별적으로 재사용하는 keyed diff는
아직 구현하지 않았습니다. 따라서 card/tab topology를 refresh마다 재생성하지 않지만 stable
detail-row instance reuse를 보장하지 않습니다.

### C5 Verification Gate

`NetworkSnapshotCodecTest`는 protocol 2에서 normal goal, completed=true/raw UNSATISFIED,
UNAVAILABLE reason, double progress, translatable Component, ItemStack icon, nested detail,
progressText 및 detailsTruncated가 server encode→client decode 경계를 통과함을 검증합니다.
malformed count와 depth 초과 recursive payload도 decode에서 거부합니다. `ConditionPresentationTest`는
presentation-only truncation과 progressText의 optional semantics를 검증합니다. `progressText`는 API
major 1의 additive 0.x refinement이며 tracking signature와 native storage layout에 영향을 주지 않습니다.

자동 검증은 화면 조작을 대체하지 않습니다. 수동 QA는 `runClient`에서 test world에 들어간 뒤 Checklist를
열고 4 tabs, tab switching, normal card, detail expand/collapse, icon/name/progress, raw translation key
미노출, refresh 및 tab return 뒤 expansion 유지, scroll wheel/scrollbar, card overlap과 layout을 확인해야 합니다.
AE2 Processor 3 rows는 `-PcampProfile=all` 환경에서 별도 확인합니다.

### 0.5.1 UI input patch

카드 영역의 grab-dragging이 LDLib2의 hover-only `MOUSE_MOVE`에 의존하던 문제를 수정했습니다.
`DRAG_UPDATE`와 `DRAG_END`를 함께 처리하여 카드 위에서 목록을 드래그할 수 있고, 실제 카드 클릭에
따른 detail expand/collapse와 충돌하지 않도록 이동 후 클릭을 억제합니다. 저장 형식, protocol,
ConditionResult semantics에는 변화가 없습니다.

등록은 `RegisterChecklistConditionsEvent`를 통해 진행되며 duplicate ID는 hard failure,
event 종료 후 등록은 거부됩니다. AND/OR/NOT는 SATISFIED/UNSATISFIED/UNAVAILABLE의
3상태 논리를 사용하고, 결정 가능한 결과를 우선하며 NOT은 Unavailable을 반전하지
않습니다. Composite 결과는 direct-child aggregate와 recursive `ConditionDetail`을
보존합니다. tracking signature와 opaque `stateVersion`은 별개입니다. 후속 단계에서
stable key command/external control, runtime routing, 계산된 detail의 ViewModel 전달을
진행합니다.

`UNAVAILABLE` 결과는 현재 public contract에서 설명 가능한 `unavailableReason`을 반드시
가집니다. numeric progress와 details는 선택적이며, unavailable이어도 이미 계산된
부분 progress/details를 보존할 수 있습니다. built-in AND/OR/NOT는 child 결과만 집계하고
persistent node state를 만들지 않습니다.

현재 UI는 CAMP authored tab prototype에 의존합니다. 범용 datapack 탭 renderer는 별도
UI 작업으로 남깁니다. 엔진·데이터 모델에 CAMP 4탭 제한을 추가하지 않습니다.
이번 C5에서는 기존 authored NBT와 UI topology를 유지한 채 recursive ViewModel과 typed Network snapshot을 추가했습니다.

### C6 internal invalidation scheduler (0.6.0)

Native goal의 자동 재평가는 서버 틱 끝에 실행되는 내부 무효화 큐로 한정합니다. `ConditionInvalidationService`는
goal→dependency와 dependency→goal 양방향 인덱스를 보유하며, 같은 틱의 중복 이벤트를 goal ID 단위로 합치고
goal ID를 사전순으로 정렬해 한 번만 평가합니다. 평가 중 새로 들어온 무효화는 현재 배치에 재진입하지 않고 다음
flush로 넘어갑니다. 서비스는 생성된 서버 스레드에서만 호출할 수 있고, 인덱스에 없는 의존성은 조용한 no-op입니다.

현재 내부 키는 `advancement:<id>`와 `lifecycle:player_roster` 두 종류입니다. Composite는 자식 키를 재귀적으로
합집합하고 중복을 제거합니다. 알려지지 않았거나 잘못된 native node는 키를 만들지 않으며 ConditionResult,
ProgressStore 상태, 기존 fallback polling 동작을 바꾸지 않습니다. 공개 dependency 선언과 exact 무효화 façade는
0.7.0의 C7에서 이 내부 스케줄러 위에 additive로 제공됩니다. 명령어와 broad 외부 제어 API는 추가하지 않습니다.

Advancement 완료 이벤트는 해당 advancement 키만 큐에 넣고, 로그인·로그아웃은 player roster 키를 큐에 넣습니다.
Reload에서는 정의 검증 뒤 인덱스를 재구성하고 오래된 dirty set을 지운 다음 native goal 전체를 명시적으로 한 번
평가합니다. Legacy advancement와 legacy custom의 기존 경로는 유지합니다. 저장 format=1, network protocol=2,
public API major=1은 그대로입니다.

## Phase A 검증

- 완료 보존 회귀 테스트는 수정 전 3개 중 1개 실패를 확인하고 수정했습니다.
- `gradlew.bat test --rerun-tasks`: 성공, JUnit 60개 / 실패 0 / 오류 0.
- Phase C1 native schema 테스트는 native/legacy 상호 배타성, unknown node 보존, cosmetic
  metadata 무시, semantic signature 변화, depth 32/node 1024 bounds를 검증합니다.
- Phase C2 native runtime 경계는 native result의 sticky completion 정책, stateless/stateful
  capability, built-in advancement의 보수적인 negation 정책을 검증합니다. 기존 dedicated server smoke에서
  condition registry `types=4, frozen=true`, CAMP `tabs=4, goals=15, errors=0`,
  서버 `Done`, TabView `headers=4, contents=4, registered=4`, UI prewarm 성공을
  확인했습니다. optional 모드 미설치 경고는 기존 경량 프로필 동작입니다.
- 기존 24개 + Phase A 11개 + Phase B 22개: 7종 legacy mapping, exact normalization, threshold/cosmetic
  안정성, matcher 변화, addon material override, nested/unknown 보존, malformed composite,
  방어 복사, canonical 숫자, 실제 CAMP 4탭/15 JSON 및 opaque state round trip,
  완료·toast 보존과 signature 전환을 검증합니다.
- 기존 실제 NBT 기반 UI 테스트 6개도 통과했습니다. 게임 내 수동 조작을 의미하지 않습니다.
- dedicated server 로그에는 기존 4탭/15목표 prewarm이 확인되어 있으나, Phase B 변경 이후
  별도 수동 화면 조작은 수행하지 않았습니다. optional 모드가 없는 경량 서버에서 해당
  목표가 Unavailable로 보고되는 것은 기존 동작입니다.
- Phase C3 bridge 회귀는 legacy manual/counter/advancement/custom 변환, checker state 방어
  복사, detail 전달, sticky completed와 raw UNSATISFIED/UNAVAILABLE 분리, missing checker
  상태 보존을 검증합니다. reload에서 native 중복 평가를 제거하고, bridge UNAVAILABLE도
  ViewModel denominator에서 제외되도록 goal-level projection을 일관되게 갱신합니다.
- 기존 ClientModel의 EventBusSubscriber deprecation 경고 2건이 남아 있습니다.
- Phase B 독립 리뷰에서 composite 평가의 불필요한 dirty state 생성이 지적되어 no-op state
  handle로 수정했고, 순수 composite 평가 회귀 테스트를 추가했습니다. 수정 후 `clean build`
  결과는 57개 테스트, 실패 0, 오류 0입니다.

## 협업 규칙

AGENTS.md에 역할 분담, 지정된 ChatGPT 대화로 직접 보고, 설계 불확실성 질문,
답변 전 의존 구현 중단, 15초 간격 확인과 응답 생성 중 대기 연장, 기계적 작업 예외를
추가했습니다. 단계별 설계 검토를 마친 후 다음 단계로 진행합니다.
