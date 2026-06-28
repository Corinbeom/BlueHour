CREATE TABLE IF NOT EXISTS culture_fit_sessions (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT       NOT NULL,
    company_name          VARCHAR(120),
    company_culture_text  TEXT         NOT NULL,
    job_description_text  TEXT,
    position_type         VARCHAR(50),
    status                VARCHAR(20)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    completed_at          TIMESTAMP
);

CREATE TABLE IF NOT EXISTS culture_fit_questions (
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT        NOT NULL,
    order_index       INT           NOT NULL,
    badge             VARCHAR(100)  NOT NULL,
    likelihood        INT           NOT NULL,
    question_text     TEXT          NOT NULL,
    intention         TEXT,
    keywords          VARCHAR(500),
    model_answer      TEXT,
    answer_text       TEXT,
    feedback_status   VARCHAR(20)   NOT NULL,
    suggested_answer  TEXT,
    alignment_note    TEXT
);

CREATE TABLE IF NOT EXISTS culture_fit_feedback_strengths (
    question_id BIGINT        NOT NULL,
    strength    VARCHAR(2000),
    idx         INT           NOT NULL
);

CREATE TABLE IF NOT EXISTS culture_fit_feedback_improvements (
    question_id BIGINT        NOT NULL,
    improvement VARCHAR(2000),
    idx         INT           NOT NULL
);

CREATE TABLE IF NOT EXISTS culture_fit_feedback_followups (
    question_id BIGINT        NOT NULL,
    followup    VARCHAR(2000),
    idx         INT           NOT NULL
);
