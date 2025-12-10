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
			--install-app)
				MODE="install-app"
				;;
			*)
				echo "Parâmetro não reconhecido: $1" >&2
				echo "Uso: $0 [--mode socket|tcp-local] [--status] [--install-app]" >&2
				exit 2
				;;
		esac
		shift || true
	done
	case "$MODE" in
		socket|tcp-local|install-app) ;;
		*)
			echo "Valor inválido para --mode: $MODE (use socket|tcp-local|install-app)" >&2
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

ensure_build_dependencies() {
	echo "Verificando dependências de build..."
	
	# Update apt only once if needed
	local apt_updated=0
	ensure_apt_update() {
		if [ $apt_updated -eq 0 ]; then
			if command -v apt-get >/dev/null 2>&1; then
				echo "Atualizando repositórios..."
				apt-get update -y
				apt_updated=1
			fi
		fi
	}

	# 1. Java 21 (JDK)
	if ! command -v javac >/dev/null 2>&1 || ! java -version 2>&1 | grep -q "21"; then
		echo "Java 21 (JDK) não encontrado. Tentando instalar..."
		ensure_apt_update
		if command -v apt-get >/dev/null 2>&1; then
			DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jdk
		else
			echo "Aviso: não foi possível instalar o Java 21. Instale manualmente."
		fi
	else
		echo "Java disponível."
	fi

	# 2. Node.js & npm
	if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
		echo "Node.js/npm não encontrados. Tentando instalar..."
		ensure_apt_update
		if command -v apt-get >/dev/null 2>&1; then
			# Instala dependência para adicionar repositório se necessário
			DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl gnupg
			# Instala Node.js 20.x (LTS)
			mkdir -p /etc/apt/keyrings
			curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
			echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list
			apt-get update -y
			DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs
		else
			echo "Aviso: não foi possível instalar o Node.js. Instale manualmente."
		fi
	else
		echo "Node.js disponível."
	fi
}

ensure_mysql_container() {
 	local CONTAINER_NAME="clusterforge-mysql"
 	local DB_ROOT_PASSWORD="secret"
 	local DB_NAME="clusterforge"
 	local DB_PORT="3306"
	
 	echo "Verificando container MySQL..."
 
 	if [ "$(docker ps -aq -f name=^/${CONTAINER_NAME}$)" ]; then
 		if [ "$(docker ps -aq -f status=exited -f name=^/${CONTAINER_NAME}$)" ]; then
 			echo "Iniciando MySQL existente..."
 			docker start $CONTAINER_NAME
 		else
 			echo "MySQL já está rodando."
 		fi
 	else
 		echo "Criando container MySQL..."
 		docker run -d \
 			--name $CONTAINER_NAME \
 			--restart always \
 			-e MYSQL_ROOT_PASSWORD=$DB_ROOT_PASSWORD \
 			-e MYSQL_DATABASE=$DB_NAME \
 			-p $DB_PORT:3306 \
 			mysql:8.0
 			
 		echo "Aguardando MySQL iniciar..."
 		sleep 10
 		echo "MySQL iniciado em porta $DB_PORT. Senha root: $DB_ROOT_PASSWORD"
 	fi
}

install_app() {
	require_root
	ensure_docker_installed
	ensure_build_dependencies
	
	# Garante que serviço docker está rodando antes de tentar subir o banco
	if ! service_active; then
		systemctl enable --now docker
	fi
	
	ensure_mysql_container
	
	local INSTALL_DIR="/opt/clusterforge"
	local USER_NAME="clusterforge"
	
	echo "== Iniciando Instalação do ClusterForge em $INSTALL_DIR =="

	# 1. Build Backend
	echo "Buildando backend..."
	if [ -f "./mvnw" ]; then # Se estiver na raiz do projeto (assume estrutura monorepo ou backend na raiz?)
        # O usuário está rodando da raiz do projeto? 
        # O script está em scripts/install.sh, então .. é a raiz
		local PROJECT_ROOT="$(dirname "$(dirname "$(realpath "$0")")")"
		cd "$PROJECT_ROOT"
		
		# Build JAR
		if [ -d "backend" ]; then
			cd backend
			./mvnw clean package -DskipTests
			cd ..
		else
			echo "Erro: pasta 'backend' não encontrada em $PROJECT_ROOT"
			exit 1
		fi
    else
         echo "Erro: não foi possível localizar a raiz do projeto."
         exit 1
    fi

	# 2. Build Frontend
	echo "Buildando frontend..."
	if [ -d "frontend" ]; then
		cd frontend
		npm install
		npm run build
		cd ..
	else
		echo "Erro: pasta 'frontend' não encontrada"
		exit 1
	fi

	# 3. Create Directories
	echo "Criando diretórios..."
	if ! id -u "$USER_NAME" >/dev/null 2>&1; then
		useradd -r -s /bin/false "$USER_NAME"
	fi
	
	mkdir -p "$INSTALL_DIR/data/templates"
	mkdir -p "$INSTALL_DIR/data/volumes"
	mkdir -p "$INSTALL_DIR/data/db"
	mkdir -p "$INSTALL_DIR/frontend"

	# 4. Copy Files
	echo "Copiando arquivos..."
	cp backend/target/clusterforge-*.jar "$INSTALL_DIR/clusterforge.jar"
	
	# Copia server.properties e ajusta para servir frontend
	cp backend/server.properties "$INSTALL_DIR/server.properties"
	
	# Ativa o serving de arquivos estáticos externos no server.properties
	if ! grep -q "spring.web.resources.static-locations" "$INSTALL_DIR/server.properties"; then
		echo "" >> "$INSTALL_DIR/server.properties"
		echo "# Servir Frontend Estático" >> "$INSTALL_DIR/server.properties"
		echo "spring.web.resources.static-locations=file:./frontend/" >> "$INSTALL_DIR/server.properties"
	fi
	
	# Configura conexão com o MySQL Container
	echo "Configurando conexão MySQL no server.properties..."
	# Comenta as linhas do H2
	sed -i 's/^spring.datasource.url=jdbc:h2/# spring.datasource.url=jdbc:h2/g' "$INSTALL_DIR/server.properties"
	sed -i 's/^spring.datasource.username=sa/# spring.datasource.username=sa/g' "$INSTALL_DIR/server.properties"
	sed -i 's/^spring.datasource.password=/# spring.datasource.password=/g' "$INSTALL_DIR/server.properties"

	# Adiciona/Descomenta configuração do MySQL
	cat >> "$INSTALL_DIR/server.properties" <<EOF

# --- Configuração Automática do MySQL (via install.sh) ---
spring.datasource.url=jdbc:mysql://localhost:3306/clusterforge?allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username=root
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=update
# ---------------------------------------------------------
EOF

	# Copia templates
	cp -r backend/data/templates/* "$INSTALL_DIR/data/templates/"

	# Copia frontend build (pasta 'out')
	cp -r frontend/out/* "$INSTALL_DIR/frontend/"

	# 5. Set Permissions
	chown -R "$USER_NAME:$USER_NAME" "$INSTALL_DIR"
	chmod 755 "$INSTALL_DIR"
	chmod 644 "$INSTALL_DIR/clusterforge.jar"
	chmod 644 "$INSTALL_DIR/server.properties"

	# 6. Create Service
	echo "Criando serviço systemd..."
	cat > "/etc/systemd/system/clusterforge.service" <<EOF
[Unit]
Description=ClusterForge Server
After=network.target docker.service
Requires=docker.service

[Service]
User=$USER_NAME
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -jar $INSTALL_DIR/clusterforge.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

	systemctl daemon-reload
	echo "Instalação concluída!"
	echo "Para iniciar: systemctl enable --now clusterforge"
	echo "Configurações em: $INSTALL_DIR/server.properties"
	echo "Frontend em: $INSTALL_DIR/frontend"
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
		install-app)
			install_app
			;;
	esac
fi


