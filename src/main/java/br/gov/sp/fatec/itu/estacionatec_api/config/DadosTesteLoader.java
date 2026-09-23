package br.gov.sp.fatec.itu.estacionatec_api.config;

import br.gov.sp.fatec.itu.estacionatec_api.entities.*;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.CadastroService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@ConditionalOnProperty(name = "estacionatec.seed.enabled", havingValue = "true")
public class DadosTesteLoader implements CommandLineRunner {
    private final PessoaRepository pessoas;
    private final PerfilRepository perfis;
    private final UsuarioRepository usuarios;
    private final VeiculoRepository veiculos;
    private final EventoAcessoRepository eventos;
    private final ImagemCapturadaRepository imagens;
    private final RelatorioRepository relatorios;
    private final ConfiguracaoRepository configuracoes;
    private final PasswordEncoder encoder;
    private final JsonMapper mapper;

    public DadosTesteLoader(PessoaRepository pessoas, PerfilRepository perfis, UsuarioRepository usuarios,
            VeiculoRepository veiculos, EventoAcessoRepository eventos, ImagemCapturadaRepository imagens,
            RelatorioRepository relatorios, ConfiguracaoRepository configuracoes, PasswordEncoder encoder,
            JsonMapper mapper) {
        this.pessoas = pessoas;
        this.perfis = perfis;
        this.usuarios = usuarios;
        this.veiculos = veiculos;
        this.eventos = eventos;
        this.imagens = imagens;
        this.relatorios = relatorios;
        this.configuracoes = configuracoes;
        this.encoder = encoder;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (usuarios.count() > 0) {
            return;
        }
        JsonNode root;
        try (var input = new ClassPathResource("data/dados-teste.json").getInputStream()) {
            root = mapper.readTree(input);
        }
        for (String nome : List.of("Administrador", "Porteiro", "Usuário")) {
            Perfil perfil = new Perfil();
            perfil.setNome(nome);
            perfis.save(perfil);
        }
        Map<String, Pessoa> proprietarios = new HashMap<>();
        for (JsonNode node : root.path("people")) {
            Pessoa pessoa = new Pessoa();
            pessoa.setNome(texto(node, "name"));
            String documento = texto(node, "document");
            pessoa.setDocumento(documento == null ? null : documento.replaceAll("\\W", ""));
            pessoa.setEmail(texto(node, "email"));
            pessoa.setTelefone(texto(node, "phone"));
            pessoa.setCategoria(texto(node, "type"));
            proprietarios.put(texto(node, "key"), pessoas.save(pessoa));
        }
        for (JsonNode node : root.path("users")) {
            Pessoa pessoa = new Pessoa();
            pessoa.setNome(texto(node, "name"));
            pessoa.setEmail(texto(node, "email"));
            pessoa.setCategoria("Funcionário");
            Usuario usuario = new Usuario();
            usuario.setPessoa(pessoas.save(pessoa));
            usuario.setPerfil(perfis.findByNome(texto(node, "role")).orElseThrow());
            usuario.setUsername(texto(node, "username"));
            usuario.setSenha(encoder.encode(root.path("demoPassword").asText()));
            usuario.setAtivo("Ativo".equals(texto(node, "status")));
            usuarios.save(usuario);
        }
        Map<String, Veiculo> cadastros = new HashMap<>();
        for (JsonNode node : root.path("vehicles")) {
            Veiculo veiculo = new Veiculo();
            veiculo.setPlaca(CadastroService.normalizarPlaca(texto(node, "plate")));
            veiculo.setPessoa(proprietarios.get(texto(node, "ownerKey")));
            veiculo.setModelo(texto(node, "model"));
            veiculo.setMarca(texto(node, "brand"));
            veiculo.setCor(texto(node, "color"));
            veiculo.setTipo(texto(node, "type"));
            cadastros.put(texto(node, "key"), veiculos.save(veiculo));
        }
        for (JsonNode node : root.path("stays")) {
            Veiculo veiculo = cadastros.get(texto(node, "vehicleKey"));
            EventoAcesso entrada = importarEvento(veiculo, "Entrada", texto(node, "entry"), null);
            if (texto(node, "exit") != null) {
                importarEvento(veiculo, "Saída", texto(node, "exit"), entrada.getId());
            } else {
                veiculo.setEstacionado(true);
            }
        }
        for (JsonNode node : root.path("images")) {
            ImagemCapturada imagem = new ImagemCapturada();
            imagem.setNomeArquivo(texto(node, "fileName"));
            imagem.setCaminhoArquivo(texto(node, "imagePath"));
            imagem.setPlacaDetectada(CadastroService.normalizarPlaca(texto(node, "plate")));
            imagem.setProprietario(texto(node, "owner"));
            imagem.setDataCaptura(LocalDateTime.parse(texto(node, "date") + "T" + texto(node, "time")));
            imagem.setTipoEvento(texto(node, "type"));
            imagem.setStatus(texto(node, "status"));
            imagem.setOrigem("DEMONSTRACAO_FRONTEND");
            imagens.save(imagem);
        }
        for (JsonNode node : root.path("reports")) {
            Relatorio relatorio = new Relatorio();
            relatorio.setNome(texto(node, "name"));
            relatorio.setDataHora(LocalDateTime.parse(texto(node, "date"), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            relatorio.setTamanho(texto(node, "size"));
            relatorio.setTipo("DEMONSTRACAO_FRONTEND");
            relatorios.save(relatorio);
        }
        for (var entry : root.path("originalFrontend").properties()) {
            Configuracao config = new Configuracao();
            config.setChave("demonstracao.frontend." + entry.getKey());
            config.setValor(entry.getValue().toString());
            configuracoes.save(config);
        }
    }

    private EventoAcesso importarEvento(Veiculo veiculo, String tipo, String dataHora, Long entradaId) {
        EventoAcesso evento = new EventoAcesso();
        evento.setVeiculo(veiculo);
        evento.setTipoEvento(tipo);
        evento.setDataHora(LocalDateTime.parse(dataHora));
        evento.setAcessoAutorizado(true);
        evento.setEntradaId(entradaId);
        evento.setPlaca(veiculo.getPlaca());
        evento.setProprietario(veiculo.getPessoa().getNome());
        evento.setDocumentoProprietario(veiculo.getPessoa().getDocumento());
        evento.setCategoria(veiculo.getPessoa().getCategoria());
        evento.setModelo(veiculo.getModelo());
        evento.setStatusCancela("DEMONSTRACAO");
        evento.setObservacao("Importado do protótipo. Não existia arquivo de imagem nem acionamento real.");
        return eventos.save(evento);
    }

    private String texto(JsonNode node, String campo) {
        JsonNode value = node.path(campo);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
