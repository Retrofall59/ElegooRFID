package com.tomyn.elegoorfid

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Formulaire de creation d'un tag Elegoo "a la main" (ajoute le 09/10/2026 a la demande de
 * Tomyn) - voir EncodeurElegoo.kt pour la construction du dump. Utile en particulier pour son
 * futur filament recycle (Lyman), sans avoir a passer par l'editeur externe elegoo-rfid-editor.
 *
 * Renvoie le dump construit (160 octets) dans l'extra "dump" du resultat - MainActivity le traite
 * exactement comme un dump importe (voir onActivityResult, CODE_CREATION) : decodage + affichage
 * + boutons Cloner/Exporter/Ajouter a la planche actives, sans avoir touche de tag physique.
 */
class CreationTagActivity : AppCompatActivity() {

    private lateinit var spinnerMatiere: Spinner
    private lateinit var spinnerSousType: Spinner
    private lateinit var champCouleur: EditText
    private lateinit var apercuCouleur: View
    private lateinit var champPoids: EditText
    private lateinit var champDiametre: EditText
    private lateinit var champTempMin: EditText
    private lateinit var champTempMax: EditText

    // Matieres triees par nom pour un menu deroulant lisible - code numerique associe a chaque
    // position, voir MaterialsElegoo.CODES_MATIERE.
    private val matieres: List<Pair<Long, String>> =
        MaterialsElegoo.CODES_MATIERE.entries.sortedBy { it.value }.map { it.key to it.value }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creation_tag)

        findViewById<ImageButton>(R.id.btnRetourCreation).setOnClickListener { finish() }

        spinnerMatiere = findViewById(R.id.spinnerMatiere)
        spinnerSousType = findViewById(R.id.spinnerSousType)
        champCouleur = findViewById(R.id.champCouleur)
        apercuCouleur = findViewById(R.id.apercuCouleur)
        champPoids = findViewById(R.id.champPoids)
        champDiametre = findViewById(R.id.champDiametre)
        champTempMin = findViewById(R.id.champTempMin)
        champTempMax = findViewById(R.id.champTempMax)

        spinnerMatiere.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, matieres.map { it.second }
        )
        spinnerMatiere.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                actualiserSousTypes(matieres[position].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        actualiserSousTypes(matieres.first().second)

        champCouleur.setOnFocusChangeListener { _, _ -> actualiserApercuCouleur() }

        findViewById<Button>(R.id.btnCreerTag).setOnClickListener { creerTag() }
    }

    private fun actualiserSousTypes(nomFamille: String) {
        val sousTypes = MaterialsElegoo.sousTypesPourFamille(nomFamille)
        // Le premier sous-type de chaque famille est toujours le "generique" (meme nom que la
        // famille, voir MaterialsElegoo.sousTypesPourFamille) - choix par defaut raisonnable.
        spinnerSousType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, sousTypes.map { it.second }
        )
        spinnerSousType.tag = sousTypes
    }

    private fun actualiserApercuCouleur() {
        try {
            val argb = Color.parseColor("#${champCouleur.text.toString().trim()}")
            apercuCouleur.backgroundTintList = ColorStateList.valueOf(argb)
        } catch (e: Exception) { /* hex incomplet/invalide pendant la saisie : pas grave, on retentera a la validation */ }
    }

    private fun creerTag() {
        val (codeMatiere, nomMatiere) = matieres[spinnerMatiere.selectedItemPosition]
        @Suppress("UNCHECKED_CAST")
        val sousTypes = spinnerSousType.tag as? List<Pair<Int, String>> ?: emptyList()
        val codeSousType = sousTypes.getOrNull(spinnerSousType.selectedItemPosition)?.first ?: 0

        val couleurHex = champCouleur.text.toString().trim().uppercase()
        if (couleurHex.length != 6 || couleurHex.any { it !in "0123456789ABCDEF" }) {
            Toast.makeText(this, "Couleur invalide : 6 caractères hexadécimaux attendus (ex. 106DD7).", Toast.LENGTH_LONG).show()
            return
        }

        val poids = champPoids.text.toString().trim().toIntOrNull()
        if (poids == null || poids <= 0) {
            Toast.makeText(this, "Poids invalide.", Toast.LENGTH_SHORT).show()
            return
        }

        val diametre = champDiametre.text.toString().trim().replace(",", ".").toDoubleOrNull()
        if (diametre == null || diametre <= 0) {
            Toast.makeText(this, "Diamètre invalide.", Toast.LENGTH_SHORT).show()
            return
        }

        val tempMin = champTempMin.text.toString().trim().toIntOrNull()
        val tempMax = champTempMax.text.toString().trim().toIntOrNull()
        if (tempMin == null || tempMax == null || tempMin <= 0 || tempMax <= 0 || tempMin > tempMax) {
            Toast.makeText(this, "Températures invalides (min ≤ max, toutes les deux requises).", Toast.LENGTH_LONG).show()
            return
        }

        val dump = EncodeurElegoo.construireDump(
            codeMatiere = codeMatiere,
            codeSousType = codeSousType,
            couleurHex = couleurHex,
            poidsGrammes = poids,
            diametreMm = diametre,
            tempMinC = tempMin,
            tempMaxC = tempMax
        )

        val resultat = Intent()
        resultat.putExtra("dump", dump)
        setResult(Activity.RESULT_OK, resultat)
        finish()
    }
}
