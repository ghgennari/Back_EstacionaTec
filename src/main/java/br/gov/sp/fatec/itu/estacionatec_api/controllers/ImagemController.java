package br.gov.sp.fatec.itu.estacionatec_api.controllers;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.ImagemResponse;
import br.gov.sp.fatec.itu.estacionatec_api.services.ImagemService;
import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ImagemController {
    private final ImagemService imagens;
    private final CameraClient camera;

    public ImagemController(ImagemService imagens, CameraClient camera) {
        this.imagens = imagens;
        this.camera = camera;
    }

    @GetMapping("/imagens")
    public List<ImagemResponse> listar() {
        return imagens.listar();
    }

    @PostMapping("/imagens")
    @ResponseStatus(HttpStatus.CREATED)
    public ImagemResponse enviar(@RequestParam("file") MultipartFile arquivo, Authentication auth) throws IOException {
        return imagens.salvar(arquivo.getBytes(), "UPLOAD_MANUAL", (Long) auth.getPrincipal());
    }

    @GetMapping(value = "/imagens/{id}/arquivo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> arquivo(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imagens.arquivo(id));
    }

    @GetMapping("/camera/status")
    public Map<String, Object> status() {
        String mensagem = camera.usaWebcam()
                ? "Webcam selecionada. Clique em Ativar webcam e permita o acesso no navegador."
                : (camera.configurada() ? "Conectando à câmera IP..."
                        : "Câmera IP não conectada. Configure o endereço de captura.");
        return Map.of("source", camera.fonte().name(), "configured", camera.configurada(), "message", mensagem);
    }

    @GetMapping("/camera/preview")
    public ResponseEntity<byte[]> visualizar() {
        byte[] quadro = camera.capturar();
        MediaType formato = quadro.length > 0 && quadro[0] == (byte) 0x89
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(formato).body(quadro);
    }

    @PostMapping("/camera/capturar")
    public ImagemResponse capturar(Authentication auth) {
        return imagens.capturar((Long) auth.getPrincipal());
    }

    @PostMapping(value = "/camera/webcam/capturar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImagemResponse capturarWebcam(@RequestParam("file") MultipartFile arquivo, Authentication auth)
            throws IOException {
        return imagens.capturarWebcam(arquivo.getBytes(), (Long) auth.getPrincipal());
    }

}
