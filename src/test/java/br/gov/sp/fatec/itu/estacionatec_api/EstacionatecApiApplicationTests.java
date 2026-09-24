package br.gov.sp.fatec.itu.estacionatec_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import br.gov.sp.fatec.itu.estacionatec_api.repositories.*;
import br.gov.sp.fatec.itu.estacionatec_api.services.ConsultaService;
import br.gov.sp.fatec.itu.estacionatec_api.services.MovimentacaoService;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:initial-empty-test;DB_CLOSE_DELAY=-1")
class EstacionatecApiApplicationTests {

    @Autowired
    private ImagemCapturadaRepository imagens;
    @Autowired
    private EventoAcessoRepository eventos;
    @Autowired
    private VeiculoRepository veiculos;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private ConsultaService consultas;
    @Autowired
    private MovimentacaoService movimentacoes;

    @Test
    void iniciaComCadastrosMasSemImagensMovimentacoesOuHistorico() {
        assertThat(usuarios.count()).isPositive();
        assertThat(veiculos.findAll()).isNotEmpty().allMatch(veiculo -> !veiculo.isEstacionado());
        assertThat(imagens.count()).isZero();
        assertThat(eventos.count()).isZero();
        assertThat(consultas.recentes()).isEmpty();
        assertThat(movimentacoes.historico(false)).isEmpty();
        assertThat(movimentacoes.historico(true)).isEmpty();
        assertThat(consultas.dashboard()).containsEntry("parked", 0)
                .containsEntry("entriesToday", 0L).containsEntry("exitsToday", 0L);
    }

}
