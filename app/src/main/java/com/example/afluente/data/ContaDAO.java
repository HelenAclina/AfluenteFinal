package com.example.afluente.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Eliminação dos dados locais do titular (LGPD, art. 18, VI).
 * <p>
 * Apaga a linha de {@code usuarios}; as chaves estrangeiras com {@code ON DELETE CASCADE}
 * removem, na mesma transação, configurações (tarifas, preferências, estimativas), registros de
 * consumo, metas, alertas e relatórios. Dados de outras contas no mesmo aparelho não são tocados.
 */
public class ContaDAO {
    private final DatabaseHelper helper;

    public ContaDAO(DatabaseHelper helper) { this.helper = helper; }

    /** Resultado da exclusão: se havia dados e o caminho da foto de perfil a apagar do disco. */
    public static final class Resultado {
        public final boolean removido;
        public final String fotoUri;

        Resultado(boolean removido, String fotoUri) { this.removido = removido; this.fotoUri = fotoUri; }
    }

    public Resultado excluirDadosLocais(String uid) {
        if (uid == null || uid.trim().isEmpty()) throw new IllegalArgumentException("UID inválido");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String foto = null;
            try (Cursor c = db.rawQuery("SELECT foto_uri FROM usuarios WHERE firebase_uid=?", new String[]{uid})) {
                if (c.moveToFirst() && !c.isNull(0)) foto = c.getString(0);
            }
            boolean removido = db.delete("usuarios", "firebase_uid=?", new String[]{uid}) > 0;
            db.setTransactionSuccessful();
            return new Resultado(removido, foto);
        } finally { db.endTransaction(); }
    }
}
