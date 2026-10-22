package br.gov.sp.fatec.itu.estacionatec_api.integration;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

/** Mantém credenciais, porta e caminho RTSP ao trocar apenas o IP. */
final class EnderecosCamera {
    private EnderecosCamera() {}

    static List<String> listar(String principal, String alternativas) {
        if (principal.isBlank()) return List.of();
        URI uri = URI.create(principal);
        if (!"rtsp".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("Configuração RTSP inválida.");
        var urls = new LinkedHashSet<String>();
        urls.add(principal);
        String prefixo = "rtsp://" + (uri.getRawUserInfo() == null ? "" : uri.getRawUserInfo() + "@");
        String sufixo = (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        for (String host : alternativas.split(",")) {
            host = host.trim();
            if (host.isEmpty()) continue;
            if (!host.matches("[a-zA-Z0-9.-]+"))
                throw new IllegalArgumentException("Host alternativo inválido.");
            urls.add(prefixo + host + sufixo);
        }
        return List.copyOf(urls);
    }
}
