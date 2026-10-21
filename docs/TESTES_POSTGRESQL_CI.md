# Rastro360 v0.5.0 — PostgreSQL isolado no GitHub Actions

## Propósito

Validar que o **mesmo código Java 21 / Spring Boot / Flyway** opera com PostgreSQL 16 sem contratar nuvem, sem instalar Docker no Windows e **sem ler, alterar, excluir ou migrar o banco H2 existente**.

Este marco prepara a migração futura. **Não realiza migração H2 → PostgreSQL**, não hospeda clientes e não muda o banco de desenvolvimento.

## O que foi acrescentado

O workflow `.github/workflows/ci.yml` contém dois jobs independentes:

- `build-and-test`: mantém `mvn -B -ntp clean verify` (H2 em memória) e valida a sintaxe dos scripts PowerShell.
- `postgres-integration`: cria um **PostgreSQL 16-alpine descartável** no runner GitHub Actions. Define exclusivamente credenciais de CI fictícias, executa `mvn -B -ntp -Dtest=PostgresFlowIT test`, e descarta o banco ao término do job.

O teste `PostgresFlowIT` só pode ser executado quando a variável `RASTRO360_POSTGRES_TEST=true` está definida. A extensão `IT` não faz parte do conjunto padrão do Maven Surefire, evitando conectar ao PostgreSQL por engano durante `mvn clean verify` local.

## O que o teste verifica

1. Conexão realmente PostgreSQL (não H2), Flyway V1/V2 aplicados e tabela `product_image` criada.
2. Localizações iniciais disponíveis e API administrativa bloqueada para visitantes anônimos.
3. Cadastro de produto fictício com SKU exclusivo e geração de código `RT-`.
4. Movimentações produção → estoque → doca; carregamento em remessa, com bloqueio de carregamento duplicado.
5. Saída, chegada e confirmação de entrega, verificando os **7 eventos internos** e os **4 eventos públicos**.
6. Upload de PNG sintético, armazenamento `BYTEA` e recuperação idêntica a partir do banco.
7. Endpoint de rastreamento público disponível e apresentando status `ENTREGUE`.

Os dados e senhas do job são **fictícios e efêmeros**. Senhas de CI não devem ser reutilizadas em serviços publicados.

## Como executar no Windows sem Docker

Não precisa fazer nada além de atualizar `main` depois da aprovação do PR. O GitHub Actions executa os testes PostgreSQL na infraestrutura temporária própria. Se quiser conferir:

    cd C:\Projetos\rastro360\Rastro360
    git switch main
    git pull --ff-only origin main
    mvn clean verify

Este comando **não executa** o `PostgresFlowIT`, não usa a configuração PostgreSQL e não toca no H2 persistente, pois as suítes padrão estão no perfil de teste em memória.

**NÃO execute** `initialize-dev-db.ps1 -CreateNew`. Não há motivo para criar ou apagar bases locais.

## Próxima evolução técnica

Antes de qualquer migração: criar inventário read-only de um **snapshot H2 consistente**, ferramenta de comparação de tabelas e fotos, importação em PostgreSQL de homologação e teste de restauração. A base H2 original permanece intacta e funciona como rollback.

A escolha de cloud gerenciada foi registrada no plano da v0.5.0, mas **nenhum domínio, plano pago ou banco externo foi contratado ou provisionado**.
