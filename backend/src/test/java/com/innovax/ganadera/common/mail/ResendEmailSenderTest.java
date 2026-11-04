package com.innovax.ganadera.common.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Resend simulado: no sale a internet. */
class ResendEmailSenderTest {

    private static final String URL = "https://resend.test";

    private MockRestServiceServer servidor;
    private ResendEmailSender sender;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        sender = new ResendEmailSender(builder.build(), "re_clave", "Gestión Ganadera <no-reply@innovax.test>");
    }

    @Test
    void mandaElEmailPorLaApiConLaClave() {
        servidor.expect(requestTo(URL + "/emails"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer re_clave"))
            .andExpect(content().json("""
                {"from":"Gestión Ganadera <no-reply@innovax.test>","to":["ana@campo.test"],
                 "subject":"Hola","text":"Texto del mail"}
                """))
            .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        sender.enviar("ana@campo.test", "Hola", "Texto del mail");

        servidor.verify();
    }

    @Test
    void siResendRechazaFallaConElMotivo() {
        servidor.expect(requestTo(URL + "/emails"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"The innovax.test domain is not verified\"}"));

        assertThatThrownBy(() -> sender.enviar("ana@campo.test", "Hola", "Texto"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("403")
            .hasMessageContaining("not verified");
    }
}
