package com.tomyn.elegoorfid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.IOException

/**
 * Planche d'etiquettes (ajoute le 09/10/2026 a la demande de Tomyn, meme principe que ce qu'il
 * avait deja sur son autre appli de lecture RFID (Bambu) : apres une lecture reussie, un bouton
 * ajoute l'etiquette de la bobine courante a une planche A4 qui s'accumule au fil des lectures ;
 * un autre bouton genere le PDF final (plusieurs pages si plus de 24 etiquettes), pret a imprimer
 * sur une planche autocollante.
 *
 * Grille GENERIQUE (demande expresse de Tomyn, pas de reference de planche precise au depart) :
 * 3 colonnes x 8 lignes = 24 etiquettes par page A4 par defaut. Reglable depuis les Parametres
 * depuis la v0.23 (voir GestionnaireParametres.lireColonnesEtiquettes/lireLignesEtiquettes) pour
 * s'adapter a une autre planche autocollante sans toucher au code - les marges ci-dessous restent
 * en dur, tout le reste du calcul de mise en page en depend automatiquement.
 *
 * Stockage : un fichier texte simple (une etiquette par ligne, champs separes par ";"), dans le
 * meme esprit que historique_scans.csv (voir MainActivity.enregistrerDansHistorique) - pas besoin
 * d'une lib JSON pour une poignee de champs.
 */
object PlancheEtiquettes {

    private const val NOM_FICHIER = "planche_etiquettes.txt"
    private const val SEPARATEUR = ";"

    // Lien encode dans le QR de chaque etiquette (voir dessinerQr plus bas et l'intent-filter
    // correspondant dans AndroidManifest.xml). Change en v0.21 : avant, le QR codait le hex brut
    // du dump, "exactement le texte qu'accepte deja Importer un dump pour cloner" d'apres le
    // commentaire d'origine (v0.18) - en realite rien ne consommait ce texte scanne, cette partie
    // de la fonctionnalite n'avait jamais ete cablee jusqu'au bout. Avec un vrai lien
    // "elegoorfid://dump/<hex>", n'importe quelle appli de scan QR (y compris le detecteur
    // integre a la plupart des appareils photo Android) propose directement "Ouvrir avec
    // ElegooRFID" - voir MainActivity.traiterIntentEventuel pour la reception.
    private const val SCHEME_QR = "elegoorfid"
    private const val HOTE_QR = "dump"

    private fun lienQrPourDump(dumpHex: String): String = "$SCHEME_QR://$HOTE_QR/$dumpHex"

    // Origine d'une bobine (ajoute en v0.24 a la demande de Tomyn, pour ne pas confondre plus
    // tard une vraie bobine Elegoo scannee avec un tag qu'il a fabrique lui-meme) - voir
    // MainActivity.afficherResultats/derniereOrigineBobine pour qui passe quoi. ORIGINE_SCAN_NFC
    // est la valeur par defaut (et la seule a ne PAS afficher de badge sur l'etiquette, voir
    // dessinerEtiquette - c'est le cas largement majoritaire, pas la peine de l'annoncer a chaque
    // fois).
    const val ORIGINE_SCAN_NFC = "Scan NFC"
    const val ORIGINE_CREATION = "Création manuelle"
    const val ORIGINE_QR = "Lecture QR (sans NFC)"

    // Mise en page de la grille (v0.23 : reglable, voir le commentaire en tete de fichier) -
    // valeurs par defaut 3x8 si rien n'a ete configure dans les Parametres.
    private fun colonnes(context: Context): Int = GestionnaireParametres.lireColonnesEtiquettes(context)
    private fun lignesParPage(context: Context): Int = GestionnaireParametres.lireLignesEtiquettes(context)
    fun etiquettesParPage(context: Context): Int = colonnes(context) * lignesParPage(context)

    // Dimensions A4 en points PDF (1pt = 1/72 pouce ; 595 x 842 = A4 a 72dpi, standard PdfDocument).
    private const val LARGEUR_PAGE = 595f
    private const val HAUTEUR_PAGE = 842f
    private const val MARGE = 24f

    data class Etiquette(
        val matiereTexte: String?,
        val sousTypeTexte: String?,
        val couleurHex: String?,
        val poidsGrammes: Int?,
        val diametreMm: Double?,
        val tempMinC: Int?,
        val tempMaxC: Int?,
        val dateAffichee: String?,
        // Dump brut (pages 0x00-0x27) en hexa, pour le QR code de reclonage - voir dessinerQr.
        // Null si le dump source etait trop court (improbable, le bouton "Ajouter a la planche"
        // n'est propose qu'apres une lecture/creation reussie, qui couvre toujours cette plage).
        val dumpHex: String?,
        // Origine de la bobine (ORIGINE_SCAN_NFC/ORIGINE_CREATION/ORIGINE_QR) - null pour les
        // etiquettes ecrites par une version anterieure a v0.24 (pas de badge affiche dans ce
        // cas, voir dessinerEtiquette).
        val origine: String?
    )

    private fun fichier(context: Context): File = File(context.getExternalFilesDir(null), NOM_FICHIER)

    /** Transforme une Etiquette en une ligne de texte stable (null -> champ vide). */
    private fun versLigne(e: Etiquette): String = listOf(
        e.matiereTexte ?: "",
        e.sousTypeTexte ?: "",
        e.couleurHex ?: "",
        e.poidsGrammes?.toString() ?: "",
        e.diametreMm?.toString() ?: "",
        e.tempMinC?.toString() ?: "",
        e.tempMaxC?.toString() ?: "",
        e.dateAffichee ?: "",
        e.dumpHex ?: "",
        e.origine ?: ""
    ).joinToString(SEPARATEUR)

    private fun depuisLigne(ligne: String): Etiquette? {
        val champs = ligne.split(SEPARATEUR)
        if (champs.size < 8) return null
        return Etiquette(
            matiereTexte = champs[0].ifBlank { null },
            sousTypeTexte = champs[1].ifBlank { null },
            couleurHex = champs[2].ifBlank { null },
            poidsGrammes = champs[3].toIntOrNull(),
            diametreMm = champs[4].toDoubleOrNull(),
            tempMinC = champs[5].toIntOrNull(),
            tempMaxC = champs[6].toIntOrNull(),
            dateAffichee = champs[7].ifBlank { null },
            // getOrNull : les lignes ecrites par une version anterieure a l'ajout du QR (v0.17 et
            // avant) n'ont que 8 champs - pas de plantage, juste pas de QR sur ces etiquettes-la.
            // Meme logique pour "origine" (v0.24, 10e champ).
            dumpHex = champs.getOrNull(8)?.ifBlank { null },
            origine = champs.getOrNull(9)?.ifBlank { null }
        )
    }

    /**
     * @param dump dump brut de la bobine (voir DecodeurElegoo/EncodeurElegoo) - sert a coder le QR de reclonage.
     * @param origine ORIGINE_SCAN_NFC/ORIGINE_CREATION/ORIGINE_QR (voir ces constantes) - ajoute
     *   en v0.24 pour ne pas confondre plus tard une vraie bobine scannee avec un tag fabrique.
     */
    fun ajouter(context: Context, info: DecodeurElegoo.InfoBobine, dump: ByteArray, origine: String = ORIGINE_SCAN_NFC) {
        val etiquette = Etiquette(
            matiereTexte = info.matiereTexte,
            sousTypeTexte = info.sousTypeTexte,
            couleurHex = info.couleurHex,
            poidsGrammes = info.poidsGrammes,
            diametreMm = info.diametreMm,
            tempMinC = info.tempMinC,
            tempMaxC = info.tempMaxC,
            dateAffichee = info.dateFabricationTexte,
            dumpHex = if (dump.size >= ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) {
                dump.copyOfRange(0, ClonageElegoo.TAILLE_MIN_DUMP_SOURCE).joinToString("") { "%02X".format(it) }
            } else null,
            origine = origine
        )
        try {
            fichier(context).appendText(versLigne(etiquette) + "\n")
        } catch (e: IOException) { /* pas grave si l'ajout echoue, l'utilisateur reessaiera */ }
    }

    fun lister(context: Context): List<Etiquette> {
        val f = fichier(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { if (it.isBlank()) null else depuisLigne(it) }
    }

    fun nombre(context: Context): Int = lister(context).size

    /** Nombre de pages A4 que produira genererPdf() pour cette liste (jamais 0 : une planche vide
     * genere quand meme une page, voir genererPdf). Utilise par l'impression directe (voir
     * ImpressionPlanche.kt) pour annoncer le nombre de pages a l'imprimante avant meme d'avoir
     * genere le PDF. */
    fun nombrePages(etiquettes: List<Etiquette>, context: Context): Int {
        val parPage = etiquettesParPage(context)
        return if (etiquettes.isEmpty()) 1 else (etiquettes.size + parPage - 1) / parPage
    }

    fun vider(context: Context) {
        try { fichier(context).delete() } catch (e: IOException) { /* rien a faire */ }
    }

    /**
     * Genere le PDF (une ou plusieurs pages, grille reglable - voir colonnes/lignesParPage) dans
     * le cache de l'appli et renvoie le fichier, pret a etre propose en "Enregistrer sous" (voir
     * MainActivity.exporterVers, qui lit un texte - ici on ecrit directement les octets du PDF
     * sur l'Uri choisi, voir genererPdfVersFlux ci-dessous).
     */
    fun genererPdf(etiquettes: List<Etiquette>, context: Context): PdfDocument {
        val document = PdfDocument()
        val nbColonnes = colonnes(context)
        val nbLignes = lignesParPage(context)
        val parPage = nbColonnes * nbLignes
        val largeurCellule = (LARGEUR_PAGE - 2 * MARGE) / nbColonnes
        val hauteurCellule = (HAUTEUR_PAGE - 2 * MARGE) / nbLignes

        val peintureCadre = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val peintureTitre = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isFakeBoldText = true
        }
        val peintureTexte = Paint().apply {
            color = Color.DKGRAY
            textSize = 8f
        }
        val peintureCouleur = Paint().apply { style = Paint.Style.FILL }

        val pages = etiquettes.chunked(parPage)
        // Une planche vide (aucune etiquette) produit tout de meme une page, pour que "Generer le
        // PDF" renvoie toujours quelque chose d'ouvrable plutot qu'un fichier PDF sans page valide.
        val pagesAGenerer = if (pages.isEmpty()) listOf(emptyList()) else pages

        for (etiquettesPage in pagesAGenerer) {
            val pageInfo = PdfDocument.PageInfo.Builder(LARGEUR_PAGE.toInt(), HAUTEUR_PAGE.toInt(), 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            for ((index, etiquette) in etiquettesPage.withIndex()) {
                val colonne = index % nbColonnes
                val ligne = index / nbColonnes
                val x = MARGE + colonne * largeurCellule
                val y = MARGE + ligne * hauteurCellule
                dessinerEtiquette(canvas, etiquette, x, y, largeurCellule, hauteurCellule, peintureCadre, peintureTitre, peintureTexte, peintureCouleur)
            }
            // Cadres des cellules restees vides sur la derniere page, pour que la feuille reste
            // decoupable/pliable proprement meme partiellement remplie.
            for (index in etiquettesPage.size until parPage) {
                val colonne = index % nbColonnes
                val ligne = index / nbColonnes
                val x = MARGE + colonne * largeurCellule
                val y = MARGE + ligne * hauteurCellule
                canvas.drawRect(x, y, x + largeurCellule, y + hauteurCellule, peintureCadre)
            }

            document.finishPage(page)
        }

        return document
    }

    private fun dessinerEtiquette(
        canvas: Canvas,
        e: Etiquette,
        x: Float,
        y: Float,
        largeur: Float,
        hauteur: Float,
        cadre: Paint,
        titre: Paint,
        texte: Paint,
        couleur: Paint
    ) {
        val rembourrage = 6f
        canvas.drawRect(x, y, x + largeur, y + hauteur, cadre)

        // QR de reclonage (ajoute le 09/10/2026) en haut a droite - voir dessinerQr. Le texte et
        // la pastille de couleur (en haut a gauche) se partagent le reste de la largeur.
        val tailleQr = minOf(hauteur - 2 * rembourrage, 56f)
        if (e.dumpHex != null) {
            val xQr = x + largeur - rembourrage - tailleQr
            val yQr = y + rembourrage
            dessinerQr(canvas, lienQrPourDump(e.dumpHex), xQr, yQr, tailleQr)
        }

        // Pastille de couleur a gauche, infos texte a droite.
        val tailleAide = hauteur - 2 * rembourrage
        val tailleCouleur = minOf(tailleAide, 22f)
        var xTexte = x + rembourrage
        if (e.couleurHex != null) {
            try {
                couleur.color = Color.parseColor("#${e.couleurHex}")
                val rect = RectF(x + rembourrage, y + rembourrage, x + rembourrage + tailleCouleur, y + rembourrage + tailleCouleur)
                canvas.drawOval(rect, couleur)
                canvas.drawOval(rect, cadre)
                xTexte = x + rembourrage + tailleCouleur + 6f
            } catch (ex: IllegalArgumentException) { /* hex invalide : pas de pastille, le texte prend toute la largeur */ }
        }

        var yTexte = y + rembourrage + 9f
        val titreTexte = listOfNotNull(e.matiereTexte, e.sousTypeTexte).joinToString(" ").ifBlank { "Bobine" }
        canvas.drawText(titreTexte, xTexte, yTexte, titre)

        val lignesInfo = mutableListOf<String>()
        // Badge d'origine (v0.24) : seulement si ce n'est PAS un vrai scan NFC (cas largement
        // majoritaire, pas besoin de l'annoncer a chaque fois) - evite de confondre plus tard une
        // vraie bobine Elegoo avec un tag cree a la main ou simplement reconsulte via QR.
        if (e.origine != null && e.origine != ORIGINE_SCAN_NFC) {
            val badge = when (e.origine) {
                ORIGINE_CREATION -> "⚠ Créé manuellement"
                ORIGINE_QR -> "⚠ Vu via QR (non re-scanné)"
                else -> "⚠ ${e.origine}"
            }
            lignesInfo.add(badge)
        }
        if (e.poidsGrammes != null) lignesInfo.add("${e.poidsGrammes}g")
        if (e.diametreMm != null) lignesInfo.add("${e.diametreMm}mm")
        if (e.tempMinC != null && e.tempMaxC != null) lignesInfo.add("${e.tempMinC}-${e.tempMaxC}°C")
        e.dateAffichee?.let { lignesInfo.add(it) }

        for (ligneTexte in lignesInfo) {
            yTexte += 10f
            if (yTexte > y + hauteur - rembourrage) break
            canvas.drawText(ligneTexte, xTexte, yTexte, texte)
        }
    }

    /**
     * Dessine un QR code encodant un lien (voir lienQrPourDump) directement sur le Canvas, module
     * par module (pas de Bitmap intermediaire) - permet de rescanner l'etiquette papier plus tard
     * pour consulter les infos de la bobine ou la recloner, SANS le tag NFC a portee (utile si le
     * tag est abime/illisible mais l'etiquette papier existe encore) - voir
     * MainActivity.traiterIntentEventuel pour la reception du lien et v0.21 dans le CHANGELOG.
     *
     * NON TESTE avec une vraie imprimante (aucune disponible ici) : la densite du QR a cette
     * taille (56pt, ~0.78cm) pour un lien d'environ 330 caracteres peut etre fine a lire pour un
     * appareil photo de telephone selon la qualite d'impression - a verifier en vrai, et a
     * agrandir dans PlancheEtiquettes si ca scanne mal.
     *
     * @return false si le QR n'a pas pu etre genere (contenu trop long, erreur zxing) - l'appelant
     *         recupere alors toute la largeur de la cellule pour le texte.
     */
    private fun dessinerQr(canvas: Canvas, contenu: String, x: Float, y: Float, taille: Float): Boolean {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
            )
            val matrice = QRCodeWriter().encode(contenu, BarcodeFormat.QR_CODE, 0, 0, hints)
            val module = taille / matrice.width
            val peintureQr = Paint().apply { style = Paint.Style.FILL; color = Color.BLACK }
            for (ty in 0 until matrice.height) {
                for (tx in 0 until matrice.width) {
                    if (matrice.get(tx, ty)) {
                        canvas.drawRect(x + tx * module, y + ty * module, x + (tx + 1) * module, y + (ty + 1) * module, peintureQr)
                    }
                }
            }
            true
        } catch (ex: WriterException) {
            false
        }
    }
}
