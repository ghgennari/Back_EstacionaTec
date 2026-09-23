package br.gov.sp.fatec.itu.estacionatec_api.services;

import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.entities.*;
import br.gov.sp.fatec.itu.estacionatec_api.dto.Dados.*;
import br.gov.sp.fatec.itu.estacionatec_api.exceptions.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConsultaService {
    private final EventoAcessoRepository eventos;
    private final RelatorioRepository relatorios;
    private final MovimentacaoService movimentacoes;

    public ConsultaService(EventoAcessoRepository eventos, RelatorioRepository relatorios,
            MovimentacaoService movimentacoes) {
        this.eventos = eventos;
        this.relatorios = relatorios;
        this.movimentacoes = movimentacoes;
    }

    public Map<String, Object> dashboard() {
        var hoje = eventos.findAll().stream().filter(e -> e.getDataHora().toLocalDate().equals(LocalDate.now())).toList();
        var ativos = movimentacoes.historico(true);
        return Map.of("parked", ativos.size(),
                "entriesToday", hoje.stream().filter(e -> e.getTipoEvento().equals("Entrada")).count(),
                "exitsToday", hoje.stream().filter(e -> e.getTipoEvento().equals("Saída")).count(),
                "vehicles", ativos);
    }

    public List<Map<String, Object>> recentes() {
        return eventos.findAllByOrderByDataHoraDesc().stream().limit(20).map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("plate", CadastroService.formatarPlaca(e.getPlaca()));
            item.put("owner", e.getProprietario());
            item.put("time", e.getDataHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            item.put("status", e.isAcessoAutorizado() ? "Autorizado" : "Negado");
            item.put("gateStatus", e.getStatusCancela());
            return item;
        }).toList();
    }

    public List<RelatorioResponse> relatorios() {
        return relatorios.findAll().stream().sorted(Comparator.comparing(Relatorio::getDataHora).reversed())
                .map(this::relatorioDto).toList();
    }

    public RelatorioResponse gerar(RelatorioRequest filtro, Long usuarioId) {
        if (filtro.start().isAfter(filtro.end())) {
            throw RegraNegocioException.conflito("Informe um período válido.");
        }
        if (!List.of("Entradas e Saídas", "Ocupação por Período", "Veículos por Tipo de Usuário",
                "Tempo Médio de Permanência").contains(filtro.type())) {
            throw RegraNegocioException.conflito("Tipo de relatório inválido.");
        }
        String placa = filtro.plate() == null || filtro.plate().isBlank()
                ? null : CadastroService.normalizarPlaca(filtro.plate());
        List<EventoAcesso> selecionados = eventos.findAllByOrderByDataHoraDesc().stream()
                .filter(e -> !e.getDataHora().toLocalDate().isBefore(filtro.start())
                        && !e.getDataHora().toLocalDate().isAfter(filtro.end()))
                .filter(e -> placa == null || placa.equals(e.getPlaca()))
                .filter(e -> filtro.userId() == null || filtro.userId().equals(e.getUsuarioId()))
                .filter(e -> filtro.eventType() == null || filtro.eventType().isBlank()
                        || filtro.eventType().equals(e.getTipoEvento())).toList();
        StringBuilder csv = new StringBuilder("\uFEFF");
        switch (filtro.type()) {
            case "Entradas e Saídas" -> {
                csv.append("Data;Evento;Placa;Proprietário;Categoria;Operador;Cancela\n");
                for (EventoAcesso e : selecionados) {
                    csv.append(linha(e.getDataHora(), e.getTipoEvento(), e.getPlaca(), e.getProprietario(),
                            e.getCategoria(), e.getUsuarioId(), e.getStatusCancela()));
                }
            }
            case "Veículos por Tipo de Usuário" -> {
                csv.append("Categoria;Veículos distintos no período\n");
                var grupos = selecionados.stream().collect(Collectors.groupingBy(EventoAcesso::getCategoria,
                        Collectors.mapping(EventoAcesso::getPlaca, Collectors.toSet())));
                grupos.forEach((categoria, placas) -> csv.append(linha(categoria, placas.size())));
            }
            case "Tempo Médio de Permanência" -> {
                csv.append("Saídas no período;Permanência média em minutos\n");
                var minutos = selecionados.stream().filter(e -> e.getEntradaId() != null)
                        .mapToLong(e -> Duration.between(eventos.findById(e.getEntradaId()).orElseThrow().getDataHora(),
                                e.getDataHora()).toMinutes()).summaryStatistics();
                csv.append(linha(minutos.getCount(), minutos.getAverage()));
            }
            case "Ocupação por Período" -> {
                if (filtro.userId() != null || (filtro.eventType() != null && !filtro.eventType().isBlank())) {
                    throw RegraNegocioException.conflito("Para ocupação, utilize apenas os filtros de período e placa.");
                }
                csv.append("Data;Veículos presentes no final do dia\n");
                var historico = movimentacoes.historico(false);
                if (java.time.temporal.ChronoUnit.DAYS.between(filtro.start(), filtro.end()) > 366) {
                    throw RegraNegocioException.conflito("Limite o relatório de ocupação a 366 dias.");
                }
                for (LocalDate dia = filtro.start(); !dia.isAfter(filtro.end()); dia = dia.plusDays(1)) {
                    LocalDateTime limite = dia.plusDays(1).atStartOfDay();
                    long quantidade = historico.stream().filter(h -> h.entry().isBefore(limite)
                            && (h.exit() == null || !h.exit().isBefore(limite)))
                            .filter(h -> placa == null || placa.equals(CadastroService.normalizarPlaca(h.plate()))).count();
                    csv.append(linha(dia, quantidade));
                }
            }
            default -> throw new IllegalArgumentException();
        }
        Relatorio relatorio = new Relatorio();
        relatorio.setNome(filtro.type() + " - " + filtro.start() + " a " + filtro.end());
        relatorio.setTipo(filtro.type());
        relatorio.setConteudo(csv.toString());
        relatorio.setTamanho(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes");
        relatorio.setDisponivel(true);
        relatorio.setUsuarioId(usuarioId);
        return relatorioDto(relatorios.save(relatorio));
    }

    public byte[] baixar(Long id) {
        Relatorio relatorio = relatorios.findById(id)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Relatório não encontrado."));
        if (!relatorio.isDisponivel()) {
            throw RegraNegocioException.naoEncontrado("Este relatório antigo era demonstrativo e não possui arquivo.");
        }
        return relatorio.getConteudo().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String linha(Object... valores) {
        return Arrays.stream(valores).map(valor -> {
            String texto = valor == null ? "" : valor.toString();
            if (texto.matches("^[=+@\\-].*")) {
                texto = "'" + texto;
            }
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }).collect(Collectors.joining(";")) + "\n";
    }

    private RelatorioResponse relatorioDto(Relatorio r) {
        return new RelatorioResponse(r.getId(), r.getNome(),
                r.getDataHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), r.getTamanho(), r.isDisponivel());
    }
}
