. (Join-Path $PSScriptRoot 'common.ps1')
Invoke-Compose up -d postgres backend
Invoke-Compose exec -T postgres psql -U signflow -d signflow -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'

