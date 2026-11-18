package com.example.afluente.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.afluente.service.NotificacaoService;

/** Base local por UID do Firebase; os dados não são sincronizados entre aparelhos. */
public class DatabaseHelper extends SQLiteOpenHelper {
    private final Context appContext;

    public DatabaseHelper(Context context) { this(context, "afluente.db"); }
    // Nome alternativo permite testes instrumentados sem tocar no banco do usuário.
    DatabaseHelper(Context context, String name) {
        super(context, name, null, 10);
        appContext = context.getApplicationContext();
    }

    /**
     * Chamado pelos DAOs depois que a transação que reavalia os alertas é confirmada.
     * Envia como notificação do sistema os alertas novos que ainda não foram notificados.
     */
    void notificarAlertasPendentes(String uid) {
        NotificacaoService.enviarPendentes(appContext, this, uid);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT, firebase_uid TEXT NOT NULL UNIQUE, nome TEXT NOT NULL, foto_uri TEXT)");
        db.execSQL("CREATE TABLE configuracoes_usuario (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE, tarifa_agua REAL, tarifa_energia REAL, residentes INTEGER, tipo_residencia TEXT, estimativa_agua REAL, estimativa_energia REAL, objetivos TEXT, notificacao_ativa INTEGER NOT NULL DEFAULT 1, tema_escuro INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE registros_consumo (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE, tipo_recurso TEXT NOT NULL CHECK(tipo_recurso IN ('AGUA','ENERGIA')), valor_consumo REAL NOT NULL CHECK(valor_consumo >= 0), data_registro TEXT NOT NULL, uuid TEXT, valor_financeiro REAL, unidade TEXT, observacao TEXT)");
        createMetas(db);
        createRelatorios(db);
        db.execSQL("CREATE INDEX idx_consumo_usuario_data ON registros_consumo(usuario_id, data_registro)");
        db.execSQL("CREATE TABLE alertas (id INTEGER PRIMARY KEY AUTOINCREMENT, usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE, titulo TEXT NOT NULL, descricao TEXT NOT NULL, uuid TEXT, tipo_alerta TEXT NOT NULL DEFAULT 'ATENCAO', lido INTEGER NOT NULL DEFAULT 0 CHECK(lido IN (0,1)), data_geracao TEXT NOT NULL, notificado INTEGER NOT NULL DEFAULT 0 CHECK(notificado IN (0,1)))");
        db.execSQL("CREATE INDEX idx_alertas_usuario_lido_data ON alertas(usuario_id, lido, data_geracao DESC, id DESC)");
    }
    private void createMetas(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE metas_consumo (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE, tipo_recurso TEXT NOT NULL CHECK(tipo_recurso IN ('AGUA','ENERGIA')), valor_meta REAL NOT NULL CHECK(valor_meta > 0), UNIQUE(usuario_id,tipo_recurso))");
    }
    private void createRelatorios(SQLiteDatabase db) {
        // Os campos de picos e médias são JSON com água e energia separadas, incluindo os meses dos picos.
        db.execSQL("CREATE TABLE relatorios (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL UNIQUE, usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE, tipo_relatorio TEXT NOT NULL CHECK(tipo_relatorio IN ('ANUAL','MENSAL')), data_geracao TEXT NOT NULL, periodo TEXT NOT NULL, total_agua REAL, total_energia REAL, total_financeiro REAL, maior_consumo TEXT, menor_consumo TEXT, media_anual TEXT, UNIQUE(usuario_id,tipo_relatorio,periodo))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN residentes INTEGER");
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN tipo_residencia TEXT");
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN estimativa_agua REAL");
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN estimativa_energia REAL");
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN objetivos TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE alertas ADD COLUMN uuid TEXT");
            db.execSQL("ALTER TABLE alertas ADD COLUMN tipo_alerta TEXT NOT NULL DEFAULT 'ATENCAO'");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE registros_consumo ADD COLUMN uuid TEXT");
            db.execSQL("ALTER TABLE registros_consumo ADD COLUMN valor_financeiro REAL");
            db.execSQL("ALTER TABLE registros_consumo ADD COLUMN unidade TEXT");
            db.execSQL("ALTER TABLE registros_consumo ADD COLUMN observacao TEXT");
            db.execSQL("UPDATE registros_consumo SET unidade = CASE tipo_recurso WHEN 'AGUA' THEN 'm³' ELSE 'kWh' END");
        }
        if (oldVersion < 6) createMetas(db);
        if (oldVersion < 7) createRelatorios(db);
        if (oldVersion < 8) db.execSQL("ALTER TABLE usuarios ADD COLUMN foto_uri TEXT");
        if (oldVersion < 9) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_consumo_usuario_data ON registros_consumo(usuario_id, data_registro)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_alertas_usuario_lido_data ON alertas(usuario_id, lido, data_geracao DESC, id DESC)");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN notificacao_ativa INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE configuracoes_usuario ADD COLUMN tema_escuro INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 10) {
            // Controla quais alertas já viraram notificação do sistema, evitando repetição.
            db.execSQL("ALTER TABLE alertas ADD COLUMN notificado INTEGER NOT NULL DEFAULT 0 CHECK(notificado IN (0,1))");
            // Alertas anteriores à atualização já foram vistos no app: não disparar em lote.
            db.execSQL("UPDATE alertas SET notificado = 1");
        }
    }
}
