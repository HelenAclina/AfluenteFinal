package com.example.afluente.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de validação dos formulários de texto (nome, e-mail e senha), em Java puro para poder
 * ser testada localmente. Cada método devolve {@code null} quando o valor é válido ou a mensagem
 * de erro a exibir no campo.
 */
public final class RegrasFormulario {
    public static final int SENHA_MINIMA = 6; // exigência do Firebase Authentication
    public static final int NOME_MAXIMO = 100;

    /** Formato usual de e-mail: usuário@domínio.extensão (equivalente prático ao Patterns do Android). */
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    /** Senhas muito usadas: o indicador as classifica como fracas mesmo que tenham 6+ caracteres. */
    private static final Set<String> SENHAS_COMUNS = new HashSet<>(Arrays.asList(
            "123456", "1234567", "12345678", "123456789", "1234567890", "654321", "000000", "111111",
            "abcdef", "abc123", "senha", "senha1", "senha123", "password", "qwerty", "afluente"));

    private RegrasFormulario() { }

    public static String nome(String texto) {
        String nome = texto == null ? "" : texto.trim();
        if (nome.isEmpty()) return "Informe seu nome.";
        if (nome.length() > NOME_MAXIMO) return "Use no máximo " + NOME_MAXIMO + " caracteres.";
        return null;
    }

    public static String email(String texto) {
        String email = texto == null ? "" : texto.trim();
        if (email.isEmpty()) return "Informe seu e-mail.";
        if (!EMAIL.matcher(email).matches()) return "Informe um e-mail válido (ex.: nome@dominio.com).";
        return null;
    }

    /** Senha para entrar: só obrigatória; quem decide se está correta é o Firebase. */
    public static String senhaInformada(String texto) {
        return texto == null || texto.isEmpty() ? "Informe sua senha." : null;
    }

    /**
     * Senha nova (cadastro). Regras da equipe: mínimo de 6 caracteres (também exigido pelo Firebase),
     * ao menos uma letra maiúscula e ao menos um caractere especial. A força continua informativa.
     */
    public static String senhaNova(String texto) {
        if (texto == null || texto.isEmpty()) return "Crie uma senha.";
        java.util.List<String> faltando = new java.util.ArrayList<>();
        if (texto.length() < SENHA_MINIMA) faltando.add("pelo menos " + SENHA_MINIMA + " caracteres");
        if (!temMaiuscula(texto)) faltando.add("uma letra maiúscula");
        if (!temEspecial(texto)) faltando.add("um caractere especial (ex.: ! @ # $ %)");
        if (faltando.isEmpty()) return null;
        return "A senha precisa ter " + juntar(faltando) + ".";
    }

    /** Letra maiúscula, incluindo acentuadas (ex.: "É"). */
    public static boolean temMaiuscula(String texto) {
        return texto != null && texto.codePoints().anyMatch(Character::isUpperCase);
    }

    /** Qualquer caractere que não seja letra, número ou espaço (ex.: ! @ # $ % & * . -). */
    public static boolean temEspecial(String texto) {
        return texto != null && texto.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }

    private static String juntar(java.util.List<String> itens) {
        if (itens.size() == 1) return itens.get(0);
        return String.join(", ", itens.subList(0, itens.size() - 1)) + " e " + itens.get(itens.size() - 1);
    }

    public static String confirmacao(String senha, String confirmacao) {
        if (confirmacao == null || confirmacao.isEmpty()) return "Confirme sua senha.";
        if (!confirmacao.equals(senha)) return "As senhas não coincidem.";
        return null;
    }

    /** Força da senha exibida no cadastro. É só um indicador visual: não bloqueia o envio. */
    public enum Forca {
        VAZIA(0, ""), CURTA(1, "Muito curta"), FRACA(1, "Fraca"), MEDIA(2, "Média"), FORTE(3, "Forte");

        public final int nivel; // 0 a 3, usado nas barras do indicador
        public final String rotulo;

        Forca(int nivel, String rotulo) { this.nivel = nivel; this.rotulo = rotulo; }
    }

    public static Forca forca(String senha) {
        if (senha == null || senha.isEmpty()) return Forca.VAZIA;
        if (senha.length() < SENHA_MINIMA) return Forca.CURTA;
        if (SENHAS_COMUNS.contains(senha.toLowerCase(Locale.ROOT)) || senha.chars().distinct().count() <= 2) return Forca.FRACA;
        int pontos = 0;
        if (senha.length() >= 8) pontos++;
        if (senha.length() >= 12) pontos++;
        if (senha.matches(".*[a-z].*") && senha.matches(".*[A-Z].*")) pontos++;
        if (senha.matches(".*[0-9].*")) pontos++;
        if (senha.matches(".*[^A-Za-z0-9].*")) pontos++;
        if (pontos <= 1) return Forca.FRACA;
        if (pontos <= 3) return Forca.MEDIA;
        return Forca.FORTE;
    }
}
