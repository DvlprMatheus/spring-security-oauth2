CREATE TABLE IF NOT EXISTS sso_providers (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    type              VARCHAR(50)  NOT NULL,
    identity_provider VARCHAR(128) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT pk_sso_providers PRIMARY KEY (id),
    CONSTRAINT uk_sso_providers_type UNIQUE (type),
    CONSTRAINT ck_sso_providers_type CHECK (
        type IN ('MICROSOFT', 'GOOGLE', 'FACEBOOK', 'APPLE', 'AMAZON')
    )
);
