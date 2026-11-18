package com.example.afluente.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Validação única dos campos numéricos digitados pela usuária (consumo, estimativa, meta e tarifa).
 * Classe Java pura, sem dependência do Android, para poder ser testada localmente.
 * <p>
 * Formato aceito: dígitos com no máximo um separador decimal (vírgula ou ponto), por exemplo
 * "12", "12,5" ou "0.894". Não aceita sinal, separador de milhar, espaços internos nem notação
 * científica. Cada erro tem uma mensagem própria, para que a mesma entrada inválida produza a
 * mesma mensagem em qualquer tela.
 */
public final class ValorDecimal {

    /** Caracteres que o teclado e a colagem podem inserir nos campos numéricos. */
    public static final String CARACTERES_PERMITIDOS = "0123456789,.";

    /** Regras de cada tipo de campo: limite superior e casas decimais. */
    public enum Regra {
        CONSUMO(ConsumoService.CONSUMO_MAXIMO, 3),
        META(new BigDecimal("1000000000"), 3),
        TARIFA(new BigDecimal("1000"), 6);

        final BigDecimal maximo;
        final int casasDecimais;

        Regra(BigDecimal maximo, int casasDecimais) {
            this.maximo = maximo;
            this.casasDecimais = casasDecimais;
        }
    }

    /** Resultado da validação: {@link #valor} preenchido ou {@link #erro} com a mensagem. */
    public static final class Resultado {
        public final BigDecimal valor;
        public final String erro;

        private Resultado(BigDecimal valor, String erro) {
            this.valor = valor;
            this.erro = erro;
        }

        public boolean valido() { return erro == null; }
    }

    public static final String ERRO_VAZIO = "Informe um valor.";
    public static final String ERRO_NEGATIVO = "O valor não pode ser negativo.";
    public static final String ERRO_FORMATO = "Use apenas números, com vírgula para os decimais (ex.: 12,5).";
    public static final String ERRO_ZERO = "O valor deve ser maior que zero.";

    private ValorDecimal() { }

    /** Valida o texto bruto do campo e devolve o número ou a mensagem de erro. */
    public static Resultado validar(String bruto, Regra regra) {
        String texto = bruto == null ? "" : bruto.trim();
        if (texto.isEmpty()) return erro(ERRO_VAZIO);
        if (texto.startsWith("-") || texto.startsWith("−")) return erro(ERRO_NEGATIVO);
        if (!texto.matches("[0-9]+([.,][0-9]+)?")) return erro(ERRO_FORMATO);

        BigDecimal valor = new BigDecimal(texto.replace(',', '.'));
        if (valor.signum() <= 0) return erro(ERRO_ZERO);
        if (valor.stripTrailingZeros().scale() > regra.casasDecimais)
            return erro("Use no máximo " + regra.casasDecimais + " casas decimais.");
        if (valor.compareTo(regra.maximo) > 0) return erro("O valor máximo é " + formatar(regra.maximo) + ".");
        return new Resultado(valor, null);
    }

    /** Remove do texto colado tudo o que não for dígito, vírgula ou ponto. */
    public static String filtrar(CharSequence texto) {
        StringBuilder limpo = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (CARACTERES_PERMITIDOS.indexOf(c) >= 0) limpo.append(c);
        }
        return limpo.toString();
    }

    private static Resultado erro(String mensagem) { return new Resultado(null, mensagem); }

    private static String formatar(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        return formato.format(valor);
    }
}
