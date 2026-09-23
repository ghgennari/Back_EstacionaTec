package br.gov.sp.fatec.itu.estacionatec_api.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_acesso", indexes = {@Index(name = "idx_evento_data", columnList = "dataHora"), @Index(name = "idx_evento_veiculo", columnList = "veiculo_id")})
public class EventoAcesso implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veiculo_id")
    private Veiculo veiculo;

    @Column(nullable = false)
    private String tipoEvento;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    private boolean acessoAutorizado;

    @ManyToOne
    @JoinColumn(name = "imagem_id", unique = true)
    private ImagemCapturada imagem;

    @Column(length = 2000)
    private String observacao;

    @Column(unique = true)
    private Long entradaId;

    @ManyToOne
    @JoinColumn(name = "entradaId", insertable = false, updatable = false)
    private EventoAcesso entrada;

    private Long usuarioId;

    @Column(unique = true)
    private String requestId;

    private String placa;

    private String proprietario;

    private String documentoProprietario;

    private String categoria;

    private String modelo;

    private String statusCancela = "PENDENTE";

    public EventoAcesso() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(Veiculo veiculo) {
        this.veiculo = veiculo;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public boolean isAcessoAutorizado() {
        return acessoAutorizado;
    }

    public void setAcessoAutorizado(boolean acessoAutorizado) {
        this.acessoAutorizado = acessoAutorizado;
    }

    public ImagemCapturada getImagem() {
        return imagem;
    }

    public void setImagem(ImagemCapturada imagem) {
        this.imagem = imagem;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public Long getEntradaId() {
        return entradaId;
    }

    public void setEntradaId(Long entradaId) {
        this.entradaId = entradaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getProprietario() {
        return proprietario;
    }

    public void setProprietario(String proprietario) {
        this.proprietario = proprietario;
    }

    public String getDocumentoProprietario() {
        return documentoProprietario;
    }

    public void setDocumentoProprietario(String documentoProprietario) {
        this.documentoProprietario = documentoProprietario;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getStatusCancela() {
        return statusCancela;
    }

    public void setStatusCancela(String statusCancela) {
        this.statusCancela = statusCancela;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof EventoAcesso entity && id != null && id.equals(entity.getId());
    }

    @Override
    public int hashCode() {
        return EventoAcesso.class.hashCode();
    }
}
