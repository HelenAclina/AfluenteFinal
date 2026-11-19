package com.example.afluente.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.afluente.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Botão de informação (ⓘ) dos campos copiados da conta: abre um modal que mostra onde ficam o
 * consumo e a tarifa.
 * <p>
 * A conta exibida é uma ilustração genérica, sem marca de concessionária nem dados pessoais.
 * Para usar uma foto real, adicione {@code res/drawable/foto_conta_agua.jpg} e/ou
 * {@code foto_conta_energia.jpg} (com nome, endereço e nº de instalação borrados): se o arquivo
 * existir, ele aparece no topo do modal automaticamente.
 */
public final class AjudaConta {
    /** Qual informação o campo pede. */
    public enum Campo { CONSUMO, TARIFA }

    /** Informa, no momento do toque, se o campo é de energia (o registro alterna água/energia). */
    public interface Recurso { boolean energia(); }

    private static final String SUFIXO_DESCRICAO = ". Toque para ver onde encontrar na conta";

    private AjudaConta() { }

    /** Adiciona o ⓘ ao rótulo do campo e abre o modal ao tocar. */
    public static void anexar(TextView rotulo, Recurso recurso, Campo campo) {
        rotulo.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_info, 0);
        rotulo.setCompoundDrawablePadding(dp(rotulo.getContext(), 6));
        rotulo.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        rotulo.setMinHeight(dp(rotulo.getContext(), 48)); // área mínima de toque recomendada
        rotulo.setClickable(true);
        rotulo.setFocusable(true);
        rotulo.setOnClickListener(v -> mostrar(v.getContext(), recurso.energia(), campo));
        atualizarDescricao(rotulo);
    }

    /** Recalcula a descrição para o TalkBack (chamar depois de trocar o texto do rótulo). */
    public static void atualizarDescricao(TextView rotulo) {
        CharSequence base = rotulo.getContentDescription() != null ? rotulo.getContentDescription() : rotulo.getText();
        String texto = base.toString().replace(SUFIXO_DESCRICAO, "");
        rotulo.setContentDescription(texto + SUFIXO_DESCRICAO);
    }

    public static void mostrar(Context context, boolean energia, Campo campo) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout conteudo = new LinearLayout(context);
        conteudo.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        conteudo.setPadding(pad, dp(context, 8), pad, 0);
        scroll.addView(conteudo);

        int foto = context.getResources().getIdentifier(energia ? "foto_conta_energia" : "foto_conta_agua", "drawable", context.getPackageName());
        if (foto != 0) {
            ImageView imagem = new ImageView(context);
            imagem.setImageResource(foto);
            imagem.setAdjustViewBounds(true);
            imagem.setContentDescription(energia ? "Foto de uma conta de energia" : "Foto de uma conta de água");
            conteudo.addView(imagem, new LinearLayout.LayoutParams(-1, -2));
            espaco(conteudo, 12);
        }
        conteudo.addView(energia ? contaEnergia(context, campo) : contaAgua(context, campo));
        espaco(conteudo, 12);
        TextView dica = texto(context, energia ? dicaEnergia(campo) : dicaAgua(campo), 14, 0, false);
        dica.setTextColor(context.getColor(R.color.onboarding_text));
        conteudo.addView(dica);
        espaco(conteudo, 8);
        TextView aviso = texto(context, context.getString(R.string.bill_help_illustration), 12, 0, false);
        aviso.setTextColor(context.getColor(R.color.onboarding_muted));
        conteudo.addView(aviso);

        new MaterialAlertDialogBuilder(context)
                .setTitle(energia ? R.string.bill_help_energy_title : R.string.bill_help_water_title)
                .setView(scroll)
                .setPositiveButton(R.string.bill_help_close, null)
                .show();
    }

    private static String dicaAgua(Campo campo) {
        return campo == Campo.CONSUMO
                ? "① Use o consumo do mês em m³ (diferença entre a leitura atual e a anterior)."
                : "② A conta de água costuma cobrar por faixas de consumo, então nem sempre mostra um preço único por m³. "
                + "Para um valor aproximado, divida o total de água e esgoto pelo consumo: R$ 120,00 ÷ 12 m³ = R$ 10,00 por m³.";
    }

    private static String dicaEnergia(Campo campo) {
        return campo == Campo.CONSUMO
                ? "① Use o consumo do mês em kWh (diferença entre a leitura atual e a anterior)."
                : "② Some os preços unitários (R$/kWh) dos itens de energia, já com tributos. "
                + "Se preferir, divida o total da energia pelo consumo: R$ 120,00 ÷ 150 kWh = R$ 0,80 por kWh.";
    }

    /** Conta de água ilustrativa (dados fictícios). */
    private static LinearLayout contaAgua(Context c, Campo campo) {
        int destaque = c.getColor(R.color.water_accent);
        LinearLayout papel = papel(c, "CONTA DE ÁGUA E ESGOTO", "Exemplo ilustrativo");
        linha(papel, c, "Leitura anterior · atual", "1.234 · 1.246", false, 0, null);
        linha(papel, c, "Consumo do mês", "12 m³", campo == Campo.CONSUMO, destaque, "①");
        linha(papel, c, "Água", "R$ 60,00", campo == Campo.TARIFA, destaque, "②");
        linha(papel, c, "Esgoto", "R$ 60,00", campo == Campo.TARIFA, destaque, "②");
        linha(papel, c, "Total a pagar", "R$ 120,00", false, 0, null);
        return papel;
    }

    /** Conta de energia ilustrativa (dados fictícios). */
    private static LinearLayout contaEnergia(Context c, Campo campo) {
        int destaque = c.getColor(R.color.energy_accent);
        LinearLayout papel = papel(c, "CONTA DE ENERGIA ELÉTRICA", "Exemplo ilustrativo");
        linha(papel, c, "Leitura anterior · atual", "8.500 · 8.650", false, 0, null);
        linha(papel, c, "Consumo do mês", "150 kWh", campo == Campo.CONSUMO, destaque, "①");
        linha(papel, c, "Energia · preço unit.", "R$ 0,35 /kWh", campo == Campo.TARIFA, destaque, "②");
        linha(papel, c, "Uso do sistema · preço unit.", "R$ 0,45 /kWh", campo == Campo.TARIFA, destaque, "②");
        linha(papel, c, "Total a pagar", "R$ 120,00", false, 0, null);
        return papel;
    }

    private static LinearLayout papel(Context c, String titulo, String subtitulo) {
        LinearLayout papel = new LinearLayout(c);
        papel.setOrientation(LinearLayout.VERTICAL);
        papel.setBackground(fundo(c, Color.WHITE, 0xFFBDBDBD, 8));
        papel.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 8));
        papel.setImportantForAccessibility(LinearLayout.IMPORTANT_FOR_ACCESSIBILITY_YES);
        TextView cabecalho = texto(c, titulo, 13, 0xFF424242, true);
        papel.addView(cabecalho);
        TextView sub = texto(c, subtitulo, 11, 0xFF757575, false);
        papel.addView(sub);
        espaco(papel, 8);
        return papel;
    }

    private static void linha(LinearLayout papel, Context c, String rotulo, String valor, boolean destacar, int cor, String marcador) {
        LinearLayout linha = new LinearLayout(c);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        linha.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        if (destacar) linha.setBackground(fundo(c, (cor & 0x00FFFFFF) | 0x26000000, cor, 6));
        TextView nome = texto(c, (destacar ? marcador + " " : "") + rotulo, 13, 0xFF424242, destacar);
        linha.addView(nome, new LinearLayout.LayoutParams(0, -2, 1));
        linha.addView(texto(c, valor, 13, 0xFF212121, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(c, 2);
        papel.addView(linha, params);
    }

    private static TextView texto(Context c, String valor, int sp, int cor, boolean negrito) {
        TextView t = new TextView(c);
        t.setText(valor);
        t.setTextSize(sp);
        if (cor != 0) t.setTextColor(cor);
        if (negrito) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private static GradientDrawable fundo(Context c, int cor, int borda, int raio) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(cor);
        d.setCornerRadius(dp(c, raio));
        d.setStroke(dp(c, 1.5f), borda);
        return d;
    }

    private static void espaco(LinearLayout pai, int altura) {
        android.view.View v = new android.view.View(pai.getContext());
        pai.addView(v, new LinearLayout.LayoutParams(1, dp(pai.getContext(), altura)));
    }

    private static int dp(Context c, float valor) {
        return (int) (valor * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
