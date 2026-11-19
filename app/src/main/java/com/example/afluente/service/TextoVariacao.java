package com.example.afluente.service;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Frases que explicam os percentuais em linguagem simples, no lugar de "↓ 73,0%".
 * Java puro, para poder ser testado localmente.
 * <p>
 * Cuidado de honestidade: o painel compara o mês atual (ainda em andamento) com o mês anterior
 * inteiro. Nesse caso a frase diz "Até agora…", porque dizer "você economizou 73%" no dia 5 do
 * mês seria falso: o mês ainda não terminou.
 */
public final class TextoVariacao {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private TextoVariacao() { }

    /** Variação percentual de {@code anterior} para {@code atual}; null se não houver base válida. */
    public static Double percentual(Double anterior, Double atual) {
        if (anterior == null || atual == null || anterior <= 0) return null;
        double variacao = (atual - anterior) / anterior * 100;
        return Double.isFinite(variacao) ? variacao : null;
    }

    /**
     * Frase de consumo de um recurso ("água" ou "energia").
     *
     * @param mesEmAndamento true quando o período atual ainda não terminou
     */
    public static String consumo(Double anterior, Double atual, String recurso, boolean mesEmAndamento) {
        Double variacao = percentual(anterior, atual);
        if (variacao == null) return "Sem mês anterior para comparar";
        String pct = formatarPct(Math.abs(variacao));
        if (pct.equals("0%")) return "Mesmo consumo de " + recurso + " do mês anterior";
        if (variacao < 0) {
            return mesEmAndamento
                    ? "Até agora, você usou " + pct + " menos " + recurso + " que no mês anterior"
                    : "Você economizou " + pct + " de " + recurso + " em relação ao mês anterior";
        }
        return mesEmAndamento
                ? "Até agora, você já usou " + pct + " mais " + recurso + " que no mês anterior"
                : "Você consumiu " + pct + " mais " + recurso + " em relação ao mês anterior";
    }

    /** Frase do gasto estimado em reais (valor já formatado como moeda). */
    public static String gasto(Double anterior, Double atual, boolean mesEmAndamento) {
        if (atual == null) return "Cadastre consumos e tarifas para calcular";
        if (anterior == null) return "Sem mês anterior para comparar";
        double diferenca = atual - anterior;
        String valor = NumberFormat.getCurrencyInstance(PT_BR).format(Math.abs(diferenca)).replace(' ', ' ');
        if (Math.abs(diferenca) < 0.005) return "Mesmo gasto do mês anterior";
        if (diferenca < 0) {
            return mesEmAndamento ? "Até agora, " + valor + " a menos que no mês anterior"
                    : "Você economizou " + valor + " em relação ao mês anterior";
        }
        return mesEmAndamento ? "Até agora, " + valor + " a mais que no mês anterior"
                : "Você gastou " + valor + " a mais que no mês anterior";
    }

    /** Comparação entre dois períodos escolhidos pela usuária (ambos fechados). */
    public static String periodos(Double primeiro, Double segundo, String rotuloPrimeiro, String rotuloSegundo) {
        Double variacao = percentual(primeiro, segundo);
        if (variacao == null) return "Sem comparação";
        String pct = formatarPct(Math.abs(variacao));
        if (pct.equals("0%")) return rotuloSegundo + " igual a " + rotuloPrimeiro;
        return rotuloSegundo + ": " + pct + (variacao < 0 ? " menos" : " mais") + " que " + rotuloPrimeiro;
    }

    /** Formata como "73%" ou "12,5%" (no máximo uma casa decimal). */
    static String formatarPct(double valor) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(1);
        return f.format(valor) + "%";
    }
}
