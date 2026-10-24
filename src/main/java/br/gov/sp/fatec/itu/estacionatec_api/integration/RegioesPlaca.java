package br.gov.sp.fatec.itu.estacionatec_api.integration;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Propõe recortes claros e horizontais; somente o OCR pode confirmar seus caracteres. */
final class RegioesPlaca {
    private RegioesPlaca() { }

    static List<Rectangle> localizar(BufferedImage imagem) {
        int passo = Math.max(1, (int) Math.ceil(imagem.getWidth() / 640.0));
        int w = imagem.getWidth() / passo, h = imagem.getHeight() / passo;
        var raster = imagem.getRaster();
        // Suaviza apenas a detecção de regiões para não fragmentar o fundo da placa
        // por linhas finas da tela. A leitura usa os pixels originais do recorte.
        int[] niveis = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int soma = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                soma += raster.getSample(Math.max(0, Math.min(imagem.getWidth() - 1, x * passo + dx * passo)),
                        Math.max(0, Math.min(imagem.getHeight() - 1, y * passo + dy * passo)), 0);
            niveis[y * w + x] = soma / 9;
        }
        List<Rectangle> candidatas = new ArrayList<>();
        int[] fila = new int[w * h];
        for (int limiar : new int[]{80, 140, 200}) {
            boolean[] visitados = new boolean[w * h];
            for (int inicio = 0; inicio < visitados.length; inicio++) {
                if (visitados[inicio]) continue;
                visitados[inicio] = true;
                if (niveis[inicio] < limiar) continue;
                int head = 0, tail = 1, minX = inicio % w, maxX = minX, minY = inicio / w, maxY = minY;
                fila[0] = inicio;
                while (head < tail) {
                    int atual = fila[head++], x = atual % w, y = atual / w;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = x + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                        int ny = y + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int pos = ny * w + nx;
                        if (visitados[pos]) continue;
                        visitados[pos] = true;
                        if (niveis[pos] >= limiar) fila[tail++] = pos;
                    }
                }
                int largura = maxX - minX + 1, altura = maxY - minY + 1;
                double proporcao = (double) largura / altura;
                if (largura < 40 || altura < 10 || proporcao < 1.8 || proporcao > 6.5
                        || (long) largura * altura > w * h / 2 || tail < largura * altura * 0.45) continue;
                Rectangle r = new Rectangle(minX * passo, minY * passo, largura * passo, altura * passo);
                if (candidatas.stream().noneMatch(a -> sobrepoe(a, r))) candidatas.add(r);
            }
        }
        return candidatas.stream().sorted(Comparator.comparingInt((Rectangle r) -> r.width * r.height).reversed())
                .limit(4).toList();
    }

    private static boolean sobrepoe(Rectangle a, Rectangle b) {
        var i = a.intersection(b);
        return !i.isEmpty() && (double) i.width * i.height / Math.min(a.width * a.height, b.width * b.height) > 0.75;
    }
}
