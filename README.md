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

**MVP 0.4.0 para desenvolvimento local.** Não publicar diretamente na internet com as credenciais de demonstração. Autenticação corporativa individual, RBAC, HTTPS, proteção CSRF, testes de carga e políticas de auditoria/privacidade precisam ser implementados antes de uso produtivo.

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

## Fotografia de produto (v0.3.0)

O Rastro360 permite **uma foto por produto**, com inclusão já no cadastro ou atualização posterior em **Produção e cadastro → Foto de um produto já cadastrado**. A foto também aparece no **Histórico do produto** e na **etiqueta impressa com QR Code** (formato 100 × 150 mm). Produtos sem foto continuam funcionando normalmente.

- Aceita somente **JPG/JPEG e PNG** de até **5 MB**; a imagem deve ser válida e possuir no máximo 24 megapixels.
- O servidor redimensiona para até 1000 px na maior dimensão, converte a PNG e remove metadados de origem (como EXIF). Não confia no nome do arquivo enviado.
- A imagem é armazenada como `BYTEA` na tabela `product_image` do **mesmo banco de dados que o produto**, e não em pastas relativas ao repositório. Dessa forma ela acompanha o **backup H2** e futuras operações de migração com os dados do produto.
- A evolução é aplicada pelo **Flyway V2__product_image.sql** (cria apenas a nova tabela): **não apaga nem recadastra produtos, lotes, códigos RT-, volumes ou eventos anteriores.**
- O upload e a visualização exigem autenticação operacional; o cliente que recebe o QR continua acessando apenas os dados reduzidos da página pública, **sem receber a fotografia**.

### Endpoints autenticados

```http
POST /api/products/{productId}/image
Content-Type: multipart/form-data
Campo: file (imagem JPG/PNG)

GET /api/products/{productId}/image
Resposta: image/png
```

As duas operações funcionam para qualquer produto previamente cadastrado; um novo upload substitui a foto anterior sem afetar o volume rastreado.

### Backup e verificação após atualizar

A foto está integrada ao banco. Como o backup automático atual é gerado **a cada inicialização**, depois de cadastrar imagens é recomendável reiniciar o servidor para gerar uma nova cópia que também as contenha. Para operações críticas, mantenha backups periódicos externos e valide a restauração.

Após `git pull --ff-only origin main`, execute `mvn clean verify` e reinicie o servidor com `mvn spring-boot:run`, **sem recriar o H2**. O Flyway aplica a nova migração sobre o mesmo banco. Verifique a foto da etiqueta e faça um ciclo de reinicialização para testar persistência.

## Etiqueta profissional para impressão térmica (v0.3.1)

O formato **100 × 150 mm** foi refinado para melhorar a leitura e identificação visual:
- Foto opcional com altura de **37 mm** e largura máxima de **75 mm**, sem recorte ou distorção.
- Cabeçalho Rastro360 e campos de SKU, lote e quantidade agrupados para conferência.
- QR Code preservado, em uma área quadrada de **53 × 53 mm** com fundo branco.
- Código `RT-` impresso abaixo do QR; a URL pública e sua codificação não mudaram.
- A folha usa `@page size: 100mm 150mm; margin: 0`. Elementos de interface não são impressos.

**Como imprimir:** em **Histórico do produto**, abra o volume e clique em **Imprimir etiqueta QR**. Na caixa de impressão selecione papel **100 × 150 mm**, margens **nenhuma**, escala **100%**, sem cabeçalhos/rodapés. Em uma impressora térmica, configure o mesmo tamanho no driver. Use a pré-visualização para verificar cortes; uma prova de impressão física ainda é necessária antes de produção.

A foto pode estar ausente ou indisponível sem impedir a emissão da etiqueta com QR. Esta versão é **apenas apresentação**: não altera tabelas, imagens armazenadas, produtos, volumes, remessas ou histórico de rastreabilidade. Os testes automatizados verificam a estrutura imprimível e continuam validando a decodificação do QR Code.

## Etiqueta com foto à esquerda e QR à direita (v0.3.2)

O layout aprovado da etiqueta de **100 × 150 mm** reserva duas colunas: foto do produto **à esquerda (até 40 × 58 mm)** e QR Code real **à direita (43 × 43 mm)**. A foto mantém a proporção original sem recorte, sendo mais visível para embalagens verticais. SKU, lote, quantidade e o código `RT-` continuam legíveis.

- Para produtos com foto, as duas colunas aparecem lado a lado. Para produtos sem foto, a coluna desaparece e o QR é centralizado automaticamente.
- O QR continua sendo gerado pelo mesmo endpoint e codifica a mesma URL de rastreio público da v0.3.1.
- Na impressão, use papel **100 × 150 mm**, margens zero, escala **100%**, sem cabeçalhos e rodapés.
- Esta atualização modifica apenas HTML/CSS e a identificação da versão: **nenhuma nova migração, alteração de banco, upload ou recadastro é necessário**.

**Validação pendente em ambiente real:** confirmar a pré-visualização do navegador, imprimir uma etiqueta e escaneá-la no celular. Os testes de CI verificam a presença do QR e estrutura da página, mas não substituem prova física de impressão e leitura.

## Consulta pública pelo celular (v0.4.0)

A rota **`/tracking.html?code=RT-...`** foi redesenhada para celular. O cliente pode abrir o QR Code, consultar o status atual, visualizar os eventos públicos de produção/saída/chegada/entrega, inserir manualmente um código ou compartilhar um link externo.

- A página consulta **`GET /api/public/track/{code}` sem credenciais**, com `Cache-Control: no-store` e cabeçalhos contra indexação por buscadores.
- Consulta atualizada **a cada 30 segundos** enquanto a página estiver visível e houver conexão. Também há botão **Atualizar agora** e atualização ao retornar à aba. Após a entrega confirmada, o polling para.
- **Não é rastreio por GPS e não oferece ETA**. O conteúdo só muda quando a operação registra etapas no Rastro360; não são feitas notificações em segundo plano.
- O público vê apenas código RT-, produto, lote, quantidade, status e eventos públicos. Não recebe nome de motorista, endereço, cliente, operador, recebedor ou fotos internas.
- A página trata indisponibilidade de rede e conserva o último resultado válido com aviso.
- **Nenhuma migração de banco**: o backend e as movimentações existentes continuam funcionando.

### Como disponibilizar ao cliente

**Não é possível usar QR Code com `localhost:8081` em outro aparelho.** A v0.4.0 prepara a experiência no celular, **mas não publica o serviço automaticamente**.

Para implantação externa, configure uma instância/reverse proxy com **HTTPS válido e DNS público**, aplicando regras de rede e segurança. A exposição pública deve permitir apenas a página `/tracking.html`, os arquivos necessários ao layout, e a API `/api/public/track/**`; mantenha `/api/products/**`, `/api/units/**`, `/api/shipments/**`, `/label.html`, `/scanner.html` e a área operacional protegidos, sem expor as credenciais de desenvolvimento.

No ambiente onde as etiquetas serão geradas, configure a URL pública **efetivamente acessível**:

```powershell
$env:APP_PUBLIC_BASE_URL="https://rastreio.seudominio.com.br"
$env:PORT="8081"
mvn spring-boot:run
```

O domínio acima é somente **ilustrativo**: é necessário configurar a infraestrutura HTTPS e garantir que o domínio sirva a consulta e sua API antes de usar as etiquetas fora do laboratório.

**Fluxo de prova:** configure e teste a URL pública no navegador anônimo de um celular, confirme uma consulta sem login, gere/reeimprima a etiqueta QR e valide o mesmo link pela câmera. Teste uma transferência, a saída e a entrega: a atualização do cliente deve aparecer em até 30 segundos com a aba aberta, se houver rede. Não publique o perfil dev com usuário `operador` e senha de demonstração.

## Próximas funcionalidades

Operações via scanner com autenticação individual, identificação GS1/SSCC, geolocalização de veículos, comprovante digital de entrega, divergências/devoluções e integração com ERP/WMS/TMS.

**Autor:** Jucelio Coelho.
