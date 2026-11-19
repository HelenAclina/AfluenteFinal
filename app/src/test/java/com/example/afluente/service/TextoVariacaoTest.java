package com.example.afluente.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Testes locais das frases explicativas dos percentuais. */
public class TextoVariacaoTest {
    @Test public void mesEmAndamentoNaoAfirmaEconomia() {
        assertEquals("Até agora, você usou 73% menos água que no mês anterior",
                TextoVariacao.consumo(10.0, 2.7, "água", true));
        assertEquals("Até agora, você já usou 12,5% mais energia que no mês anterior",
                TextoVariacao.consumo(200.0, 225.0, "energia", true));
    }

    @Test public void mesFechadoUsaAFraseDeEconomia() {
        assertEquals("Você economizou 73% de água em relação ao mês anterior",
                TextoVariacao.consumo(10.0, 2.7, "água", false));
        assertEquals("Você consumiu 50% mais água em relação ao mês anterior",
                TextoVariacao.consumo(10.0, 15.0, "água", false));
    }

    @Test public void semBaseValida() {
        assertEquals("Sem mês anterior para comparar", TextoVariacao.consumo(null, 5.0, "água", true));
        assertEquals("Sem mês anterior para comparar", TextoVariacao.consumo(0.0, 5.0, "água", true));
        assertNull(TextoVariacao.percentual(0.0, 5.0));
        assertEquals("Mesmo consumo de água do mês anterior", TextoVariacao.consumo(10.0, 10.0, "água", true));
    }

    @Test public void gasto() {
        assertEquals("Até agora, R$ 12,30 a menos que no mês anterior", TextoVariacao.gasto(100.0, 87.7, true));
        assertEquals("Até agora, R$ 5,00 a mais que no mês anterior", TextoVariacao.gasto(100.0, 105.0, true));
        assertEquals("Cadastre consumos e tarifas para calcular", TextoVariacao.gasto(100.0, null, true));
    }

    @Test public void periodos() {
        assertEquals("nov/2026: 25% menos que out/2026", TextoVariacao.periodos(8.0, 6.0, "out/2026", "nov/2026"));
        assertEquals("Sem comparação", TextoVariacao.periodos(null, 6.0, "out/2026", "nov/2026"));
    }
}
