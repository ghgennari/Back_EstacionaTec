package br.gov.sp.fatec.itu.estacionatec_api;

import br.gov.sp.fatec.itu.estacionatec_api.integration.OcrClient;
import org.junit.jupiter.api.Test;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OcrImageValidationTests {
    @Test
    void rejeitaDimensoesExcessivasAntesDeDecodificarPixels() {
        var registry = IIORegistry.getDefaultInstance();
        var provider = new LeitorTesteProvider();
        registry.registerServiceProvider(provider);
        try {
            assertThatThrownBy(() -> new OcrClient("", 0, 0, 1, 1).reconhecer(new byte[]{42}))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Imagem inválida");
            assertThat(provider.decodificou).isFalse();
        } finally {
            registry.deregisterServiceProvider(provider);
        }
    }

    @Test
    void rejeitaArquivoSemImagemSemInvocarMotorNativo() {
        assertThatThrownBy(() -> new OcrClient("", 0, 0, 1, 1).reconhecer(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Imagem inválida");
    }

    // O leitor simula um cabeçalho enorme sem alocar memória ou depender de imagem maliciosa real.
    private static class LeitorTesteProvider extends ImageReaderSpi {
        boolean decodificou;

        LeitorTesteProvider() {
            super("EstacionaTec", "1", new String[]{"teste"}, new String[]{"teste"},
                    new String[]{"image/teste"}, LeitorTeste.class.getName(),
                    new Class<?>[]{ImageInputStream.class}, null, false, null, null, null, null,
                    false, null, null, null, null);
        }

        @Override public boolean canDecodeInput(Object source) throws IOException {
            if (!(source instanceof ImageInputStream input)) return false;
            input.mark();
            try { return input.read() == 42; }
            finally { input.reset(); }
        }
        @Override public ImageReader createReaderInstance(Object extension) { return new LeitorTeste(this); }
        @Override public String getDescription(Locale locale) { return "Leitor de teste de dimensões"; }
    }

    private static class LeitorTeste extends ImageReader {
        private final LeitorTesteProvider provider;
        LeitorTeste(LeitorTesteProvider provider) { super(provider); this.provider = provider; }
        @Override public int getNumImages(boolean search) { return 1; }
        @Override public int getWidth(int index) { return 20_000; }
        @Override public int getHeight(int index) { return 20_000; }
        @Override public Iterator<ImageTypeSpecifier> getImageTypes(int index) {
            return List.of(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB)).iterator();
        }
        @Override public IIOMetadata getStreamMetadata() { return null; }
        @Override public IIOMetadata getImageMetadata(int index) { return null; }
        @Override public BufferedImage read(int index, ImageReadParam param) {
            provider.decodificou = true;
            throw new AssertionError("Não deve decodificar pixels antes de validar dimensões.");
        }
    }
}
