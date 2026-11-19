package com.example.afluente.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Faixas de status da meta: exceder pouco e exceder muito não têm mais o mesmo rótulo. */
public class StatusMetaTest {
    @Test public void faixas() {
        assertEquals(StatusMeta.Nivel.SEM_META, StatusMeta.classificar(10.0, null));
        assertEquals(StatusMeta.Nivel.SEM_REGISTROS, StatusMeta.classificar(null, 100.0));
        assertEquals(StatusMeta.Nivel.DENTRO, StatusMeta.classificar(80.0, 100.0));
        assertEquals(StatusMeta.Nivel.PERTO, StatusMeta.classificar(95.0, 100.0));
        assertEquals(StatusMeta.Nivel.PERTO, StatusMeta.classificar(100.0, 100.0)); // igual à meta não excede
        assertEquals(StatusMeta.Nivel.ACIMA, StatusMeta.classificar(166.0, 165.0));  // caso do print: 1 kWh acima
        assertEquals(StatusMeta.Nivel.ACIMA, StatusMeta.classificar(120.0, 100.0));
        assertEquals(StatusMeta.Nivel.MUITO_ACIMA, StatusMeta.classificar(121.0, 100.0));
        assertEquals(StatusMeta.Nivel.MUITO_ACIMA, StatusMeta.classificar(166.0, 1.0)); // caso do print: 165 kWh acima
    }

    @Test public void somaDePontoFlutuanteNaoContaComoExcesso() {
        assertEquals(StatusMeta.Nivel.PERTO, StatusMeta.classificar(0.1 + 0.2, 0.3));
    }

    @Test public void textoDoExcesso() {
        assertEquals("Excedeu em 1 kWh (0,6% acima da meta)", StatusMeta.textoExcesso(166, 165, "kWh"));
        assertEquals("Excedeu em 165 kWh (166 vezes a meta)", StatusMeta.textoExcesso(166, 1, "kWh"));
        assertEquals("Excedeu em 2,5 m³ (25% acima da meta)", StatusMeta.textoExcesso(12.5, 10, "m³"));
        assertNull(StatusMeta.textoExcesso(9, 10, "m³"));
    }
}
