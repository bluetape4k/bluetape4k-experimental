-- ============================================================
-- V1: 예약 시스템 초기 스키마
-- ============================================================

-- 클리닉
CREATE TABLE IF NOT EXISTS scheduling_clinics (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    slot_duration_minutes INTEGER    DEFAULT 30,
    timezone        VARCHAR(50)     DEFAULT 'UTC',
    locale          VARCHAR(20)     DEFAULT 'ko-KR',
    max_concurrent_patients INTEGER DEFAULT 1,
    open_on_holidays BOOLEAN        DEFAULT FALSE
);

-- 운영 시간
CREATE TABLE IF NOT EXISTS scheduling_operating_hours (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id   BIGINT      NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    open_time   TIME        NOT NULL,
    close_time  TIME        NOT NULL,
    is_active   BOOLEAN     DEFAULT TRUE,
    CONSTRAINT fk_operating_hours_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 클리닉 기본 휴게 시간
CREATE TABLE IF NOT EXISTS scheduling_clinic_default_break_times (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id   BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    CONSTRAINT fk_default_break_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 요일별 휴게 시간
CREATE TABLE IF NOT EXISTS scheduling_break_times (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id   BIGINT      NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    CONSTRAINT fk_break_times_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 클리닉 임시 휴진
CREATE TABLE IF NOT EXISTS scheduling_clinic_closures (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id    BIGINT  NOT NULL,
    closure_date DATE    NOT NULL,
    reason       VARCHAR(500),
    is_full_day  BOOLEAN DEFAULT TRUE,
    start_time   TIME,
    end_time     TIME,
    CONSTRAINT fk_closures_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 의사
CREATE TABLE IF NOT EXISTS scheduling_doctors (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id     BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    specialty     VARCHAR(255),
    provider_type VARCHAR(30)  DEFAULT 'DOCTOR',
    max_concurrent_patients INTEGER,
    CONSTRAINT fk_doctors_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 의사 근무 스케줄
CREATE TABLE IF NOT EXISTS scheduling_doctor_schedules (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id   BIGINT      NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id)
        REFERENCES scheduling_doctors(id) ON DELETE CASCADE
);

-- 의사 부재
CREATE TABLE IF NOT EXISTS scheduling_doctor_absences (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id    BIGINT NOT NULL,
    absence_date DATE   NOT NULL,
    start_time   TIME,
    end_time     TIME,
    reason       VARCHAR(500),
    CONSTRAINT fk_absences_doctor FOREIGN KEY (doctor_id)
        REFERENCES scheduling_doctors(id) ON DELETE CASCADE
);

-- 진료 유형
CREATE TABLE IF NOT EXISTS scheduling_treatment_types (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id                BIGINT       NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    category                 VARCHAR(30)  DEFAULT 'TREATMENT',
    default_duration_minutes INTEGER      NOT NULL,
    required_provider_type   VARCHAR(30)  DEFAULT 'DOCTOR',
    consultation_method      VARCHAR(30),
    requires_equipment       BOOLEAN      DEFAULT FALSE,
    max_concurrent_patients  INTEGER,
    CONSTRAINT fk_treatment_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 장비
CREATE TABLE IF NOT EXISTS scheduling_equipments (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id              BIGINT       NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    usage_duration_minutes INTEGER      NOT NULL,
    quantity               INTEGER      DEFAULT 1,
    CONSTRAINT fk_equipments_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 진료-장비 매핑
CREATE TABLE IF NOT EXISTS scheduling_treatment_equipments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    treatment_type_id BIGINT NOT NULL,
    equipment_id      BIGINT NOT NULL,
    CONSTRAINT fk_te_treatment FOREIGN KEY (treatment_type_id)
        REFERENCES scheduling_treatment_types(id) ON DELETE CASCADE,
    CONSTRAINT fk_te_equipment FOREIGN KEY (equipment_id)
        REFERENCES scheduling_equipments(id) ON DELETE CASCADE,
    CONSTRAINT uq_treatment_equipment UNIQUE (treatment_type_id, equipment_id)
);

-- 상담 주제
CREATE TABLE IF NOT EXISTS scheduling_consultation_topics (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id                BIGINT       NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    description              VARCHAR(500),
    default_duration_minutes INTEGER      DEFAULT 30,
    CONSTRAINT fk_topics_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE
);

-- 공휴일
CREATE TABLE IF NOT EXISTS scheduling_holidays (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE         NOT NULL UNIQUE,
    name         VARCHAR(255) NOT NULL,
    recurring    BOOLEAN      DEFAULT FALSE
);

-- 예약
CREATE TABLE IF NOT EXISTS scheduling_appointments (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    clinic_id             BIGINT       NOT NULL,
    doctor_id             BIGINT       NOT NULL,
    treatment_type_id     BIGINT       NOT NULL,
    equipment_id          BIGINT,
    patient_name          VARCHAR(255) NOT NULL,
    patient_phone         VARCHAR(50),
    patient_external_id   VARCHAR(255),
    appointment_date      DATE         NOT NULL,
    start_time            TIME         NOT NULL,
    end_time              TIME         NOT NULL,
    consultation_topic_id BIGINT,
    consultation_method   VARCHAR(30),
    reschedule_from_id    BIGINT,
    status                VARCHAR(30)  DEFAULT 'REQUESTED',
    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_clinic FOREIGN KEY (clinic_id)
        REFERENCES scheduling_clinics(id) ON DELETE CASCADE,
    CONSTRAINT fk_appt_doctor FOREIGN KEY (doctor_id)
        REFERENCES scheduling_doctors(id) ON DELETE CASCADE,
    CONSTRAINT fk_appt_treatment FOREIGN KEY (treatment_type_id)
        REFERENCES scheduling_treatment_types(id) ON DELETE CASCADE,
    CONSTRAINT fk_appt_equipment FOREIGN KEY (equipment_id)
        REFERENCES scheduling_equipments(id) ON DELETE SET NULL,
    CONSTRAINT fk_appt_topic FOREIGN KEY (consultation_topic_id)
        REFERENCES scheduling_consultation_topics(id) ON DELETE SET NULL
);

-- 예약 메모
CREATE TABLE IF NOT EXISTS scheduling_appointment_notes (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT       NOT NULL,
    note_type      VARCHAR(50)  NOT NULL,
    content        TEXT         NOT NULL,
    created_by     VARCHAR(255),
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notes_appt FOREIGN KEY (appointment_id)
        REFERENCES scheduling_appointments(id) ON DELETE CASCADE
);

-- 재배정 후보
CREATE TABLE IF NOT EXISTS scheduling_reschedule_candidates (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_appointment_id  BIGINT  NOT NULL,
    candidate_date           DATE    NOT NULL,
    start_time               TIME    NOT NULL,
    end_time                 TIME    NOT NULL,
    doctor_id                BIGINT  NOT NULL,
    priority                 INTEGER DEFAULT 0,
    selected                 BOOLEAN DEFAULT FALSE,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reschedule_appt FOREIGN KEY (original_appointment_id)
        REFERENCES scheduling_appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_reschedule_doctor FOREIGN KEY (doctor_id)
        REFERENCES scheduling_doctors(id) ON DELETE CASCADE
);

-- 이벤트 로그
CREATE TABLE IF NOT EXISTS scheduling_appointment_event_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    BIGINT       NOT NULL,
    clinic_id    BIGINT       NOT NULL,
    payload_json TEXT         NOT NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 알림 이력
CREATE TABLE IF NOT EXISTS scheduling_notification_history (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT      NOT NULL,
    channel_type   VARCHAR(30) NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    recipient      VARCHAR(255),
    payload_json   TEXT        NOT NULL,
    status         VARCHAR(20) DEFAULT 'SUCCESS',
    error_message  TEXT,
    created_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_appt FOREIGN KEY (appointment_id)
        REFERENCES scheduling_appointments(id) ON DELETE CASCADE
);
