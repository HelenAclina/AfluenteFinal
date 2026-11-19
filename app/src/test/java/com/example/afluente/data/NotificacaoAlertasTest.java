package com.example.afluente.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.example.afluente.service.NotificacaoService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Verifica que os alertas UC18/UC19 viram notificação do sistema, uma única vez e no canal correto. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NotificacaoAlertasTest {
    private static final String UID = "uid-teste-notificacao";
    private Context context;
    private DatabaseHelper helper;
    private NotificationManager manager;

    @Before public void preparar() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("teste-notificacao.db");
        helper = new DatabaseHelper(context, "teste-notificacao.db");
        manager = context.getSystemService(NotificationManager.class);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO usuarios(firebase_uid,nome) VALUES(?,?)", new Object[]{UID, "Teste"});
        db.execSQL("INSERT INTO configuracoes_usuario(usuario_id,notificacao_ativa) SELECT id,1 FROM usuarios WHERE firebase_uid=?", new Object[]{UID});
    }

    @After public void limpar() { helper.close(); context.deleteDatabase("teste-notificacao.db"); }

    private void permitir(boolean permitido) {
        if (permitido) shadowOf((Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        else shadowOf((Application) context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private String hoje() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }

    /** Meta de 1 m³ e consumo de 5 m³ no mês: gera o alerta UC18. */
    private void ultrapassarMeta() {
        new MetaDAO(helper).salvarOuAtualizarMeta(UID, "AGUA", new BigDecimal("1"));
        new RegistroConsumoDAO(helper).inserirRegistro(UID, "AGUA", new BigDecimal("5"), hoje(), null);
    }

    private int pendentes() {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM alertas WHERE notificado=0", null)) {
            c.moveToFirst(); return c.getInt(0);
        }
    }

    @Test public void metaUltrapassadaGeraNotificacaoNoCanalDeAltaImportancia() {
        permitir(true);
        ultrapassarMeta();

        assertEquals(1, shadowOf(manager).getAllNotifications().size());
        Notification n = shadowOf(manager).getAllNotifications().get(0);
        assertEquals(NotificacaoService.CANAL_ALERTAS, n.getChannelId());
        assertEquals("Meta de água ultrapassada", n.extras.getString(Notification.EXTRA_TITLE));
        NotificationChannel canal = manager.getNotificationChannel(NotificacaoService.CANAL_ALERTAS);
        assertNotNull(canal);
        assertEquals(NotificationManager.IMPORTANCE_HIGH, canal.getImportance());
        assertEquals(0, pendentes());
    }

    @Test public void novoRegistroNoMesmoAlertaNaoRepeteNotificacao() {
        permitir(true);
        ultrapassarMeta();
        new RegistroConsumoDAO(helper).inserirRegistro(UID, "AGUA", new BigDecimal("2"), hoje(), null);
        assertEquals(1, shadowOf(manager).getAllNotifications().size());
    }

    @Test public void semPermissaoMantemPendenteEEnviaDepois() {
        permitir(false);
        ultrapassarMeta();
        assertEquals(0, shadowOf(manager).getAllNotifications().size());
        assertEquals(1, pendentes());

        permitir(true);
        NotificacaoService.enviarPendentes(context, helper, UID);
        assertEquals(1, shadowOf(manager).getAllNotifications().size());
        assertEquals(0, pendentes());
    }

    @Test public void preferenciaDesativadaNaoNotifica() {
        permitir(true);
        helper.getWritableDatabase().execSQL("UPDATE configuracoes_usuario SET notificacao_ativa=0");
        ultrapassarMeta();
        assertEquals(0, shadowOf(manager).getAllNotifications().size());
        assertEquals(0, pendentes()); // não dispara em lote se a preferência for reativada
    }
}
