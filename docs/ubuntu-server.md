# API e PostgreSQL no Ubuntu Server (192.168.1.250)

Instale Docker Engine e o plugin Compose seguindo o repositório apt oficial:
https://docs.docker.com/engine/install/ubuntu/
No servidor, confirme `sudo docker info` e `sudo docker compose version`.
Não é necessário instalar Java ou Maven no host: o Dockerfile compila a API.

## Primeira instalação

```bash
sudo apt update
sudo apt install -y git
git clone https://github.com/ghgennari/Back_EstacionaTec.git
cd Back_EstacionaTec
cp .env.example .env
chmod 600 .env
nano .env
```

Preencha `DB_PASSWORD`, `ADMIN_EMAIL` e `ADMIN_PASSWORD`. Use senhas próprias;
a senha do administrador precisa ter pelo menos 12 caracteres (até 72 bytes).
Coloque valores com caracteres especiais entre aspas simples no arquivo .env.
O arquivo .env é ignorado pelo Git. `API_BIND_ADDRESS=192.168.1.250` publica a API
no endereço da rede informado; mantenha esse IP reservado no roteador.

```bash
sudo docker compose config --quiet
sudo docker compose up -d --build --wait --wait-timeout 180
sudo docker compose ps
curl http://192.168.1.250:8080/health
```

O resultado esperado do health contém `UP`. Também é possível abrir esse endereço
em outro computador na mesma rede. As rotas da aplicação começam em `/api`;
a raiz `/` não contém uma tela. O front-end é um projeto separado e ainda precisa
ser configurado para usar esta API. Se houver acesso direto entre origens pelo
navegador, configure `CORS_ALLOWED_ORIGINS` com a origem exata do front-end.

O PostgreSQL só é acessível na rede interna do Compose, pelo nome `postgres`.
O perfil `prod` cria o administrador inicial e não carrega dados fictícios.
O volume `postgres-server-data` armazena o banco; `api-data` guarda as imagens.
O banco local do Windows não é copiado para este servidor.
Esta configuração usa atualização de esquema pelo Hibernate; migrações
versionadas ainda não foram implementadas. Um banco H2 de implantação anterior
não é migrado automaticamente: preserve-o e planeje a transferência antes da troca.

## Operação

```bash
# Acompanhar inicialização ou erros
sudo docker compose logs --tail=100 api postgres
# Atualizar após novos commits
git pull --ff-only
sudo docker compose up -d --build --wait --wait-timeout 180
# Parar preservando os volumes
sudo docker compose down
```

Não use `down -v`: remove os volumes e seus dados. Volumes não substituem backup;
copie backups do banco e das imagens para outro dispositivo.
As senhas iniciais só são aplicadas na criação do banco/administrador;
editar o .env posteriormente não altera automaticamente essas credenciais.

Este roteiro usa HTTP para validação na rede local. Para uso com dados reais,
configure HTTPS. Não é necessário encaminhar portas no roteador para acesso local.
O serviço reinicia com Docker; o notebook precisa permanecer ligado e sem suspender.
