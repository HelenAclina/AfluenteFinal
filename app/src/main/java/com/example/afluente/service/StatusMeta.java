package com.example.afluente.service;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Classifica o consumo do mês em relação à meta em faixas, em vez de só "Dentro da meta" /
 * "Elevado". Assim, exceder em 1 kWh e exceder em 165 kWh deixam de ter o mesmo rótulo.
 * Java puro, testado localmente.
 * <pre>
 *   consumo ÷ meta ≤ 80%          → Dentro da meta     (verde)
 *   80% &lt; consumo ÷ meta ≤ 100%  → Perto do limite    (amarelo)
 *   100% &lt; consumo ÷ meta ≤ 120% → Acima da meta      (laranja)
 *   consumo ÷ meta &gt; 120%         → Muito acima da meta (vermelho)
 * </pre>
 */
public final class StatusMeta {
    /** A partir daqui o consumo está "perto do limite". */
    public static final double LIMITE_PERTO = 0.80;
    /** Acima daqui (20% além da meta) o consumo está "muito acima". */
    public static final double LIMITE_MUITO_ACIMA = 1.20;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public enum Nivel {
        SEM_META("Sem meta"),
        SEM_REGISTROS("Sem registros"),
        DENTRO("Dentro da meta"),
        PERTO("Perto do limite"),
        ACIMA("Acima da meta"),
        MUITO_ACIMA("Muito acima da meta");

        public final String rotulo;

        Nivel(String rotulo) { this.rotulo = rotulo; }

        public boolean excedeu() { return this == ACIMA || this == MUITO_ACIMA; }
    }

    private StatusMeta() { }

    public static Nivel classificar(Double consumo, Double meta) {
        if (meta == null || meta <= 0) return Nivel.SEM_META;
        if (consumo == null) return Nivel.SEM_REGISTROS;
        if (!excedeu(consumo, meta)) return consumo / meta > LIMITE_PERTO ? Nivel.PERTO : Nivel.DENTRO;
        return consumo / meta > LIMITE_MUITO_ACIMA ? Nivel.MUITO_ACIMA : Nivel.ACIMA;
    }

    /** Ignora diferenças mínimas da soma em ponto flutuante (ex.: 0,1 + 0,2). */
    public static boolean excedeu(double consumo, double meta) {
        return consumo > meta && consumo - meta > Math.max(1e-12, 16 * Math.ulp(Math.max(Math.abs(consumo), Math.abs(meta))));
    }

    /**
     * Texto do excesso, com a proporção: "Excedeu em 1 kWh (1% acima da meta)" ou, quando o
     * consumo passa do dobro, "Excedeu em 165 kWh (166 vezes a meta)". Null se não excedeu.
     */
    public static String textoExcesso(double consumo, double meta, String unidade) {
        if (meta <= 0 || !excedeu(consumo, meta)) return null;
        NumberFormat n = NumberFormat.getNumberInstance(PT_BR);
        n.setMaximumFractionDigits(2);
        double razao = consumo / meta;
        String proporcao;
        if (razao >= 2) {
            NumberFormat v = NumberFormat.getNumberInstance(PT_BR);
            v.setMaximumFractionDigits(1);
            proporcao = v.format(razao) + " vezes a meta";
        } else {
            NumberFormat p = NumberFormat.getNumberInstance(PT_BR);
            p.setMaximumFractionDigits(1);
            proporcao = p.format((razao - 1) * 100) + "% acima da meta";
        }
        return "Excedeu em " + n.format(consumo - meta) + " " + unidade + " (" + proporcao + ")";
    }
}
