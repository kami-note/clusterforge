#!/bin/bash

# Script de teste para o sistema de métricas do ClusterForge
# Autor: Teste de Métricas Relativas aos Limites

BASE_URL="http://localhost:8080/api"
USERNAME="admin"
PASSWORD="admin"

echo "=========================================="
echo "  TESTE DO SISTEMA DE MÉTRICAS"
echo "=========================================="
echo ""

# Cores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. Fazer login
echo -e "${BLUE}[1/6]${NC} Fazendo login..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo -e "${RED}❌ Erro no login${NC}"
  echo "Resposta: $LOGIN_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✅ Login realizado com sucesso${NC}"
echo ""

# 2. Listar clusters
echo -e "${BLUE}[2/6]${NC} Listando clusters..."
CLUSTERS_RESPONSE=$(curl -s -X GET "$BASE_URL/clusters" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

echo "Resposta: $CLUSTERS_RESPONSE" | jq '.' 2>/dev/null || echo "$CLUSTERS_RESPONSE"
echo ""

# Extrair ID do primeiro cluster (se existir)
CLUSTER_ID=$(echo $CLUSTERS_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$CLUSTER_ID" ]; then
  echo -e "${YELLOW}⚠️  Nenhum cluster encontrado. Criando um cluster de teste...${NC}"
  echo ""
  
  # 3. Criar cluster de teste
  echo -e "${BLUE}[3/6]${NC} Criando cluster de teste..."
  CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/clusters" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "templateName": "webserver-php",
      "baseName": "teste-metricas",
      "cpuLimit": 1.5,
      "memoryLimit": 1024,
      "diskLimit": 10,
      "networkLimit": 50
    }')
  
  echo "Resposta: $CREATE_RESPONSE" | jq '.' 2>/dev/null || echo "$CREATE_RESPONSE"
  CLUSTER_ID=$(echo $CREATE_RESPONSE | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
  
  if [ -z "$CLUSTER_ID" ]; then
    echo -e "${RED}❌ Erro ao criar cluster${NC}"
    exit 1
  fi
  
  echo -e "${GREEN}✅ Cluster criado com ID: $CLUSTER_ID${NC}"
  echo ""
  echo -e "${YELLOW}⏳ Aguardando 10 segundos para o cluster iniciar...${NC}"
  sleep 10
else
  echo -e "${GREEN}✅ Cluster encontrado com ID: $CLUSTER_ID${NC}"
  echo ""
fi

# 4. Verificar status de saúde do cluster
echo -e "${BLUE}[4/6]${NC} Verificando status de saúde do cluster $CLUSTER_ID..."
HEALTH_RESPONSE=$(curl -s -X GET "$BASE_URL/health/clusters/$CLUSTER_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

echo "Status de Saúde:"
echo "$HEALTH_RESPONSE" | jq '.' 2>/dev/null || echo "$HEALTH_RESPONSE"
echo ""

# 5. Obter métricas em tempo real
echo -e "${BLUE}[5/6]${NC} Obtendo métricas em tempo real do cluster $CLUSTER_ID..."
METRICS_RESPONSE=$(curl -s -X GET "$BASE_URL/monitoring/clusters/$CLUSTER_ID/metrics" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json")

echo -e "${GREEN}📊 Métricas Coletadas:${NC}"
echo "$METRICS_RESPONSE" | jq '.' 2>/dev/null || echo "$METRICS_RESPONSE"
echo ""

# Analisar métricas calculadas
echo -e "${BLUE}[6/6]${NC} Analisando cálculos de métricas relativas aos limites..."
echo ""

# Extrair valores das métricas usando jq ou grep
if command -v jq &> /dev/null; then
  CPU_PERCENT=$(echo $METRICS_RESPONSE | jq -r '.cpuUsagePercent // "N/A"')
  CPU_LIMIT=$(echo $METRICS_RESPONSE | jq -r '.cpuLimitCores // "N/A"')
  MEM_USAGE=$(echo $METRICS_RESPONSE | jq -r '.memoryUsageMb // "N/A"')
  MEM_LIMIT=$(echo $METRICS_RESPONSE | jq -r '.memoryLimitMb // "N/A"')
  MEM_PERCENT=$(echo $METRICS_RESPONSE | jq -r '.memoryUsagePercent // "N/A"')
  DISK_LIMIT=$(echo $METRICS_RESPONSE | jq -r '.diskLimitMb // "N/A"')
  NET_LIMIT=$(echo $METRICS_RESPONSE | jq -r '.networkLimitMbps // "N/A"')
else
  CPU_PERCENT=$(echo $METRICS_RESPONSE | grep -o '"cpuUsagePercent":[0-9.]*' | cut -d':' -f2 || echo "N/A")
  CPU_LIMIT=$(echo $METRICS_RESPONSE | grep -o '"cpuLimitCores":[0-9.]*' | cut -d':' -f2 || echo "N/A")
  MEM_USAGE=$(echo $METRICS_RESPONSE | grep -o '"memoryUsageMb":[0-9]*' | cut -d':' -f2 || echo "N/A")
  MEM_LIMIT=$(echo $METRICS_RESPONSE | grep -o '"memoryLimitMb":[0-9]*' | cut -d':' -f2 || echo "N/A")
  MEM_PERCENT=$(echo $METRICS_RESPONSE | grep -o '"memoryUsagePercent":[0-9.]*' | cut -d':' -f2 || echo "N/A")
  DISK_LIMIT=$(echo $METRICS_RESPONSE | grep -o '"diskLimitMb":[0-9]*' | cut -d':' -f2 || echo "N/A")
  NET_LIMIT=$(echo $METRICS_RESPONSE | grep -o '"networkLimitMbps":[0-9]*' | cut -d':' -f2 || echo "N/A")
fi

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}RESUMO DAS MÉTRICAS (RELATIVAS AOS LIMITES DO CONTAINER)${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${GREEN}CPU:${NC}"
echo "  • Uso: ${CPU_PERCENT}% (relativo ao limite do container)"
echo "  • Limite configurado: ${CPU_LIMIT} cores"
echo ""
echo -e "${GREEN}Memória:${NC}"
echo "  • Uso: ${MEM_USAGE} MB"
echo "  • Limite configurado: ${MEM_LIMIT} MB"
if [ "$MEM_PERCENT" != "N/A" ] && [ "$MEM_PERCENT" != "null" ]; then
  echo -e "  • Percentual calculado: ${GREEN}${MEM_PERCENT}%${NC} (relativo ao limite do container)"
  echo ""
  # Validar cálculo
  if [ "$MEM_USAGE" != "N/A" ] && [ "$MEM_LIMIT" != "N/A" ] && [ "$MEM_USAGE" != "null" ] && [ "$MEM_LIMIT" != "null" ]; then
    EXPECTED_PERCENT=$(awk "BEGIN {printf \"%.2f\", ($MEM_USAGE / $MEM_LIMIT) * 100}")
    if [ "$(printf "%.1f" $MEM_PERCENT)" = "$(printf "%.1f" $EXPECTED_PERCENT)" ]; then
      echo -e "  ${GREEN}✅ Validação: Percentual calculado corretamente!${NC}"
    else
      echo -e "  ${RED}❌ Validação: Percentual não corresponde ao esperado${NC}"
      echo -e "     Esperado: ${EXPECTED_PERCENT}%, Obtido: ${MEM_PERCENT}%"
    fi
  fi
else
  echo -e "  ${YELLOW}⚠️  Percentual não calculado ainda${NC}"
fi
echo ""
echo -e "${GREEN}Disco:${NC}"
echo "  • Limite configurado: ${DISK_LIMIT} MB"
echo ""
echo -e "${GREEN}Rede:${NC}"
echo "  • Limite configurado: ${NET_LIMIT} MB/s"
echo ""
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# 6. Verificar métricas históricas (últimas 24h)
echo -e "${BLUE}[EXTRA]${NC} Verificando métricas históricas..."
START_TIME=$(date -u -d '24 hours ago' +"%Y-%m-%dT%H:%M:%S" 2>/dev/null || date -u -v-24H +"%Y-%m-%dT%H:%M:%S" 2>/dev/null || echo "")
END_TIME=$(date -u +"%Y-%m-%dT%H:%M:%S" 2>/dev/null || echo "")

if [ ! -z "$START_TIME" ] && [ ! -z "$END_TIME" ]; then
  HISTORY_RESPONSE=$(curl -s -X GET "$BASE_URL/monitoring/clusters/$CLUSTER_ID/metrics/history?startTime=$START_TIME&endTime=$END_TIME" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json")
  
  HISTORY_COUNT=$(echo $HISTORY_RESPONSE | grep -o '{\|}' | wc -l || echo "0")
  if [ "$HISTORY_COUNT" -gt "2" ]; then
    echo -e "${GREEN}✅ ${HISTORY_COUNT} registros históricos encontrados${NC}"
  else
    echo -e "${YELLOW}⚠️  Nenhum registro histórico disponível ainda${NC}"
  fi
else
  echo -e "${YELLOW}⚠️  Não foi possível obter métricas históricas (formato de data não suportado)${NC}"
fi

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ TESTE CONCLUÍDO${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "Para testar novamente, execute: ./test-metrics.sh"
echo "Para ver logs do backend: tail -f logs/backend.log (se estiver usando install.sh)"
echo ""

