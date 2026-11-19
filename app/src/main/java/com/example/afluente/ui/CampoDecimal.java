package com.example.afluente.ui;

import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.method.DigitsKeyListener;
import android.widget.EditText;

import com.example.afluente.service.ValorDecimal;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;

/** Configura e lê campos numéricos decimais da mesma forma em todas as telas. */
final class CampoDecimal {
    private CampoDecimal() { }

    /**
     * Teclado numérico decimal, sem sinal, aceitando vírgula ou ponto. O filtro também limpa o
     * texto colado, que não passa pelo teclado.
     */
    static void configurar(EditText campo) {
        campo.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        campo.setKeyListener(DigitsKeyListener.getInstance(ValorDecimal.CARACTERES_PERMITIDOS));
        campo.setFilters(new InputFilter[]{new FiltroDecimal()});
        campo.setSingleLine(true);
    }

    /** Valida o campo; em caso de erro, mostra a mensagem no TextInputLayout e devolve null. */
    static BigDecimal ler(EditText campo, TextInputLayout container, ValorDecimal.Regra regra) {
        container.setError(null);
        ValorDecimal.Resultado r = ValorDecimal.validar(texto(campo), regra);
        if (!r.valido()) container.setError(r.erro);
        return r.valor;
    }

    /** Variante para campos criados em diálogos, sem TextInputLayout. */
    static BigDecimal ler(EditText campo, ValorDecimal.Regra regra) {
        campo.setError(null);
        ValorDecimal.Resultado r = ValorDecimal.validar(texto(campo), regra);
        if (!r.valido()) campo.setError(r.erro);
        return r.valor;
    }

    static String texto(EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    private static final class FiltroDecimal implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            CharSequence trecho = source.subSequence(start, end);
            String limpo = ValorDecimal.filtrar(trecho);
            return limpo.length() == trecho.length() ? null : limpo;
        }
    }
}
