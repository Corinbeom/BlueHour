# [PRD] Blue Hour — 취업 준비 올인원 플랫폼

## 1. 프로젝트 개요

- **프로젝트 명:** Blue Hour (DevWeb)
- **목적:** AI 기반 이력서 분석·맞춤 면접 준비, CS 퀴즈, 지원 현황 관리를 하나의 흐름으로 연결하는 취업 준비 플랫폼
- **핵심 가치:**
  - **Context-Aware:** 내 이력서에 특화된 포지션별 실전 면접 질문 생성
  - **Structured Tracking:** 파편화된 지원 정보를 한눈에 관리
  - **Data-Driven Feedback:** CS 문제 풀이·면접 답변에 대한 AI 피드백

---

## 2. 주요 기능 상세

### 2.1. AI 면접 준비 (Resume Analyzer)

**기능:** 이력서/포트폴리오 파일을 사전 업로드하고, 포지션별 맞춤 면접 질문과 AI 답변 피드백을 제공하는 세션 기반 면접 연습 시스템

**상세 요구사항:**
- 이력서·포트폴리오 파일(PDF/Text) 사전 업로드 및 텍스트 파싱
- 파일 업로드 없이 포지션·URL만으로도 세션 생성 가능
- 포지션(BE, FE, iOS, Android, DE, Infra, PM, QA, General) 선택에 따른 프롬프트 전략 분기
- AI 생성 질문 데이터: 질문 텍스트, 출제 의도, 핵심 키워드, 답변 팁
- 질문별 사용자 답변 → AI 피드백 생성 (강점, 개선점, AI 모범 답안, 후속 질문)
- 세션 상태 흐름: `CREATED` → `QUESTIONS_READY` → `COMPLETED`
- 세션 완료 후 종합 리포트 제공 (점수, 역량 갭 분석, 개선 포인트)

**도메인 객체 책임:**
| 객체 | 책임 |
|---|---|
| `Resume` | 사전 업로드된 파일 메타데이터 및 fileType(RESUME/PORTFOLIO) 관리 |
| `StoredFileRef` | 저장 파일 경로·원본명·MIME 타입 Value Object |
| `ResumeSession` | 면접 세션 상태(CREATED/QUESTIONS_READY/COMPLETED) 및 생명주기 관리 |
| `ResumeQuestion` | 단일 면접 질문 + 답변 시도 목록(attempts) 관리 |
| `ResumeAnswerAttempt` | 사용자 답변 텍스트 + AI 피드백 결과 보유 |
| `Feedback` | AI 피드백 값 객체 (강점, 개선점, 모범 답안, 후속 질문) |
| `InterviewQuestion` | 질문·출제 의도·키워드·답변 팁 Value Object |
| `QuestionGenerator` | AI 포트와 협력하여 면접 질문 배치 생성 (domain service) |
| `AnswerFeedbackGenerator` | 사용자 답변 + 질문 컨텍스트 기반 AI 피드백 생성 (domain service) |
| `PositionPromptRegistry` | 포지션별 특화 시스템 프롬프트 레지스트리 |

---

### 2.2. CS 문제 풀이 (Study Quiz)

**기능:** 9개 토픽·3단계 난이도의 문제 은행 기반 퀴즈 세션과 AI 주관식 채점 피드백

**상세 요구사항:**
- 9개 토픽: OS, NETWORK, DB, SPRING, JAVA, DATA_STRUCTURE, ALGORITHM, ARCHITECTURE, CLOUD
- 3단계 난이도: LOW, MID, HIGH
- 문제 유형: 객관식(MULTIPLE_CHOICE) 60% + 주관식(SHORT_ANSWER) 40% 비율
- 세션당 문제 수: 5~10개 선택
- 문제 은행(seed data) 우선 활용 → 부족 시 AI fallback 생성
- 주관식 답변 AI 채점: 핵심 키워드 기반 정오 판별 + 상세 피드백
- 토픽별 정답률 통계 및 레이더 차트 제공

**도메인 객체 책임:**
| 객체 | 책임 |
|---|---|
| `CsQuizSession` | 퀴즈 세션 메타데이터(난이도·토픽·제목) 및 문제 목록 관리 |
| `CsQuizQuestion` | 단일 퀴즈 문제 (MCQ/SA 유형, 사용자 답변, 정오 여부) |
| `CsQuestionBankItem` | 문제 은행 항목 (topic·difficulty·type별 영속 저장) |
| `CsQuizFeedbackGenerator` | 주관식 답변 AI 채점 및 키워드 기반 피드백 생성 |

**인프라:**
| 컴포넌트 | 역할 |
|---|---|
| `CsBankLoaderService` | classpath seed JSON 파싱 → 문제 은행 적재 (조합별 10개 목표) |

---

### 2.3. 지원 현황 관리 (Application Tracker)

**기능:** 채용 지원 이력을 단계별로 관리하고 메모를 기록하는 칸반 기반 트래커

**상세 요구사항:**
- 전형 단계 관리: 서류 지원 → 서류 합격 → 1차 면접 → 최종 합격/불합격 등
- 지원 항목별 메모 작성·수정·삭제
- 지원 현황 요약 통계 (대시보드 연동)

**도메인 객체 책임:**
| 객체 | 책임 |
|---|---|
| `RecruitmentEntry` | 전형 상태·회사명·포지션·지원일 등 지원 정보 관리 |
| `RecruitmentEntryNote` | 지원 항목에 연결된 메모 엔티티 |

---

### 2.4. 인증 (Authentication)

**기능:** OAuth2 소셜 로그인 기반 사용자 인증

**상세 요구사항:**
- Google, Kakao OAuth2 로그인 지원
- 로그인 성공 시 JWT를 httpOnly Cookie로 발급 (유효기간 24h)
- 모든 API는 JWT 검증 후 접근 허용

**도메인 객체 책임:**
| 객체 | 책임 |
|---|---|
| `Member` | 이메일·OAuth 제공자·표시명·프로필 사진 등 사용자 정보 관리 |
| `JwtTokenProvider` | JWT 발급·검증 |
| `DevWebOAuth2UserService` | OAuth2 사용자 정보 처리 및 Member 생성/조회 |

---

## 3. 기술 스택

### Frontend
| 항목 | 선택 |
|---|---|
| 프레임워크 | Next.js 16 (App Router) |
| 언어 | TypeScript 5 |
| UI | Tailwind CSS 4, shadcn/ui |
| 서버 상태 | TanStack Query 5 |
| 런타임 | Node.js 24 |

### Backend
| 항목 | 선택 |
|---|---|
| 프레임워크 | Spring Boot 3.2, Java 17 |
| ORM | Spring Data JPA (Hibernate) |
| 인증 | Spring Security + OAuth2 Client + JJWT |
| AI | Google Gemini 2.5-flash (기본) + Groq (fallback) |
| 문서화 | SpringDoc OpenAPI (Swagger UI) |
| 테스트 | JUnit 5, Mockito, AssertJ |

### 인프라 / 데이터
| 항목 | 선택 |
|---|---|
| DB (dev) | H2 in-memory |
| DB (prod) | PostgreSQL (Neon) |
| 캐싱 | Redis — stats(5분), sessionList(2분), questionBank(1시간) |
| 파일 저장 | 로컬 파일시스템 (dev) / AWS S3 (prod 예정) |
| 배포 | Render (backend), Vercel (frontend) |

---

## 4. 아키텍처

### Backend — Hexagonal (Layered)
```
api/        → REST 컨트롤러 + 요청/응답 DTO
domain/     → 순수 비즈니스 로직 (엔티티, 포트 인터페이스, 도메인 서비스)
infra/      → 포트 구현체 (JPA, Gemini AI, Groq AI, 파일 저장, 텍스트 파싱)
common/     → ApiResponse 래퍼, 전역 예외 처리, AuthUtils
```

- `domain/`은 `infra/`를 알지 못함 (DIP 실현)
- 모든 API 응답: `ApiResponse<T>(success, data, error)` 통일
- Rich Domain Model: 비즈니스 로직은 도메인 엔티티 내부에 위치

### Frontend — Feature-Based
```
src/app/          → Next.js 라우팅 전용
src/features/     → 도메인별 모듈 (api + components + hooks)
src/components/   → 공유 레이아웃 컴포넌트
src/lib/api.ts    → 기본 fetch 래퍼
```

### API 엔드포인트 맵
| 컨트롤러 | 경로 | 역할 |
|---|---|---|
| `ResumeController` | `/api/resumes` | 파일 사전 업로드 CRUD |
| `ResumeSessionController` | `/api/resume/sessions` | 면접 세션 생성·조회·삭제 |
| `ResumeQuestionController` | `/api/resume/questions` | AI 면접 질문 답변·피드백 |
| `CsQuizSessionController` | `/api/cs-quiz/sessions` | CS 퀴즈 세션 관리 |
| `CsQuizQuestionController` | `/api/cs-quiz/questions` | CS 문제 답변·피드백 |
| `RecruitmentEntryController` | `/api/recruitment` | 지원 현황 CRUD |
| `MemberController` | `/api/members` | 사용자 프로필 |
| `AuthController` | `/api/auth` | JWT 발급·갱신 |
