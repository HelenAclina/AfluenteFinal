package com.example.afluente.service;

import android.content.Context;

import com.example.afluente.data.ContaDAO;
import com.example.afluente.data.DatabaseHelper;

/**
 * Apaga tudo o que o AFLUENTE guarda neste aparelho sobre a conta (LGPD, art. 18, VI).
 * <p>
 * Ordem do fluxo completo (ver PerfilFragment): 1) reautenticar com a senha; 2) excluir a conta
 * no Firebase Authentication; 3) só então apagar os dados locais com este serviço. Assim, se a
 * exclusão remota falhar, nada local é perdido e a pessoa pode tentar de novo.
 */
public final class ExclusaoContaService {
    /** Preferências do app (rascunhos do cadastro e controle de permissão de notificação). Nomes iguais aos de OnboardingStep4Activity e DashboardActivity. */
    private static final String[] PREFERENCIAS = {"onboarding_draft", "notificacoes"};

    private ExclusaoContaService() { }

    /** @return true se todos os dados locais foram apagados. Chamar fora da thread principal. */
    public static boolean apagarDadosDoAparelho(Context context, String uid) {
        Context app = context.getApplicationContext();
        boolean ok = true;
        DatabaseHelper helper = new DatabaseHelper(app);
        try {
            ContaDAO.Resultado r = new ContaDAO(helper).excluirDadosLocais(uid);
            ok &= FotoPerfilService.excluir(app, r.fotoUri);
        } catch (RuntimeException e) {
            ok = false;
        } finally { helper.close(); }
        for (String nome : PREFERENCIAS) ok &= app.getSharedPreferences(nome, Context.MODE_PRIVATE).edit().clear().commit();
        NotificacaoService.cancelarTodas(app);
        return ok;
    }
}
