#!/bin/bash

# Script para executar testes com Docker real
# Uso: ./run-tests-with-docker.sh [test-class-pattern]

set -e

# Verifica se Docker está disponível
if ! docker info > /dev/null 2>&1; then
    echo "Erro: Docker não está disponível ou não está rodando."
    echo "Certifique-se de que o Docker está instalado e o daemon está em execução."
    exit 1
fi

echo "Docker detectado. Executando testes com Docker real..."
echo ""

# Define variáveis de ambiente para habilitar Docker real
export DOCKER_INTEGRATION_TEST=1
export DOCKER_TEST_ALLOW_PING=1

# Executa os testes
if [ -z "$1" ]; then
    # Executa todos os testes
    ./mvnw test
else
    # Executa testes específicos
    ./mvnw test -Dtest="$1"
fi

