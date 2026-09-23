package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.ImagemResponse;
import br.gov.sp.fatec.itu.estacionatec_api.entities.ImagemCapturada;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.ImagemCapturadaRepository;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import br.gov.sp.fatec.itu.estacionatec_api.integration.CameraClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;

@Service
public class ImagemService {
    private final ImagemCapturadaRepository imagens;
    private final Path diretorio;
    private final CameraClient camera;
    private final AuditoriaService auditoria;

    public ImagemService(ImagemCapturadaRepository imagens, CameraClient camera, AuditoriaService auditoria,
            @Value("${estacionatec.storage:storage/imagens}") String diretorio) {
        this.imagens = imagens;
        this.camera = camera;
        this.auditoria = auditoria;
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
    }

    public ImagemResponse capturar(Long usuarioId) {
        try {
            return salvar(camera.capturar(), "CAMERA", usuarioId);
        } catch (RegraNegocioException exception) {
            auditoria.registrar(usuarioId, "FALHA_CAMERA", exception.getMessage());
            throw exception;
        }
    }

    public ImagemResponse salvar(byte[] bytes, String origem, Long usuarioId) {
        if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "Envie uma imagem PNG ou JPEG de até 5 MB.");
        }
        Path arquivo = null;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Arquivo inválido.");
            }
            var reader = readers.next();
            java.awt.image.BufferedImage imagem;
            try {
                reader.setInput(input);
                String formato = reader.getFormatName();
                if (!(formato.equalsIgnoreCase("JPEG") || formato.equalsIgnoreCase("PNG"))
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 16000000) {
                    throw new IllegalArgumentException("Formato ou resolução não permitido.");
                }
                imagem = reader.read(0);
            } finally {
                reader.dispose();
            }
            Files.createDirectories(diretorio);
            String nome = UUID.randomUUID() + ".png";
            arquivo = diretorio.resolve(nome);
            ImageIO.write(imagem, "png", arquivo.toFile());
            ImagemCapturada registro = new ImagemCapturada();
            registro.setNomeArquivo(nome);
            registro.setCaminhoArquivo(nome);
            registro.setOrigem(origem);
            registro.setDisponivel(true);
            registro.setStatus("Pendente");
            registro.setUsuarioId(usuarioId);
            return dto(imagens.save(registro));
        } catch (Exception exception) {
            if (arquivo != null) {
                try {
                    Files.deleteIfExists(arquivo);
                } catch (java.io.IOException ignored) {
                    auditoria.registrar(usuarioId, "FALHA_LIMPEZA_IMAGEM", arquivo.getFileName().toString());
                }
            }
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST,
                    "Não foi possível armazenar a imagem. Use PNG ou JPEG de até 16 megapixels.");
        }
    }

    public byte[] arquivo(Long id) {
        ImagemCapturada imagem = imagens.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Imagem não encontrada."));
        if (!imagem.isDisponivel()) {
            throw RegraNegocioException.naoEncontrado("O arquivo deste registro demonstrativo não existe.");
        }
        Path caminho = diretorio.resolve(imagem.getCaminhoArquivo()).normalize();
        if (!caminho.startsWith(diretorio)) {
            throw RegraNegocioException.naoEncontrado("Imagem indisponível.");
        }
        try {
            return Files.readAllBytes(caminho);
        } catch (java.io.IOException exception) {
            throw RegraNegocioException.naoEncontrado("Arquivo da imagem não encontrado.");
        }
    }

    public List<ImagemResponse> listar() {
        return imagens.findAll().stream().sorted(Comparator.comparing(ImagemCapturada::getDataCaptura).reversed())
                .map(this::dto).toList();
    }

    public ImagemResponse dto(ImagemCapturada i) {
        return new ImagemResponse(i.getId(), CadastroService.formatarPlaca(i.getPlacaDetectada()),
                i.getProprietario(), i.getDataCaptura().toLocalTime().toString(),
                i.getDataCaptura().toLocalDate().toString(), i.getTipoEvento(), i.getStatus(),
                i.getNomeArquivo(), "/api/imagens/" + i.getId() + "/arquivo", i.isDisponivel());
    }
}
