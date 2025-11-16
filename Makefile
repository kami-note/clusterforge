SHELL := /usr/bin/bash

SCRIPT := /home/levi/Projects/clusterforge-f/scripts/install.sh
DOCKER_DAEMON_JSON := /etc/docker/daemon.json
DOCKER_OVERRIDE := /etc/systemd/system/docker.service.d/override.conf

.PHONY: help status up-socket up-tcp down-tcp restart-docker test test-ping test-integration

help:
	@echo "Targets disponíveis:"
	@echo "  make status        - Mostra diagnóstico atual (Docker/Compose, serviço, overrides, TCP _ping)"
	@echo "  make up-socket     - Configura uso local via socket (recomendado)"
	@echo "  make up-tcp        - Configura API TCP local (127.0.0.1:2375)"
	@echo "  make down-tcp      - Desativa API TCP local (remove override e ajusta daemon.json, com backup)"
	@echo "  make restart-docker- Reinicia o serviço Docker"
	@echo "  make test          - Roda testes unitários do backend (sem ping real)"
	@echo "  make test-ping     - Roda testes com ping real ao Docker (DOCKER_TEST_ALLOW_PING=1)"
	@echo "  make test-integration - Roda todos os testes incluindo integração com Docker (DOCKER_INTEGRATION_TEST=1)"

status:
	sudo bash $(SCRIPT) --status

up-socket:
	sudo bash $(SCRIPT) --mode socket

up-tcp:
	sudo DOCKER_API_HOST=127.0.0.1 DOCKER_API_PORT=2375 bash $(SCRIPT) --mode tcp-local

down-tcp:
	@set -euo pipefail; \
	echo "Desativando API TCP local (com backups)..."; \
	TS="$$(date +%Y%m%d_%H%M%S)"; \
	if [ -f "$(DOCKER_OVERRIDE)" ]; then \
		echo "Removendo override: $(DOCKER_OVERRIDE)"; \
		sudo cp -a "$(DOCKER_OVERRIDE)" "$(DOCKER_OVERRIDE).bak_$${TS}"; \
		sudo rm -f "$(DOCKER_OVERRIDE)"; \
	else \
		echo "Override ausente: $(DOCKER_OVERRIDE)"; \
	fi; \
	if [ -f "$(DOCKER_DAEMON_JSON)" ]; then \
		echo "Backup de $(DOCKER_DAEMON_JSON) -> $(DOCKER_DAEMON_JSON).bak_$${TS}"; \
		sudo cp -a "$(DOCKER_DAEMON_JSON)" "$(DOCKER_DAEMON_JSON).bak_$${TS}"; \
		echo '{ "hosts": ["unix:///var/run/docker.sock"] }' | sudo tee "$(DOCKER_DAEMON_JSON)" >/dev/null; \
		echo "Arquivo atualizado para desabilitar TCP."; \
	else \
		echo "Arquivo $(DOCKER_DAEMON_JSON) não existe; nada a ajustar."; \
	fi; \
	echo "Recarregando systemd e reiniciando Docker..."; \
	sudo systemctl daemon-reload; \
	sudo systemctl restart docker; \
	if systemctl is-active --quiet docker; then echo "Docker ativo."; else echo "Falha ao iniciar Docker" >&2; exit 1; fi; \
	echo "Feito."

restart-docker:
	sudo systemctl daemon-reload
	sudo systemctl restart docker
	@systemctl is-active --quiet docker && echo "Docker ativo." || (echo "Falha ao iniciar Docker" >&2; exit 1)

test:
	@bash -lc 'cd /home/levi/Projects/clusterforge-f/backend && ./mvnw -q -DskipITs test'

test-ping:
	@bash -lc 'cd /home/levi/Projects/clusterforge-f/backend && DOCKER_TEST_ALLOW_PING=1 ./mvnw -q -DskipITs test'

test-integration:
	@bash -lc 'cd /home/levi/Projects/clusterforge-f/backend && DOCKER_INTEGRATION_TEST=1 ./mvnw test'


