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
}
