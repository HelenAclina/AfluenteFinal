package com.example.afluente.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.example.afluente.ui.DashboardActivity;
import com.example.afluente.ui.NotificacoesActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforma os alertas locais (UC18 meta ultrapassada e UC19 aumento anormal) em notificações
 * do sistema. Os alertas continuam sendo gerados pelo {@link AlertaService}; esta classe só
 * entrega ao Android os que ainda não foram notificados (coluna {@code alertas.notificado}).
 * <p>
 * O canal usa {@link NotificationManager#IMPORTANCE_HIGH}, necessário para a notificação aparecer
 * como alerta flutuante (heads-up) na tela, e não apenas na barra de status.
 */
public final class NotificacaoService {
    /** Id novo: a importância de um canal não pode ser aumentada depois de criado. */
    public static final String CANAL_ALERTAS = "alertas_consumo";

    private NotificacaoService() { }

    /** Cria o canal (Android 8+). Pode ser chamado várias vezes: o sistema ignora duplicatas. */
    public static void criarCanal(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel canal = new NotificationChannel(CANAL_ALERTAS,
                context.getString(R.string.notification_channel_alerts), NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription(context.getString(R.string.notification_channel_alerts_description));
        canal.enableVibration(true);
        canal.enableLights(true);
        canal.setShowBadge(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(canal);
    }

    /** Android 13+ exige a permissão POST_NOTIFICATIONS concedida em tempo de execução. */
    public static boolean temPermissao(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Verdadeiro se o app pode exibir notificações (permissão e chave do sistema ativas). */
    public static boolean podeNotificar(Context context) {
        return temPermissao(context) && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Envia os alertas não lidos e ainda não notificados. Deve ser chamado fora de transação e
     * fora da thread principal.
     * <ul>
     *   <li>Preferência desativada no perfil: marca como notificados sem exibir, para não disparar
     *   um lote de alertas antigos quando a preferência for reativada.</li>
     *   <li>Sem permissão do sistema: mantém pendentes; serão enviados quando a permissão for concedida.</li>
     * </ul>
     */
    public static void enviarPendentes(Context context, SQLiteOpenHelper helper, String uid) {
        if (uid == null) return;
        try {
            List<Long> ids = new ArrayList<>();
            List<String[]> textos = new ArrayList<>();
            boolean preferenciaAtiva = true;
            String sql = "SELECT a.id,a.titulo,a.descricao,COALESCE(c.notificacao_ativa,1) FROM alertas a " +
                    "JOIN usuarios u ON u.id=a.usuario_id LEFT JOIN configuracoes_usuario c ON c.usuario_id=u.id " +
                    "WHERE u.firebase_uid=? AND a.notificado=0 AND a.lido=0 ORDER BY a.id";
            try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid})) {
                while (c.moveToNext()) {
                    ids.add(c.getLong(0));
                    textos.add(new String[]{c.getString(1), c.getString(2)});
                    preferenciaAtiva = c.getInt(3) != 0;
                }
            }
            if (ids.isEmpty()) return;
            if (preferenciaAtiva) {
                if (!podeNotificar(context)) return;
                criarCanal(context);
                for (int i = 0; i < ids.size(); i++) exibir(context, ids.get(i), textos.get(i)[0], textos.get(i)[1]);
            }
            SQLiteDatabase db = helper.getWritableDatabase();
            for (Long id : ids) db.execSQL("UPDATE alertas SET notificado=1 WHERE id=?", new Object[]{id});
        } catch (RuntimeException ignorado) {
            // A notificação é complementar: falhas não podem desfazer o registro do consumo.
        }
    }

    /** Remove a notificação de um alerta lido no app. */
    public static void cancelar(Context context, long alertaId) {
        NotificationManagerCompat.from(context).cancel(idNotificacao(alertaId));
    }

    /** Remove todas as notificações do app (ex.: ao desativar a preferência). */
    public static void cancelarTodas(Context context) {
        NotificationManagerCompat.from(context).cancelAll();
    }

    private static void exibir(Context context, long alertaId, String titulo, String descricao) {
        // Ao tocar: abre a lista de alertas, com o painel na pilha para o botão voltar.
        Intent abrir = new Intent(context, NotificacoesActivity.class);
        PendingIntent pendente = TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, DashboardActivity.class))
                .addNextIntent(abrir)
                .getPendingIntent(idNotificacao(alertaId), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CANAL_ALERTAS)
                .setSmallIcon(R.drawable.ic_notificacao)
                .setColor(ContextCompat.getColor(context, R.color.brand_deep_blue))
                .setContentTitle(titulo)
                .setContentText(descricao)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(descricao))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Android 7.x (sem canais)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pendente)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(idNotificacao(alertaId), builder.build());
        } catch (SecurityException semPermissao) {
            // Permissão revogada entre a verificação e o envio.
        }
    }

    private static int idNotificacao(long alertaId) { return (int) (alertaId % Integer.MAX_VALUE); }
}
