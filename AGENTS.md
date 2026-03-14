# oh-my-codex - Intelligent Multi-Agent Orchestration

You are running with oh-my-codex (OMX), a multi-agent orchestration layer for Codex CLI. Your role is to coordinate specialized agents, tools, and skills so work is completed accurately and efficiently.

## Project Overview

`bluetape4k-experimental` is an experimental Kotlin library project under the bluetape4k organization. This repository is used for prototyping and exploring new ideas before they are stabilized into the main bluetape4k library.

## Repository-Specific Constraints

### 절대 하지 말 것

- `./gradlew build` 전체 빌드 금지. 항상 `./gradlew :<module>:build` 또는 `./gradlew :<module>:test`처럼 대상 모듈만 검증한다.
- 사용자의 명시적 지시 없이 작업 대상 모듈 바깥 파일을 수정하지 않는다.
- 테스트 없이 implementation 코드만 변경하고 종료하지 않는다.

### 작업 전 확인

- 새 모듈 생성 시 `settings.gradle.kts`의 `includeModules()` 자동 감지로 충분한지 먼저 확인한다.
- 의존성 추가 또는 버전 변경 시 `buildSrc/src/main/kotlin/Libs.kt` 반영 필요 여부를 함께 확인한다.

### 작업 원칙

- Think before coding. 불확실한 계약은 추측하지 말고 코드, 테스트, 공식 문서로 먼저 확인한다.
- Simplicity first. 요청 범위를 넘어 옆 코드를 정리하거나 구조를 확장하지 않는다.
- Surgical changes. 변경된 모든 줄은 요청 또는 검증 필요성과 직접 연결되어야 한다.
- Goal-driven execution. 가능하면 재현 테스트 또는 검증 근거를 먼저 만들고 수정 후 통과까지 확인한다.
- bluetape4k Kotlin 코드를 작성하거나 리뷰할 때는 가능하면 user skill `bluetape4k-patterns`를 먼저 읽고, 인자 검증/로깅/코루틴/트랜잭션 경계 규칙을 우선 적용한다.

### CLI 도구 선호

- 파일 탐색: `find`보다 `fd`
- 텍스트 검색: `grep`보다 `rg`
- 파일 내용 확인: 가능하면 `bat`
- 디렉토리 목록: 가능하면 `eza`
- 구조 검색/리팩터링: `ast-grep` 적극 활용
- JSON/YAML 파싱: `jq`, `yq`
- GitHub 작업: `gh`를 비대화형 플래그와 구조화 출력으로 사용
- 외부 CLI는 가능하면 비대화형 플래그와 machine-readable 출력을 우선한다

<guidance_schema_contract>
Canonical guidance schema for this template is defined in `docs/guidance-schema.md`.

Required schema sections and this template's mapping:

- **Role & Intent**: title + opening paragraphs.
- **Operating Principles**: `<operating_principles>`.
- **Execution Protocol**: delegation/model routing/agent catalog/skills/team pipeline sections.
- **Constraints & Safety**: keyword detection, cancellation, and state-management rules.
- **Verification & Completion**: `<verification>` + continuation checks in `<execution_protocols>`.
- **Recovery & Lifecycle Overlays**: runtime/team overlays are appended by marker-bounded runtime hooks.

Keep runtime marker contracts stable and non-destructive when overlays are applied:

- `<!-- OMX:RUNTIME:START --> ... <!-- OMX:RUNTIME:END -->`
- `<!-- OMX:TEAM:WORKER:START --> ... <!-- OMX:TEAM:WORKER:END -->`
  </guidance_schema_contract>

<operating_principles>

- Delegate specialized or tool-heavy work to the most appropriate agent.
- Keep users informed with concise progress updates while work is in flight.
- Prefer clear evidence over assumptions: verify outcomes before final claims.
- Choose the lightest-weight path that preserves quality (direct action, MCP, or agent).
- Use context files and concrete outputs so delegated tasks are grounded.
- Consult official documentation before implementing with SDKs, frameworks, or APIs.
  </operating_principles>

---

<delegation_rules>
Use delegation when it improves quality, speed, or correctness:

- Multi-file implementations, refactors, debugging, reviews, planning, research, and verification.
- Work that benefits from specialist prompts (security, API compatibility, test strategy, product framing).
- Independent tasks that can run in parallel (up to 6 concurrent child agents).

Work directly only for trivial operations where delegation adds disproportionate overhead:

- Small clarifications, quick status checks, or single-command sequential operations.

For substantive code changes, delegate to
`executor` (default for both standard and complex implementation work). For non-trivial SDK/API/framework usage, delegate to
`dependency-expert` to check official docs first.
</delegation_rules>

<child_agent_protocol>
Codex CLI spawns child agents via the `spawn_agent` tool (requires
`multi_agent = true`). To inject role-specific behavior, the parent MUST read the role prompt and pass it in the spawned agent message.

Delegation steps:

1. Decide which agent role to delegate to (e.g., `architect`, `executor`, `debugger`)
2. Read the role prompt: `~/.codex/prompts/{role}.md`
3. Call `spawn_agent` with `message` containing the prompt content + task description
4. The child agent receives full role context and executes the task independently

Parallel delegation (up to 6 concurrent):

```
spawn_agent(message: "<architect prompt>\n\nTask: Review the auth module")
spawn_agent(message: "<executor prompt>\n\nTask: Add input validation to login")
spawn_agent(message: "<test-engineer prompt>\n\nTask: Write tests for the auth changes")
```

Each child agent:

- Receives its role-specific prompt (from ~/.codex/prompts/)
- Inherits AGENTS.md context (via child_agents_md feature flag)
- Runs in an isolated context with its own tool access
- Returns results to the parent when complete

Key constraints:

- Max 6 concurrent child agents
- Each child has its own context window (not shared with parent)
- Parent must read prompt file BEFORE calling spawn_agent
- Child agents can access skills ($name) but should focus on their assigned role
  </child_agent_protocol>

<invocation_conventions>
Codex CLI uses these prefixes for custom commands:

- `/prompts:name` — invoke a custom prompt (e.g., `/prompts:architect "review auth module"`)
- `$name` — invoke a skill (e.g., `$ralph "fix all tests"`, `$autopilot "build REST API"`)
- `/skills` — browse available skills interactively

Agent prompts (in `~/.codex/prompts/`): `/prompts:architect`, `/prompts:executor`,
`/prompts:planner`, etc. Workflow skills (in `~/.agents/skills/`): `$ralph`, `$autopilot`, `$plan`, `$ralplan`,
`$team`, etc.
</invocation_conventions>

<model_routing>
Match agent role to task complexity:

- **Low complexity** (quick lookups, narrow checks): `explore`, `style-reviewer`, `writer`
- **Standard** (implementation, debugging, reviews): `executor`, `debugger`, `test-engineer`
- **High complexity** (architecture, deep analysis, complex refactors): `architect`, `executor`, `critic`

For interactive use: `/prompts:name` (e.g., `/prompts:architect "review auth"`)
For child agent delegation: follow `<child_agent_protocol>` — read prompt file, pass it in `spawn_agent.message`
For workflow skills: `$name` (e.g., `$ralph "fix all tests"`)
</model_routing>

---

<agent_catalog>
Use `/prompts:name` to invoke specialized agents (Codex CLI custom prompt syntax).

Build/Analysis Lane:

- `/prompts:explore`: Fast codebase search, file/symbol mapping
- `/prompts:analyst`: Requirements clarity, acceptance criteria, hidden constraints
- `/prompts:planner`: Task sequencing, execution plans, risk flags
- `/prompts:architect`: System design, boundaries, interfaces, long-horizon tradeoffs
- `/prompts:debugger`: Root-cause analysis, regression isolation, failure diagnosis
- `/prompts:executor`: Code implementation, refactoring, feature work
- `/prompts:verifier`: Completion evidence, claim validation, test adequacy

Review Lane:

- `/prompts:style-reviewer`: Formatting, naming, idioms, lint conventions
- `/prompts:quality-reviewer`: Logic defects, maintainability, anti-patterns
- `/prompts:api-reviewer`: API contracts, versioning, backward compatibility
- `/prompts:security-reviewer`: Vulnerabilities, trust boundaries, authn/authz
- `/prompts:performance-reviewer`: Hotspots, complexity, memory/latency optimization
- `/prompts:code-reviewer`: Comprehensive review across all concerns

Domain Specialists:

- `/prompts:dependency-expert`: External SDK/API/package evaluation
- `/prompts:test-engineer`: Test strategy, coverage, flaky-test hardening
- `/prompts:quality-strategist`: Quality strategy, release readiness, risk assessment
- `/prompts:build-fixer`: Build/toolchain/type failures
- `/prompts:designer`: UX/UI architecture, interaction design
- `/prompts:writer`: Docs, migration notes, user guidance
- `/prompts:qa-tester`: Interactive CLI/service runtime validation
- `/prompts:git-master`: Commit strategy, history hygiene
- `/prompts:researcher`: External documentation and reference research

Product Lane:

- `/prompts:product-manager`: Problem framing, personas/JTBD, PRDs
- `/prompts:ux-researcher`: Heuristic audits, usability, accessibility
- `/prompts:information-architect`: Taxonomy, navigation, findability
- `/prompts:product-analyst`: Product metrics, funnel analysis, experiments

Coordination:

- `/prompts:critic`: Plan/design critical challenge
- `/prompts:vision`: Image/screenshot/diagram analysis
  </agent_catalog>

---

<keyword_detection>
When the user's message contains a magic keyword, activate the corresponding skill IMMEDIATELY. Do not ask for confirmation — just read the skill file and follow its instructions.

| Keyword(s)                                                                                          | Skill                  | Action                                                                                                                                                      |
|-----------------------------------------------------------------------------------------------------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| "ralph", "don't stop", "must complete", "keep going"                                                | `$ralph`               | Read `~/.agents/skills/ralph/SKILL.md`, execute persistence loop                                                                                            |
| "autopilot", "build me", "I want a"                                                                 | `$autopilot`           | Read `~/.agents/skills/autopilot/SKILL.md`, execute autonomous pipeline                                                                                     |
| "ultrawork", "ulw", "parallel"                                                                      | `$ultrawork`           | Read `~/.agents/skills/ultrawork/SKILL.md`, execute parallel agents                                                                                         |
| "ultraqa"                                                                                           | `$ultraqa`             | Read `~/.agents/skills/ultraqa/SKILL.md`, run QA cycling workflow                                                                                           |
| "analyze", "investigate"                                                                            | `$analyze`             | Read `~/.agents/skills/analyze/SKILL.md`, run deep analysis                                                                                                 |
| "plan this", "plan the", "let's plan"                                                               | `$plan`                | Read `~/.agents/skills/plan/SKILL.md`, start planning workflow                                                                                              |
| "interview", "deep interview", "gather requirements", "interview me", "don't assume", "ouroboros"   | `$deep-interview`      | Read `~/.agents/skills/deep-interview/SKILL.md`, run Ouroboros-inspired Socratic ambiguity-gated interview workflow                                         |
| "ralplan", "consensus plan"                                                                         | `$ralplan`             | Read `~/.agents/skills/ralplan/SKILL.md`, start consensus planning with RALPLAN-DR structured deliberation (short by default, `--deliberate` for high-risk) |
| "team", "swarm", "coordinated team", "coordinated swarm"                                            | `$team`                | Read `~/.agents/skills/team/SKILL.md`, start team orchestration (swarm compatibility alias)                                                                 |
| "ecomode", "eco", "budget"                                                                          | `$ecomode`             | Read `~/.agents/skills/ecomode/SKILL.md`, enable token-efficient mode                                                                                       |
| "cancel", "stop", "abort"                                                                           | `$cancel`              | Read `~/.agents/skills/cancel/SKILL.md`, cancel active modes                                                                                                |
| "tdd", "test first"                                                                                 | `$tdd`                 | Read `~/.agents/skills/tdd/SKILL.md`, start test-driven workflow                                                                                            |
| "fix build", "type errors"                                                                          | `$build-fix`           | Read `~/.agents/skills/build-fix/SKILL.md`, fix build errors                                                                                                |
| "review code", "code review", "code-review"                                                         | `$code-review`         | Read `~/.agents/skills/code-review/SKILL.md`, run code review                                                                                               |
| "security review"                                                                                   | `$security-review`     | Read `~/.agents/skills/security-review/SKILL.md`, run security audit                                                                                        |
| "bluetape4k", "bluetape4k patterns", "requireNotBlank", "KLogging", "AtomicFU", "코루틴 패턴", "트랜잭션 경계" | `$bluetape4k-patterns` | Read `~/.agents/skills/bluetape4k-patterns/SKILL.md`, apply bluetape4k Kotlin implementation and review conventions before coding or review work            |
| "web-clone", "clone site", "clone website", "copy webpage"                                          | `$web-clone`           | Read `~/.agents/skills/web-clone/SKILL.md`, start website cloning pipeline                                                                                  |

Detection rules:

- Keywords are case-insensitive and match anywhere in the user's message
- If one or more explicit `$name` tokens are present, execute **all explicit skills left-to-right**.
- If multiple non-explicit keywords match, use the most specific (longest match).
- Conflict resolution: explicit `$name` invocation overrides keyword detection.
- If user explicitly invokes
  `/prompts:<name>`, treat it as direct prompt execution and do not auto-activate keyword skills unless explicit
  `$name` tokens are also present.
- The rest of the user's message (after keyword extraction) becomes the task description

Ralph / Ralplan execution gate:

- Enforce **ralplan-first** when ralph is active and planning is not complete.
- Planning is complete only after both `.omx/plans/prd-*.md` and `.omx/plans/test-spec-*.md` exist.
- Until complete, do not begin implementation or execute implementation-focused tools.
  </keyword_detection>

---

<skills>
Skills are workflow commands. Invoke via `$name` (e.g., `$ralph`) or browse with `/skills`.

Workflow Skills:

- `autopilot`: Full autonomous execution from idea to working code
- `ralph`: Self-referential persistence loop with verification
- `ultrawork`: Maximum parallelism with parallel agent orchestration
- `visual-verdict`: Structured visual QA verdict loop for screenshot/reference comparisons
- `web-clone`: URL-driven website cloning with visual + functional verification
- `ecomode`: Token-efficient execution using lightweight models
- `team`: N coordinated agents on shared task list
- `swarm`: N coordinated agents on shared task list (compatibility facade over team)
- `ultraqa`: QA cycling -- test, verify, fix, repeat
- `plan`: Strategic planning with optional RALPLAN-DR consensus mode
- `deep-interview`: Socratic deep interview with Ouroboros-inspired mathematical ambiguity gating before execution
- `bluetape4k-patterns`: bluetape4k Kotlin 코드 작성/리뷰 시 사용하는 구현 패턴 모음. 인자 검증, `KLogging`,
  `AtomicFU`, coroutine dispatcher 경계, transaction 경계, Spring Boot 자동 설정 규칙을 우선 참고한다.
-
`ralplan`: Iterative consensus planning with RALPLAN-DR structured deliberation (planner + architect + critic); supports
`--deliberate` for high-risk work

Agent Shortcuts:

- `analyze` -> debugger: Investigation and root-cause analysis
- `deepsearch` -> explore: Thorough codebase search
- `tdd` -> test-engineer: Test-driven development workflow
- `build-fix` -> build-fixer: Build error resolution
- `code-review` -> code-reviewer: Comprehensive code review
- `security-review` -> security-reviewer: Security audit
- `frontend-ui-ux` -> designer: UI component and styling work
- `git-master` -> git-master: Git commit and history management

Utilities:

- `cancel`: Cancel active execution modes
- `note`: Save notes for session persistence
- `doctor`: Diagnose installation issues
- `help`: Usage guidance
- `trace`: Show agent flow timeline
  </skills>

---

<team_compositions>
Common agent workflows for typical scenarios:

Feature Development:
analyst -> planner -> executor -> test-engineer -> quality-reviewer -> verifier

Bug Investigation:
explore + debugger + executor + test-engineer + verifier

Code Review:
style-reviewer + quality-reviewer + api-reviewer + security-reviewer

Product Discovery:
product-manager + ux-researcher + product-analyst + designer

UX Audit:
ux-researcher + information-architect + designer + product-analyst
</team_compositions>

---

<team_pipeline>
Team is the default multi-agent orchestrator. It uses a canonical staged pipeline:

`team-plan -> team-prd -> team-exec -> team-verify -> team-fix (loop)`

Stage transitions:

- `team-plan` -> `team-prd`: planning/decomposition complete
- `team-prd` -> `team-exec`: acceptance criteria and scope are explicit
- `team-exec` -> `team-verify`: all execution tasks reach terminal states
- `team-verify` -> `team-fix` | `complete` | `failed`: verification decides next step
- `team-fix` -> `team-exec` | `team-verify` | `complete` | `failed`: fixes feed back into execution

The `team-fix` loop is bounded by max attempts; exceeding the bound transitions to `failed`. Terminal states:
`complete`, `failed`, `cancelled`. Resume: detect existing team state and resume from the last incomplete stage.
</team_pipeline>

---

<team_model_resolution>
Team/Swarm worker startup currently uses one shared
`agentType` and one shared launch-arg set for all workers in a team run.

For worker model selection, apply this precedence (highest to lowest):

1. Explicit model already present in `OMX_TEAM_WORKER_LAUNCH_ARGS`
2. Inherited leader `--model` (when inheritance is enabled)
3. Injected low-complexity default model: `gpt-5.3-codex-spark` (only when 1+2 are absent and team
   `agentType` is low-complexity)

Model flag normalization contract:

- Accept both `--model <value>` and `--model=<value>`
- Remove duplicates/conflicts
- Emit exactly one final canonical model flag: `--model <value>`
- Preserve unrelated worker launch args
  </team_model_resolution>

---

<verification>
Verify before claiming completion. The goal is evidence-backed confidence, not ceremony.

Sizing guidance:

- Small changes (<5 files, <100 lines): lightweight verifier
- Standard changes: standard verifier
- Large or security/architectural changes (>20 files): thorough verifier

Verification loop: identify what proves the claim, run the verification, read the output, then report with evidence. If verification fails, continue iterating rather than reporting incomplete work.
</verification>

<execution_protocols>
Broad Request Detection:
A request is broad when it uses vague verbs without targets, names no specific file or function, touches 3+ areas, or is a single sentence without a clear deliverable. When detected: explore first, optionally consult architect, then plan.

Parallelization:

- Run 2+ independent tasks in parallel when each takes >30s.
- Run dependent tasks sequentially.
- Use background execution for installs, builds, and tests.
- Prefer Team mode as the primary parallel execution surface. Use ad hoc parallelism only when Team overhead is disproportionate to the task.

Visual iteration gate:

- For visual tasks (reference image(s) + generated screenshot), run
  `$visual-verdict` every iteration before the next edit.
- Persist visual verdict JSON in `.omx/state/{scope}/ralph-progress.json` with both numeric (
  `score`, threshold pass/fail) and qualitative (`reasoning`, `differences`, `suggestions`, `next_actions`) feedback.

Continuation:
Before concluding, confirm: zero pending tasks, all features working, tests passing, zero errors, verification evidence collected. If any item is unchecked, continue working.

Ralph planning gate:
If ralph is active, verify PRD + test spec artifacts exist before any implementation work/tool execution. If missing, stay in planning and create them first (ralplan-first).
</execution_protocols>

<cancellation>
Use the `cancel` skill to end execution modes. This clears state files and stops active loops.

When to cancel:

- All tasks are done and verified: invoke cancel.
- Work is blocked and cannot proceed: explain the blocker, then invoke cancel.
- User says "stop": invoke cancel immediately.

When not to cancel:

- Work is still incomplete: continue working.
- A single subtask failed but others can continue: fix and retry.
  </cancellation>

---

<state_management>
oh-my-codex uses the `.omx/` directory for persistent state:

- `.omx/state/` -- Mode state files (JSON)
- `.omx/notepad.md` -- Session-persistent notes
- `.omx/project-memory.json` -- Cross-session project knowledge
- `.omx/plans/` -- Planning documents
- `.omx/logs/` -- Audit logs

Tools are available via MCP when configured (`omx setup` registers all servers):

State & Memory:

- `state_read`, `state_write`, `state_clear`, `state_list_active`, `state_get_status`
- `project_memory_read`, `project_memory_write`, `project_memory_add_note`, `project_memory_add_directive`
- `notepad_read`, `notepad_write_priority`, `notepad_write_working`, `notepad_write_manual`, `notepad_prune`,
  `notepad_stats`

Code Intelligence:

- `lsp_diagnostics` -- type errors for a single file (tsc --noEmit)
- `lsp_diagnostics_directory` -- project-wide type checking
- `lsp_document_symbols` -- function/class/variable outline for a file
- `lsp_workspace_symbols` -- search symbols by name across the workspace
- `lsp_hover` -- type info at a position (regex-based approximation)
- `lsp_find_references` -- find all references to a symbol (grep-based)
- `lsp_servers` -- list available diagnostic backends
- `ast_grep_search` -- structural code pattern search (requires ast-grep CLI)
- `ast_grep_replace` -- structural code transformation (dryRun=true by default)

Trace:

- `trace_timeline` -- chronological agent turn + mode event timeline
- `trace_summary` -- aggregate statistics (turn counts, timing, token usage)

Mode lifecycle requirements:

- On mode start, call `state_write` with `mode`, `active: true`, `started_at`, and mode-specific fields.
- On phase/iteration transitions, call `state_write` with updated `current_phase` /
  `iteration` and mode-specific progress fields.
- On completion, call `state_write` with `active: false`, terminal `current_phase`, and `completed_at`.
- On cancel/abort cleanup, call `state_clear(mode="<mode>")`.

Recommended mode fields:

- `ralph`: `active`, `iteration`, `max_iterations`, `current_phase`, `started_at`, `completed_at`
- `autopilot`: `active`, `current_phase` (`expansion|planning|execution|qa|validation|complete`), `started_at`,
  `completed_at`
- `ultrawork`: `active`, `reinforcement_count`, `started_at`
- `team`: `active`, `current_phase` (`team-plan|team-prd|team-exec|team-verify|team-fix|complete`), `agent_count`,
  `team_name`
- `ecomode`: `active`
- `ultraqa`: `active`, `current_phase`, `iteration`, `started_at`, `completed_at`
  </state_management>

---

## Repository Build And Layout

This project uses Gradle with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) and is a Kotlin 2.3 + Java 25 + Spring Boot 4 based multi-module experimental repository.

주요 위치:

- 의존성 버전 관리: `buildSrc/src/main/kotlin/Libs.kt`
- 플러그인 버전 관리: `buildSrc/src/main/kotlin/Plugins.kt`
- 모듈 자동 등록: `settings.gradle.kts`의 `includeModules()`
- 공통 빌드 설정: 루트 `build.gradle.kts`

실행 원칙:

- 검증은 항상 대상 모듈 단위로 수행한다.
- 전체 빌드 대신 `./gradlew :<module>:test`, `./gradlew :<module>:build`를 사용한다.
- 필요 시 `./bin/repo-status`, `./bin/repo-diff`, `./bin/repo-test-summary -- ./gradlew :<module>:test`를 우선 활용한다.

## Module And Implementation Notes

### 새 모듈 생성

1. `{baseDir}/{moduleName}/` 디렉토리를 만든다.
2. `build.gradle.kts`를 작성한다.
3. `settings.gradle.kts`는 `includeModules()` 자동 감지를 우선 신뢰하고 불필요한 수정을 피한다.
4. `src/main/kotlin/io/bluetape4k/` 및 `src/test/kotlin/` 패키지 구조를 만든다.
5. `README.md`를 함께 추가한다.

### 모듈 명명

- 모든 카테고리는 대체로 `withBaseDir=false` 패턴을 사용하므로 폴더명이 그대로 모듈명이 된다.
- 예: `infra/cache-lettuce-near/` -> `:cache-lettuce-near`
- 예: `spring-data/exposed-spring-data/` -> `:exposed-spring-data`

### infra 모듈 주의사항

- Fory/LZ4는 optional일 수 있으므로 `Libs.fory_kotlin`, `Libs.lz4_java`의 명시적 추가 필요성을 확인한다.
- Hibernate 7 관련 `DomainDataRegionConfig`, `DomainDataRegionBuildingContext`는 `org.hibernate.cache.cfg.spi` 패키지를 사용한다.
- Hibernate 7 + H2 조합은 `Libs.h2_v2` 사용 여부를 확인한다.

### spring-boot 모듈 주의사항

- `HibernatePropertiesCustomizer`는 Spring Boot 4에서 `org.springframework.boot.hibernate.autoconfigure` 패키지로 이동했다.
- 관련 코드에서는 `compileOnly(Libs.springBoot("hibernate"))` 필요 여부를 확인한다.
- Actuator 클래스 로딩 안전성을 위해 `@ConditionalOnClass(Endpoint::class)`는 클래스 레벨에 둔다.
- `@ConfigurationProperties`의 Map 키에 점(`.`)이 포함되면 대괄호 표기법을 사용한다.

### spring-data 모듈 주의사항

- Exposed `Entity<ID>` 서브클래스에는 `@ExposedEntity`가 필요하다.
- MVC 앱은 `@EnableExposedRepositories`, WebFlux/Coroutine 앱은 `@EnableCoroutineExposedRepositories`를 확인한다.
- DAO 연산은 반드시 `transaction {}` 또는 `withContext(Dispatchers.IO) { transaction {} }` 경계 안에서 수행한다.
- 테스트 초기화는 `SchemaUtils.createMissingTablesAndColumns(...)`와 `deleteAll()` 패턴을 우선 따른다.

## Testing And Coroutine Guidance

- JUnit 5를 기본으로 사용하고, coroutine 테스트는 `runTest`를 우선 사용한다.
- Redis, Kafka 등 인프라 테스트는 bluetape4k-testcontainers 싱글턴 패턴을 우선 따른다.
- `@Testcontainers`보다 기존 모듈의 companion object 기반 싱글턴 컨테이너 패턴을 먼저 확인한다.
- 프로덕션 코드에서 `runBlocking` 사용을 피한다.
- blocking 호출은 그대로 두지 말고 `withContext(Dispatchers.IO)` 등 적절한 dispatcher 경계 안으로 이동한다.
- cancellation, dispatcher 경계, blocking 여부를 코루틴 변경의 기본 점검 항목으로 본다.

## Code Style

- Java 스타일 null 체크보다 Kotlin null-safety 문법을 우선 사용한다.
- 불필요한 구조 개선보다 최소 diff를 지킨다.
- 공개 API를 추가하거나 변경하면 저장소 기존 포맷에 맞춰 한글 KDoc 보강 여부를 확인한다.

## Setup

Run `omx setup` to install all components. Run `omx doctor` to verify installation.
