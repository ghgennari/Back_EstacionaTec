package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.*;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class CadastroService {
    private final PessoaRepository pessoas;
    private final VeiculoRepository veiculos;
    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final EventoAcessoRepository eventos;
    private final PasswordEncoder encoder;

    public CadastroService(PessoaRepository pessoas, VeiculoRepository veiculos,
            UsuarioRepository usuarios, PerfilRepository perfis, EventoAcessoRepository eventos,
            PasswordEncoder encoder) {
        this.pessoas = pessoas;
        this.veiculos = veiculos;
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.eventos = eventos;
        this.encoder = encoder;
    }

    public List<PessoaResponse> pessoas() {
        return pessoas.findAll().stream().map(this::pessoaDto).toList();
    }

    public PessoaResponse salvarPessoa(Long id, PessoaRequest dados) {
        Pessoa pessoa = id == null ? new Pessoa() : pessoas.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Pessoa não encontrada."));
        String documento = dados.document().replaceAll("[^a-zA-Z0-9]", "");
        if (documento.isBlank()) {
            throw RegraNegocioException.conflito("Informe um documento válido.");
        }
        validarCategoria(dados.type());
        pessoa.setNome(dados.name().trim());
        pessoa.setDocumento(documento);
        pessoa.setEmail(dados.email() == null ? null : dados.email().trim().toLowerCase(Locale.ROOT));
        pessoa.setTelefone(dados.phone());
        pessoa.setCategoria(dados.type());
        pessoa.setAtivo(dados.active() == null || dados.active());
        return pessoaDto(pessoas.saveAndFlush(pessoa));
    }

    public void excluirPessoa(Long id) {
        if (veiculos.existsByPessoaId(id) || usuarios.existsByPessoaId(id)) {
            throw RegraNegocioException.conflito("Esta pessoa possui veículos ou usuário vinculados.");
        }
        pessoas.delete(pessoas.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Pessoa não encontrada.")));
    }

    public List<VeiculoResponse> veiculos() {
        return veiculos.findAll().stream().map(this::veiculoDto).toList();
    }

    public VeiculoResponse salvarVeiculo(Long id, VeiculoRequest dados) {
        Veiculo veiculo = id == null ? new Veiculo() : veiculos.bloquearPorId(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Veículo não encontrado."));
        veiculo.setPessoa(pessoas.findById(dados.ownerId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Proprietário não encontrado.")));
        veiculo.setPlaca(normalizarPlaca(dados.plate()));
        veiculo.setModelo(dados.model().trim());
        veiculo.setCor(dados.color());
        veiculo.setMarca(dados.brand());
        if (!List.of("Carro", "Moto", "Outro").contains(dados.type())) {
            throw RegraNegocioException.conflito("Tipo de veículo inválido.");
        }
        veiculo.setTipo(dados.type());
        veiculo.setAutorizado(dados.authorized() == null || dados.authorized());
        return veiculoDto(veiculos.saveAndFlush(veiculo));
    }

    public void excluirVeiculo(Long id) {
        Veiculo veiculo = veiculos.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Veículo não encontrado."));
        if (eventos.existsByVeiculoId(id) || veiculo.isEstacionado()) {
            throw RegraNegocioException.conflito("Este veículo não pode ser excluído porque possui histórico ou está no estacionamento. "
                    + "Desativar a autorização impede novas entradas, mas permite registrar a saída e mantém o histórico.");
        }
        veiculos.delete(veiculo);
    }

    public List<UsuarioResponse> usuarios() {
        return usuarios.findAll().stream().map(this::usuarioDto).toList();
    }

    public UsuarioResponse salvarUsuario(Long id, UsuarioRequest dados) {
        Usuario usuario = id == null ? new Usuario() : usuarios.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Usuário não encontrado."));
        if (id == null && (dados.password() == null || dados.password().length() < 6)) {
            throw RegraNegocioException.conflito("A senha deve conter pelo menos 6 caracteres.");
        }
        Pessoa pessoa = usuario.getPessoa() == null ? new Pessoa() : usuario.getPessoa();
        String email = dados.email().trim().toLowerCase(Locale.ROOT);
        usuarios.findByPessoaEmailIgnoreCase(email).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw RegraNegocioException.conflito("E-mail já cadastrado."); });
        pessoa.setNome(dados.name().trim());
        pessoa.setEmail(email);
        if (pessoa.getCategoria() == null) {
            pessoa.setCategoria("Funcionário");
        }
        usuario.setPessoa(pessoas.save(pessoa));
        usuario.setUsername(dados.username().trim().toLowerCase(Locale.ROOT));
        usuario.setPerfil(perfis.findByNome(dados.role())
                .orElseThrow(() -> RegraNegocioException.conflito("Perfil inválido.")));
        if (!List.of("Ativo", "Inativo").contains(dados.status())) {
            throw RegraNegocioException.conflito("Status inválido.");
        }
        usuario.setAtivo(dados.status().equals("Ativo"));
        if (dados.password() != null && !dados.password().isBlank()) {
            if (dados.password().length() < 6 || dados.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                throw RegraNegocioException.conflito("A senha deve ter ao menos 6 caracteres e até 72 bytes.");
            }
            usuario.setSenha(encoder.encode(dados.password()));
        }
        return usuarioDto(usuarios.saveAndFlush(usuario));
    }

    public void excluirUsuario(Long id, Long atualId) {
        if (id.equals(atualId)) {
            throw RegraNegocioException.conflito("Você não pode excluir sua própria conta.");
        }
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Usuário não encontrado."));
        usuario.setAtivo(false);
    }

    public PessoaResponse pessoaDto(Pessoa p) {
        return new PessoaResponse(p.getId(), p.getNome(), p.getDocumento(), p.getEmail(),
                p.getTelefone(), p.getCategoria(), p.isAtivo());
    }

    public VeiculoResponse veiculoDto(Veiculo v) {
        return new VeiculoResponse(v.getId(), formatarPlaca(v.getPlaca()), v.getModelo(), v.getCor(),
                v.getPessoa().getId(), v.getPessoa().getNome(), v.getTipo(), v.getPessoa().getCategoria(),
                v.getMarca(), v.isAutorizado());
    }

    public UsuarioResponse usuarioDto(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getPessoa().getNome(), u.getUsername(),
                u.getPessoa().getEmail(), u.getPerfil().getNome(), u.isAtivo() ? "Ativo" : "Inativo");
    }

    public static String normalizarPlaca(String valor) {
        String placa = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (!placa.matches("(?:[A-Z]{3}-?[0-9]{4}|[A-Z]{3}[0-9][A-Z][0-9]{2})")) {
            throw RegraNegocioException.conflito("Informe uma placa válida: ABC-1234 ou ABC1D23.");
        }
        return placa.replace("-", "");
    }

    public static String formatarPlaca(String placa) {
        return placa != null && placa.matches("[A-Z]{3}[0-9]{4}")
                ? placa.substring(0, 3) + "-" + placa.substring(3) : placa;
    }

    private void validarCategoria(String categoria) {
        if (!List.of("Aluno", "Professor", "Funcionário", "Visitante").contains(categoria)) {
            throw RegraNegocioException.conflito("Categoria inválida.");
        }
    }
}
