# PostgreSQL local no Docker

O Docker Desktop deve estar iniciado. A API roda pelo Maven/VS Code e o banco
roda em um container PostgreSQL 17, acessível somente em `localhost:5432`.
O arquivo `compose.postgres.yaml` é independente do `compose.yaml` de produção,
que executa API e PostgreSQL no servidor (veja [Ubuntu Server](ubuntu-server.md)). Esta etapa não migra dados de bancos H2 existentes.

No PowerShell, execute na pasta do projeto:

```powershell
cd C:\EstacionaTec\estacionatec-api
$env:DB_PASSWORD = 'EstacionaTecLocal123'
docker compose -f compose.postgres.yaml up -d --wait
$env:SPRING_PROFILES_ACTIVE = 'postgres'
.\mvnw.cmd spring-boot:run
```

A senha acima é exclusiva para desenvolvimento local. Use a mesma senha
no Compose e na API. As variáveis valem somente para o terminal atual.
Para iniciar pelo botão Run do VS Code, configure no lançamento as variáveis
`SPRING_PROFILES_ACTIVE=postgres` e `DB_PASSWORD=EstacionaTecLocal123`.

O Hibernate cria/atualiza as tabelas com `ddl-auto=update` neste perfil local.
A carga fictícia ocorre quando não existem usuários; reiniciar preserva os dados.
O login de demonstração continua sendo `joao@edu.br` / `EstacionaTec@123`.
O front-end continua acessando a API na porta 8080. O console H2 fica desabilitado.

Para conferir o banco:

```powershell
docker compose -f compose.postgres.yaml ps
docker compose -f compose.postgres.yaml exec postgres psql -U estacionatec -d estacionatec -c "SELECT count(*) FROM veiculos;"
```

Conexão em um cliente SQL: host `localhost`, porta `5432`, banco e usuário
`estacionatec`, senha definida em `DB_PASSWORD`.

Para parar o banco, preservando os registros:

```powershell
docker compose -f compose.postgres.yaml stop
```

O volume `postgres-data` persiste após reinícios e `docker compose down`.
Não use `down -v` se quiser manter os dados: essa opção remove o volume.
Alterar `DB_PASSWORD` não troca a senha de um banco já inicializado;
nesse caso é necessário alterar a senha do usuário dentro do PostgreSQL.
Se a porta 5432 estiver ocupada, defina `$env:DB_PORT = '5433'` antes de iniciar
o Compose e a API no mesmo terminal.

Os testes existentes continuam usando H2: execute `.\mvnw.cmd test` em um
terminal sem `SPRING_PROFILES_ACTIVE=postgres`. A adoção em produção deve incluir
migrações versionadas de esquema e planejamento da transferência dos dados.
