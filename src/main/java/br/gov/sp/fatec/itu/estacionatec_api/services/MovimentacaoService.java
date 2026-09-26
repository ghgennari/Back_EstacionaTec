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
    private final ImagemService captura;

    public MovimentacaoService(VeiculoRepository veiculos, EventoAcessoRepository eventos,
            ImagemCapturadaRepository imagens, AuditoriaService auditoria, ImagemService captura) {
        this.veiculos = veiculos;
        this.eventos = eventos;
        this.imagens = imagens;
        this.auditoria = auditoria;
        this.captura = captura;
    }

    @Transactional
    public Long registrar(MovimentacaoRequest dados, String tipo, Long usuarioId) {
        try {
            String placa = CadastroService.normalizarPlaca(dados.plate());
            boolean entrada = tipo.equals("Entrada");
            if (!entrada && dados.visitor() != null) {
                throw RegraNegocioException.conflito("Os dados do visitante só podem ser informados na entrada.");
            }
            Veiculo veiculo = veiculos.bloquearPorPlaca(placa).orElse(null);
            EventoAcesso visitanteAtivo = eventos.bloquearVisitanteAtivo(placa).orElse(null);
            var anterior = eventos.findByRequestId(dados.requestId());
            if (anterior.isPresent()) {
                EventoAcesso evento = anterior.get();
                boolean mesmaPlaca = evento.getVeiculo() == null ? placa.equals(evento.getPlaca())
                        : veiculo != null && evento.getVeiculo().getId().equals(veiculo.getId());
                if (!mesmaPlaca
                        || !evento.getTipoEvento().equals(tipo) || !usuarioId.equals(evento.getUsuarioId())
                        || !Objects.equals(evento.isCapturaAutomatica() || evento.getImagem() == null
                                ? null : evento.getImagem().getId(), dados.imageId())
                        || (dados.visitor() != null
                                && (!Objects.equals(evento.getProprietario(), dados.visitor().responsibleName().trim())
                                        || !Objects.equals(evento.getModelo(), dados.visitor().model().trim())))) {
                    throw RegraNegocioException.conflito("Identificador já utilizado em outra operação.");
                }
                return evento.getId();
            }
            EventoAcesso evento = new EventoAcesso();
            if (visitanteAtivo != null) {
                if (entrada) {
                    throw RegraNegocioException.conflito("Este veículo já possui uma entrada ativa.");
                }
                evento.setPlaca(visitanteAtivo.getPlaca());
                evento.setProprietario(visitanteAtivo.getProprietario());
                evento.setCategoria(visitanteAtivo.getCategoria());
                evento.setModelo(visitanteAtivo.getModelo());
                evento.setEntradaId(visitanteAtivo.getId());
                visitanteAtivo.setPlacaVisitanteAtivo(null);
            } else if (veiculo != null) {
                preencherMovimentacaoCadastrada(evento, veiculo, dados, entrada);
            } else {
                preencherEntradaVisitante(evento, placa, dados.visitor(), entrada);
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
            evento.setTipoEvento(tipo);
            evento.setDataHora(LocalDateTime.now());
            evento.setAcessoAutorizado(true);
            evento.setImagem(imagem);
            evento.setUsuarioId(usuarioId);
            evento.setRequestId(dados.requestId());
            if (imagem != null) {
                imagem.setUtilizada(true);
                imagem.setPlacaDetectada(evento.getPlaca());
                imagem.setProprietario(evento.getProprietario());
                imagem.setTipoEvento(tipo);
                imagem.setStatus("Autorizado");
            }
            return eventos.saveAndFlush(evento).getId();
        } catch (RegraNegocioException exception) {
            auditoria.registrar(usuarioId, "MOVIMENTACAO_NEGADA", dados.plate() + ": " + exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void capturarImagemEntrada(Long eventoId, Long usuarioId) {
        EventoAcesso evento = eventos.bloquear(eventoId).orElseThrow();
        if (!evento.getTipoEvento().equals("Entrada") || !usuarioId.equals(evento.getUsuarioId())
                || evento.getImagem() != null || evento.isCapturaAutomatica()) {
            return;
        }
        // O registro já foi confirmado. Reenvios não devem gerar novas fotos.
        evento.setCapturaAutomatica(true);
        try {
            var foto = captura.capturar(usuarioId);
            ImagemCapturada imagem = imagens.bloquear(foto.id()).orElseThrow();
            imagem.setUtilizada(true);
            imagem.setPlacaDetectada(evento.getPlaca());
            imagem.setProprietario(evento.getProprietario());
            imagem.setTipoEvento("Entrada");
            imagem.setStatus("Autorizado");
            evento.setImagem(imagem);
        } catch (RegraNegocioException exception) {
            evento.setAvisoImagem("Entrada registrada, mas não foi possível capturar a imagem da câmera.");
        }
    }

    private void preencherEntradaVisitante(EventoAcesso evento, String placa, VisitanteRequest dados, boolean entrada) {
        if (!entrada) {
            throw RegraNegocioException.conflito("Este veículo não possui entrada ativa.");
        }
        if (dados == null) {
            throw new RegraNegocioException(org.springframework.http.HttpStatus.CONFLICT,
                    "Informe o responsável e o modelo para registrar a entrada do visitante.",
                    "VEICULO_NAO_CADASTRADO");
        }
        evento.setPlaca(placa);
        evento.setProprietario(dados.responsibleName().trim());
        evento.setCategoria("Visitante");
        evento.setModelo(dados.model().trim());
        evento.setPlacaVisitanteAtivo(placa);
    }

    private void preencherMovimentacaoCadastrada(EventoAcesso evento, Veiculo veiculo,
            MovimentacaoRequest dados, boolean entrada) {
        if (entrada && (!veiculo.isAutorizado() || !veiculo.getPessoa().isAtivo())) {
            throw RegraNegocioException.conflito("Veículo ou proprietário sem autorização ativa.");
        }
        if (dados.visitor() != null) {
            throw RegraNegocioException.conflito("Esta placa já está cadastrada. Cancele o formulário e registre a entrada pela placa.");
        }
        if (entrada == veiculo.isEstacionado()) {
            throw RegraNegocioException.conflito(entrada
                    ? "Este veículo já possui uma entrada ativa." : "Este veículo não possui entrada ativa.");
        }
        evento.setVeiculo(veiculo);
        evento.setPlaca(veiculo.getPlaca());
        evento.setProprietario(veiculo.getPessoa().getNome());
        evento.setDocumentoProprietario(veiculo.getPessoa().getDocumento());
        evento.setCategoria(veiculo.getPessoa().getCategoria());
        evento.setModelo(veiculo.getModelo());
        if (!entrada) {
            evento.setEntradaId(eventos.findFirstByVeiculoIdAndTipoEventoOrderByDataHoraDesc(veiculo.getId(), "Entrada")
                    .orElseThrow(() -> RegraNegocioException.conflito("Entrada ativa não encontrada.")).getId());
        }
        veiculo.setEstacionado(entrada);
    }

    @Transactional(readOnly = true)
    public MovimentacaoResponse resposta(Long id, String aviso) {
        EventoAcesso evento = eventos.findById(id).orElseThrow();
        if (evento.getAvisoImagem() != null) {
            aviso = evento.getAvisoImagem() + (aviso == null ? "" : " " + aviso);
        }
        LocalDateTime entrada = evento.getEntradaId() == null ? evento.getDataHora()
                : eventos.findById(evento.getEntradaId()).orElseThrow().getDataHora();
        return new MovimentacaoResponse(id, evento.getVeiculo() == null ? null : evento.getVeiculo().getId(),
                CadastroService.formatarPlaca(evento.getPlaca()),
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
                    boolean usarCadastro = somenteAtivos && veiculo != null;
                    // A lista operacional usa o cadastro atual; o histórico mantém os dados do evento.
                    return new HistoricoResponse(evento.getId(), CadastroService.formatarPlaca(
                            usarCadastro ? veiculo.getPlaca() : evento.getPlaca()),
                            usarCadastro ? veiculo.getPessoa().getNome() : evento.getProprietario(),
                            usarCadastro ? veiculo.getPessoa().getDocumento() : evento.getDocumentoProprietario(),
                            usarCadastro ? veiculo.getModelo() : evento.getModelo(),
                            evento.getDataHora(), saida.map(EventoAcesso::getDataHora).orElse(null),
                            usarCadastro ? veiculo.getPessoa().getCategoria() : evento.getCategoria(),
                            veiculo == null ? null : veiculo.getMarca());
                })
                .filter(registro -> !somenteAtivos || registro.exit() == null).toList();
    }
}
