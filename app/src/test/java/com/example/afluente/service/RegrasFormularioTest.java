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

    @Test public void senhaNovaExigeTamanhoMaiusculaEEspecial() {
        assertEquals("Crie uma senha.", RegrasFormulario.senhaNova(""));
        assertEquals("A senha precisa ter pelo menos 6 caracteres, uma letra maiúscula e um caractere especial (ex.: ! @ # $ %).",
                RegrasFormulario.senhaNova("abc"));
        assertEquals("A senha precisa ter uma letra maiúscula.", RegrasFormulario.senhaNova("agua#2026"));
        assertEquals("A senha precisa ter um caractere especial (ex.: ! @ # $ %).", RegrasFormulario.senhaNova("Agua2026"));
        assertEquals("A senha precisa ter pelo menos 6 caracteres.", RegrasFormulario.senhaNova("Ab#1"));
        assertNull(RegrasFormulario.senhaNova("Agua#2026"));
        assertNull(RegrasFormulario.senhaNova("Ébano.1")); // maiúscula acentuada e ponto contam
        assertEquals("A senha precisa ter um caractere especial (ex.: ! @ # $ %).", RegrasFormulario.senhaNova("Agua 2026")); // espaço não conta
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
