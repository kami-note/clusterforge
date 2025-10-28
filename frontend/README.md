# ClusterForge - Frontend

Sistema de gerenciamento de clusters desenvolvido com Next.js 15, React 19 e TypeScript.

## 🚀 Tecnologias

- **Next.js 15** - Framework React
- **React 19** - Biblioteca UI
- **TypeScript** - Tipagem estática
- **Tailwind CSS** - Estilização
- **Radix UI** - Componentes acessíveis
- **Recharts** - Gráficos e visualizações
- **Sonner** - Notificações toast

## 📁 Estrutura do Projeto

```
frontend/
├── src/
│   ├── app/                    # Páginas da aplicação
│   │   ├── admin/              # Painel administrativo
│   │   ├── client/             # Painel do cliente
│   │   ├── auth/               # Autenticação
│   │   └── page.tsx            # Página inicial
│   ├── components/
│   │   ├── admin/              # Componentes admin
│   │   ├── client/             # Componentes cliente
│   │   ├── clusters/           # Gerenciamento de clusters
│   │   ├── layout/             # Componentes de layout
│   │   └── ui/                 # Componentes UI reutilizáveis
│   ├── hooks/                  # Custom hooks
│   └── lib/                    # Utilitários
```

## 🎯 Funcionalidades

### Autenticação
- Login com diferentes tipos de usuário (admin/cliente)
- Redirecionamento automático baseado no tipo
- Gerenciamento de sessão com localStorage

### Dashboard Admin
- Visão geral do sistema
- Gráficos de métricas (usuários, clusters, recursos)
- Atividade recente
- Gerenciamento completo de clusters

### Dashboard Cliente
- Visão dos clusters próprios
- Controle de recursos (start/stop/restart)
- Monitoramento em tempo real
- Criação de novos clusters

### Gerenciamento de Clusters
- Criar novos clusters com templates
- Visualizar detalhes completos
- Console interativo
- Monitoramento de recursos em tempo real
- Logs de aplicação
- Acesso FTP/SFTP
- Controle de banco de dados

## 🏃 Como Executar

### Instalação
```bash
cd frontend
npm install
```

### Desenvolvimento
```bash
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000) no navegador.

### Build de Produção
```bash
npm run build
npm start
```

## 👤 Credenciais de Teste

- **Email Admin**: `admin@example.com`
- **Senha**: qualquer valor

## 📝 Notas

- Dados atualmente armazenados em `localStorage`
- Pronto para integração com backend Spring Boot
- Sistema de rotas protegidas por role
- Dark mode suportado

## 🔗 Integração com Backend

Para conectar com a API backend:
1. Configure a URL da API em `.env.local`
2. Atualize os hooks `useAuth` e `useClusters`
3. Remova os mocks e implemente chamadas reais
