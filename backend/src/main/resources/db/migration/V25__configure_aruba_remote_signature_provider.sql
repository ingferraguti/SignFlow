insert into signature_providers (
    id, code, name, adapter_type, base_url, authentication_mode, credential_reference,
    supports_visible_signature, supports_multiple_signature, active
) values (
    '77777777-7777-7777-7777-777777777772', 'ARUBA-REMOTE', 'Aruba Firma Remota (ARSS)',
    'ARUBA_ARSS', 'https://arss.demo.firma-automatica.it/ArubaSignService/ArubaSignService', 'USERNAME_OTP',
    'env://SIGNFLOW_ARUBA_USERNAME,SIGNFLOW_ARUBA_PASSWORD', false, false, true
);

insert into signature_accounts (
    id, application_user_id, natural_person_id, signature_provider_id, account_alias,
    provider_username, certificate_alias, display_name, signature_type, qualified, active
) select
    '88888888-8888-8888-8888-888888888882', null, natural_person_id,
    '77777777-7777-7777-7777-777777777772', 'aruba-demo-remote',
    null, 'AS0', 'Aruba Firma Remota demo', 'REMOTE', false, true
from application_users
where id='55555555-5555-5555-5555-555555555552';
