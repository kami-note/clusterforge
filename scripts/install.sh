# Install dependencies
# Install docker compose and docker.io

# TODO: Install dependencies
# TODO: Install docker compose and docker.io

# open API of docker compose


#!/usr/bin/env bash
set -euo pipefail

# Configura a Docker Engine local para uso por um orquestrador.
# Modos suportados:
#   - socket (padrão): usa unix:///var/run/docker.sock (mais seguro e simples localmente)
#   - tcp-local: expõe API TCP em 127.0.0.1:2375 (sem TLS, apenas loopback)
# Seleção:
#   - variável DOCKER_API_MODE=socket|tcp-local
#   - ou parâmetro --mode socket|tcp-local
# Requisitos: sistema com systemd, privilégios de root

DOCKER_TCP_HOST="${DOCKER_API_HOST:-127.0.0.1}"
DOCKER_TCP_PORT="${DOCKER_API_PORT:-2375}"
DOCKER_DAEMON_JSON="/etc/docker/daemon.json"
DOCKER_SYSTEMD_DROPIN_DIR="/etc/systemd/system/docker.service.d"
DOCKER_SYSTEMD_OVERRIDE="${DOCKER_SYSTEMD_DROPIN_DIR}/override.conf"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
MODE="${DOCKER_API_MODE:-socket}"

require_root() {
	if [ "$(id -u)" -ne 0 ]; then
		echo "Este script precisa ser executado como root (use sudo)." >&2
		exit 1
	fi
}

ensure_commands() {
	# Utilitários básicos
	for cmd in systemctl grep sed awk; do
		if ! command -v "$cmd" >/dev/null 2>&1; then
			echo "Comando obrigatório não encontrado: $cmd" >&2
			exit 1
		fi
	done
	# curl é usado para validar a API
	if ! command -v curl >/dev/null 2>&1; then
		if command -v apt-get >/dev/null 2>&1; then
			apt-get update -y
			DEBIAN_FRONTEND=noninteractive apt-get install -y curl
		else
			echo "curl não encontrado e gerenciador apt-get indisponível; instale o curl para validação opcional." >&2
		fi
	fi
}

ensure_docker_installed() {
	# Instala Docker Engine e Docker Compose (plugin v2) em sistemas baseados em apt
	if command -v docker >/dev/null 2>&1 && docker --version >/dev/null 2>&1; then
		:
	else
		if command -v apt-get >/dev/null 2>&1; then
			apt-get update -y
			DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io
		else
			echo "Gerenciador apt-get indisponível. Instale o Docker manualmente e rode novamente." >&2
			exit 1
		fi
	fi
	# Compose v2 como plugin
	if docker compose version >/dev/null 2>&1; then
		:
	else
		if command -v apt-get >/dev/null 2>&1; then
			DEBIAN_FRONTEND=noninteractive apt-get install -y docker-compose-plugin
			if ! docker compose version >/dev/null 2>&1; then
				echo "Falha ao disponibilizar 'docker compose'. Verifique a instalação do plugin." >&2
				exit 1
			fi
		else
			echo "Não foi possível instalar docker compose (plugin). Instale manualmente e tente novamente." >&2
			exit 1
		fi
	fi
	echo "Docker instalado: $(docker --version 2>/dev/null || echo 'desconhecido')"
	echo "Compose instalado: $(docker compose version 2>/dev/null || echo 'desconhecido')"
}

backup_file_if_exists() {
	local file_path="$1"
	if [ -f "$file_path" ]; then
		cp -a "$file_path" "${file_path}.bak_${TIMESTAMP}"
		echo "Backup criado: ${file_path}.bak_${TIMESTAMP}"
	fi
}

print_kv() {
	printf "%-32s %s\n" "$1" "$2"
}

user_in_group() {
	local user="$1" group="$2"
	id -nG "$user" 2>/dev/null | tr ' ' '\n' | grep -qx "$group"
}

has_systemd_override() {
	[ -f "$DOCKER_SYSTEMD_OVERRIDE" ]
}

has_daemon_tcp_host() {
	local host_tcp="tcp://${DOCKER_TCP_HOST}:${DOCKER_TCP_PORT}"
	[ -f "$DOCKER_DAEMON_JSON" ] && grep -q "\"$host_tcp\"" "$DOCKER_DAEMON_JSON"
}

service_active() {
	systemctl is-active --quiet docker
}

api_ping_ok() {
	local url="http://${DOCKER_TCP_HOST}:${DOCKER_TCP_PORT}/_ping"
	command -v curl >/dev/null 2>&1 && [ "$(curl -sS --max-time 2 "$url" || true)" = "OK" ]
}

print_status() {
	echo "== Diagnóstico Docker =="
	print_kv "docker disponível" "$(command -v docker >/dev/null 2>&1 && echo OK || echo NAO)"
	print_kv "docker compose disponível" "$(docker compose version >/dev/null 2>&1 && echo OK || echo NAO)"
	print_kv "serviço docker ativo" "$(service_active && echo OK || echo NAO)"

	local target_user="${SUDO_USER:-${USER:-}}"
	if [ -n "$target_user" ]; then
		print_kv "usuário no grupo docker" "$(user_in_group "$target_user" docker && echo OK || echo NAO)"
	else
		print_kv "usuário detectado" "indisponível"
	fi

	print_kv "override systemd" "$(has_systemd_override && echo PRESENTE || echo AUSENTE)"
	print_kv "daemon.json tem tcp" "$(has_daemon_tcp_host && echo SIM || echo NAO)"
	print_kv "API TCP _ping (127.0.0.1:${DOCKER_TCP_PORT})" "$(api_ping_ok && echo OK || echo FALHA)"
	echo "Modo desejado: $MODE"
}

parse_args() {
	while [ $# -gt 0 ]; do
		case "$1" in
			--mode)
				shift
				MODE="${1:-socket}"
				;;
			--status)
				STATUS_ONLY=1
				;;
			*)
				echo "Parâmetro não reconhecido: $1" >&2
				echo "Uso: $0 [--mode socket|tcp-local] [--status]" >&2
				exit 2
				;;
		esac
		shift || true
	done
	case "$MODE" in
		socket|tcp-local) ;;
		*)
			echo "Valor inválido para --mode: $MODE (use socket|tcp-local)" >&2
			exit 2
			;;
	esac
}

create_systemd_override() {
	mkdir -p "$DOCKER_SYSTEMD_DROPIN_DIR"
	# Override para remover ExecStart padrão (-H fd://) e deixar o dockerd ler o daemon.json
	cat >/tmp/docker_override.conf <<'EOF'
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd
EOF
	# Apenas atualiza quando houver mudança
	if [ ! -f "$DOCKER_SYSTEMD_OVERRIDE" ] || ! diff -q /tmp/docker_override.conf "$DOCKER_SYSTEMD_OVERRIDE" >/dev/null 2>&1; then
		backup_file_if_exists "$DOCKER_SYSTEMD_OVERRIDE"
		mv /tmp/docker_override.conf "$DOCKER_SYSTEMD_OVERRIDE"
		echo "Systemd override atualizado em: $DOCKER_SYSTEMD_OVERRIDE"
	else
		rm -f /tmp/docker_override.conf
	fi
}

configure_daemon_json_hosts() {
	local host_tcp="tcp://${DOCKER_TCP_HOST}:${DOCKER_TCP_PORT}"
	local host_unix="unix:///var/run/docker.sock"

	# Escreve um daemon.json mínimo focado em 'hosts'
	cat >/tmp/daemon.json <<EOF
{
  "hosts": ["$host_unix", "$host_tcp"]
}
EOF

	# Aplica apenas se necessário
	if [ -f "$DOCKER_DAEMON_JSON" ]; then
		# Verifica se já contém o host TCP desejado
		if grep -q "\"$host_tcp\"" "$DOCKER_DAEMON_JSON"; then
			echo "daemon.json já contém o host $host_tcp; nenhuma alteração necessária."
			rm -f /tmp/daemon.json
			return
		fi
		backup_file_if_exists "$DOCKER_DAEMON_JSON"
	fi

	mv /tmp/daemon.json "$DOCKER_DAEMON_JSON"
	chmod 0644 "$DOCKER_DAEMON_JSON"
	echo "Atualizado: $DOCKER_DAEMON_JSON"
}

restart_docker() {
	systemctl daemon-reload
	systemctl enable --now docker
	systemctl restart docker
	systemctl is-active --quiet docker && echo "Docker service ativo."
}

validate_api() {
	local url="http://${DOCKER_TCP_HOST}:${DOCKER_TCP_PORT}/_ping"
	if command -v curl >/dev/null 2>&1; then
		# Espera breve para o daemon subir
		sleep 1
		set +e
		local resp
		resp="$(curl -sS --max-time 3 "$url" || true)"
		set -e
		if [ "$resp" = "OK" ]; then
			echo "Validação OK: Docker API responde em $url"
		else
			echo "Aviso: não foi possível validar a API em $url. Resposta: ${resp:-<vazia>}" >&2
			echo "Dicas: verifique firewall local, logs com 'journalctl -u docker', e o conteúdo de $DOCKER_DAEMON_JSON" >&2
		fi
	else
		echo "Validação pulada (curl ausente). A API deve estar em http://${DOCKER_TCP_HOST}:${DOCKER_TCP_PORT}/"
	fi
}

configure_docker_socket_local() {
	require_root
	ensure_commands
	ensure_docker_installed

	# Checagem idempotente: serviço ativo e usuário no grupo docker
	local target_user="${SUDO_USER:-${USER:-}}"
	if service_active && [ -n "$target_user" ] && user_in_group "$target_user" docker; then
		echo "Nada a fazer: Docker ativo e usuário '$target_user' já no grupo docker."
		return 0
	fi

	# Para modo socket, não precisamos forçar 'hosts' no daemon.json,
	# apenas garantir que o serviço esteja ativo e o usuário pertença ao grupo docker.
	systemctl enable --now docker
	systemctl is-active --quiet docker && echo "Docker service ativo (socket)."

	# Gerenciar grupo docker
	if getent group docker >/dev/null 2>&1; then
		:
	else
		groupadd docker
	fi

	# Escolhe usuário chamador para adicionar ao grupo (prioriza SUDO_USER)
	local target_user="${SUDO_USER:-${USER:-}}"
	if [ -n "$target_user" ]; then
		usermod -aG docker "$target_user" || true
		echo "Usuário '$target_user' adicionado ao grupo 'docker' (logout/login pode ser necessário)."
	else
		echo "Aviso: não foi possível determinar usuário para adicionar ao grupo docker." >&2
	fi

	# Validação básica via socket
	if command -v docker >/dev/null 2>&1; then
		set +e
		if [ -n "$target_user" ]; then
			sudo -u "$target_user" docker version >/dev/null 2>&1
		else
			docker version >/dev/null 2>&1
		fi
		local ec=$?
		set -e
		if [ $ec -eq 0 ]; then
			echo "Validação OK: docker via socket funcional."
		else
			echo "Nota: pode ser necessário relogar para aplicar a associação ao grupo docker." >&2
		fi
	else
		echo "docker CLI não encontrado; instale docker.io/docker-ce para validação via socket." >&2
	fi
	echo "Modo 'socket' configurado (unix:///var/run/docker.sock)."
}

configure_docker_api_tcp_local() {
	require_root
	ensure_commands
	ensure_docker_installed

	# Checagem idempotente: override presente, daemon.json com tcp e ping OK
	if has_systemd_override && has_daemon_tcp_host && api_ping_ok; then
		echo "Nada a fazer: API TCP local já configurada e respondendo."
		return 0
	fi

	create_systemd_override
	configure_daemon_json_hosts
	restart_docker
	validate_api
	echo "Docker Engine API habilitada em http://${DOCKER_TCP_HOST}:${DOCKER_TCP_PORT} (loopback)."
	echo "Nota de segurança: porta acessível apenas localmente. Para acesso remoto, use TLS em 2376."
}

# Executa somente se chamado diretamente (não em source)
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
	parse_args "$@"
	if [ "${STATUS_ONLY:-0}" -eq 1 ]; then
		print_status
		exit 0
	fi
	case "$MODE" in
		socket)
			configure_docker_socket_local
			;;
		tcp-local)
			configure_docker_api_tcp_local
			;;
	esac
fi


