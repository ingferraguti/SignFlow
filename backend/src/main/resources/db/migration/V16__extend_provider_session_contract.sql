alter table provider_sessions
    add column provider_session_reference varchar(255),
    add column challenge_reference varchar(255),
    add column correlation_id varchar(100);

comment on column provider_sessions.provider_session_reference is
    'Opaque provider-neutral session reference. Never contains credentials.';
comment on column provider_sessions.challenge_reference is
    'Opaque challenge reference. OTP and equivalent secrets are never stored.';
comment on column provider_sessions.correlation_id is
    'Correlation identifier used to trace the provider lifecycle without sensitive payloads.';
