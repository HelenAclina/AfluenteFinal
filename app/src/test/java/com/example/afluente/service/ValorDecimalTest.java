package com.example.afluente.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import org.junit.Test;

/** Testes locais (sem celular) da validação única dos campos numéricos. */
public class ValorDecimalTest {
    private static void erro(String entrada, ValorDecimal.Regra regra, String mensagem) {
        ValorDecimal.Resultado r = ValorDecimal.validar(entrada, regra);
        assertNull("Deveria rejeitar: " + entrada, r.valor);
        assertEquals(mensagem, r.erro);
    }

    @Test public void aceitaVirgulaEPonto() {
        assertEquals(0, new BigDecimal("12.5").compareTo(ValorDecimal.validar("12,5", ValorDecimal.Regra.CONSUMO).valor));
        assertEquals(0, new BigDecimal("12.5").compareTo(ValorDecimal.validar(" 12.5 ", ValorDecimal.Regra.CONSUMO).valor));
        assertTrue(ValorDecimal.validar("0,894", ValorDecimal.Regra.TARIFA).valido());
    }

    @Test public void rejeitaNegativoComMensagemPropria() {
        erro("-5", ValorDecimal.Regra.CONSUMO, ValorDecimal.ERRO_NEGATIVO);
        erro("-0,5", ValorDecimal.Regra.TARIFA, ValorDecimal.ERRO_NEGATIVO);
        erro("−5", ValorDecimal.Regra.META, ValorDecimal.ERRO_NEGATIVO);
    }

    @Test public void rejeitaZero() {
        erro("0", ValorDecimal.Regra.CONSUMO, ValorDecimal.ERRO_ZERO);
        erro("0,000", ValorDecimal.Regra.META, ValorDecimal.ERRO_ZERO);
    }

    @Test public void caracteresInvalidosTemAMesmaMensagemEmTodasAsRegras() {
        for (ValorDecimal.Regra regra : ValorDecimal.Regra.values()) {
            erro("12;5", regra, ValorDecimal.ERRO_FORMATO);
            erro("12,5,1", regra, ValorDecimal.ERRO_FORMATO);
            erro("1e3", regra, ValorDecimal.ERRO_FORMATO);
            erro("+5", regra, ValorDecimal.ERRO_FORMATO);
            erro(",5", regra, ValorDecimal.ERRO_FORMATO);
            erro("abc", regra, ValorDecimal.ERRO_FORMATO);
        }
    }

    @Test public void rejeitaVazio() {
        erro("", ValorDecimal.Regra.CONSUMO, ValorDecimal.ERRO_VAZIO);
        erro("   ", ValorDecimal.Regra.CONSUMO, ValorDecimal.ERRO_VAZIO);
        erro(null, ValorDecimal.Regra.CONSUMO, ValorDecimal.ERRO_VAZIO);
    }

    @Test public void respeitaCasasDecimaisELimite() {
        erro("1,0001", ValorDecimal.Regra.CONSUMO, "Use no máximo 3 casas decimais.");
        assertFalse(ValorDecimal.validar("1,000100", ValorDecimal.Regra.CONSUMO).valido());
        assertTrue(ValorDecimal.validar("1,0000", ValorDecimal.Regra.CONSUMO).valido());
        assertTrue(ValorDecimal.validar("1000000", ValorDecimal.Regra.CONSUMO).valido());
        erro("1000000,001", ValorDecimal.Regra.CONSUMO, "O valor máximo é 1.000.000.");
        erro("1000,01", ValorDecimal.Regra.TARIFA, "O valor máximo é 1.000.");
    }

    @Test public void filtroRemoveCaracteresColados() {
        assertEquals("125", ValorDecimal.filtrar("-12;5"));
        assertEquals("1.234,5", ValorDecimal.filtrar("R$ 1.234,5"));
    }
}
