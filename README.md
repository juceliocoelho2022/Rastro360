# Rastro360 — Sistema de Rastreabilidade de Produtos

Sistema para acompanhar o ciclo de vida de produtos e volumes, da fabricação à entrega ao cliente.

**Stack:** Java 21, Spring Boot 3.5.5, API REST, Spring JDBC, PostgreSQL, H2 (ambiente local), Flyway, HTML/CSS/JavaScript e Docker Compose.

## Etapas de rastreamento

1. Cadastro de produto (SKU), lote e unidade logística com código de rastreio único.
2. Registro de produção e movimentação interna entre setores.
3. Transferência para a doca de expedição.
4. Vinculação de volumes à remessa e carregamento no caminhão.
5. Registro de saída para transporte.
6. Registro de chegada ao cliente e confirmação de entrega.
7. Consulta interna da linha do tempo e acompanhamento público reduzido.

## Rodar localmente

Requisitos: JDK 21 e Maven 3.9+.

```bash
mvn clean test
mvn spring-boot:run
```

Abra http://localhost:8080 — credenciais **apenas para desenvolvimento local**: `operador` / `dev12345`. O perfil de desenvolvimento usa H2.

## Rodar com PostgreSQL e Docker

```bash
cp .env.example .env
# Edite .env e configure senhas fortes
docker compose up --build -d
```

O Docker Compose expõe o serviço somente em `127.0.0.1:8080`.

## Segurança e status

**MVP 0.1.0 para desenvolvimento local.** Não publicar diretamente na internet com as credenciais de demonstração. Autenticação corporativa individual, RBAC, HTTPS, proteção CSRF, testes de carga e políticas de auditoria/privacidade precisam ser implementados antes de uso produtivo.

> Os testes foram incluídos no projeto, mas ainda precisam ser executados com Maven. Não há confirmação de build aprovado neste ambiente.

## Próximas funcionalidades

Leitura e impressão de QR Code, app para conferência operacional, identificação GS1/SSCC, geolocalização de veículos, comprovante digital de entrega, divergências/devoluções e integração com ERP/WMS/TMS.

**Autor:** Jucelio Coelho.
