package com.example.afluente.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.example.afluente.service.ValorDecimal;

/** Coleta estimativas opcionais; não registra consumo real nem conclui o cadastro. */
public class OnboardingStep3Activity extends AppCompatActivity {
    public static final String EXTRA_WATER = "estimated_water";
    public static final String EXTRA_ENERGY = "estimated_energy";
    public static final String EXTRA_UNKNOWN = "consumption_unknown";
    private TextInputEditText water, energy;
    private TextInputLayout waterLayout, energyLayout;
    private FormularioValidado form;
    private CheckBox unknown;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_consumo);
        EdgeInsets.aplicar(this);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.onboarding_background));
        water = findViewById(R.id.etConsumoAgua);
        energy = findViewById(R.id.etConsumoEnergia);
        waterLayout = findViewById(R.id.inputAgua);
        energyLayout = findViewById(R.id.inputEnergia);
        CampoDecimal.configurar(water);
        CampoDecimal.configurar(energy);
        unknown = findViewById(R.id.cbNaoSeiConsumo);
        Bundle state = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (state != null) {
            water.setText(state.getString(EXTRA_WATER, ""));
            energy.setText(state.getString(EXTRA_ENERGY, ""));
            unknown.setChecked(state.getBoolean(EXTRA_UNKNOWN, false));
        }
        // Campos opcionais: vazio é aceito; preenchido precisa ser válido. Continuar exige
        // ao menos um consumo informado ou a opção "ainda não sei".
        FormularioValidado.Regra opcional = t -> t.trim().isEmpty() ? null : ValorDecimal.validar(t, ValorDecimal.Regra.CONSUMO).erro;
        form = new FormularioValidado(findViewById(R.id.btnContinuar))
                .campo(water, waterLayout, opcional)
                .campo(energy, energyLayout, opcional)
                .condicao(() -> unknown.isChecked() || !value(water).isEmpty() || !value(energy).isEmpty());
        AjudaConta.anexar(findViewById(R.id.labelAgua), () -> false, AjudaConta.Campo.CONSUMO);
        AjudaConta.anexar(findViewById(R.id.labelEnergia), () -> true, AjudaConta.Campo.CONSUMO);
        unknown.setOnCheckedChangeListener((button, checked) -> setUnknown(checked));
        setUnknown(unknown.isChecked());
        findViewById(R.id.btnVoltar).setOnClickListener(v -> returnToPreviousStep());
        findViewById(R.id.btnContinuar).setOnClickListener(v -> continueOnboarding());
    }

    private void setUnknown(boolean checked) {
        if (checked) {
            water.setText("");
            energy.setText("");
            waterLayout.setError(null);
            energyLayout.setError(null);
        }
        waterLayout.setEnabled(!checked);
        energyLayout.setEnabled(!checked);
        if (form != null) form.reavaliar();
    }

    private void continueOnboarding() {
        if (!unknown.isChecked()) {
            boolean validWater = validate(water, waterLayout);
            boolean validEnergy = validate(energy, energyLayout);
            if (!validWater || !validEnergy) return;
            if (value(water).isEmpty() && value(energy).isEmpty()) {
                Toast.makeText(this, R.string.consumption_empty, Toast.LENGTH_LONG).show();
                return;
            }
        }
        OnboardingSession.waterEstimate = value(water);
        OnboardingSession.energyEstimate = value(energy);
        startActivity(new Intent(this, OnboardingStep4Activity.class));
    }

    private boolean validate(TextInputEditText field, TextInputLayout layout) {
        layout.setError(null);
        if (value(field).isEmpty()) return true; // Apenas um dos recursos pode ser informado.
        return CampoDecimal.ler(field, layout, ValorDecimal.Regra.CONSUMO) != null;
    }

    private String value(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_WATER, value(water));
        state.putString(EXTRA_ENERGY, value(energy));
        state.putBoolean(EXTRA_UNKNOWN, unknown.isChecked());
        super.onSaveInstanceState(state);
    }

    private void returnToPreviousStep() {
        Intent result = new Intent();
        result.putExtra(EXTRA_WATER, value(water));
        result.putExtra(EXTRA_ENERGY, value(energy));
        result.putExtra(EXTRA_UNKNOWN, unknown.isChecked());
        setResult(RESULT_OK, result);
        finish();
    }

    @Override public void onBackPressed() { returnToPreviousStep(); }
}
