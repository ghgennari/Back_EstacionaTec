package br.gov.sp.fatec.itu.estacionatec_api.security;

import br.gov.sp.fatec.itu.estacionatec_api.entities.Usuario;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TokenFilter extends OncePerRequestFilter {
    private final SessaoService sessoes;

    public TokenFilter(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Usuario usuario = sessoes.autenticar(header.substring(7));
            if (usuario != null) {
                var auth = new UsernamePasswordAuthenticationToken(usuario.getId(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().getNome())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
