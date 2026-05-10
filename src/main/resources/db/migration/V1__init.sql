-- liveklass-be-assignment :: V1 initial schema
-- 생성 순서는 외래 키 참조 순서를 고려 (FK 제약은 일부 테이블에 부여)

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('CREATOR', 'CLASSMATE'))
);

CREATE TABLE lectures (
    id              BIGSERIAL PRIMARY KEY,
    creator_id      BIGINT NOT NULL REFERENCES users(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    enrolled_count  INTEGER NOT NULL DEFAULT 0 CHECK (enrolled_count >= 0),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT lectures_dates_chk CHECK (start_date <= end_date),
    CONSTRAINT lectures_capacity_chk CHECK (enrolled_count <= capacity)
);

CREATE INDEX idx_lectures_status ON lectures(status);
CREATE INDEX idx_lectures_creator ON lectures(creator_id);

CREATE TABLE payment_intents (
    id               BIGSERIAL PRIMARY KEY,
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    enrollment_id    BIGINT NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_intents_enrollment ON payment_intents(enrollment_id);

CREATE TABLE enrollments (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    lecture_id          BIGINT NOT NULL REFERENCES lectures(id),
    status              VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    applied_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    payment_intent_id   BIGINT REFERENCES payment_intents(id)
);

-- [추가] 부분 UNIQUE 인덱스: 같은 사용자가 같은 강의에 대해 active 신청을 1개만 가질 수 있음
CREATE UNIQUE INDEX uq_enrollments_active
    ON enrollments(user_id, lecture_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_enrollments_lecture_status ON enrollments(lecture_id, status);
CREATE INDEX idx_enrollments_user_status ON enrollments(user_id, status);

CREATE TABLE waitlist_entries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    lecture_id  BIGINT NOT NULL REFERENCES lectures(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_waitlist_user_lecture UNIQUE (user_id, lecture_id)
);

CREATE INDEX idx_waitlist_lecture_created ON waitlist_entries(lecture_id, created_at);
