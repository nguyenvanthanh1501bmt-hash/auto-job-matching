# TODO: add build and run targets.
# Example targets: up, down, test, build
.PHONY: up down logs backend-run backend-build crawl-mock raw-jobs mongo-shell qdrant-health minio-health

up:
	cp -n .env.example .env || true
	docker compose --env-file .env up -d

down:
	docker compose --env-file .env down

logs:
	docker compose --env-file .env logs -f

backend-build:
	cd backend && mvn clean package -DskipTests

backend-run:
	cd backend && mvn -pl autojob-app -am spring-boot:run -Dspring-boot.run.profiles=local

crawl-mock:
	curl -X POST http://localhost:8080/api/admin/crawlers/mock/run | jq

raw-jobs:
	curl http://localhost:8080/api/raw-jobs | jq

mongo-shell:
	docker compose exec mongo mongosh -u root -p password --authenticationDatabase admin autojob

qdrant-health:
	curl http://localhost:6333/collections | jq

minio-health:
	curl http://localhost:9000/minio/health/live