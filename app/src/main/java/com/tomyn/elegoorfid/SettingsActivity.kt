package com.tomyn.elegoorfid

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnRetour).setOnClickListener { finish() }

        val interrupteur = findViewById<Switch>(R.id.interrupteurVibration)
        interrupteur.isChecked = GestionnaireParametres.lireVibrationFinLecture(this)
        interrupteur.setOnCheckedChangeListener { _, active ->
            GestionnaireParametres.ecrireVibrationFinLecture(this, active)
        }

        val interrupteurSon = findViewById<Switch>(R.id.interrupteurSon)
        interrupteurSon.isChecked = GestionnaireParametres.lireSonFinLecture(this)
        interrupteurSon.setOnCheckedChangeListener { _, active ->
            GestionnaireParametres.ecrireSonFinLecture(this, active)
        }

        val champColonnes = findViewById<EditText>(R.id.champColonnesEtiquettes)
        val champLignes = findViewById<EditText>(R.id.champLignesEtiquettes)
        champColonnes.setText(GestionnaireParametres.lireColonnesEtiquettes(this).toString())
        champLignes.setText(GestionnaireParametres.lireLignesEtiquettes(this).toString())
        findViewById<Button>(R.id.btnEnregistrerGrilleEtiquettes).setOnClickListener {
            enregistrerGrilleEtiquettes(champColonnes, champLignes)
        }

        findViewById<TextView>(R.id.texteVersion).text = try {
            val infos = packageManager.getPackageInfo(packageName, 0)
            "ElegooRFID — version ${infos.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            "ElegooRFID"
        }
    }

    /**
     * Valide et enregistre la grille d'etiquettes reglable (ajoute en v0.23 a la demande de
     * Tomyn, pour s'adapter a une autre planche autocollante sans toucher au code). Bornes
     * (1..MAX, voir GestionnaireParametres) : evite une grille illisible ou un calcul de mise en
     * page degenere (division par zero notamment) si une valeur absurde est saisie par erreur.
     */
    private fun enregistrerGrilleEtiquettes(champColonnes: EditText, champLignes: EditText) {
        val colonnes = champColonnes.text.toString().toIntOrNull()
        val lignes = champLignes.text.toString().toIntOrNull()
        if (colonnes == null || lignes == null ||
            colonnes < 1 || colonnes > GestionnaireParametres.COLONNES_ETIQUETTES_MAX ||
            lignes < 1 || lignes > GestionnaireParametres.LIGNES_ETIQUETTES_MAX
        ) {
            Toast.makeText(
                this,
                "Valeurs invalides (colonnes 1-${GestionnaireParametres.COLONNES_ETIQUETTES_MAX}, " +
                    "lignes 1-${GestionnaireParametres.LIGNES_ETIQUETTES_MAX}).",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        GestionnaireParametres.ecrireGrilleEtiquettes(this, colonnes, lignes)
        Toast.makeText(this, "Grille enregistrée : $colonnes × $lignes = ${colonnes * lignes} étiquettes/page.", Toast.LENGTH_SHORT).show()
    }
}
