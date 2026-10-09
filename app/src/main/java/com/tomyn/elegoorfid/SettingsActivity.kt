package com.tomyn.elegoorfid

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    // Reglages regroupes ici pour pouvoir les reactualiser d'un coup apres une restauration
    // (voir importerReglagesDepuisFichier/onActivityResult) sans dupliquer la lecture des champs.
    private lateinit var interrupteurVibration: Switch
    private lateinit var interrupteurSonChamp: Switch
    private lateinit var champColonnesChamp: EditText
    private lateinit var champLignesChamp: EditText

    // Contenu en attente d'ecriture pendant l'export des reglages (voir exporterReglagesVersFichier
    // / onActivityResult) - meme principe que MainActivity.contenuAExporter.
    private var contenuReglagesAExporter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnRetour).setOnClickListener { finish() }

        interrupteurVibration = findViewById(R.id.interrupteurVibration)
        interrupteurVibration.setOnCheckedChangeListener { _, active ->
            GestionnaireParametres.ecrireVibrationFinLecture(this, active)
        }

        interrupteurSonChamp = findViewById(R.id.interrupteurSon)
        interrupteurSonChamp.setOnCheckedChangeListener { _, active ->
            GestionnaireParametres.ecrireSonFinLecture(this, active)
        }

        champColonnesChamp = findViewById(R.id.champColonnesEtiquettes)
        champLignesChamp = findViewById(R.id.champLignesEtiquettes)
        findViewById<Button>(R.id.btnEnregistrerGrilleEtiquettes).setOnClickListener {
            enregistrerGrilleEtiquettes(champColonnesChamp, champLignesChamp)
        }

        findViewById<Button>(R.id.btnExporterReglages).setOnClickListener { exporterReglagesVersFichier() }
        findViewById<Button>(R.id.btnImporterReglages).setOnClickListener { importerReglagesDepuisFichier() }

        actualiserChampsDepuisReglages()

        findViewById<TextView>(R.id.texteVersion).text = try {
            val infos = packageManager.getPackageInfo(packageName, 0)
            "ElegooRFID — version ${infos.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            "ElegooRFID"
        }
    }

    /** Relit tous les champs depuis GestionnaireParametres - appele au demarrage et apres une restauration. */
    private fun actualiserChampsDepuisReglages() {
        interrupteurVibration.isChecked = GestionnaireParametres.lireVibrationFinLecture(this)
        interrupteurSonChamp.isChecked = GestionnaireParametres.lireSonFinLecture(this)
        champColonnesChamp.setText(GestionnaireParametres.lireColonnesEtiquettes(this).toString())
        champLignesChamp.setText(GestionnaireParametres.lireLignesEtiquettes(this).toString())
    }

    /**
     * Sauvegarde/restauration des reglages (ajoutee en v0.27 a la demande de Tomyn) : voir
     * GestionnaireParametres.exporterReglages/importerReglages pour le format. Meme mecanisme
     * d'export que MainActivity.exporterVers (ACTION_CREATE_DOCUMENT + onActivityResult), mais
     * duplique ici plutot que partage entre les deux Activity - rester simple, un seul reglage a
     * exporter ne justifie pas de faire communiquer les deux ecrans.
     */
    private fun exporterReglagesVersFichier() {
        contenuReglagesAExporter = GestionnaireParametres.exporterReglages(this)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "reglages_elegoorfid.txt")
        }
        try {
            startActivityForResult(intent, CODE_EXPORT_REGLAGES)
        } catch (e: Exception) {
            contenuReglagesAExporter = null
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importerReglagesDepuisFichier() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, CODE_IMPORT_REGLAGES)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CODE_EXPORT_REGLAGES -> {
                val contenu = contenuReglagesAExporter
                contenuReglagesAExporter = null
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                if (contenu == null) {
                    Toast.makeText(this, "Export interrompu (l'appli a été relancée), recommence.", Toast.LENGTH_LONG).show()
                    return
                }
                try {
                    val flux = contentResolver.openOutputStream(uri) ?: throw IOException("fichier inaccessible")
                    flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this, "Réglages enregistrés.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erreur export : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            CODE_IMPORT_REGLAGES -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                try {
                    val contenu = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.toString(Charsets.UTF_8) ?: throw IOException("fichier inaccessible")
                    if (GestionnaireParametres.importerReglages(this, contenu)) {
                        actualiserChampsDepuisReglages()
                        Toast.makeText(this, "Réglages restaurés.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Fichier non reconnu : aucun réglage ElegooRFID trouvé dedans.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Erreur d'import : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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

    companion object {
        private const val CODE_EXPORT_REGLAGES = 401
        private const val CODE_IMPORT_REGLAGES = 402
    }
}
