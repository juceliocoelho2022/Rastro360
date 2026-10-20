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

O perfil `dev` agora utiliza um banco H2 em uma **pasta fixa fora do Git**, que **nunca é criado automaticamente ao iniciar**. Primeiro, escolha como inicializá-lo (com o servidor parado):

```powershell
# Na pasta do projeto. Cria um banco novo vazio APENAS por escolha explícita.
.\\scripts\\initialize-dev-db.ps1 -CreateNew

# Se houver um banco legado COM DADOS, use esta alternativa em vez de -CreateNew:
# .\\scripts\\initialize-dev-db.ps1 -SourceFile "C:\\caminho\\antigo\\rastro360.mv.db"

mvn clean verify
$env:PORT = "8081"
mvn spring-boot:run
```

Abra http://localhost:8081 — credenciais **apenas para desenvolvimento local**: `operador` / `dev12345`.

O arquivo fica em `$HOME\\.rastro360\\data\\rastro360.mv.db` (Windows: `C:\\Users\\SEU_USUARIO\\.rastro360\\data\\rastro360.mv.db`). Pode configurar outro local fixo com `$env:RASTRO360_DB_PATH = "D:/dados/rastro360"` (SEM extensão `.mv.db`) antes de **inicializar e executar** a aplicação. A ausência do arquivo impede o Spring Boot de iniciar, em vez de abrir um banco vazio silenciosamente.

A cada inicialização bem-sucedida no perfil `dev`, o sistema cria um **backup H2 consistente** em `$HOME\\.rastro360\\backups`. É um backup de inicialização; ele NÃO substitui um plano periódico de backup, retenção e teste de restauração.

**Antes de migrar:** não remova os arquivos antigos. Examine a contagem real de dados em cada um; copiar um banco vazio não restaura registros antigos. Interrompa o Spring Boot antes de copiar um `.mv.db`. Se o banco de destino já existir, o script se recusa a sobrescrevê-lo.

Os testes automatizados usam H2 **em memória** separado; não modificam o banco local persistente.

## Rodar com PostgreSQL e Docker

```bash
cp .env.example .env
# Edite .env e configure senhas fortes
docker compose up --build -d
```

O Docker Compose expõe o serviço somente em `127.0.0.1:8080`.

## Segurança e status

**MVP 0.2.1 para desenvolvimento local.** Não publicar diretamente na internet com as credenciais de demonstração. Autenticação corporativa individual, RBAC, HTTPS, proteção CSRF, testes de carga e políticas de auditoria/privacidade precisam ser implementados antes de uso produtivo.

> O pipeline CI executa Maven/Java 21 em cada PR e push para main; consulte o GitHub Actions para o resultado da revisão atual.

## QR Code e etiquetas (v0.2.0)

O volume continua com o mesmo código `RT-` da versão anterior. **Nenhuma migração adicional de banco é necessária.**

- **Gerar e imprimir:** acesse **Histórico do produto**, localize um volume e clique em **Imprimir etiqueta QR**. A etiqueta é formatada para papel 100 × 150 mm e contém produto, SKU, lote, quantidade, código RT- e QR Code.
- **QR PNG autenticado:** `GET /api/units/{trackingCode}/qr.png`. Só gera para códigos existentes e não permite consulta anônima da etiqueta operacional.
- **Consultar pelo QR:** o QR codifica a URL `/tracking.html?code=RT-...` e abre a consulta pública com dados limitados.
- **Leitura com celular:** use **Ler QR Code** no menu lateral ou abra `/scanner.html`. Toque em **Iniciar leitura** para conceder acesso à câmera e abrir o histórico do volume identificado. Em aparelhos incompatíveis, digite o código manualmente.

### URL pública nas etiquetas

Por padrão, a URL inserida no QR usa o domínio e a porta usados para solicitar a imagem. Isso funciona para testes locais, mas `localhost` de um computador **não pode ser aberto em outro aparelho**.

Antes de imprimir etiquetas que serão usadas pelo cliente, configure uma URL HTTPS que ele possa acessar:

```powershell
$env:APP_PUBLIC_BASE_URL="https://rastro.suaempresa.com.br"
$env:PORT="8081"
mvn spring-boot:run
```

O endereço configurado deve servir a página `/tracking.html` e a API pública `/api/public/track/{code}`. Você pode usar o mesmo endereço para gerar QR e consultar histórico. *A URL acima é apenas um exemplo; é necessário publicar o sistema atrás de um proxy HTTPS e configurar domínio/rede antes de usá-la.*

### Requisitos da câmera

O leitor usa **BarcodeDetector** (API nativa, sem envio das imagens a serviços externos) e **getUserMedia**. Ambos dependem de suporte do navegador. Para liberar câmera em um celular, é necessário **HTTPS válido**; `http://localhost` funciona somente no próprio dispositivo. Em HTTP de uma máquina da rede, a câmera geralmente será bloqueada pelo navegador. A entrada manual continuará disponível.

### Testes automatizados

```powershell
mvn clean verify
```

Além de `TrackingFlowTest`, `QrCodeIntegrationTest` valida:
1. PNG válido de 320 × 320 pixels;
2. leitura do QR com o decoder ZXing e o link correto do volume;
3. acesso restrito para gerar QR e abrir páginas operacionais;
4. resposta 404 para códigos inexistentes;
5. manutenção da consulta pública sem login.

### Limitações desta etapa

A leitura no celular é **consultiva**: não registra carregamento, movimentação nem comprovante de entrega automaticamente. Ainda não há RBAC corporativo, gestão de identidades, comunicação HTTPS embutida ou suporte a câmera em todos os navegadores. Para implantação produtiva, esses pontos e a proteção de credenciais precisam ser tratados antes de abrir a aplicação na internet.

## Próximas funcionalidades

Operações via scanner com autenticação individual, identificação GS1/SSCC, geolocalização de veículos, comprovante digital de entrega, divergências/devoluções e integração com ERP/WMS/TMS.

**Autor:** Jucelio Coelho.
