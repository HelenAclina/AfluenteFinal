package com.example.afluente.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Testes locais (sem celular) das regras dos formulários de texto e do indicador de senha. */
public class RegrasFormularioTest {
    @Test public void nome() {
        assertNull(RegrasFormulario.nome("Helen Aclina"));
        assertEquals("Informe seu nome.", RegrasFormulario.nome("   "));
        assertNotNull(RegrasFormulario.nome(new String(new char[101]).replace('\0', 'a')));
    }

    @Test public void email() {
        assertNull(RegrasFormulario.email("helen@exemplo.com.br"));
        assertNull(RegrasFormulario.email("  a.b+tcc@etec.sp.gov.br "));
        assertEquals("Informe seu e-mail.", RegrasFormulario.email(""));
        for (String invalido : new String[]{"helen", "helen@", "helen@exemplo", "@exemplo.com", "helen@@exemplo.com", "helen@exemplo.c", "he len@exemplo.com"})
            assertEquals(invalido, "Informe um e-mail válido (ex.: nome@dominio.com).", RegrasFormulario.email(invalido));
    }

    @Test public void senhaNovaSoBloqueiaAbaixoDoMinimoDoFirebase() {
        assertEquals("Crie uma senha.", RegrasFormulario.senhaNova(""));
        assertEquals("A senha deve ter pelo menos 6 caracteres.", RegrasFormulario.senhaNova("12345"));
        assertNull(RegrasFormulario.senhaNova("123456")); // fraca, mas aceita: o indicador é só visual
    }

    @Test public void confirmacao() {
        assertEquals("Confirme sua senha.", RegrasFormulario.confirmacao("abcdef", ""));
        assertEquals("As senhas não coincidem.", RegrasFormulario.confirmacao("abcdef", "abcdeg"));
        assertNull(RegrasFormulario.confirmacao("abcdef", "abcdef"));
    }

    @Test public void forcaDaSenha() {
        assertEquals(RegrasFormulario.Forca.VAZIA, RegrasFormulario.forca(""));
        assertEquals(RegrasFormulario.Forca.CURTA, RegrasFormulario.forca("Ab1!"));
        assertEquals(RegrasFormulario.Forca.FRACA, RegrasFormulario.forca("123456"));   // senha comum
        assertEquals(RegrasFormulario.Forca.FRACA, RegrasFormulario.forca("aaaaaaaa")); // caractere repetido
        assertEquals(RegrasFormulario.Forca.FRACA, RegrasFormulario.forca("abcdefg"));
        assertEquals(RegrasFormulario.Forca.MEDIA, RegrasFormulario.forca("agua2026x"));
        assertEquals(RegrasFormulario.Forca.FORTE, RegrasFormulario.forca("Agua#Energia2026"));
    }
}
