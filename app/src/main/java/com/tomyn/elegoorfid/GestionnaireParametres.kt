package com.tomyn.elegoorfid

import android.content.Context

object GestionnaireParametres {

    private const val FICHIER = "elegoorfid_parametres"
    private const val CLE_VIBRATION = "vibration_fin_lecture"
    // Ajoute en v0.21 a la demande de Tomyn : un bip different succes/erreur pour scanner sans
    // avoir a regarder l'ecran a chaque fois - voir MainActivity.jouerSonResultat.
    private const val CLE_SON = "son_fin_lecture"

    fun lireVibrationFinLecture(context: Context): Boolean =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getBoolean(CLE_VIBRATION, true)

    fun ecrireVibrationFinLecture(context: Context, active: Boolean) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putBoolean(CLE_VIBRATION, active).apply()
    }

    fun lireSonFinLecture(context: Context): Boolean =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getBoolean(CLE_SON, true)

    fun ecrireSonFinLecture(context: Context, active: Boolean) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putBoolean(CLE_SON, active).apply()
    }

    // Grille d'etiquettes reglable (ajoute en v0.23 a la demande de Tomyn) : avant, 3x8 etait
    // code en dur dans PlancheEtiquettes - si Tomyn change un jour de planche autocollante, un
    // reglage evite de redemander un changement de code pour un simple ajustement de mise en page.
    private const val CLE_COLONNES_ETIQUETTES = "colonnes_etiquettes"
    private const val CLE_LIGNES_ETIQUETTES = "lignes_etiquettes"
    const val COLONNES_ETIQUETTES_DEFAUT = 3
    const val LIGNES_ETIQUETTES_DEFAUT = 8
    // Bornes larges mais raisonnables (evite une grille illisible ou un calcul de mise en page
    // degenere si une valeur absurde est saisie par erreur).
    const val COLONNES_ETIQUETTES_MAX = 10
    const val LIGNES_ETIQUETTES_MAX = 20

    fun lireColonnesEtiquettes(context: Context): Int =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .getInt(CLE_COLONNES_ETIQUETTES, COLONNES_ETIQUETTES_DEFAUT)

    fun lireLignesEtiquettes(context: Context): Int =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .getInt(CLE_LIGNES_ETIQUETTES, LIGNES_ETIQUETTES_DEFAUT)

    fun ecrireGrilleEtiquettes(context: Context, colonnes: Int, lignes: Int) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putInt(CLE_COLONNES_ETIQUETTES, colonnes)
            .putInt(CLE_LIGNES_ETIQUETTES, lignes)
            .apply()
    }

    // Sources de la base de filaments JSON (ajoute en v0.30, voir ImportBaseDonneesActivity) :
    // les Uri des fichiers choisis par Tomyn, memorisees pour ne pas avoir a les reselectionner a
    // chaque lancement. Separateur "||" plutot qu'une virgule : un chemin de fichier peut en
    // contenir une (ex. "mon,dossier"), jamais une sequence "||".
    private const val CLE_SOURCES_BASE_JSON = "sources_base_json"
    private const val SEPARATEUR_SOURCES_BASE_JSON = "||"

    fun lireSourcesBaseJson(context: Context): List<String> {
        val brut = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .getString(CLE_SOURCES_BASE_JSON, "") ?: ""
        return if (brut.isEmpty()) emptyList() else brut.split(SEPARATEUR_SOURCES_BASE_JSON).filter { it.isNotBlank() }
    }

    fun ecrireSourcesBaseJson(context: Context, uris: List<String>) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putString(CLE_SOURCES_BASE_JSON, uris.joinToString(SEPARATEUR_SOURCES_BASE_JSON))
            .apply()
    }

    /**
     * Sauvegarde/restauration des reglages (ajoute en v0.27 a la demande de Tomyn, pour ne pas
     * tout reconfigurer a la main en cas de changement de telephone ou de reinstallation). Format
     * texte volontairement simple ("cle=valeur", une ligne par reglage) plutot que du JSON : pas
     * de dependance supplementaire (org.json n'est utilise nulle part ailleurs dans l'appli), et
     * c'est le meme principe que les CSV deja utilises pour l'historique/l'export - facile a
     * relire a l'oeil si besoin.
     */
    private const val CLE_SAUVEGARDE_VIBRATION = "vibration"
    private const val CLE_SAUVEGARDE_SON = "son"
    private const val CLE_SAUVEGARDE_COLONNES = "colonnesEtiquettes"
    private const val CLE_SAUVEGARDE_LIGNES = "lignesEtiquettes"

    fun exporterReglages(context: Context): String {
        val lignes = listOf(
            "# ElegooRFID - sauvegarde des reglages",
            "$CLE_SAUVEGARDE_VIBRATION=${lireVibrationFinLecture(context)}",
            "$CLE_SAUVEGARDE_SON=${lireSonFinLecture(context)}",
            "$CLE_SAUVEGARDE_COLONNES=${lireColonnesEtiquettes(context)}",
            "$CLE_SAUVEGARDE_LIGNES=${lireLignesEtiquettes(context)}"
        )
        return lignes.joinToString("\n")
    }

    /**
     * @return true si au moins un reglage reconnu a ete restaure, false si le fichier est vide ou
     * ne contient rien de reconnaissable (fichier corrompu ou sans rapport) - les cles absentes ou
     * invalides sont simplement ignorees plutot que de faire echouer toute la restauration, pour
     * rester tolerant a une sauvegarde partielle ou faite par une version anterieure.
     */
    fun importerReglages(context: Context, contenu: String): Boolean {
        var auMoinsUnReglageRestaure = false
        for (ligne in contenu.lines()) {
            val ligneNettoyee = ligne.trim()
            if (ligneNettoyee.isEmpty() || ligneNettoyee.startsWith("#")) continue
            val separateur = ligneNettoyee.indexOf('=')
            if (separateur <= 0) continue
            val cle = ligneNettoyee.substring(0, separateur).trim()
            val valeur = ligneNettoyee.substring(separateur + 1).trim()
            when (cle) {
                CLE_SAUVEGARDE_VIBRATION -> valeur.toBooleanStrictOrNull()?.let { ecrireVibrationFinLecture(context, it); auMoinsUnReglageRestaure = true }
                CLE_SAUVEGARDE_SON -> valeur.toBooleanStrictOrNull()?.let { ecrireSonFinLecture(context, it); auMoinsUnReglageRestaure = true }
                CLE_SAUVEGARDE_COLONNES -> valeur.toIntOrNull()?.let {
                    if (it in 1..COLONNES_ETIQUETTES_MAX) {
                        ecrireGrilleEtiquettes(context, it, lireLignesEtiquettes(context))
                        auMoinsUnReglageRestaure = true
                    }
                }
                CLE_SAUVEGARDE_LIGNES -> valeur.toIntOrNull()?.let {
                    if (it in 1..LIGNES_ETIQUETTES_MAX) {
                        ecrireGrilleEtiquettes(context, lireColonnesEtiquettes(context), it)
                        auMoinsUnReglageRestaure = true
                    }
                }
            }
        }
        return auMoinsUnReglageRestaure
    }
}
