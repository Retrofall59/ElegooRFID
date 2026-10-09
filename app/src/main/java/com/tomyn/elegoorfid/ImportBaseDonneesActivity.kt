package com.tomyn.elegoorfid

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Ecran d'import de tags depuis une base de donnees JSON de filaments (ajoute le 09/10/2026 a la
 * demande de Tomyn, suite aux 36 fichiers de bases communautaires qu'il a fournis - un par
 * fabricant, voir FilamentDatabase.kt pour le format et MaterialsJsonMapping.kt pour le
 * rapprochement des matieres).
 *
 * Fonctionnement : Tomyn choisit un ou plusieurs fichiers JSON (selecteur multi-fichiers standard
 * Android - ouvrir son dossier et tout selectionner d'un coup fonctionne aussi bien que choisir un
 * seul fichier). Les fichiers choisis sont memorises (GestionnaireParametres) pour ne pas avoir a
 * les reselectionner a chaque lancement ; une nouvelle selection vient s'AJOUTER aux precedentes
 * (une marque reselectionnee remplace son ancienne version).
 *
 * Ensuite : fabricant -> gamme -> couleurs a cocher, exactement comme demande. Une gamme dont la
 * matiere n'a pas d'equivalent Elegoo, ou dont la temperature/poids/diametre manque dans le
 * fichier, est signalee et toutes ses couleurs sont grisees - pas de valeur inventee. Une couleur
 * en degrade ("hexes") est grisee individuellement, meme dans une gamme par ailleurs supportee.
 *
 * Renvoie les dumps generes via le champ statique [dumpsGeneres] (memes limites que
 * MainActivity.contenuAExporter : perdu si l'appli est relancee entre la generation et la lecture
 * du resultat, cas deja accepte ailleurs dans le projet) plutot que par les extras de l'Intent -
 * evite toute gymnastique Parcelable/Serializable pour une simple liste de (nom, ByteArray).
 */
class ImportBaseDonneesActivity : AppCompatActivity() {

    private lateinit var txtStatutBase: TextView
    private lateinit var carteSelectionBase: View
    private lateinit var spinnerFabricantBase: Spinner
    private lateinit var spinnerGammeBase: Spinner
    private lateinit var txtAvertissementGammeBase: TextView
    private lateinit var containerCouleursBase: LinearLayout
    private lateinit var btnGenererTagsBase: Button

    private var fabricants: List<FilamentDatabase.Fabricant> = emptyList()
    private var filamentCourant: FilamentDatabase.Filament? = null
    private var resolutionMatiereCourante: MaterialsJsonMapping.Resultat? = null
    private var lignesCouleurs: List<Pair<CheckBox, FilamentDatabase.Couleur>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_base_donnees)

        findViewById<ImageButton>(R.id.btnRetourImportBase).setOnClickListener { finish() }

        txtStatutBase = findViewById(R.id.txtStatutBase)
        carteSelectionBase = findViewById(R.id.carteSelectionBase)
        spinnerFabricantBase = findViewById(R.id.spinnerFabricantBase)
        spinnerGammeBase = findViewById(R.id.spinnerGammeBase)
        txtAvertissementGammeBase = findViewById(R.id.txtAvertissementGammeBase)
        containerCouleursBase = findViewById(R.id.containerCouleursBase)
        btnGenererTagsBase = findViewById(R.id.btnGenererTagsBase)

        findViewById<Button>(R.id.btnChoisirFichiersBase).setOnClickListener { demarrerChoixFichiers() }
        findViewById<Button>(R.id.btnToutCocherBase).setOnClickListener { basculerToutCocher() }
        btnGenererTagsBase.setOnClickListener { genererTags() }

        spinnerFabricantBase.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fabricants.getOrNull(position)?.let { actualiserSpinnerGamme(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerGammeBase.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fabricants.getOrNull(spinnerFabricantBase.selectedItemPosition)
                    ?.filaments?.getOrNull(position)?.let { actualiserListeCouleurs(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        chargerSourcesPersistees()
    }

    private fun chargerSourcesPersistees() {
        val sources = GestionnaireParametres.lireSourcesBaseJson(this)
        if (sources.isNotEmpty()) chargerDepuisUris(sources.map { Uri.parse(it) })
    }

    private fun demarrerChoixFichiers() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, CODE_CHOISIR_FICHIERS)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun nomDepuisUri(uri: Uri): String =
        uri.toString().substringAfterLast('/').substringBefore('?').ifBlank { "fichier" }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CODE_CHOISIR_FICHIERS || resultCode != Activity.RESULT_OK) return

        val uris = mutableListOf<Uri>()
        val clip = data?.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
        } else {
            data?.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "Aucun fichier sélectionné.", Toast.LENGTH_SHORT).show()
            return
        }

        // Fusionne avec les sources deja memorisees (voir le commentaire en tete de fichier) -
        // une nouvelle selection s'ajoute plutot que de remplacer tout ce qui etait charge avant.
        val sourcesExistantes = GestionnaireParametres.lireSourcesBaseJson(this).toMutableList()
        for (uri in uris) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Pas grave : la lecture immediate ci-dessous fonctionnera quand meme, seule la
                // persistance apres redemarrage de l'appli serait perdue pour ce fichier.
            }
            val uriTexte = uri.toString()
            if (uriTexte !in sourcesExistantes) sourcesExistantes.add(uriTexte)
        }
        GestionnaireParametres.ecrireSourcesBaseJson(this, sourcesExistantes)
        chargerDepuisUris(sourcesExistantes.map { Uri.parse(it) })
    }

    /**
     * Charge et analyse chaque Uri. Un fichier illisible ou qui n'a pas le format attendu
     * (JSON invalide, pas de "manufacturer"/"filaments") est simplement ignore - et retire de la
     * liste persistee, pour ne pas retenter indefiniment un fichier devenu inaccessible (droit
     * revoque, fichier deplace/supprime depuis le choix initial).
     */
    private fun chargerDepuisUris(uris: List<Uri>) {
        val fabricantsCharges = mutableListOf<FilamentDatabase.Fabricant>()
        val uriValides = mutableListOf<String>()
        var echecs = 0
        for (uri in uris) {
            try {
                val contenu = contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
                val fabricant = contenu?.let { FilamentDatabase.analyser(it) }
                if (fabricant != null) {
                    fabricantsCharges.removeAll { it.nom == fabricant.nom }
                    fabricantsCharges.add(fabricant)
                    uriValides.add(uri.toString())
                } else {
                    echecs++
                }
            } catch (e: Exception) {
                echecs++
            }
        }
        if (uriValides.size != uris.size) {
            GestionnaireParametres.ecrireSourcesBaseJson(this, uriValides)
        }

        fabricants = fabricantsCharges.sortedBy { it.nom }
        txtStatutBase.text = when {
            fabricants.isEmpty() -> "Aucun fichier reconnu — vérifie que ce sont bien des fichiers JSON de base de filaments (champs \"manufacturer\"/\"filaments\")."
            echecs > 0 -> "${fabricants.size} fabricant(s) chargé(s) ($echecs fichier(s) ignoré(s), non reconnus)."
            else -> "${fabricants.size} fabricant(s) chargé(s)."
        }

        val visible = if (fabricants.isEmpty()) View.GONE else View.VISIBLE
        carteSelectionBase.visibility = visible
        btnGenererTagsBase.visibility = visible
        if (fabricants.isNotEmpty()) actualiserSpinnerFabricants()
    }

    private fun actualiserSpinnerFabricants() {
        spinnerFabricantBase.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, fabricants.map { it.nom }
        )
        fabricants.firstOrNull()?.let { actualiserSpinnerGamme(it) }
    }

    /** @return null si la gamme est exploitable telle quelle, sinon la raison a afficher (et qui grise toutes ses couleurs). */
    private fun raisonGammeNonSupportee(f: FilamentDatabase.Filament, resolution: MaterialsJsonMapping.Resultat?): String? = when {
        resolution == null -> "Matière « ${f.materiauJson} » non reconnue par le format Elegoo."
        f.tempMinC == null || f.tempMaxC == null -> "Température d'extrusion absente de ce fichier."
        f.poidsGrammes == null -> "Poids absent de ce fichier."
        f.diametreMm == null -> "Diamètre absent de ce fichier."
        else -> null
    }

    private fun actualiserSpinnerGamme(fabricant: FilamentDatabase.Fabricant) {
        val libelles = fabricant.filaments.map { f ->
            val resolution = MaterialsJsonMapping.resoudre(f.materiauJson)
            val base = FilamentDatabase.nomGammeAffiche(f)
            when {
                raisonGammeNonSupportee(f, resolution) != null -> "⚠ $base"
                resolution?.approximatif == true -> "$base (approx.)"
                else -> base
            }
        }
        spinnerGammeBase.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, libelles)
        fabricant.filaments.firstOrNull()?.let { actualiserListeCouleurs(it) }
    }

    private fun actualiserListeCouleurs(filament: FilamentDatabase.Filament) {
        filamentCourant = filament
        val resolution = MaterialsJsonMapping.resoudre(filament.materiauJson)
        resolutionMatiereCourante = resolution
        val raison = raisonGammeNonSupportee(filament, resolution)
        val gammeSupportee = raison == null

        when {
            raison != null -> {
                txtAvertissementGammeBase.text = "⚠ $raison Toutes les couleurs de cette gamme sont grisées."
                txtAvertissementGammeBase.visibility = View.VISIBLE
            }
            resolution?.approximatif == true -> {
                txtAvertissementGammeBase.text = "ℹ Matière « ${filament.materiauJson} » reconnue approximativement : le tag utilisera le sous-type générique de sa famille, pas un équivalent exact."
                txtAvertissementGammeBase.visibility = View.VISIBLE
            }
            else -> txtAvertissementGammeBase.visibility = View.GONE
        }

        containerCouleursBase.removeAllViews()
        val nouvellesLignes = mutableListOf<Pair<CheckBox, FilamentDatabase.Couleur>>()
        val tailleSwatch = (28 * resources.displayMetrics.density).toInt()
        val couleurTexteNormal = ContextCompat.getColor(this, R.color.texte_principal)
        val couleurTexteGrise = ContextCompat.getColor(this, R.color.texte_secondaire)

        for (couleur in filament.couleurs) {
            val ligneSupportee = gammeSupportee && !couleur.multiTon

            val pastille = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(tailleSwatch, tailleSwatch)
                setBackgroundResource(R.drawable.forme_couleur)
                couleur.hexRgb?.let { hex ->
                    try { backgroundTintList = ColorStateList.valueOf(Color.parseColor("#$hex")) } catch (e: Exception) { /* hex invalide : pastille non teintee, pas bloquant */ }
                }
            }

            val caseACocher = CheckBox(this).apply {
                text = FilamentDatabase.nomAffiche(filament, couleur) +
                    if (gammeSupportee && couleur.multiTon) "  (dégradé non supporté)" else ""
                setTextColor(if (ligneSupportee) couleurTexteNormal else couleurTexteGrise)
                isChecked = false
            }

            val ligne = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
                addView(pastille)
                addView(caseACocher)
            }
            containerCouleursBase.addView(ligne)
            if (ligneSupportee) nouvellesLignes.add(caseACocher to couleur)
        }
        lignesCouleurs = nouvellesLignes
    }

    private fun basculerToutCocher() {
        if (lignesCouleurs.isEmpty()) return
        val toutDejaCoche = lignesCouleurs.all { (case, _) -> case.isChecked }
        lignesCouleurs.forEach { (case, _) -> case.isChecked = !toutDejaCoche }
    }

    private fun genererTags() {
        val filament = filamentCourant ?: return
        val resolution = resolutionMatiereCourante
        val poids = filament.poidsGrammes
        val diametre = filament.diametreMm
        val tempMin = filament.tempMinC
        val tempMax = filament.tempMaxC
        if (resolution == null || poids == null || diametre == null || tempMin == null || tempMax == null) {
            Toast.makeText(this, "Cette gamme n'est pas exploitable (voir l'avertissement ci-dessus).", Toast.LENGTH_LONG).show()
            return
        }

        val selection = lignesCouleurs.filter { (case, _) -> case.isChecked }
        if (selection.isEmpty()) {
            Toast.makeText(this, "Coche au moins une couleur avant de générer.", Toast.LENGTH_SHORT).show()
            return
        }

        val dumps = selection.mapNotNull { (_, couleur) ->
            val hex = couleur.hexRgb ?: return@mapNotNull null
            val nom = FilamentDatabase.nomAffiche(filament, couleur)
            val dump = EncodeurElegoo.construireDump(
                codeMatiere = resolution.codeMatiere,
                codeSousType = resolution.codeSousType,
                couleurHex = hex,
                poidsGrammes = poids,
                diametreMm = diametre,
                tempMinC = tempMin,
                tempMaxC = tempMax
            )
            nom to dump
        }

        if (dumps.isEmpty()) {
            Toast.makeText(this, "Aucune couleur exploitable dans la sélection.", Toast.LENGTH_SHORT).show()
            return
        }

        dumpsGeneres = dumps
        setResult(Activity.RESULT_OK, Intent())
        finish()
    }

    companion object {
        private const val CODE_CHOISIR_FICHIERS = 9001

        /** Lu par MainActivity.onActivityResult (CODE_IMPORT_BASE) juste apres que cette
         * activite se termine avec RESULT_OK - voir le commentaire en tete de fichier sur le
         * choix de ne pas passer par les extras de l'Intent. */
        var dumpsGeneres: List<Pair<String, ByteArray>> = emptyList()
    }
}
