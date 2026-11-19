package com.example.afluente.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.afluente.MainActivity;
import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.PerfilDAO;
import com.example.afluente.service.ExclusaoContaService;
import com.example.afluente.service.FotoPerfilService;
import com.example.afluente.service.NotificacaoService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import com.example.afluente.service.RegrasFormulario;
import com.example.afluente.service.ValorDecimal;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Firebase identifica a conta; tarifas e preferências permanecem neste aparelho. */
public class PerfilFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private DatabaseHelper helper;
    private PerfilDAO dao;
    private View root;
    private FirebaseUser user;
    private PerfilDAO.Perfil perfil;
    private boolean applying;
    private final Locale locale = new Locale("pt", "BR");
    private final FotoPerfilService.Listener photoListener = sucesso -> {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (root == null || user == null || current == null || !user.getUid().equals(current.getUid())) return;
        root.findViewById(R.id.avatarPerfil).setEnabled(true);
        if (sucesso) load(); else toast("Não foi possível salvar a foto. Selecione uma imagem de até 25 MB.");
    };
    // GetContent concede acesso temporário à imagem escolhida sem permissões amplas de mídia.
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) enviarAlertasPendentes(); else avisarNotificacoesBloqueadas();
            });
    private final ActivityResultLauncher<String> photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoSelected);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_perfil, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        root = view;
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { requireActivity().finish(); return; }
        executor = Executors.newSingleThreadExecutor();
        helper = new DatabaseHelper(requireContext().getApplicationContext());
        dao = new PerfilDAO(helper);
        String email = user.getEmail() == null ? "—" : user.getEmail();
        text(R.id.tvEmailCabecalho).setText(email);
        text(R.id.tvEmailPerfil).setText(email);
        setName(user.getDisplayName());
        FotoPerfilService.observar(user.getUid(), photoListener);
        view.findViewById(R.id.avatarPerfil).setEnabled(!FotoPerfilService.ocupado(user.getUid()));
        view.findViewById(R.id.avatarPerfil).setOnClickListener(v -> {
            try { photoPicker.launch("image/*"); }
            catch (android.content.ActivityNotFoundException e) { toast("Nenhum seletor de imagens disponível."); }
        });
        view.findViewById(R.id.rowNome).setOnClickListener(v -> editName());
        view.findViewById(R.id.rowEmail).setOnClickListener(v -> editEmail());
        view.findViewById(R.id.rowAlterarSenha).setOnClickListener(v -> resetPassword());
        view.findViewById(R.id.rowTarifaAgua).setOnClickListener(v -> editTariff(true));
        view.findViewById(R.id.rowTarifaEnergia).setOnClickListener(v -> editTariff(false));
        view.findViewById(R.id.btnSairConta).setOnClickListener(v -> confirmLogout());
        view.findViewById(R.id.btnExcluirConta).setOnClickListener(v -> confirmDeleteAccount());
        ((TextView) view.findViewById(R.id.tvVersaoApp)).setText(versaoDoApp());
        SwitchMaterial notifications = view.findViewById(R.id.switchNotificacoes);
        notifications.setOnCheckedChangeListener((button, checked) -> {
            if (applying || perfil == null) return;
            button.setEnabled(false);
            ContentValues changes = new ContentValues(); changes.put("notificacao_ativa", checked ? 1 : 0);
            update(changes, () -> {
                perfil.notificacoes = checked; button.setEnabled(true);
                if (checked) garantirPermissaoNotificacoes(); else NotificacaoService.cancelarTodas(requireContext());
            }, () -> {
                applying = true; button.setChecked(perfil.notificacoes); applying = false; button.setEnabled(true);
            });
        });
        SwitchMaterial darkMode = view.findViewById(R.id.switchTemaEscuro);
        darkMode.setOnCheckedChangeListener((button, checked) -> {
            if (applying || perfil == null) return;
            button.setEnabled(false);
            ContentValues changes = new ContentValues(); changes.put("tema_escuro", checked ? 1 : 0);
            update(changes, () -> {
                perfil.temaEscuro = checked;
                button.setEnabled(true);
                AppCompatDelegate.setDefaultNightMode(checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }, () -> {
                applying = true; button.setChecked(perfil.temaEscuro); applying = false; button.setEnabled(true);
            });
        });
        BottomNavigationView nav = view.findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(R.id.nav_perfil);
        nav.setOnItemSelectedListener(item -> Navegacao.abrir(requireActivity(), item.getItemId()));
        load();
    }

    private void onPhotoSelected(Uri uri) {
        if (uri == null || root == null || user == null) return;
        root.findViewById(R.id.avatarPerfil).setEnabled(false);
        FotoPerfilService.salvar(requireContext(), user.getUid(), user.getDisplayName(), uri);
    }

    private void load() {
        final View view = root;
        final String uid = user.getUid();
        final PerfilDAO currentDao = dao;
        final Context app = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                PerfilDAO.Perfil data = currentDao.carregar(uid);
                Bitmap photo = data == null ? null : FotoPerfilService.carregar(app, data.fotoUri, 256);
                main.post(() -> {
                    if (root != view) { if (photo != null) photo.recycle(); return; }
                    ImageView image = view.findViewById(R.id.imgFotoPerfil);
                    image.setImageBitmap(photo);
                    image.setVisibility(photo == null ? View.GONE : View.VISIBLE);
                    view.findViewById(R.id.tvAvatar).setVisibility(photo == null ? View.VISIBLE : View.GONE);
                    perfil = data == null ? new PerfilDAO.Perfil() : data;
                    if (data == null) perfil.notificacoes = true;
                    String firebaseName = user.getDisplayName();
                    if (firebaseName != null && !firebaseName.trim().isEmpty()) setName(firebaseName);
                    else if (perfil.nome != null && !perfil.nome.trim().isEmpty()) setName(perfil.nome);
                    text(R.id.tvTarifaAgua).setText(tariff(perfil.tarifaAgua, "m³"));
                    text(R.id.tvTarifaEnergia).setText(tariff(perfil.tarifaEnergia, "kWh"));
                    SwitchMaterial notifications = view.findViewById(R.id.switchNotificacoes);
                    applying = true; notifications.setChecked(perfil.notificacoes); applying = false;
                    notifications.setEnabled(true);
                    SwitchMaterial darkMode = view.findViewById(R.id.switchTemaEscuro);
                    applying = true; darkMode.setChecked(perfil.temaEscuro); applying = false;
                    darkMode.setEnabled(true);
                });
            } catch (RuntimeException e) { main.post(() -> { if (root == view) toast("Não foi possível carregar o perfil."); }); }
        });
    }

    @Override public void onResume() {
        super.onResume();
        // A troca de e-mail só se efetiva depois que o link enviado pelo Firebase é confirmado.
        if (user == null || root == null) return;
        final View view = root;
        user.reload().addOnCompleteListener(task -> {
            if (root != view || !task.isSuccessful()) return;
            String email = user.getEmail();
            text(R.id.tvEmailCabecalho).setText(email == null ? "—" : email);
            text(R.id.tvEmailPerfil).setText(email == null ? "—" : email);
        });
    }

    private EditText dialogField(int type, String initial) {
        EditText field = new EditText(requireContext());
        field.setInputType(type);
        field.setSingleLine(true);
        if (initial != null) { field.setText(initial); field.setSelection(field.length()); }
        return field;
    }

    /** Padrão de formulário nos diálogos: "Salvar" só fica ativo com o campo válido. */
    private FormularioValidado validar(AlertDialog dialog, EditText field, FormularioValidado.Regra regra) {
        FormularioValidado form = new FormularioValidado(dialog.getButton(AlertDialog.BUTTON_POSITIVE)).campo(field, regra);
        dialog.setOnDismissListener(d -> form.liberar());
        return form;
    }

    private android.widget.FrameLayout fieldContainer(EditText field) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int pad = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(pad, 0, pad, 0);
        container.addView(field);
        return container;
    }

    private void editName() {
        EditText field = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                user.getDisplayName() != null ? user.getDisplayName() : perfil == null ? "" : perfil.nome);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Alterar nome")
                .setView(fieldContainer(field)).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = field.getText().toString().trim();
            String erroNome = RegrasFormulario.nome(name);
            if (erroNome != null) { field.setError(erroNome); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final View view = root;
            UserProfileChangeRequest request = new UserProfileChangeRequest.Builder().setDisplayName(name).build();
            user.updateProfile(request).addOnCompleteListener(task -> {
                if (root != view) return;
                if (!task.isSuccessful()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("Não foi possível alterar o nome. Verifique a conexão e tente novamente.");
                    return;
                }
                setName(name);
                if (perfil != null) perfil.nome = name;
                dialog.dismiss();
                // Atualização Firebase bem-sucedida: espelha o nome local sem mexer nas tarifas.
                final PerfilDAO currentDao = dao;
                final String uid = user.getUid();
                executor.execute(() -> {
                    try {
                        currentDao.salvarNome(uid, name);
                        main.post(() -> { if (root == view) toast("Nome atualizado."); });
                    } catch (RuntimeException e) {
                        main.post(() -> { if (root == view) toast("Nome atualizado na conta, mas não foi possível atualizar os dados locais."); });
                    }
                });
            });
        }));
        dialog.show();
        validar(dialog, field, RegrasFormulario::nome);
    }

    private void editEmail() {
        EditText field = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, user.getEmail());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Alterar e-mail")
                .setMessage("Enviaremos um link de confirmação para o novo endereço. O e-mail atual continua válido até a confirmação.")
                .setView(fieldContainer(field)).setNegativeButton("Cancelar", null).setPositiveButton("Enviar link", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = field.getText().toString().trim();
            String erroEmail = RegrasFormulario.email(email);
            if (erroEmail != null) { field.setError(erroEmail); return; }
            if (email.equalsIgnoreCase(user.getEmail())) { field.setError("Informe um e-mail diferente do atual."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            sendEmailVerification(email, dialog, false);
        }));
        dialog.show();
        validar(dialog, field, t -> {
            String erro = RegrasFormulario.email(t);
            return erro == null && t.trim().equalsIgnoreCase(user.getEmail()) ? "Informe um e-mail diferente do atual." : erro;
        });
    }

    private void sendEmailVerification(String email, AlertDialog dialog, boolean reauthenticated) {
        final View view = root;
        user.verifyBeforeUpdateEmail(email).addOnCompleteListener(task -> {
            if (root != view) return;
            if (task.isSuccessful()) {
                dialog.dismiss();
                new MaterialAlertDialogBuilder(requireContext()).setTitle("Confirme o novo e-mail")
                        .setMessage("Enviamos um link para " + email + ". Abra-o para confirmar a alteração. Até lá, use o e-mail atual para entrar.")
                        .setPositiveButton("Entendi", null).show();
            } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException && !reauthenticated) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                requestReauthentication(email, dialog);
            } else {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                toast(task.getException() instanceof FirebaseAuthUserCollisionException
                        ? "Este e-mail já está vinculado a outra conta."
                        : "Não foi possível enviar o link. Verifique a conexão e tente novamente.");
            }
        });
    }

    private void requestReauthentication(String email, AlertDialog emailDialog) {
        String currentEmail = user.getEmail();
        if (currentEmail == null || currentEmail.isEmpty()) {
            toast("Saia e entre novamente para confirmar sua identidade."); return;
        }
        EditText password = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Confirme sua identidade")
                .setMessage("Digite sua senha atual para alterar o e-mail.")
                .setView(fieldContainer(password)).setNegativeButton("Cancelar", null).setPositiveButton("Confirmar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String secret = password.getText().toString();
            if (secret.isEmpty()) { password.setError("Informe sua senha."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final View view = root;
            user.reauthenticate(EmailAuthProvider.getCredential(currentEmail, secret)).addOnCompleteListener(task -> {
                password.setText("");
                if (root != view) return;
                if (task.isSuccessful()) {
                    dialog.dismiss();
                    emailDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    sendEmailVerification(email, emailDialog, true);
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("Não foi possível confirmar sua identidade. Confira a senha e a conexão.");
                }
            });
        }));
        dialog.show();
        validar(dialog, password, RegrasFormulario::senhaInformada);
    }

    /** Ao ativar a preferência, garante que o Android também permite exibir as notificações. */
    private void garantirPermissaoNotificacoes() {
        Context context = requireContext();
        if (!NotificacaoService.temPermissao(context) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else if (!NotificacaoService.podeNotificar(context)) {
            avisarNotificacoesBloqueadas();
        } else {
            enviarAlertasPendentes();
        }
    }

    /** Permissão negada ou notificações desligadas no sistema: oferece abrir as configurações do app. */
    private void avisarNotificacoesBloqueadas() {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.notification_permission_denied)
                .setNegativeButton("Agora não", null)
                .setPositiveButton(R.string.notification_open_settings, (d, w) -> {
                    Intent intent;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                    } else {
                        intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", requireContext().getPackageName(), null));
                    }
                    startActivity(intent);
                }).show();
    }

    private void enviarAlertasPendentes() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Context context = requireContext().getApplicationContext();
        String uid = user.getUid();
        if (executor == null || executor.isShutdown()) return;
        executor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(context);
            try { NotificacaoService.enviarPendentes(context, helper, uid); } finally { helper.close(); }
        });
    }

    private void editTariff(boolean water) {
        if (perfil == null) { toast("Aguarde o carregamento do perfil."); return; }
        EditText field = new EditText(requireContext());
        CampoDecimal.configurar(field);
        Double existing = water ? perfil.tarifaAgua : perfil.tarifaEnergia;
        if (existing != null) field.setText(BigDecimal.valueOf(existing).stripTrailingZeros().toPlainString().replace('.', ','));
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(requireContext());
        int horizontal = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
        wrap.setPadding(horizontal, 0, horizontal, 0); wrap.addView(field);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(water ? "Tarifa de água (R$/m³)" : "Tarifa de energia (R$/kWh)")
                .setMessage("A nova tarifa será usada nos próximos registros e nas estimativas do painel. Os custos já gravados nos registros e nos relatórios não serão alterados.")
                .setView(wrap).setNegativeButton("Cancelar", null).setNeutralButton(R.string.bill_help_open, null).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(ignored -> {
            // "Onde encontro na conta?" abre a ajuda sem fechar o diálogo da tarifa.
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> AjudaConta.mostrar(requireContext(), !water, AjudaConta.Campo.TARIFA));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                BigDecimal parsed = CampoDecimal.ler(field, ValorDecimal.Regra.TARIFA);
                if (parsed == null) return;
                double value = parsed.doubleValue();
                ContentValues changes = new ContentValues(); changes.put(water ? "tarifa_agua" : "tarifa_energia", value);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                update(changes, () -> {
                    if (water) perfil.tarifaAgua = value; else perfil.tarifaEnergia = value;
                    text(water ? R.id.tvTarifaAgua : R.id.tvTarifaEnergia).setText(tariff(value, water ? "m³" : "kWh"));
                    dialog.dismiss(); toast("Tarifa atualizada.");
                }, () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
            });
        });
        dialog.show();
        validar(dialog, field, t -> ValorDecimal.validar(t, ValorDecimal.Regra.TARIFA).erro);
    }

    private void update(ContentValues changes, Runnable success, Runnable failure) {
        final View view = root;
        final PerfilDAO currentDao = dao;
        final String uid = user.getUid();
        final String name = perfil != null && perfil.nome != null ? perfil.nome : user.getDisplayName() == null ? "Usuário" : user.getDisplayName();
        executor.execute(() -> {
            try {
                currentDao.atualizar(uid, name, changes);
                main.post(() -> { if (root == view) success.run(); });
            } catch (RuntimeException e) {
                main.post(() -> { if (root == view) { failure.run(); toast("Não foi possível salvar. Tente novamente."); } });
            }
        });
    }

    private void resetPassword() {
        String email = user.getEmail();
        if (email == null || email.isEmpty()) { toast("A conta não possui e-mail para recuperação."); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Alterar senha")
                .setMessage("Enviar instruções de redefinição para " + email + "?")
                .setNegativeButton("Cancelar", null).setPositiveButton("Enviar", (d, w) ->
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                            if (!isAdded()) return;
                            toast(task.isSuccessful() ? "Se a conta permitir, as instruções serão enviadas ao seu e-mail." : "Não foi possível enviar. Verifique sua conexão e tente novamente.");
                        })).show();
    }

    /** "Versão 1.0 · a1b2c3d": o código depois do ponto é o commit do build (ajuda a saber qual APK está instalado). */
    private String versaoDoApp() {
        try {
            String nome = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
            return "Versão " + nome;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) { return ""; }
    }

    /** LGPD, art. 18, VI: 1º passo — explica o que será apagado e que a ação é permanente. */
    private void confirmDeleteAccount() {
        if (user == null) return;
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(R.string.delete_account_continue, (d, w) -> requestPasswordForDeletion())
                .show();
    }

    /**
     * 2º passo — senha atual. Ordem: reautenticar → excluir no Firebase Auth → apagar os dados
     * do aparelho. Se algo falhar antes da exclusão remota, nada é apagado.
     */
    private void requestPasswordForDeletion() {
        String email = user.getEmail();
        if (email == null || email.isEmpty()) { toast("Saia e entre novamente para confirmar sua identidade."); return; }
        EditText password = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.delete_account_confirm_title)
                .setMessage(R.string.delete_account_confirm_message)
                .setView(fieldContainer(password)).setNegativeButton("Cancelar", null)
                .setPositiveButton(R.string.delete_account_confirm, null).create();
        final Context app = requireContext().getApplicationContext();
        dialog.setOnShowListener(ignored -> {
            android.widget.Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.alert_red_text));
            confirm.setOnClickListener(v -> {
                String secret = password.getText().toString();
                if (secret.isEmpty()) { password.setError("Informe sua senha."); return; }
                setDeleting(dialog, password, true);
                final View view = root;
                final FirebaseUser account = user;
                final String uid = account.getUid();
                account.reauthenticate(EmailAuthProvider.getCredential(email, secret)).addOnCompleteListener(auth -> {
                    password.setText(""); // não manter a senha na memória da tela
                    if (!auth.isSuccessful()) {
                        if (root == view) setDeleting(dialog, password, false);
                        Toast.makeText(app, auth.getException() instanceof FirebaseAuthInvalidCredentialsException
                                ? R.string.delete_account_wrong_password : R.string.delete_account_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    account.delete().addOnCompleteListener(deletion -> {
                        if (!deletion.isSuccessful()) {
                            if (root == view) setDeleting(dialog, password, false);
                            Toast.makeText(app, R.string.delete_account_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        // Conta remota excluída: apaga os dados deste aparelho fora da thread principal.
                        new Thread(() -> {
                            boolean local = ExclusaoContaService.apagarDadosDoAparelho(app, uid);
                            main.post(() -> finishDeletion(app, dialog, local));
                        }, "afluente-exclusao-conta").start();
                    });
                });
            });
        });
        dialog.show();
        validar(dialog, password, RegrasFormulario::senhaInformada);
    }

    private void setDeleting(AlertDialog dialog, EditText password, boolean deleting) {
        dialog.setCancelable(!deleting);
        password.setEnabled(!deleting);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!deleting);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!deleting && password.length() > 0);
    }

    /** 3º passo — encerra a sessão e volta para a tela inicial, sem possibilidade de voltar ao Perfil. */
    private void finishDeletion(Context app, AlertDialog dialog, boolean localOk) {
        if (dialog.isShowing()) dialog.dismiss();
        FirebaseAuth.getInstance().signOut();
        OnboardingSession.clear();
        Toast.makeText(app, localOk ? R.string.delete_account_done : R.string.delete_account_partial, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(app, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        app.startActivity(intent);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Sair da conta?")
                .setMessage("Você precisará entrar novamente neste aparelho.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Sair", (d, w) -> {
                    Context context = requireContext();
                    FirebaseAuth.getInstance().signOut();
                    OnboardingSession.clear();
                    context.getSharedPreferences(OnboardingStep4Activity.DRAFT_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply();
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_OPEN_LOGIN, true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }).show();
    }

    private void setName(String name) {
        String display = name == null || name.trim().isEmpty() ? "Usuário" : name.trim();
        text(R.id.tvNomeCabecalho).setText(display); text(R.id.tvNomePerfil).setText(display);
        text(R.id.tvAvatar).setText(FotoPerfilService.iniciais(display));
    }
    private String tariff(Double value, String unit) { return value == null || value <= 0 ? "Não configurada" : NumberFormat.getCurrencyInstance(locale).format(value) + "/" + unit; }
    private TextView text(int id) { return root.findViewById(id); }
    private void toast(String message) { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }
    @Override public void onDestroyView() {
        if (user != null) FotoPerfilService.removerObservador(user.getUid(), photoListener);
        root = null;
        if (executor != null) { DatabaseHelper close = helper; executor.execute(close::close); executor.shutdown(); }
        super.onDestroyView();
    }
}
