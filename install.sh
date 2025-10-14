#!/bin/bash

#######################################################################
#                                                                     #
#  ClusterForge - Instalação e Inicialização                         #
#                                                                     #
#  Este script:                                                       #
#  - Instala Docker, MySQL, Java                                      #
#  - Configura Docker daemon (storage quotas)                         #
#  - Cria estrutura de diretórios                                     #
#  - Inicia sistema usando binário em build/                          #
#                                                                     #
#######################################################################

set -e

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configurações
MYSQL_USER="clusterforge_user"
MYSQL_PASSWORD="123"
MYSQL_DATABASE="clusterforge"

#######################################################################
# Funções
#######################################################################

print_header() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  $1${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }

check_root() {
    if [ "$EUID" -eq 0 ]; then
        print_error "NÃO execute como root!"
        exit 1
    fi
}

install_docker() {
    if command -v docker &> /dev/null; then
        print_success "Docker já instalado"
        return 0
    fi
    
    echo "Instalando Docker..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq apt-transport-https ca-certificates curl gnupg
    
    sudo mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    sudo apt-get update -qq
    sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo usermod -aG docker $USER
    
    print_success "Docker instalado"
    print_warning "Faça LOGOUT e LOGIN para aplicar permissões"
}

configure_docker() {
    echo "Configurando Docker daemon (storage quotas)..."
    
    [ -f "/etc/docker/daemon.json" ] && sudo cp /etc/docker/daemon.json /etc/docker/daemon.json.backup
    
    sudo tee /etc/docker/daemon.json > /dev/null << 'EOF'
{
  "storage-driver": "overlay2",
  "storage-opts": ["overlay2.override_kernel_check=true"],
  "log-driver": "json-file",
  "log-opts": {"max-size": "10m", "max-file": "3"}
}
EOF
    
    sudo systemctl daemon-reload
    sudo systemctl restart docker
    print_success "Docker configurado"
}

install_mysql() {
    echo "Iniciando MySQL..."
    
    if docker ps | grep -q clusterforge-mysql; then
        print_success "MySQL já rodando"
        return 0
    fi
    
    cd backend
    docker-compose up -d mysql
    sleep 8
    print_success "MySQL iniciado"
    cd ..
}

install_java() {
    if command -v java &> /dev/null; then
        print_success "Java já instalado"
        return 0
    fi
    
    echo "Instalando Java..."
    sudo apt-get install -y -qq openjdk-17-jdk
    print_success "Java instalado"
}

setup_project() {
    echo "Criando estrutura de diretórios..."
    
    mkdir -p backend/data/{scripts,templates,clusters}
    mkdir -p logs
    
    chmod +x backend/data/scripts/*.sh 2>/dev/null || true
    
    print_success "Estrutura criada"
}

start_backend() {
    echo "Iniciando ClusterForge..."
    
    # Verificar se binário existe
    if [ ! -f "build/clusterforge.jar" ]; then
        print_error "Binário não encontrado em build/clusterforge.jar"
        print_warning "Compile o projeto primeiro: cd backend && ./mvnw package"
        exit 1
    fi
    
    # Parar se já estiver rodando
    if [ -f "logs/backend.pid" ]; then
        OLD_PID=$(cat logs/backend.pid)
        if ps -p $OLD_PID > /dev/null 2>&1; then
            print_warning "ClusterForge já está rodando (PID: $OLD_PID)"
            return 0
        fi
    fi
    
    # Iniciar usando o binário
    nohup java -jar build/clusterforge.jar \
        --spring.profiles.active=dev \
        > logs/backend.log 2>&1 &
    
    PID=$!
    echo $PID > logs/backend.pid
    
    print_success "Backend iniciado (PID: $PID)"
    
    # Aguardar inicialização
    echo "Aguardando inicialização..."
    sleep 10
    
    # Verificar se está rodando
    if ps -p $PID > /dev/null 2>&1; then
        print_success "Sistema rodando!"
    else
        print_error "Falha ao iniciar. Ver: logs/backend.log"
        exit 1
    fi
}

#######################################################################
# Main
#######################################################################

clear
print_header "         CLUSTERFORGE - INSTALAÇÃO"

check_root

echo -e "${CYAN}Este script irá:${NC}"
echo "  • Instalar Docker, MySQL, Java"
echo "  • Configurar Docker daemon (storage quotas)"
echo "  • Criar estrutura de diretórios"
echo "  • Iniciar ClusterForge"
echo ""

read -p "Continuar? (s/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Ss]$ ]]; then
    print_warning "Cancelado"
    exit 0
fi

print_header "          INSTALANDO"
install_docker
configure_docker
install_java

print_header "          CONFIGURANDO"
install_mysql
setup_project

print_header "          INICIANDO"
start_backend

# Final
echo ""
print_header "          ✅ CONCLUÍDO"

echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ClusterForge instalado e rodando!                             ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}🌐 Backend API:${NC}"
echo "  http://localhost:8080"
echo ""
echo -e "${CYAN}📋 Gerenciar:${NC}"
echo "  Ver log:  tail -f logs/backend.log"
echo "  Parar:    kill \$(cat logs/backend.pid)"
echo "  Reiniciar: java -jar build/clusterforge.jar"
echo ""
echo -e "${CYAN}📚 Documentação:${NC}"
echo "  README.md"
echo "  Doc/README.md (completa)"
echo ""

if id -nG | grep -qw docker; then
    print_success "Sistema pronto! 🚀"
else
    print_warning "Faça LOGOUT/LOGIN para permissões do Docker"
    echo "  ou execute: newgrp docker"
fi

echo ""
