# Blue Hour

> AI 기반 취업 준비 올인원 플랫폼 — 이력서 분석·맞춤 면접 준비·CS 퀴즈·지원 현황 관리를 하나의 흐름으로

[![Backend](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Frontend](https://img.shields.io/badge/Next.js-16-000000?style=flat&logo=next.js&logoColor=white)](https://nextjs.org)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org)

---

## 주요 기능

### AI 면접 준비
이력서·포트폴리오를 업로드하면 포지션에 맞는 면접 질문을 자동 생성하고, 답변에 대한 AI 피드백을 받을 수 있습니다.

- PDF/Text 파일 파싱 후 Gemini 2.5-flash 기반 질문 생성
- 포지션별 특화 프롬프트 (BE, FE, iOS, Android, DE, Infra, PM, QA)
- 답변 제출 시 강점·개선점·AI 모범 답안·후속 질문 제공
- 세션 완료 후 종합 리포트 (점수, 역량 갭 분석)

### CS 문제 풀이
9개 토픽·3단계 난이도의 문제 은행 기반 퀴즈와 주관식 AI 채점을 제공합니다.

- 토픽: OS, DB, Network, Java, Spring, DS, Algorithm, Design Patterns, CS General
- 객관식 60% + 주관식 40% 비율, 세션당 5~10문항
- 문제 은행 우선 활용 + 부족 시 AI 자동 생성 (fallback)
- 토픽별 정답률 통계 및 레이더 차트

### 지원 현황 관리
채용 지원 이력을 전형 단계별로 관리하고 메모를 남길 수 있는 트래커입니다.

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | Next.js 16, React 19, TypeScript 5, Tailwind CSS 4, TanStack Query 5 |
| Backend | Spring Boot 3.2, Java 17, Spring Data JPA, Spring Security |
| AI | Google Gemini 2.5-flash (기본), Groq (fallback) |
| 인증 | OAuth2 (Google, Kakao) + JWT httpOnly Cookie |
| DB | PostgreSQL (Neon) / H2 (dev) |
| 캐싱 | Redis |
| 배포 | Render (backend), Vercel (frontend) |

---

## 아키텍처

### Backend — Hexagonal
```
api/        → REST 컨트롤러 + DTO
domain/     → 비즈니스 로직 (엔티티, 포트, 도메인 서비스)
infra/      → 포트 구현체 (JPA, AI 어댑터, 파일 저장)
common/     → 공통 응답 래퍼, 예외 처리
```

도메인 계층은 인프라에 의존하지 않으며, 포트 인터페이스를 통해 외부 시스템과 통신합니다.

### Frontend — Feature-Based
```
src/app/        → Next.js 라우팅
src/features/   → 도메인별 모듈 (api + components + hooks)
src/components/ → 공유 레이아웃
```

---

## 로컬 실행

### 사전 준비
- Java 17+, Node.js 24+
- `backend/.env.properties` 파일에 환경 변수 설정

```properties
GEMINI_API_KEY=your_key
GOOGLE_CLIENT_ID=your_id
GOOGLE_CLIENT_SECRET=your_secret
KAKAO_CLIENT_ID=your_id
KAKAO_CLIENT_SECRET=your_secret
JWT_SECRET=your_secret_at_least_32_bytes
```

`prod` 프로필에서는 `JWT_SECRET`이 필수이며 UTF-8 기준 32바이트 이상이어야 합니다. 누락되거나 너무 짧으면 서버가 시작되지 않습니다.

### Backend
```bash
cd backend
./gradlew bootRun
# http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### Frontend
```bash
cd frontend
npm install
npm run dev
# http://localhost:3000
```

루트 디렉터리에서는 아래 명령으로 동일하게 프론트엔드 개발 서버를 실행할 수 있습니다.

```bash
npm run dev
```

---

## 프로젝트 구조

```
DevWeb/
├── backend/          # Spring Boot
│   └── src/main/java/com/devweb/
│       ├── api/      # 컨트롤러 + DTO
│       ├── domain/   # 도메인 모델 + 포트
│       ├── infra/    # JPA, AI, 파일 저장
│       └── common/   # 공통 유틸
└── frontend/         # Next.js
    └── src/
        ├── app/      # 라우팅
        ├── features/ # 도메인별 모듈
        └── components/
```
