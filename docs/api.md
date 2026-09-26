# API e equipamentos

Base: `http://localhost:8080/api`. No Angular, `/api` usa o proxy de desenvolvimento.

## Autenticação e rotas

`POST /auth/login` recebe `{"email":"joao@edu.br","password":"EstacionaTec@123"}` e retorna `token` e `user`. Use `Authorization: Bearer <token>` nas demais rotas. `POST /auth/logout` invalida a sessão. `401` indica sessão ausente ou inválida; `403` indica permissão insuficiente. Erros de negócio retornam `message`.

| Método | Caminho após `/api` | Uso |
| --- | --- | --- |
| GET, POST | `/pessoas` | Listar e cadastrar. |
| PUT, DELETE | `/pessoas/{id}` | Atualizar ou excluir cadastro sem vínculos. |
| GET, POST | `/veiculos` | Listar e cadastrar; vínculo por `ownerId`. |
| PUT, DELETE | `/veiculos/{id}` | Atualizar ou excluir se não houver histórico. |
| GET, POST | `/usuarios` | Listar e cadastrar; apenas administrador. |
| PUT, DELETE | `/usuarios/{id}` | Atualizar ou desativar. |
| POST | `/imagens` | Upload multipart no campo `file`. |
| GET | `/imagens` | Metadados de imagens. |
| GET | `/imagens/{id}/arquivo` | Arquivo PNG autenticado. |
| GET | `/camera/status` | Retorna `source` (`WEBCAM` ou `IP`), `configured` e mensagem; não confirma conexão física. |
| POST | `/camera/capturar` | Capturar foto da câmera IP, validar e salvar. No modo webcam retorna 409 orientando o envio de um quadro. |
| POST | `/camera/webcam/capturar` | Foto multipart no campo `file`, capturada pelo navegador; apenas em modo WEBCAM, retorna 201. |
| POST | `/movimentacoes/entrada` | Registrar entrada e tentar abrir a cancela. |
| POST | `/movimentacoes/saida` | Registrar saída e tentar abrir a cancela. |
| GET | `/movimentacoes/ativos` | Veículos com entrada ativa. |
| GET | `/historico` | Estadias e eventual saída. |
| POST | `/cancela/abrir` | Repetir acionamento autorizado do evento recente. |
| GET | `/dashboard` | Indicadores e veículos presentes. |
| GET | `/monitoramento/eventos` | Últimos vinte eventos. |
| GET, POST | `/relatorios` | Listar referências ou gerar CSV. |
| GET | `/relatorios/{id}/arquivo` | Baixar CSV. |
| POST | `/auth/recuperar-senha` | Indisponível enquanto não houver serviço de e-mail. |

Os campos dos cadastros estão em `dto/Dados.java`. Exemplo de entrada ou saída:

```json
{
  "plate": "ABC-1234",
  "requestId": "f5c1d214-2895-43a1-9a74-8cb4a70cec20"
}
```

`imageId` é opcional: registros manuais não exigem imagem. Se for informado, deve apontar para uma imagem válida, disponível, ainda não utilizada e enviada ou capturada pelo operador nos últimos dez minutos.

Use UUID novo por operação e preserve-o ao repetir a mesma requisição após falha de rede. Uma resposta `200` pode conter `gateStatus: "ERRO"` e `warning`: o registro foi salvo, mas a abertura não foi confirmada. Não envie outra entrada/saída só para tentar abrir o portão. Use `/cancela/abrir` com `{"eventId":20}`.

## Câmera HTTP de snapshot

Configure a URL que retorna JPEG/PNG. Exemplo Git Bash, ajustando ao equipamento:

```bash
export CAMERA_SNAPSHOT_URL='http://192.168.0.50/snapshot.jpg'
./mvnw spring-boot:run
```

No PowerShell: `$env:CAMERA_SNAPSHOT_URL = 'http://192.168.0.50/snapshot.jpg'` antes de executar `./mvnw.cmd spring-boot:run`.

Sem URL, a captura responde `503`. Há limites de tamanho, resolução, conexão e leitura. A URL vem apenas da configuração do servidor. RTSP, ONVIF, vídeo contínuo e autenticação específica do fabricante não fazem parte deste adaptador inicial; confirme o endpoint com o modelo de câmera escolhido.

## ESP32

Configure `ESP32_OPEN_URL` para o endpoint do firmware. O servidor faz POST com:

```json
{"commandId":"evento-20","action":"OPEN"}
```

A resposta HTTP 200 deve conter confirmação correspondente:

```json
{"commandId":"evento-20","opened":true}
```

O firmware deve confirmar após executar o comando e guardar IDs executados. Ao receber o mesmo `commandId`, deve responder sem acionar o relé novamente. Isso evita abertura duplicada quando a resposta original se perde na rede.

Ausência de configuração, falha HTTP, timeout e confirmação inválida geram erro e log. `ABERTA` representa a confirmação do comando pelo firmware, não telemetria contínua da posição da cancela. Sensores, autenticação e HTTPS/TLS ainda precisam ser validados com o equipamento.

## Relatórios

```json
{
  "start": "2026-08-01",
  "end": "2026-08-31",
  "type": "Entradas e Saídas",
  "plate": "",
  "userId": null,
  "eventType": ""
}
```

Tipos: `Entradas e Saídas`, `Ocupação por Período`, `Veículos por Tipo de Usuário` e `Tempo Médio de Permanência`. `userId` filtra o operador que registrou o evento. `eventType` é `Entrada`, `Saída` ou vazio. Ocupação aceita apenas período e placa, por até 366 dias.

Ocupação é medida no final de cada dia. Veículos por categoria conta placas distintas com eventos no período. Permanência média considera saídas no período e o intervalo desde suas entradas. Os eventos antigos de demonstração não tinham operador e não aparecem quando se filtra um operador específico.
