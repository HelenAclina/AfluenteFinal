package com.example.afluente.ui;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.PerfilDAO;
import com.example.afluente.service.NotificacaoService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {
    private final ExecutorService themeExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private static final String PREFS = "notificacoes";
    private static final String KEY_PERMISSAO_PEDIDA = "permissao_pedida";
    private String uid;
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                // Concedida: envia agora os alertas que ficaram pendentes por falta de permissão.
                if (granted && uid != null && !destroyed) enviarPendentes(uid);
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dashboard);
        EdgeInsets.aplicar(this);
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.dashboard_container, new DashboardFragment()).commit();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        uid = user.getUid();
        String uid = this.uid;
        android.content.Context context = getApplicationContext();
        NotificacaoService.criarCanal(context);
        themeExecutor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(context);
            try {
                PerfilDAO.Perfil perfil = new PerfilDAO(helper).carregar(uid);
                boolean dark = perfil != null && perfil.temaEscuro;
                new Handler(Looper.getMainLooper()).post(() -> {
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (destroyed || current == null || !uid.equals(current.getUid())) return;
                    int mode = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                    if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
                    if (perfil == null || perfil.notificacoes) pedirPermissaoNotificacoes();
                });
            } catch (RuntimeException ignored) {
                // Em caso de erro, mantém o tema atual sem alterar a preferência.
            } finally { helper.close(); }
        });
    }

    /** Android 13+: pede a permissão uma única vez; depois, a usuária pode ativá-la pelo Perfil. */
    private void pedirPermissaoNotificacoes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || NotificacaoService.temPermissao(this)) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PERMISSAO_PEDIDA, false)) return;
        prefs.edit().putBoolean(KEY_PERMISSAO_PEDIDA, true).apply();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void enviarPendentes(String uid) {
        android.content.Context context = getApplicationContext();
        themeExecutor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(context);
            try { NotificacaoService.enviarPendentes(context, helper, uid); } finally { helper.close(); }
        });
    }

    @Override protected void onDestroy() { destroyed = true; themeExecutor.shutdown(); super.onDestroy(); }
}
