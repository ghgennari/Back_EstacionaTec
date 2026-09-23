package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.*;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class MovimentacaoService {
    private final VeiculoRepository veiculos;
    private final EventoAcessoRepository eventos;
    private final ImagemCapturadaRepository imagens;
    private final AuditoriaService auditoria;

    public MovimentacaoService(VeiculoRepository veiculos, EventoAcessoRepository eventos,
            ImagemCapturadaRepository imagens, AuditoriaService auditoria) {
        this.veiculos = veiculos;
        this.eventos = eventos;
        this.imagens = imagens;
        this.auditoria = auditoria;
    }

    @Transactional
    public Long registrar(MovimentacaoRequest dados, String tipo, Long usuarioId) {
        try {
            String placa = CadastroService.normalizarPlaca(dados.plate());
            Veiculo veiculo = veiculos.bloquearPorPlaca(placa)
                    .orElseThrow(() -> RegraNegocioException.conflito("Veículo não cadastrado ou não autorizado."));
            var anterior = eventos.findByRequestId(dados.requestId());
            if (anterior.isPresent()) {
                EventoAcesso evento = anterior.get();
                if (!evento.getVeiculo().getId().equals(veiculo.getId())
                        || !evento.getTipoEvento().equals(tipo) || !usuarioId.equals(evento.getUsuarioId())
                        || !Objects.equals(evento.getImagem() == null ? null : evento.getImagem().getId(), dados.imageId())) {
                    throw RegraNegocioException.conflito("Identificador já utilizado em outra operação.");
                }
                return evento.getId();
            }
            if (!veiculo.isAutorizado() || !veiculo.getPessoa().isAtivo()) {
                throw RegraNegocioException.conflito("Veículo ou proprietário sem autorização ativa.");
            }
            boolean entrada = tipo.equals("Entrada");
            if (entrada == veiculo.isEstacionado()) {
                throw RegraNegocioException.conflito(entrada
                        ? "Este veículo já possui uma entrada ativa." : "Este veículo não possui entrada ativa.");
            }
            ImagemCapturada imagem = null;
            if (dados.imageId() != null) {
                imagem = imagens.bloquear(dados.imageId())
                        .orElseThrow(() -> RegraNegocioException.conflito("Imagem informada não encontrada."));
                if (!imagem.isDisponivel() || imagem.isUtilizada() || !usuarioId.equals(imagem.getUsuarioId())
                        || imagem.getDataCaptura().isBefore(LocalDateTime.now().minusMinutes(10))) {
                    throw RegraNegocioException.conflito("Envie uma nova imagem para esta movimentação (validade de 10 minutos).");
                }
            }
            EventoAcesso evento = new EventoAcesso();
            evento.setVeiculo(veiculo);
            evento.setTipoEvento(tipo);
            evento.setDataHora(LocalDateTime.now());
            evento.setAcessoAutorizado(true);
            evento.setImagem(imagem);
            evento.setUsuarioId(usuarioId);
            evento.setRequestId(dados.requestId());
            evento.setPlaca(veiculo.getPlaca());
            evento.setProprietario(veiculo.getPessoa().getNome());
            evento.setDocumentoProprietario(veiculo.getPessoa().getDocumento());
            evento.setCategoria(veiculo.getPessoa().getCategoria());
            evento.setModelo(veiculo.getModelo());
            if (!entrada) {
                evento.setEntradaId(eventos.findFirstByVeiculoIdAndTipoEventoOrderByDataHoraDesc(veiculo.getId(), "Entrada")
                        .orElseThrow(() -> RegraNegocioException.conflito("Entrada ativa não encontrada.")).getId());
            }
            if (imagem != null) {
                imagem.setUtilizada(true);
                imagem.setPlacaDetectada(veiculo.getPlaca());
                imagem.setProprietario(veiculo.getPessoa().getNome());
                imagem.setTipoEvento(tipo);
                imagem.setStatus("Autorizado");
            }
            veiculo.setEstacionado(entrada);
            return eventos.saveAndFlush(evento).getId();
        } catch (RegraNegocioException exception) {
            auditoria.registrar(usuarioId, "MOVIMENTACAO_NEGADA", dados.plate() + ": " + exception.getMessage());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public MovimentacaoResponse resposta(Long id, String aviso) {
        EventoAcesso evento = eventos.findById(id).orElseThrow();
        LocalDateTime entrada = evento.getEntradaId() == null ? evento.getDataHora()
                : eventos.findById(evento.getEntradaId()).orElseThrow().getDataHora();
        return new MovimentacaoResponse(id, evento.getVeiculo().getId(), CadastroService.formatarPlaca(evento.getPlaca()),
                evento.getProprietario(), evento.getCategoria(), evento.getModelo(), entrada,
                evento.getTipoEvento().equals("Saída") ? evento.getDataHora() : null, evento.getStatusCancela(), aviso);
    }

    @Transactional(readOnly = true)
    public List<HistoricoResponse> historico(boolean somenteAtivos) {
        return eventos.findAllByOrderByDataHoraDesc().stream()
                .filter(evento -> evento.getTipoEvento().equals("Entrada") && evento.isAcessoAutorizado())
                .map(evento -> {
                    var saida = eventos.findByEntradaId(evento.getId());
                    Veiculo veiculo = evento.getVeiculo();
                    // A lista operacional usa o cadastro atual; o histórico mantém os dados do evento.
                    return new HistoricoResponse(evento.getId(), CadastroService.formatarPlaca(
                            somenteAtivos ? veiculo.getPlaca() : evento.getPlaca()),
                            somenteAtivos ? veiculo.getPessoa().getNome() : evento.getProprietario(),
                            somenteAtivos ? veiculo.getPessoa().getDocumento() : evento.getDocumentoProprietario(),
                            somenteAtivos ? veiculo.getModelo() : evento.getModelo(),
                            evento.getDataHora(), saida.map(EventoAcesso::getDataHora).orElse(null),
                            somenteAtivos ? veiculo.getPessoa().getCategoria() : evento.getCategoria(), veiculo.getMarca());
                })
                .filter(registro -> !somenteAtivos || registro.exit() == null).toList();
    }
}
