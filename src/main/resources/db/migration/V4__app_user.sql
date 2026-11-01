-- M3.5 — identidade no banco (antes: usuário/senha em application.yml, em texto puro)

CREATE TABLE app_user
(
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT        NOT NULL,
    -- sempre bcrypt; nunca a senha em claro
    password_hash TEXT        NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT app_user_username_unique UNIQUE (username)
);

CREATE TABLE user_profile
(
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role    TEXT   NOT NULL,

    PRIMARY KEY (user_id, role)
);
