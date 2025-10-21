#!/bin/bash

# Script de build para ClusterForge
# Este script compila o projeto e gera tanto o JAR quanto o JSON de configuração

echo "🚀 Iniciando build do ClusterForge..."

# Navegar para o diretório backend
cd "$(dirname "$0")/backend"

# Limpar builds anteriores
echo "🧹 Limpando builds anteriores..."
./mvnw clean

# Compilar e gerar JAR
echo "🔨 Compilando projeto e gerando JAR..."
./mvnw package -DskipTests

# Verificar se o build foi bem-sucedido
if [ $? -eq 0 ]; then
    echo "✅ Build concluído com sucesso!"
    
    # Copiar JAR para o diretório build
    echo "📦 Copiando JAR para diretório build..."
    mkdir -p ../build
    cp target/clusterforge-*.jar ../build/clusterforge.jar
    
    # Copiar JSON de configuração se existir
    if [ -f "target/classes/config.json" ]; then
        echo "📋 Copiando JSON de configuração..."
        cp target/classes/config.json ../build/config.json
        echo "📄 Conteúdo do arquivo de configuração:"
        cat ../build/config.json
    else
        echo "⚠️  Arquivo config.json não encontrado em target/classes/"
    fi
    
    echo ""
    echo "🎉 Build finalizado! Arquivos gerados:"
    echo "   📦 JAR: build/clusterforge.jar"
    echo "   📋 Config: build/config.json"
    echo ""
    echo "Para executar o projeto:"
    echo "   java -jar build/clusterforge.jar"
    
else
    echo "❌ Erro durante o build!"
    exit 1
fi
