#!/bin/bash

# Script para executar testes de integração do ClusterForge
# Uso: ./run-integration-tests.sh [tipo]
# Tipos: unit, integration, all, testcontainers

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Função para print colorido
print_color() {
    printf "${1}${2}${NC}\n"
}

# Função para mostrar ajuda
show_help() {
    print_color $BLUE "ClusterForge - Script de Testes de Integração"
    echo ""
    echo "Uso: $0 [tipo]"
    echo ""
    echo "Tipos de teste:"
    echo "  unit         - Executa apenas testes unitários (rápidos)"
    echo "  integration  - Executa testes de integração com MockMvc"
    echo "  testcontainers - Executa testes com TestContainers"
    echo "  all          - Executa todos os testes (unit + integration)"
    echo "  limits       - Executa teste de limites com Docker real"
    echo "  clean        - Limpa target e executa todos os testes"
    echo ""
    echo "Exemplos:"
    echo "  $0 unit"
    echo "  $0 integration"
    echo "  $0 all"
    echo ""
}

# Função para verificar pré-requisitos
check_prerequisites() {
    print_color $BLUE "Verificando pré-requisitos..."
    
    # Verificar se Maven está instalado
    if ! command -v mvn &> /dev/null; then
        print_color $RED "❌ Maven não encontrado. Instale o Maven primeiro."
        exit 1
    fi
    
    # Verificar se Java está instalado
    if ! command -v java &> /dev/null; then
        print_color $RED "❌ Java não encontrado. Instale o Java primeiro."
        exit 1
    fi
    
    print_color $GREEN "✅ Pré-requisitos verificados"
}

# Função para executar testes unitários
run_unit_tests() {
    print_color $BLUE "🧪 Executando testes unitários..."
    mvn test -Dtest="*Test,!*IntegrationTest,!*IT"
    print_color $GREEN "✅ Testes unitários concluídos"
}

# Função para executar testes de integração
run_integration_tests() {
    print_color $BLUE "🔗 Executando testes de integração..."
    mvn verify -Dtest="*IntegrationTest,!*TestContainersIntegrationTest,!*LimitsIntegrationTest"
    print_color $GREEN "✅ Testes de integração concluídos"
}

# Função para executar testes com TestContainers
run_testcontainers_tests() {
    print_color $BLUE "🐳 Executando testes com TestContainers..."
    
    # Verificar se Docker está rodando
    if ! docker info &> /dev/null; then
        print_color $YELLOW "⚠️  Docker não está rodando. Iniciando Docker..."
        if ! sudo systemctl start docker &> /dev/null; then
            print_color $RED "❌ Não foi possível iniciar o Docker"
            exit 1
        fi
    fi
    
    # Habilitar reutilização de containers para performance
    export TESTCONTAINERS_REUSE_ENABLE=true
    
    mvn verify -Dtest="*TestContainersIntegrationTest"
    print_color $GREEN "✅ Testes com TestContainers concluídos"
}

# Função para executar teste de limites
run_limits_test() {
    print_color $BLUE "🚀 Executando teste de limites com Docker real..."
    
    # Verificar se Docker está rodando
    if ! docker info &> /dev/null; then
        print_color $RED "❌ Docker não está rodando. Inicie o Docker primeiro."
        exit 1
    fi
    
    mvn verify -Dtest="ClusterLimitsIntegrationTest"
    print_color $GREEN "✅ Teste de limites concluído"
}

# Função para executar todos os testes
run_all_tests() {
    print_color $BLUE "🚀 Executando todos os testes..."
    
    # Executar testes unitários primeiro
    run_unit_tests
    
    # Executar testes de integração
    run_integration_tests
    
    # Executar testes com TestContainers
    run_testcontainers_tests
    
    print_color $GREEN "✅ Todos os testes concluídos"
}

# Função para limpar e executar todos os testes
run_clean_tests() {
    print_color $BLUE "🧹 Limpando e executando todos os testes..."
    mvn clean verify
    print_color $GREEN "✅ Limpeza e testes concluídos"
}

# Função principal
main() {
    # Verificar argumentos
    if [ $# -eq 0 ]; then
        show_help
        exit 1
    fi
    
    # Verificar pré-requisitos
    check_prerequisites
    
    # Executar comando baseado no argumento
    case $1 in
        "unit")
            run_unit_tests
            ;;
        "integration")
            run_integration_tests
            ;;
        "testcontainers")
            run_testcontainers_tests
            ;;
        "limits")
            run_limits_test
            ;;
        "all")
            run_all_tests
            ;;
        "clean")
            run_clean_tests
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            print_color $RED "❌ Tipo de teste inválido: $1"
            show_help
            exit 1
            ;;
    esac
}

# Executar função principal com todos os argumentos
main "$@"
