# EstacionaTec API

Back-end Java 21 e Spring Boot 4.1.1 integrado ao `estacionatec-web`, com Maven Wrapper, Spring MVC, JPA, validação, Spring Security e H2 em memória. As camadas seguem as anotações da aula: `controllers`, `entities`, `repositories` e `services`.

## Executar no VS Code

Abra esta pasta como projeto. A configuração local em `.vscode/settings.json` aponta para o Java 21 encontrado na extensão Java do VS Code. Feche o terminal antigo usando a lixeira e abra um novo para carregar as variáveis.

No Git Bash:

```bash
./mvnw -version
./mvnw test
./mvnw spring-boot:run
```

No PowerShell, use `./mvnw.cmd` em vez de `./mvnw`.

Se aparecer `No compiler is provided in this environment`, o Maven está usando um Java sem compilador. No PowerShell já aberto, selecione o Java 21 verificado nesta máquina:

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.vscode\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -version
.\mvnw.cmd spring-boot:run
```

Apesar do nome da pasta `jre`, essa instalação contém `javac` e o módulo `jdk.compiler`, verificados nesta máquina. As variáveis acima valem para o terminal atual. As configurações em `.vscode/settings.json` só são aplicadas aos novos terminais quando esta pasta é aberta como projeto no VS Code.

O comando `mvn` exige Maven instalado no PATH. O Wrapper incluído no projeto baixa a versão necessária automaticamente. O primeiro uso precisa de internet. `./mvnw -version` deve mostrar Java 21.

Os downloads locais ficam em `.maven-cache`, ignorado pelo Git. As configurações do VS Code também são locais. O caminho do Java da extensão pode mudar quando ela for atualizada; nesse caso, ajuste o caminho ou instale um JDK 21 separado e configure `JAVA_HOME` para ele.

Para executar em um Git Bash fora do VS Code, nesta máquina:

```bash
export JAVA_HOME='C:/Users/Admin/.vscode/extensions/redhat.java-1.56.0-win32-x64/jre/21.0.12.1-win32-x86_64'
export MAVEN_USER_HOME='C:/EstacionaTec/estacionatec-api/.maven-cache'
export MAVEN_OPTS='-Dmaven.repo.local=C:/EstacionaTec/estacionatec-api/.maven-cache/repository'
./mvnw spring-boot:run
```

## Conferir o H2

Para usar PostgreSQL local com persistência, siga [PostgreSQL no Docker](docs/postgresql.md).
O perfil `postgres` substitui o H2 nessa execução; sem perfil, o padrão continua sendo H2.

Após aparecer `Started EstacionatecApiApplication`, abra http://localhost:8080/h2-console.

- JDBC URL: `jdbc:h2:mem:estacionatec`
- User Name: `sa`
- Password: vazio

Conecte e execute `SELECT * FROM VEICULOS;`. As tabelas são criadas pelo Hibernate a partir das entidades. O banco perde seus dados ao encerrar a aplicação e é preenchido novamente no próximo início. O servidor está configurado para acesso apenas pela própria máquina.

A API usa rotas em `/api` e exige login para consultas e operações. Não há página inicial em `http://localhost:8080/`. Use Ctrl+C no terminal para encerrar.

## Executar o front-end

Em outro terminal Git Bash:

```bash
cd /c/EstacionaTec/estacionatec-web
npm start
```

Abra http://localhost:4200. O Angular encaminha `/api/**` para `http://127.0.0.1:8080`, conforme `proxy.conf.json`. Reinicie `npm start` se o servidor já estava aberto antes da alteração do proxy.

| Perfil | E-mail ou usuário | Senha de teste |
| --- | --- | --- |
| Administrador | `joao@edu.br` ou `joao.carlos` | `EstacionaTec@123` |
| Porteiro | `marcos@edu.br` ou `marcos.oliveira` | `EstacionaTec@123` |

A conta `juliana@edu.br` está inativa e não pode entrar. As senhas são convertidas para BCrypt na carga inicial. A senha acima é exclusiva dos cadastros fictícios desta base local.

O administrador gerencia cadastros. Porteiros consultam dados e registram movimentações, imagens e acionamentos. O perfil comum possui consultas, sem permissão para movimentar veículos. As permissões são verificadas pela API. O token expira em oito horas e é invalidado no logout ou quando o servidor reinicia.

## Testar entrada e saída sem equipamentos

1. Faça login e abra **Registrar Entrada**. Os veículos cadastrados começam fora do estacionamento.
2. Informe uma placa cadastrada e registre a entrada manualmente.
3. O aviso de ESP32 desconectado é esperado: a entrada foi salva, mas o portão não abriu.
4. Abra **Registrar Saída** e registre a saída do veículo que acabou de entrar.
5. Consulte dashboard, eventos recentes e histórico: os dados vêm das operações registradas na API.

Entrada e saída manuais não exigem câmera nem imagem. As telas não possuem controles de envio ou captura. A API ainda aceita `imageId` opcional para integração com câmera; quando informado, a imagem precisa ser válida, recente e ainda não utilizada.

Veículos podem ser editados mesmo estando estacionados. A lista de veículos presentes usa o cadastro atualizado, inclusive a placa corrigida; eventos anteriores mantêm seus dados históricos.

**Abrir Portão** tenta novamente o comando do evento registrado, com autorização válida por dois minutos e pertencente ao operador. Uma confirmação de abertura não é repetida. Uma falha de hardware não apaga a movimentação já registrada.

## JSON e dados de demonstração

O [dados-teste.json](src/main/resources/data/dados-teste.json) contém as informações de teste do front-end. `DadosTesteLoader` lê o arquivo e insere os dados com JPA após criar as tabelas. Nas anotações da aula, o mecanismo era `data.sql`; aqui usamos um carregador Java para atender ao formato JSON solicitado.

Imagens, eventos de acesso e histórico começam vazios. Nenhum veículo é marcado como estacionado pela carga inicial. Pessoas, veículos, usuários e relatórios demonstrativos continuam disponíveis. `originalFrontend` conserva apenas as referências de cadastros e relatórios; os exemplos de movimentação e imagem foram removidos. Os indicadores são calculados a partir dos eventos registrados pelo usuário.

Roberto Lima e Fernanda Costa tinham nome e placa no dashboard, mas não tinham cadastros completos. Campos desconhecidos permanecem vazios ou como `Não informado`; não foram inventados documentos pessoais. Os usuários também possuem registros de pessoa, conforme o DER.

As referências antigas de relatórios não incluíam arquivos reais e permanecem indisponíveis para download. Os exemplos de imagens e eventos foram removidos. Novos registros manuais podem ser feitos sem imagem.

Os novos arquivos ficam em `storage/imagens`, fora dos recursos públicos, e são servidos por endpoint autenticado. Ao reiniciar o H2, arquivos físicos antigos podem permanecer sem referência; não há limpeza automática nesta etapa. Para desabilitar a carga, configure `estacionatec.seed.enabled=false`; será necessário preparar usuários fora da carga de demonstração.

## Funcionalidades e próximos módulos

Estão integrados login/logout, cadastros, autorização por perfil, entrada e saída, imagens, histórico, dashboard, monitoramento por consulta periódica e relatórios CSV. As regras de vínculo, placa, duplicidade e movimentação são validadas no servidor.

Recuperação de senha informa indisponibilidade enquanto não houver serviço de e-mail. Relatórios PDF, transmissão de vídeo IP, MQTT e OCR/ALPR não foram implementados nesta etapa. A webcam exibe vídeo local pelo navegador e envia fotos à API; a câmera IP tem adaptador HTTP de snapshot. A cancela tem comando HTTP com confirmação. O funcionamento físico precisa ser validado com os equipamentos. PostgreSQL está disponível para desenvolvimento local pelo perfil `postgres`; veja [a configuração](docs/postgresql.md).

## Testar com a webcam do notebook

A configuração padrão é `estacionatec.camera.source=${CAMERA_SOURCE:WEBCAM}`.
Inicie a API e o front-end, abra `http://localhost:4200`, entre em Monitoramento
e clique em **Ativar webcam**. Permita o acesso solicitado pelo navegador.
**Capturar Imagem** envia uma foto para o back-end; ela aparece em Imagens Capturadas.
O navegador usa a câmera do computador que abriu a página, sem áudio.
Ao desligar a webcam ou sair da tela, as trilhas de vídeo são encerradas.
O vídeo não é transmitido ao servidor nem gravado continuamente.

Use localhost nos testes ou HTTPS fora dele. Se a câmera estiver ocupada,
feche o aplicativo que a está utilizando. Entrada e saída manuais continuam
independentes da câmera.

Para voltar à captura de câmera IP, configure `CAMERA_SOURCE=IP` e
`CAMERA_SNAPSHOT_URL` com o endereço HTTP/HTTPS que retorna uma foto, depois
reinicie a API. Exemplo no PowerShell:

```powershell
$env:CAMERA_SOURCE = 'IP'
$env:CAMERA_SNAPSHOT_URL = 'http://IP_DA_CAMERA/CAMINHO_DO_SNAPSHOT'
.\mvnw.cmd spring-boot:run
```

O endereço exato depende do modelo. O adaptador atual de IP não implementa
autenticação específica do fabricante nem RTSP. Para voltar à webcam,
defina `CAMERA_SOURCE=WEBCAM` e reinicie a API.

Veja [arquitetura e tabelas](docs/arquitetura.md) e [contratos HTTP e configuração dos equipamentos](docs/api.md).

## Testes

API: `./mvnw test`. Front-end: `npm test -- --watch=false` e `npm run build` na pasta `estacionatec-web`.

Os testes cobrem autenticação, permissões, vínculos, upload, entrada/saída, histórico, relatórios, duplicidade, concorrência e falhas de hardware. Os clientes de câmera e ESP32 usam servidores HTTP locais de teste. Os testes das telas verificam requisições, erros e atualização após confirmação da API. Eles não substituem a validação física dos equipamentos.

Referências técnicas: [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [proxy Angular](https://angular.dev/tools/cli/serve).
