package com.flowora.erp.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    @Test
    void generatesRfc6238CompatibleSixDigitCode() {
        TotpService service = new TotpService();
        String code = service.generate("JBSWY3DPEHPK3PXP", 59L / 30L);

        assertThat(code).matches("\\d{6}");
        assertThat(code).isEqualTo("996554");
    }

    @Test
    void createsBase32SecretWithEnoughEntropy() {
        String secret = new TotpService().newSecret();

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    }
}
