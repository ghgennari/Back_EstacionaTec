package br.gov.sp.fatec.itu.estacionatec_api.config;

import br.gov.sp.fatec.itu.estacionatec_api.services.DadosPessoais;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NormalizarTelefones implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public NormalizarTelefones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Compatível com PostgreSQL e H2. Atualiza apenas cadastros antigos com máscara.
        // A comparação com o valor original preserva alterações concorrentes.
        var telefones = jdbc.query("select id, telefone from pessoas where telefone is not null",
                (rs, row) -> new Telefone(rs.getLong("id"), rs.getString("telefone")));
        for (var telefone : telefones) {
            String normalizado = DadosPessoais.telefone(telefone.valor());
            if (!normalizado.equals(telefone.valor())) {
                jdbc.update("update pessoas set telefone = ? where id = ? and telefone = ?",
                        normalizado, telefone.id(), telefone.valor());
            }
        }
    }

    private record Telefone(long id, String valor) {
    }
}
