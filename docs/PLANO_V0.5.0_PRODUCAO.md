# Rastro360 — Plano de evolução v0.5.0 (pré-produção)

**Status:** PROPOSTA PARA APROVAÇÃO — planejamento, NÃO implantação.
**Última atualização:** 21/10/2026.
**Escopo:** habilitar pilotos com clientes reais sem depender de Windows local ou de endereços trycloudflare.com efêmeros.

## 1. Estado atual confirmado no repositório

- Java 21 / Spring Boot 3.5.5; Spring JDBC; Flyway; API REST; painel HTML/JS.
- Desenvolvimento: H2 persistente no diretório de usuário, com backup ZIP consistente a cada inicialização. Isto NÃO constitui política de backup contínuo.
- Já existem V1__schema.sql e V2__product_image.sql: produto, local, remessa, volume, histórico de eventos e imagem em BYTEA.
- Docker Compose já define PostgreSQL 16-alpine com volume nomeado e aplicação no perfil postgres. Isso não significa que os dados existentes do H2 já foram migrados.
- Portal público para celular já validado em teste, através de gateway restrito em 127.0.0.1:8090 e Quick Tunnel temporário; código RT e consulta pública operacionais.
- Arquitetura operacional atual: HTTP Basic com um usuário na memória e CSRF desativado. Não está pronta para exposição direta na internet.
- CI atual: Maven/Java 21 + validação da sintaxe PowerShell. Ainda precisa de testes PostgreSQL reais, migração e segurança E2E.

**Regra permanente:** não executar scripts de inicialização de H2 vazio, não apagar banco/backup antigos, não substituir a base operacional por uma base vazia. Migração somente com inventário, backup, restore e comparação.

## 2. Arquitetura-alvo recomendada

Cliente com QR impresso → https://rastreio.DOMINIO.com.br → proxy/gateway com allowlist GET → API pública Spring Boot (somente código, produto, lote, quantidade, estado e eventos permitidos).

Operadores autenticados → https://app.DOMINIO.com.br → acesso protegido por identidade (MFA e papéis) → aplicação administrativa Spring Boot.

Spring Boot → PostgreSQL persistente não exposto à internet → backups automáticos cifrados, retenção e teste de restauração.

- Recomendar hospedagem confiável 24/7: PaaS com app/container e PostgreSQL gerenciado, ou VPS com operações sob responsabilidade explícita. A escolha final depende de orçamento, volume e necessidade de administração.
- DNS e TLS de hostname estável. Cloudflare Tunnel nomeado pode ser usado com domínio sob controle do projeto, mas nunca Quick Tunnel para etiquetas definitivas.
- Aplicação e banco em rede privada; somente endpoints estritamente necessários publicados. Postgres sem porta pública.
- Separar logicamente o acesso ao portal público e ao administrativo. Falha na proteção de um não deve expor o outro.
- O nome dos domínios acima é um exemplo de arquitetura: nenhuma propriedade ou disponibilidade de domínio foi verificada.

## 3. Critérios de aceite da versão v0.5.0

**M0 — Inventário e decisões**
- Definir titular do domínio, provedor, região, orçamento máximo/mês, responsáveis e dados que podem ser públicos.
- Confirmar política de privacidade, retenção, dados pessoais e responsabilidades segundo a LGPD.
- Criar inventário não destrutivo da base H2: tabelas, quantidades, produtos, fotos, volumes, remessas, eventos, IDs e códigos RT.

**M1 — Segurança antes da publicação**
- Retirar credenciais padrão de todos os ambientes externos; segredos apenas em gerenciador/variáveis seguras, jamais em Git ou logs.
- Autenticação individual do operador com MFA e papéis claros (ADMIN/OPERADOR/AUDITOR), sem compartilhar contas.
- Adequar CSRF, sessão, cookies Secure/HttpOnly/SameSite conforme forma escolhida de autenticação.
- Rate limiting em consulta por código e upload; headers HTTPS, CSP sem dependência de unsafe-inline quando viável, validação de entradas e proteção de logs.
- Publicar apenas rotas públicas no hostname de rastreio; verificar 401/403/404 para todas as rotas administrativas externamente, inclusive POST, upload e documentação.
- Testes automatizados de autorização, tentativas anônimas, controle de escopo de dados e comportamento sob falha.

**M2 — PostgreSQL e migração reversível**
- Subir PostgreSQL de homologação isolado e executar Flyway no banco vazio (sem apontar para base de produção).
- Tirar backup H2 consistente + cópia imutável, documentar local e SHA-256; ensaiar restauração sem alterar o arquivo original.
- Desenvolver exportação H2 somente leitura e importação PostgreSQL transacional/idempotente, mantendo UUIDs, códigos RT, SKU, lote, timestamps, status, vínculos, eventos e foto BYTEA. Não presumir que pg_dump migra H2.
- Respeitar dependências entre tabelas: local, produto, remessa, volume, eventos e imagens. Lidar com locais padrão já semeados pelo Flyway, sem duplicar/alterar IDs.
- Comparar contagens por tabela, chaves, vínculos, checksums de imagens e amostras de linhas do tempo; rejeitar migração diante de divergências.
- Simular interrupção e rollback/restauração. Só efetuar cutover com janela definida e backup validado; H2 original conservado.
- Testar fluxo completo em PostgreSQL: produção → estoque → doca → carregamento → saída → chegada → entrega e leitura de QR.

**M3 — Publicação estável**
- Configurar DNS real e certificado HTTPS, healthchecks, restart automático, logs centralizados, métricas, alertas, limites de recursos e rollback de versão.
- APP_PUBLIC_BASE_URL apontando para URL permanente. Imprimir QR novo e validar no celular fora da rede interna.
- Confirmar comportamento após reinício/deploy sem perda de dados, alteração de URL ou indisponibilidade prolongada.
- Sem GPS/ETA fictício: consulta pública permanece baseada nos eventos registrados pelo operador.

**M4 — Backup e operação**
- Backups automáticos de PostgreSQL criptografados e guardados fora do servidor/volume primário; política de retenção, controle de acesso e alertas em caso de falha.
- Para uso inicial, propor RPO <= 24 h e RTO <= 8 h, SUJEITOS à aprovação e medição em teste de recuperação. Requisitos mais exigentes podem demandar PITR/WAL e maior custo.
- Executar e documentar restauração real em ambiente separado, com fotos e rastreios conferidos.
- Runbooks: iniciar/parar, trocar segredos, recuperação, indisponibilidade, atualização de versão, vazamento e atendimento.
- Fazer monitoramento de disponibilidade e avisos de falha, sem publicação de dados sensíveis.

**M5 — Homologação final e liberação comercial**
- Matriz de testes para QR (Android/iOS), consulta no celular, atualizações de status, impressão 100x150 mm, autenticação/roles e falhas de rede.
- Testes de carga básicos, bloqueio de enumeração e comportamento com volumes inexistentes.
- Revisão de segurança e privacidade antes do primeiro cliente; nenhum acesso externo ao painel via credenciais compartilhadas.
- Aprovação formal: responsável pelo domínio, segurança, banco, dados públicos, custo e aceite dos testes. Sem isso, manter o sistema como demonstração.

## 4. Sequência segura de implementação sugerida

1. Primeiro PR técnico: testes de integração PostgreSQL via Testcontainers, baseline de autorização/CSRF e execução no CI. Sem migração ainda.
2. Segundo PR: exportador/validador H2 → PostgreSQL que opere apenas sobre snapshot; testar em homologação.
3. Terceiro PR: autenticação individual + RBAC + estratégia de sessão/CSRF; atualizar as telas operacionais.
4. Quarto PR: deploy reproduzível, publicação de hostname estável, gateway de produção e observabilidade.
5. Quinto PR: backups programados + exercício de restore + testes E2E; checklist de go-live.

Sequenciamento pode ser reajustado para não expor endpoints sem autenticação adequada. Cada PR exige CI verde e avaliação antes de merge.

## 5. Limites explícitos do planejamento

- Nada neste documento compra domínio, cria conta cloud, gera cobrança, ativa domínio nem torna o Rastro360 público.
- Nada neste documento acessa ou altera o H2 do Windows do proprietário.
- Testes de aplicativo não substituem revisão independente de segurança para uso comercial.
- A demonstração no celular prova o fluxo de rastreio, NÃO prova disponibilidade 24x7, backups testados ou conformidade integral com LGPD.
- A escolha do provedor e os custos devem ser confirmados com preços atuais antes da contratação.

## 6. Decisão necessária para sair do planejamento

**Escolher uma rota de hospedagem**:
- A — PaaS com PostgreSQL gerenciado: menos operação de servidor, custo recorrente dependente do uso.
- B — VPS com Docker/PostgreSQL: maior controle e maior responsabilidade por segurança/backup/atualizações.

Depois confirmar se o projeto já possui domínio. A compra/contratação só ocorre mediante autorização explícita do proprietário.

## Referências técnicas oficiais

- Cloudflare Quick Tunnels (somente testes): https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/
- Cloudflare Tunnel gerenciado: https://developers.cloudflare.com/tunnel/get-started/
- PostgreSQL backup e restore: https://www.postgresql.org/docs/current/backup.html
- Spring Security CSRF: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
