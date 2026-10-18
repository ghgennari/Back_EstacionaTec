package br.gov.sp.fatec.itu.estacionatec_api.services;

public final class DadosPessoais {
    private DadosPessoais() {
    }

    // O campo também aceita documentos alfanuméricos, além de CPF.
    public static String documento(String valor) {
        return valor == null ? null : valor.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static String telefone(String valor) {
        return valor == null ? null : valor.replaceAll("[^0-9]", "");
    }
}
