# OCR e entrada automatica

## Alternativa: webcam do computador

Administradores podem escolher **Camera IP** ou **Camera deste computador (webcam)**
na tela Camera ao Vivo. A escolha e local a essa tela: nao muda o `.env`, nao altera
a camera dos outros computadores e volta ao padrao do servidor ao recarregar/sair.
No notebook Acer, o navegador abre a webcam padrao do proprio notebook.

Acesse pelo dominio HTTPS e autorize a camera quando o navegador solicitar. HTTP
por IP da rede (por exemplo, `http://192.168.1.250:8081`) normalmente nao permite
webcam; `localhost` e a excecao para desenvolvimento. Se aparecer permissao negada
ou camera ocupada, libere a permissao ou feche o outro programa e clique Ativar webcam.

Na webcam, o video e exibido localmente. Um quadro JPEG e enviado para OCR a cada
1500 ms apos a resposta anterior, sem pedidos simultaneos e somente com a aba
visivel. Duas leituras confirmadas permitem entrada automatica com as mesmas regras
da IP. A foto associada a entrada vem da webcam, sem depender da camera IP.
O recorte configurado para a IP nao e aplicado a webcam.

Mantenha **Camera ao Vivo** aberta e visivel para o OCR local. A tela Registrar
Entrada continua acompanhando a fonte configurada no servidor. Desligar a webcam,
trocar para IP ou sair da pagina libera o dispositivo e interrompe novos envios;
um pedido ja enviado pode terminar. Capturar Imagem e Testar leitura OCR usam a
camera escolhida. O seletor e as rotas `/api/camera/local/**` sao exclusivos de
administrador; os demais perfis mantem o acesso que ja tinham a fonte do servidor.

O acesso fisico a webcam depende da permissao do navegador; testes automatizados
simulam o dispositivo, portanto confirme a webcam do Acer antes da apresentacao.
Referencia: [getUserMedia e HTTPS](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).

## Camera IP

O acompanhamento autenticado (`POST /api/camera/ocr/acompanhar`) ativa a leitura
enquanto **Camera ao Vivo** ou **Registrar Entrada** estiver aberta. Fechar essas
telas interrompe o acompanhamento; este modo nao e um leitor autonomo 24 horas.
Nao e necessario manter o DevTools/Network aberto.

Depois de duas leituras consecutivas da mesma placa com confianca de pelo menos
70%, o servidor registra **Entrada**, captura uma foto e solicita abertura ao ESP32.
Nao existe clique de confirmacao do operador. O usuario autenticado que consumir
a deteccao fica associado ao evento. Saida continua sendo uma operacao separada.

O veiculo deve estar cadastrado, autorizado, com proprietario ativo e sem entrada
aberta. Desconhecidos continuam exigindo cadastro/manual de visitante. O mesmo
identificador de deteccao e consumido uma vez, inclusive com varias abas; locks no
banco tambem impedem duas entradas ativas. Falha no ESP32 nao desfaz uma entrada
ja gravada e nao provoca novos comandos a cada consulta.

## Diagnosticar a leitura real

1. Cadastre a placa exata do teste, autorizada e sem entrada ativa.
2. Mostre a placa no notebook e enquadre a tela na camera, com caracteres grandes,
   horizontais, sem reflexos, cortes ou desfoque. Mantenha-a por varios ciclos.
3. Em Camera ao Vivo, use **Testar leitura OCR**. O resultado distingue falha do
   motor de nenhuma placa encontrada e mostra placa, confianca e tempo.
4. Confira a mensagem **Entrada registrada automaticamente** e o Historico.
5. Se falhar, use Capturar Imagem e baixe a foto em Imagens Capturadas. Precisamos
   dessa foto da camera e da placa esperada para reproduzir o problema. Uma imagem
   original da placa nao reproduz os efeitos de fotografa-la em uma tela.

`POST /api/camera/ocr/testar` aceita multipart `file` (ate 5 MB) para repetir OCR de
uma captura. Esse endpoint e `/diagnosticar` nao gravam entradas; o acompanhamento
da tela, se ativo em paralelo, continua automatico. Todos exigem autenticacao e
perfil Administrador/Porteiro. Nao registre tokens ou URLs RTSP nos logs.

Para limitar o OCR a area da placa, configure no `.env` do servidor:

```dotenv
OCR_REGION_X=0
OCR_REGION_Y=0
OCR_REGION_WIDTH=1
OCR_REGION_HEIGHT=1
OCR_INTERVAL_MS=1500
OCR_MINIMUM_CONFIDENCE=70
```

Coordenadas proporcionais a imagem original: X/Y sao a origem; WIDTH/HEIGHT sao
o tamanho. X+WIDTH e Y+HEIGHT devem ser <=1. O padrao cobre a imagem inteira.
Reduzir indiscriminadamente a confianca aumenta o risco de entrada para a placa
errada; prefira melhorar o enquadramento. A confianca do Tesseract nao e uma
probabilidade de acerto calibrada.

## Rede e processamento

- Antes: aproximadamente 5 JPEGs/s por aba. Com 106 kB por JPEG, isso representa
  cerca de 530 kB/s ou 1,9 GB/h por cliente (estimativa, sem cabecalhos).
- Agora: no maximo 1 JPEG/s, largura maxima de 800 px, qualidade JPEG 65%.
  Downloads param quando a aba fica oculta. Um quadro em andamento pode terminar.
- O servidor compartilha a previa por 900 ms. OCR e fotos usam o quadro maior,
  limitado a 1920 px, independente da previa. O FFmpeg produz 2 quadros/s em vez de 5.
- O OCR continua no servidor; o navegador recebe apenas resultados JSON. A previa
  fica em memoria e nao cria uma foto no banco a cada atualizacao.
- A alteracao reduz trafego servidor-navegador. O fluxo RTSP camera-servidor mantem
  o bitrate configurado na propria camera. Mais clientes multiplicam o trafego web.
- JPEG por consulta e simples, mas nao e a opcao mais eficiente para video fluido.
  Para muitos clientes ou alta taxa de quadros, avaliar video H.264/WebRTC com um
  gateway. A previa de 1 fps prioriza economia, nao fluidez.

Para medir no ambiente real, limpe Network, mantenha a aba visivel por 60 segundos
e anote Transferred e quantidade de `preview`. Compare o mesmo enquadramento.
`blob:` nao representa um segundo download da imagem. Na rede local o trafego
fica na LAN; pelo dominio ele atravessa o tunnel e consome upload do servidor.

## Validacao e limites

Os testes cobrem entrada automatica, duplicatas, rejeicoes, falha de cancela,
limites da previa e pausa em aba oculta. O teste nativo executa Tesseract real
com placas sinteticas e uma cena JPEG sintetica de 1920x1080. O Dockerfile executa
esse teste na imagem de producao durante o build.

Esses testes nao comprovam leitura pela camera de uma tela fisica. A aprovacao
desse cenario exige a captura real e a confirmacao do evento no servidor.

Referencias: [qualidade e segmentacao no Tesseract](https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html)
e [visibilidade da pagina](https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API).
