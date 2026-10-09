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
}
