package com.bluehour.infra.ai;

import com.bluehour.domain.coach.port.CoachAiPort;
import com.bluehour.domain.studyquiz.session.model.CsQuizDifficulty;
import com.bluehour.domain.studyquiz.session.model.CsQuizQuestionType;
import com.bluehour.domain.studyquiz.session.model.CsQuizTopic;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 모든 AI 어댑터가 공유하는 프롬프트 빌더.
 * 프롬프트 변경 시 이 파일만 수정하면 Gemini/Groq 등 모든 어댑터에 반영된다.
 */
public final class AiPromptBuilder {

    private static final int QUESTIONS_TARGET = 5;

    /**
     * 모든 프롬프트에 공통으로 적용되는 JSON 출력 포맷 규칙.
     * 어댑터의 systemInstruction에 포함되어 AI에게 전달된다.
     */
    public static final String JSON_FORMAT_RULES =
            "- JSON은 한 줄로(minified) 출력하세요. 공백/개행/설명 문장 금지.\n"
            + "- 모든 문자열 값에는 줄바꿈을 넣지 마세요(필요하면 \\n 으로 escape).\n"
            + "- 문자열 값 안에는 큰따옴표(\") 문자를 넣지 마세요(필요하면 괄호나 작은따옴표로 표현).";

    private AiPromptBuilder() {}

    public static String buildCoachAnalysisPrompt(CoachAiPort.CoachContext context) {
        String trackRule = context.technicalTrack()
                ? """
                - 개발 직무입니다. CS 퀴즈 정확도와 기술 지식 학습을 핵심 준비 축으로 분석하세요.
                - 낮은 퀴즈 정확도나 부족한 풀이 수가 있으면 기술 면접 대비 계획에 반영하세요.
                """
                : """
                - 비개발 직무입니다. CS 퀴즈를 핵심 평가 기준으로 쓰지 마세요.
                - 이력서 분석, 지원 현황, 면접 연습, 직무 키워드 정합성, 포트폴리오/경험 정리를 중심으로 분석하세요.
                - 퀴즈 기록이 있더라도 부가 학습 신호로만 짧게 해석하세요.
                """;
        String example = context.technicalTrack()
                ? "{\"score\":68,\"primary\":\"프론트엔드 개발자\",\"strengths\":[\"DS 78%\",\"면접 3회 완료\"],\"gaps\":[\"OS 45% 취약\",\"이력서 분석 공백\"],\"plan\":[{\"d\":1,\"do\":\"OS 퀴즈 10문제\"},{\"d\":2,\"do\":\"이력서 키워드 보강\"},{\"d\":3,\"do\":\"면접 연습 1회\"}],\"today\":\"OS 퀴즈 10문제 — 지금 바로\"}"
                : "{\"score\":68,\"primary\":\"프로덕트 매니저\",\"strengths\":[\"지원 3건\",\"면접 2회 완료\"],\"gaps\":[\"이력서 분석 공백\",\"경험 정리 부족\"],\"plan\":[{\"d\":1,\"do\":\"이력서 핵심 경험 보강\"},{\"d\":2,\"do\":\"지원 현황 3건 정리\"},{\"d\":3,\"do\":\"면접 연습 1회\"}],\"today\":\"이력서 핵심 경험 보강 — 지금 바로\"}";
        return """
                목표직무: %s
                직무군: %s
                기술트랙: %s
                지원: %d건 (%s)
                이력서: %d개, 마지막분석: %s
                면접연습: 완료%d/%d회
                퀴즈정확도: %s / 총%d문제

                위 데이터로 첫 번째 목표직무 중심의 취업 준비 상태를 분석하세요.
                출력은 반드시 아래 JSON 스키마를 정확히 따르세요:
                %s

                규칙:
                - score는 0~100 정수입니다.
                - primary는 targetRoles 중 하나를 선택합니다.
                - strengths와 gaps는 각각 최대 2개, 각 항목 20자 이내입니다.
                - plan은 정확히 3일치이며 d는 1,2,3만 사용합니다.
                - today는 오늘 바로 실행할 1가지 행동만 40자 이내로 씁니다.
                - 직무와 무관한 CS 항목이 있으면 일반 역량 관점으로 해석하세요.
                %s
                """.formatted(
                String.join(", ", context.targetRoles()),
                context.roleCategory(),
                context.technicalTrack(),
                context.totalApplications(),
                formatStatusCounts(context.statusCounts()),
                context.resumeCount(),
                context.daysSinceLastAnalysis() < 0 ? "없음" : context.daysSinceLastAnalysis() + "일전",
                context.interviewCompleted(),
                context.interviewTotal(),
                formatQuizAccuracy(context.quizAccuracy()),
                context.quizTotalAttempts(),
                example,
                trackRule
        );
    }

    private static String formatStatusCounts(Map<String, Integer> statusCounts) {
        if (statusCounts == null || statusCounts.isEmpty()) return "단계 없음";
        return statusCounts.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("단계 없음");
    }

    private static String formatQuizAccuracy(Map<String, Double> quizAccuracy) {
        if (quizAccuracy == null || quizAccuracy.isEmpty()) return "없음";
        return quizAccuracy.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + Math.round(entry.getValue() * 100) + "%")
                .reduce((a, b) -> a + ", " + b)
                .orElse("없음");
    }

    public static String buildQuestionsPrompt(String positionType, String resumeText, String portfolioText, String portfolioUrl, List<String> targetTechnologies) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                아래 입력을 기반으로 실제 면접에서 나올 법한 질문 %d개를 만들어 주세요.

                출력은 반드시 아래 JSON 스키마를 정확히 따르세요:
                {
                  "questions": [
                    {
                      "badge": "질문 분류",
                      "likelihood": 80,
                      "question": "질문 본문",
                      "intention": "출제 의도",
                      "keywords": "키워드1, 키워드2",
                      "modelAnswer": "모범 답안"
                    }
                  ]
                }

                각 필드 설명:
                - badge: 질문 분류(예: 프로젝트 기반, 기술적 난관, 협업/행동, 기술 스택, 아키텍처, 성능/최적화, 운영/장애대응, 채용공고 기술, 포지션 역량 검증)
                - likelihood: 출제 확률(0~100 정수)
                - question: 질문 본문
                - intention: 출제 의도(한두 문장)
                - keywords: 핵심 키워드(쉼표 구분)
                - modelAnswer: 모범 답안(3~7문장)

                [TargetPosition]
                %s

                질문 구성 원칙:
                A) 이력서/포트폴리오 배경이 지원 포지션(%s)과 동일하거나 유사한 경우:
                   - %d개 모두 이력서/포트폴리오 경험 기반 질문으로 구성하세요.
                B) 이력서/포트폴리오 배경이 지원 포지션(%s)과 다른 경우:
                   - 3개: 이력서/포트폴리오 내용을 %s 포지션 관점으로 재해석한 경험 기반 질문
                           (보유 기술/프로젝트 경험을 지원 포지션에서 어떻게 활용/연결할지 묻는 방식)
                   - 2개: %s 포지션 면접에서 모든 지원자에게 공통으로 출제되는 핵심 역량/기술 지식 질문
                           (이력서 근거 없이 생성 가능. badge는 반드시 "포지션 역량 검증" 사용)
                           예: FE 포지션이면 브라우저 렌더링, 상태관리, 번들링 등 FE 핵심 개념

                추가 규칙:
                - 날조/허위 경험 질문 금지: 이력서에 없는 경험을 한 것처럼 묻지 마세요.
                - 질문은 가능한 한 구체적으로(프로젝트/기술/의사결정/성과 검증).
                - [PortfolioText]나 [PortfolioUrl]이 제공된 경우, 포트폴리오 내용에서도 반드시 질문을 생성하세요.
                  - 이력서 + 포트폴리오 모두 있으면: 이력서 기반 최소 2개 + 포트폴리오 기반 최소 1개, 나머지는 교차 구성.
                  - 포트폴리오 기반 질문의 badge에는 '포트폴리오 기반'을 포함해 주세요.
                - 길이 제한을 지켜주세요:
                  - question: 250자 이내
                  - intention: 350자 이내
                  - keywords: 200자 이내
                  - modelAnswer: 500자 이내(단락 1개)
                """.formatted(QUESTIONS_TARGET,
                nullToEmpty(positionType),
                nullToEmpty(positionType),
                QUESTIONS_TARGET,
                nullToEmpty(positionType),
                nullToEmpty(positionType),
                nullToEmpty(positionType)));

        if (targetTechnologies != null && !targetTechnologies.isEmpty()) {
            String techsCsv = String.join(", ", targetTechnologies);
            sb.append("""

                [TargetTechnologies]
                %s

                - 위 기술 스택은 지원 대상 회사의 채용공고에 명시된 요구 기술입니다.
                - 이력서/포트폴리오에서 해당 기술 관련 경험이 있으면, 그 경험을 깊이 파고드는 질문을 우선 생성하세요.
                - 해당 기술 경험이 없더라도, 지원자의 유사 경험과 연결하여 '이 기술을 어떻게 학습/적용할 것인지' 묻는 질문을 1개 이상 포함하세요.
                - 채용공고 기술 관련 질문의 badge에는 '채용공고 기술'을 포함해 주세요.
                """.formatted(techsCsv));
        }

        sb.append("""

                [ResumeText]
                %s

                [PortfolioText]
                %s

                [PortfolioUrl]
                %s
                """.formatted(
                nullToEmpty(resumeText),
                nullToEmpty(portfolioText),
                nullToEmpty(portfolioUrl)
        ));

        return sb.toString();
    }

    public static String buildFeedbackPrompt(String question, String intention, String keywords, String modelAnswer, String answerText) {
        return """
                다음 질문과 사용자 답변을 평가해 주세요.
                출력은 반드시 아래 JSON 스키마를 정확히 따르세요:
                {"strengths":["잘한 점"],"improvements":["개선할 점"],"suggestedAnswer":"개선 예시 답변","followups":["꼬리질문"]}

                각 필드:
                - strengths: 잘한 점(2~5개, 배열)
                - improvements: 개선할 점(2~5개, 배열)
                - suggestedAnswer: 개선 예시 답변(한 단락, 문자열)
                - followups: 추가 꼬리질문(1~3개, 배열)

                기준:
                - 정확성/구체성/근거/깊이/커뮤니케이션을 종합적으로 봅니다.
                - 답변이 모호하면 어떤 정보를 추가해야 하는지 구체적으로 제시합니다.
                - suggestedAnswer는 900자 이내로 작성하세요.

                [Question]
                %s

                [Intention]
                %s

                [Keywords]
                %s

                [ModelAnswer]
                %s

                [UserAnswer]
                %s
                """.formatted(
                nullToEmpty(question),
                nullToEmpty(intention),
                nullToEmpty(keywords),
                nullToEmpty(modelAnswer),
                nullToEmpty(answerText)
        );
    }

    public static String buildCsQuizQuestionsPrompt(Set<CsQuizTopic> topics, CsQuizDifficulty difficulty, CsQuizQuestionType type, int count) {
        return buildCsQuizQuestionsPrompt(topics, difficulty, type, count, List.of());
    }

    public static String buildCsQuizQuestionsPrompt(Set<CsQuizTopic> topics, CsQuizDifficulty difficulty, CsQuizQuestionType type, int count, List<String> existingPrompts) {
        String topicsCsv = topics.stream().map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("");
        String exclusionBlock = "";
        if (existingPrompts != null && !existingPrompts.isEmpty()) {
            String list = existingPrompts.stream()
                    .limit(10)
                    .map(p -> "- " + p)
                    .collect(java.util.stream.Collectors.joining("\n"));
            exclusionBlock = "\n아래 문제들과 완전히 다른 내용·주제로만 생성하세요(중복 금지):\n" + list + "\n";
        }
        return ("""
                아래 조건을 만족하는 CS 퀴즈 문제를 정확히 %d개 생성하세요.
                난이도는 모두 %s, 토픽은 다음 목록 중에서만 선택하세요: %s
                문제 유형은 모두 %s 입니다.

                출력은 반드시 JSON으로만, 아래 스키마를 지키세요:
                {
                  "questions": [
                    {
                      "topic": "OS|NETWORK|DB|SPRING|JAVA|DATA_STRUCTURE|ALGORITHM|ARCHITECTURE|CLOUD",
                      "difficulty": "LOW|MID|HIGH",
                      "type": "MULTIPLE_CHOICE|SHORT_ANSWER",
                      "prompt": "문제 본문",
                      "choices": ["보기1", "보기2", "보기3", "보기4"],
                      "correctChoiceIndex": 0,
                      "referenceAnswer": "정답/해설(짧게)",
                      "rubricKeywords": ["키워드1","키워드2"]
                    }
                  ]
                }

                규칙:
                - topic/difficulty/type은 반드시 조건과 일치해야 합니다.
                - prompt는 220자 이내로 작성하세요.
                - MULTIPLE_CHOICE:
                  - choices는 반드시 4개
                  - correctChoiceIndex는 0~3
                  - referenceAnswer는 350자 이내
                  - rubricKeywords는 빈 배열로
                - SHORT_ANSWER:
                  - choices는 빈 배열
                  - correctChoiceIndex는 -1
                  - referenceAnswer는 550자 이내
                  - rubricKeywords는 3~6개
                """.formatted(count, difficulty.name(), topicsCsv, type.name())) + exclusionBlock;
    }

    public static String buildCsMultipleChoiceFeedbackPrompt(
            CsQuizTopic topic,
            CsQuizDifficulty difficulty,
            String question,
            List<String> choices,
            int correctChoiceIndex,
            int selectedChoiceIndex
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                다음 객관식 문제에 대해, 사용자의 선택이 왜 맞/틀렸는지 설명하고 피드백을 제공하세요.
                출력은 반드시 JSON으로만, 아래 필드를 포함하세요:
                - strengths: 잘한 점(0~3개)
                - improvements: 개선할 점(0~3개)
                - suggestedAnswer: 정답/해설(한 단락, 500자 이내)
                - followups: 추가 꼬리질문(0~2개)

                제약:

                [Topic] %s
                [Difficulty] %s
                [Question] %s
                [Choices]
                """.formatted(topic.name(), difficulty.name(), nullToEmpty(question)));
        for (int i = 0; i < choices.size(); i++) {
            sb.append(i).append(". ").append(choices.get(i)).append("\n");
        }
        sb.append("[CorrectChoiceIndex] ").append(correctChoiceIndex).append("\n");
        sb.append("[SelectedChoiceIndex] ").append(selectedChoiceIndex).append("\n");
        return sb.toString();
    }

    public static String buildCsShortAnswerFeedbackPrompt(
            CsQuizTopic topic,
            CsQuizDifficulty difficulty,
            String question,
            String referenceAnswer,
            List<String> rubricKeywords,
            String userAnswer
    ) {
        String keywords = (rubricKeywords == null || rubricKeywords.isEmpty()) ? "" : String.join(", ", rubricKeywords);
        return """
                다음 주관식 문제에 대해 사용자의 답변을 정성 평가하고 피드백을 제공하세요.
                출력은 반드시 JSON으로만, 아래 필드를 포함하세요:
                - strengths: 잘한 점(0~5개)
                - improvements: 개선할 점(0~5개)
                - suggestedAnswer: 개선 예시 답변(한 단락, 700자 이내)
                - followups: 추가 꼬리질문(0~3개)

                제약:
                - 추측/날조 금지. 모르면 '부족'으로 판단하고 어떤 포인트가 필요한지 제시하세요.

                [Topic] %s
                [Difficulty] %s
                [Question] %s
                [RubricKeywords] %s
                [ReferenceAnswer] %s
                [UserAnswer] %s
                """.formatted(
                topic.name(),
                difficulty.name(),
                nullToEmpty(question),
                nullToEmpty(keywords),
                nullToEmpty(referenceAnswer),
                nullToEmpty(userAnswer)
        );
    }

    private static final int MAX_PREVIOUS_QUESTIONS = 50;

    public static String buildQuestionsPromptWithHistory(String positionType, String resumeText, String portfolioText,
                                                          String portfolioUrl, List<String> targetTechnologies,
                                                          List<String> previousQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildQuestionsPrompt(positionType, resumeText, portfolioText, portfolioUrl, targetTechnologies));

        if (previousQuestions != null && !previousQuestions.isEmpty()) {
            List<String> limited = previousQuestions.size() > MAX_PREVIOUS_QUESTIONS
                    ? previousQuestions.subList(0, MAX_PREVIOUS_QUESTIONS)
                    : previousQuestions;

            sb.append("""

                    [PreviouslyAskedQuestions]
                    아래는 이전에 이미 출제한 질문 목록입니다.
                    이 질문들과 동일하거나 유사한 질문은 절대 생성하지 마세요.
                    대신, 아직 다루지 않은 새로운 관점/주제/기술적 깊이에서 질문을 만들어 주세요.
                    """);
            for (String q : limited) {
                sb.append("- ").append(q).append("\n");
            }
        }

        return sb.toString();
    }

    public static String buildSessionReportPrompt(String sessionData) {
        return """
                아래는 한 면접 연습 세션의 전체 질문-답변-피드백 데이터입니다.
                이 데이터를 종합 분석하여 면접 회고 리포트를 생성하세요.

                출력은 반드시 아래 JSON 스키마를 정확히 따르세요:
                {
                  "executiveSummary": "전반적인 면접 준비 상태 요약 (2~4문장)",
                  "badgeSummaries": [
                    {
                      "badge": "질문 분류명",
                      "summary": "이 유형에 대한 종합 분석 (2~3문장)",
                      "strengths": ["이 유형에서 잘한 점"],
                      "weaknesses": ["이 유형에서 부족한 점"]
                    }
                  ],
                  "repeatedGaps": ["세션 전체에서 반복적으로 나타난 역량 갭/약점"],
                  "topImprovements": [
                    {
                      "title": "개선 포인트 제목",
                      "description": "구체적인 개선 방법 (2~3문장)"
                    }
                  ],
                  "overallScore": 7,
                  "closingAdvice": "다음 면접을 위한 마무리 조언 (2~3문장)"
                }

                각 필드 설명:
                - executiveSummary: 면접 준비 수준에 대한 전반적 평가. 강점과 약점을 균형 있게 언급.
                - badgeSummaries: 질문 유형(badge)별 강점/약점 자연어 요약. 답변이 있는 유형만 포함.
                - repeatedGaps: 여러 질문에 걸쳐 반복적으로 나타난 역량 갭 (2~5개).
                - topImprovements: 다음 면접을 위한 가장 중요한 개선 포인트 3개. 구체적이고 실행 가능해야 함.
                - overallScore: 1~10 정수. 전반적인 면접 준비 수준 점수.
                - closingAdvice: 격려와 함께 다음 단계를 제시하는 마무리 조언.

                규칙:
                - 과장/추측 금지. 제공된 데이터에서만 근거를 잡아주세요.
                - 미답변 질문은 '미답변'으로 명시하되, 점수/분석에서 별도로 다루세요.
                - 길이 제한:
                  - executiveSummary: 500자 이내
                  - badgeSummary.summary: 300자 이내
                  - repeatedGaps 각 항목: 150자 이내
                  - improvement.description: 300자 이내
                  - closingAdvice: 300자 이내

                [SessionData]
                %s
                """.formatted(nullToEmpty(sessionData));
    }

    public static String buildCoachingReportPrompt(String coachingData) {
        return """
                아래는 한 사용자의 여러 면접 연습 세션에서 수집된 종합 데이터입니다.
                다수 세션의 리포트와 통계를 분석하여, 장기적 성장 추이와 맞춤 학습 계획을 포함한 AI 코칭 리포트를 생성하세요.

                출력은 반드시 아래 JSON 스키마를 정확히 따르세요:
                {
                  "overallAssessment": "전반적인 면접 준비 수준 종합 평가 (3~5문장). 전체 세션 데이터를 관통하는 패턴과 수준을 분석.",
                  "growthTrajectory": "성장 궤적 분석 (3~5문장). 초기 세션 대비 최근 세션에서 어떤 변화가 있는지, 점수 추이와 개선/퇴보 영역을 구체적으로 서술.",
                  "persistentStrengths": ["여러 세션에 걸쳐 지속적으로 나타나는 강점 (3~5개)"],
                  "persistentWeaknesses": ["여러 세션에 걸쳐 반복적으로 나타나는 약점 (3~5개)"],
                  "learningPlan": [
                    {
                      "priority": 1,
                      "area": "학습 영역",
                      "action": "구체적인 학습 방법/행동 (2~3문장)",
                      "reason": "이 영역을 우선 개선해야 하는 이유 (1~2문장)"
                    }
                  ],
                  "readinessScore": 7,
                  "nextSteps": "다음 면접 준비를 위한 구체적 제안 (2~4문장)"
                }

                각 필드 설명:
                - overallAssessment: 모든 세션 데이터를 종합한 전반적 면접 준비 수준 평가.
                - growthTrajectory: 시간 순서대로 세션을 비교하여 성장/정체/퇴보 패턴 분석.
                - persistentStrengths: 단일 세션이 아닌, 여러 세션에 걸쳐 반복되는 강점.
                - persistentWeaknesses: 여러 세션에 걸쳐 해결되지 않은 약점.
                - learningPlan: 우선순위별 학습 계획 (3~5개). priority는 1부터 시작. 구체적이고 실행 가능해야 함.
                - readinessScore: 1~10 정수. 현재 면접 준비 완성도.
                - nextSteps: 다음 면접까지 해야 할 구체적인 행동 제안.

                규칙:
                - 과장/추측 금지. 제공된 데이터에서만 근거를 잡아주세요.
                - 세션이 1개뿐이면 growthTrajectory에 '단일 세션이므로 추이 분석 불가'라고 명시하세요.
                - 길이 제한:
                  - overallAssessment: 600자 이내
                  - growthTrajectory: 600자 이내
                  - persistentStrengths/persistentWeaknesses 각 항목: 150자 이내
                  - learningPlan.action: 300자 이내
                  - learningPlan.reason: 200자 이내
                  - nextSteps: 400자 이내

                [CoachingData]
                %s
                """.formatted(nullToEmpty(coachingData));
    }

    public static String buildJdMatchPrompt(String resumeText, String portfolioText, String jdText) {
        return """
                아래 이력서/포트폴리오와 채용공고(JD)를 분석하여 키워드 매칭률과 상세 분석 결과를 반환하세요.

                출력은 반드시 아래 JSON 스키마를 정확히 따르세요:
                {
                  "matchRate": 75,
                  "matchedKeywords": [
                    { "keyword": "Spring Boot", "category": "기술 스택" }
                  ],
                  "missingKeywords": [
                    { "keyword": "Kubernetes", "importance": "HIGH", "suggestion": "Docker 경험을 쿠버네티스 개념으로 확장하는 학습을 권장합니다." }
                  ],
                  "summary": "전반적인 매칭 상태 요약 (3~5문장)",
                  "recommendations": ["구체적인 보완 제안 1", "구체적인 보완 제안 2"]
                }

                각 필드 설명:
                - matchRate: 이력서/포트폴리오 키워드와 JD 요구사항의 매칭률 (0~100 정수)
                - matchedKeywords: 이력서에 존재하고 JD에서도 요구하는 키워드 목록
                  - keyword: 키워드 (기술명, 역량, 자격증 등)
                  - category: 분류 (예: 기술 스택, 언어/프레임워크, 자격증/학력, 경험/역량, 도메인 지식)
                - missingKeywords: JD가 요구하지만 이력서에 없거나 부족한 키워드 목록
                  - keyword: 부족한 키워드
                  - importance: 중요도 (HIGH/MID/LOW)
                  - suggestion: 해당 키워드를 보완하기 위한 구체적 방법 (1~2문장)
                - summary: 매칭 상태 전반에 대한 자연어 요약 (300자 이내)
                - recommendations: 지원 성공률을 높이기 위한 보완 제안 (2~5개 배열, 각 150자 이내)

                분석 규칙:
                - matchRate는 JD 핵심 요구사항 대비 이력서 보유 역량 비율로 계산하세요.
                - 추측/날조 금지: 이력서에 없는 기술을 있다고 판단하지 마세요.
                - [PortfolioText]가 있으면 이력서와 함께 종합 분석하세요.
                - matchedKeywords: 최소 1개, 최대 20개
                - missingKeywords: 최소 0개, 최대 15개

                [ResumeText]
                %s

                [PortfolioText]
                %s

                [JobDescription]
                %s
                """.formatted(
                nullToEmpty(resumeText),
                nullToEmpty(portfolioText),
                nullToEmpty(jdText)
        );
    }

    /**
     * 대화형 면접관 시스템 프롬프트 빌더.
     * conductInterview 메서드에서 사용.
     */
    public static String buildConversationalInterviewSystemPrompt(String positionType, String resumeContext) {
        return """
                당신은 %s 포지션 전문 AI 면접관입니다.
                아래 지원자의 이력서/포트폴리오를 기반으로 대화형 면접을 진행합니다.

                [지원자 이력서/포트폴리오 컨텍스트]
                %s

                [면접 진행 원칙]
                1. 이력서 기반 경험·프로젝트·기술 스택을 중심으로 질문합니다.
                2. 이전 답변에 따라 꼬리 질문 또는 다른 역량 영역으로 전환합니다.
                3. 각 질문은 하나의 명확한 주제에 집중합니다. 복수 질문 금지.
                4. 피드백·점수·평가는 절대 하지 않습니다. 면접 진행만 합니다.
                5. 답변이 짧거나 불충분하면 "구체적으로 말씀해 주시겠어요?" 식의 심화 질문을 합니다.
                6. 충분한 영역을 커버했거나 maxTurns에 도달하면 isComplete=true로 면접을 종료합니다.

                [질문 유형 다양화]
                - 프로젝트 경험 기반: "~프로젝트에서 어떤 역할을 하셨나요?"
                - 기술적 의사결정: "왜 그 기술/아키텍처를 선택했나요?"
                - 문제해결 경험: "가장 어려웠던 기술적 난관과 해결 방법은?"
                - 협업/커뮤니케이션: "팀에서 의견 충돌 시 어떻게 해결했나요?"
                - 성장/학습: "최근 학습한 기술과 실제 적용 경험은?"

                [종료 결정 규칙]
                - isComplete=false: 아직 커버할 역량 영역이 남아있음
                - isComplete=true: 핵심 역량을 충분히 확인했거나 maxTurns에 도달한 경우

                [JSON 포맷 규칙]
                - JSON은 한 줄로(minified) 출력하세요. 공백/개행/설명 문장 금지.
                - 모든 문자열 값에는 줄바꿈을 넣지 마세요.
                - 문자열 값 안에는 큰따옴표(\") 문자를 넣지 마세요.
                """.formatted(nullToEmpty(positionType), nullToEmpty(resumeContext));
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
