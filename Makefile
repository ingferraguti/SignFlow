COMPOSE ?= docker compose -f compose.yaml
ifneq ($(wildcard .local/compose.tls.yaml),)
COMPOSE += -f .local/compose.tls.yaml
endif

.PHONY: up down logs test backend-test frontend-test lint build
up:
	$(COMPOSE) up --build -d

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

test: backend-test frontend-test

backend-test:
	cd backend && mvn test

frontend-test:
	cd frontend && npm install && npm run lint && npm run build

lint:
	cd frontend && npm install && npm run lint

build:
	$(COMPOSE) build
