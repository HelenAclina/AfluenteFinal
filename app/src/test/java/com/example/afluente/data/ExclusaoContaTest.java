package com.example.afluente.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** LGPD: a exclusão apaga todos os dados locais do titular e preserva os de outras contas do aparelho. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ExclusaoContaTest {
    private static final String[] TABELAS = {"configuracoes_usuario", "registros_consumo", "metas_consumo", "alertas", "relatorios"};
    private Context context;
    private DatabaseHelper helper;

    @Before public void preparar() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("teste-exclusao.db");
        helper = new DatabaseHelper(context, "teste-exclusao.db");
        popular("uid-helen", "Helen");
        popular("uid-outra", "Outra pessoa");
    }

    @After public void limpar() { helper.close(); context.deleteDatabase("teste-exclusao.db"); }

    private void popular(String uid, String nome) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO usuarios(firebase_uid,nome,foto_uri) VALUES(?,?,?)", new Object[]{uid, nome, "profile_photos/avatar-" + uid + ".png"});
        String id = "(SELECT id FROM usuarios WHERE firebase_uid='" + uid + "')";
        db.execSQL("INSERT INTO configuracoes_usuario(usuario_id,tarifa_agua) VALUES(" + id + ",5.0)");
        db.execSQL("INSERT INTO registros_consumo(usuario_id,tipo_recurso,valor_consumo,data_registro) VALUES(" + id + ",'AGUA',12,'2026-11-01')");
        db.execSQL("INSERT INTO metas_consumo(uuid,usuario_id,tipo_recurso,valor_meta) VALUES('m-" + uid + "'," + id + ",'AGUA',10)");
        db.execSQL("INSERT INTO alertas(usuario_id,titulo,descricao,data_geracao) VALUES(" + id + ",'t','d','2026-11-01 10:00:00')");
        db.execSQL("INSERT INTO relatorios(uuid,usuario_id,tipo_relatorio,data_geracao,periodo) VALUES('r-" + uid + "'," + id + ",'ANUAL','2026-11-01','2026')");
    }

    private int contar(String tabela, String uid) {
        String sql = "usuarios".equals(tabela)
                ? "SELECT COUNT(*) FROM usuarios WHERE firebase_uid=?"
                : "SELECT COUNT(*) FROM " + tabela + " t JOIN usuarios u ON u.id=t.usuario_id WHERE u.firebase_uid=?";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid})) { c.moveToFirst(); return c.getInt(0); }
    }

    private int totalSemDono(String tabela) {
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + tabela + " WHERE usuario_id NOT IN (SELECT id FROM usuarios)", null)) {
            c.moveToFirst(); return c.getInt(0);
        }
    }

    @Test public void apagaTodosOsDadosDoTitular() {
        ContaDAO.Resultado r = new ContaDAO(helper).excluirDadosLocais("uid-helen");
        assertTrue(r.removido);
        assertEquals("profile_photos/avatar-uid-helen.png", r.fotoUri); // devolvido para apagar o arquivo
        assertEquals(0, contar("usuarios", "uid-helen"));
        for (String tabela : TABELAS) assertEquals(tabela + " sem dono", 0, totalSemDono(tabela));
    }

    @Test public void preservaOutraContaDoMesmoAparelho() {
        new ContaDAO(helper).excluirDadosLocais("uid-helen");
        assertEquals(1, contar("usuarios", "uid-outra"));
        for (String tabela : TABELAS) assertEquals(tabela, 1, contar(tabela, "uid-outra"));
    }

    @Test public void contaSemDadosLocaisNaoFalha() {
        ContaDAO.Resultado r = new ContaDAO(helper).excluirDadosLocais("uid-inexistente");
        assertFalse(r.removido);
    }
}
