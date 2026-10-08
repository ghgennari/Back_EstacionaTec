package br.gov.sp.fatec.itu.estacionatec_api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class OcrNativeTests {
    @Test
    void executaMotorRealEmImagemDePlacaSintetica() throws Exception {
        OcrRuntimeCheck.main(new String[0]);
    }
}
