# AI 게이트웨이 정책 (Phase 7-1)

CSTalk/ECH에서 **모든 LLM(또는 유사 추론) 호출은 백엔드 게이트웨이를 통해서만** 허용하는 것을 원칙으로 한다. 본 문서는 **코드에 반영된 동작**과 **운영·법무 판단이 필요한 경계**를 구분해 적는다.

## 1. 기본 비유출 (Default deny to public internet LLM)

- **채널·DM·첨부 등 협업 원문**은 **공용 인터넷 상의 LLM API**로 **기본 전송하지 않는다.**
- 기본 설정: `app.ai.allow-external-llm=false` (환경 변수 `AI_ALLOW_EXTERNAL_LLM`로 덮어쓸 수 있음).
- 이 상태에서 `POST /api/ai/gateway/chat` 는 **HTTP 403** (`AI_GATEWAY_BLOCKED`) 로 응답하며, **프롬프트 본문은 감사 로그에 저장하지 않는다.** 감사에는 `purpose`, `promptChars`(길이), `channelId` 등 **메타**만 남긴다.

## 2. 예외(허용) 검토 절차

아래를 **모두** 충족할 때만 `app.ai.allow-external-llm=true` 및 실제 제공자 연동을 검토한다.

1. **데이터 경로**: 온프레미스 추론, 전용 VPC/전용 테넌트, 또는 계약상 **미학습·비보존**이 명시된 엔드포인트.
2. **법무·보안 합의**: DPIA/보존 정책·하위처리자·재전송 금지 조항 등.
3. **최소화**: 가능한 한 **비식별·요약·권한 필터 이후**의 텍스트만 전송.

예외 승인 후에도 **클라이언트·Realtime이 LLM으로 직접 붙지 않도록** 게이트웨이 단일 진입점을 유지한다.

## 3. RAG / 임베딩 경계

- **인덱싱 대상**은 권한 모델과 일치해야 한다(타인이 읽을 수 없는 메시지는 검색·임베딩 대상에서 제외).
- 외부 임베딩 API를 쓰는 경우 **청크 최소화·비식별** 별도 평가; 협업 **원문 전체**를 기본값으로 내보내지 않는다.

## 4. PII 마스킹 (설계 의무)

- 게이트웨이 진입 전(또는 직후) **마스킹 파이프라인**을 둘 것(사번·계좌·주민번호 형식 등).  
- **현재 구현:** `AiGatewayPiiMasker` — 주민등록번호 **유사 형태**(YYMMDD-성별자리+6자리) 및 **16자리 카드 번호 그룹**을 휴리스틱으로 `[REDACTED_*]` 치환. 감사 detail의 `piiRedactions`는 치환 **건수**만 기록한다(마스킹 후 본문은 저장하지 않음).  
- 사번 등 조직 고유 패턴 확장·학습형 NER은 **후속**이며, DPIA 없이 마스킹만으로 충분하다고 보지 않는다.

## 5. 레이트 리밋 (게이트웨이 사번별)

- 기본: 분·시간 창별 호출 상한(`app.ai.chat-max-requests-per-minute`, `app.ai.chat-max-requests-per-hour`). 초과 시 **HTTP 429** (`AI_GATEWAY_RATE_LIMITED`).
- 구현은 프로세스 **인메모리**이며 재시작 시 카운터 초기화; 다중 인스턴스·클러스터에서는 공유 저장소 기반으로 교체 검토.

## 6. (선택) OpenAI 호환 HTTP 제공자

- `app.ai.llm.http-enabled=true` 이고 `base-url`·`api-key`가 비어 있지 않을 때만 게이트웨이가 외부로 **마스킹된 사용자 프롬프트**만 전송한다(`POST {base}/v1/chat/completions`). 비밀은 저장소·환경 변수로만 두고 클라이언트에 노출하지 않는다.

## 7. 구현 참조

| 항목 | 위치 |
| :--- | :--- |
| 정책·한도·LLM HTTP | `app.ai.allow-external-llm`, `app.ai.policy-version`, `app.ai.chat-max-requests-*`, `app.ai.llm.*` (`application.yml`) |
| 엔드포인트 | `GET /api/ai/gateway/status`, `POST /api/ai/gateway/chat` |
| 감사 타입 | `AI_GATEWAY_POLICY_BLOCKED`, `AI_GATEWAY_PROVIDER_NOT_CONFIGURED`, `AI_GATEWAY_LLM_SUCCEEDED`, `AI_GATEWAY_LLM_FAILED` (detail: `citedDistinct`, `piiRedactions` 등) |
| 근거 인용 | `POST /chat` 본문 `citedMessageIds`(최대 20·중복 제거) — 비어 있지 않으면 **반드시** 동일 요청의 `channelId`와 일치하는 메시지여야 함(멤버 검증 포함). |
| 제품 방향 요약 | [COLLABORATION_TOOL_DIRECTION.md](./COLLABORATION_TOOL_DIRECTION.md) §5~§7 |

`allow-external-llm=true` 인 경우에도 **LLM HTTP가 구성되지 않았으면** `POST /chat` 는 **HTTP 501** (`AI_GATEWAY_NOT_CONFIGURED`) 로 응답한다(오동작 전송 방지).
