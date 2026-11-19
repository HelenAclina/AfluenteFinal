package com.example.afluente.ui;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Padrão único dos formulários do AFLUENTE:
 * <ol>
 *   <li>o botão de confirmação fica desativado até todos os campos estarem válidos;</li>
 *   <li>a validação acontece enquanto a pessoa digita, mas a mensagem de erro só aparece após uma
 *   pequena espera sem digitar (ou ao sair do campo), para não acusar erro no meio da digitação;</li>
 *   <li>campos ainda não editados não mostram erro, mesmo inválidos (evita a tela "toda vermelha").</li>
 * </ol>
 * A validação final continua sendo repetida no clique e nas camadas de serviço/DAO.
 */
public final class FormularioValidado {
    /** Devolve null quando o texto é válido ou a mensagem de erro. */
    public interface Regra { String validar(String texto); }

    /** Condição que não depende de texto (ex.: uma opção selecionada). */
    public interface Condicao { boolean atendida(); }

    /** Espera após a última tecla antes de exibir a mensagem de erro. */
    public static final long ESPERA_MS = 600;

    private final View botao;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Campo> campos = new ArrayList<>();
    private final List<Condicao> condicoes = new ArrayList<>();
    private final Runnable exibirErros = this::atualizarMensagens;
    private boolean ocupado;

    public FormularioValidado(View botao) { this.botao = botao; }

    /** Campo com o erro exibido no próprio EditText. */
    public FormularioValidado campo(EditText campo, Regra regra) { return adicionar(campo, null, regra); }

    /** Campo com o erro exibido no TextInputLayout (padrão Material). */
    public FormularioValidado campo(EditText campo, TextInputLayout container, Regra regra) { return adicionar(campo, container, regra); }

    public FormularioValidado condicao(Condicao condicao) { condicoes.add(condicao); reavaliar(); return this; }

    /** Recalcula o estado do botão (chamar quando uma condição externa mudar). */
    public void reavaliar() {
        boolean valido = !ocupado;
        for (Campo c : campos) valido &= c.regra.validar(c.texto()) == null;
        for (Condicao c : condicoes) valido &= c.atendida();
        botao.setEnabled(valido);
        // Botões desenhados à mão não têm aparência de desativado; os MaterialButton já têm.
        if (!(botao instanceof MaterialButton)) botao.setAlpha(valido ? 1f : 0.5f);
    }

    /** Desativa o botão enquanto uma operação (login, gravação) está em andamento. */
    public void setOcupado(boolean ocupado) { this.ocupado = ocupado; reavaliar(); }

    /** Mostra todos os erros de uma vez e informa se o formulário é válido (rede de segurança no clique). */
    public boolean validarTudo() {
        boolean valido = true;
        Campo primeiroInvalido = null;
        for (Campo c : campos) {
            c.editado = true;
            String erro = c.regra.validar(c.texto());
            c.mostrar(erro);
            if (erro != null && primeiroInvalido == null) primeiroInvalido = c;
            valido &= erro == null;
        }
        for (Condicao c : condicoes) valido &= c.atendida();
        if (primeiroInvalido != null) primeiroInvalido.campo.requestFocus();
        return valido;
    }

    /** Remove esperas pendentes (chamar ao fechar a tela ou o diálogo). */
    public void liberar() { handler.removeCallbacks(exibirErros); }

    /** Acrescenta " *" ao rótulo e anuncia "obrigatório" no leitor de tela. */
    public static void marcarObrigatorio(TextView rotulo) {
        CharSequence atual = rotulo.getText();
        String texto = atual.toString().endsWith(" *") ? atual.toString().substring(0, atual.length() - 2) : atual.toString();
        SpannableString marcado = new SpannableString(texto + " *");
        marcado.setSpan(new ForegroundColorSpan(Color.parseColor("#D32F2F")), texto.length() + 1, texto.length() + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        rotulo.setText(marcado);
        rotulo.setContentDescription(texto + ", obrigatório");
    }

    /** Marca como obrigatório o hint de um TextInputLayout. */
    public static void marcarObrigatorio(TextInputLayout container) {
        CharSequence hint = container.getHint();
        if (hint != null && !hint.toString().endsWith(" *")) container.setHint(hint + " *");
    }

    private FormularioValidado adicionar(EditText campo, TextInputLayout container, Regra regra) {
        Campo c = new Campo(campo, container, regra);
        campos.add(c);
        campo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                c.editado = true;
                c.mostrar(null);       // enquanto digita, não acusa erro
                reavaliar();           // o botão reflete o estado na hora
                handler.removeCallbacks(exibirErros);
                handler.postDelayed(exibirErros, ESPERA_MS);
            }
        });
        View.OnFocusChangeListener anterior = campo.getOnFocusChangeListener();
        campo.setOnFocusChangeListener((v, temFoco) -> {
            if (anterior != null) anterior.onFocusChange(v, temFoco);
            if (!temFoco && c.editado) c.mostrar(c.regra.validar(c.texto()));
        });
        reavaliar();
        return this;
    }

    /** Atualiza as mensagens de todos os campos já editados (ex.: senha e confirmação dependem uma da outra). */
    private void atualizarMensagens() {
        for (Campo c : campos) if (c.editado) c.mostrar(c.regra.validar(c.texto()));
    }

    private static final class Campo {
        final EditText campo;
        final TextInputLayout container;
        final Regra regra;
        boolean editado;

        Campo(EditText campo, TextInputLayout container, Regra regra) {
            this.campo = campo; this.container = container; this.regra = regra;
        }

        String texto() { return campo.getText() == null ? "" : campo.getText().toString(); }

        void mostrar(String erro) {
            if (container != null) {
                container.setError(erro); // mantém o espaço do erro: o layout não "pula"
            } else if (erro == null) {
                if (campo.getError() != null) campo.setError(null);
            } else {
                campo.setError(erro);
            }
        }
    }
}
