COMPOSE ?= docker compose

.PHONY: up down logs test backend-test frontend-test lint build
up:
	$(COMPOSE) up --build

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
