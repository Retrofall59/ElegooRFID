package com.tomyn.elegoorfid

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var imgNfc: ImageView
    private lateinit var vuCouleur: View
    private lateinit var txtStatut: TextView
    private lateinit var layoutLignesInfo: LinearLayout
    private lateinit var btnReglagesNfc: Button
    private lateinit var btnCloner: Button
    private lateinit var btnAnnulerClonage: Button
    private lateinit var btnEffacer: Button
    private lateinit var btnAjouterPlanche: Button
    private lateinit var btnGenererPdfPlanche: Button
    private lateinit var btnClonerLot: Button
    private lateinit var nfcAdapter: NfcAdapter

    private val dernieresLignesInfo = mutableListOf<Pair<Int, String>>()
    private var dernierDumpTexte: String = ""
    private var dernierResume: String = ""
    private var dernierScanReussi = false
    private var contenuAExporter: String? = null
    private var dernierInfoBobine: DecodeurElegoo.InfoBobine? = null
    // Origine de la derniere bobine affichee (ajoute en v0.24, voir afficherResultats) - sert a
    // distinguer sur l'etiquette imprimee un vrai scan NFC d'une creation manuelle ou d'une
    // consultation via QR sans NFC, pour ne pas les confondre plus tard (voir
    // PlancheEtiquettes.Etiquette.origine).
    private var derniereOrigineBobine: String = PlancheEtiquettes.ORIGINE_SCAN_NFC

    // --- Planche d'etiquettes : voir PlancheEtiquettes.kt. Le PDF genere est ecrit directement
    // sur l'Uri choisi par l'utilisateur au moment ou l'export se concretise (onActivityResult,
    // CODE_EXPORT_PLANCHE) - contrairement a contenuAExporter (texte), on le regenere a la volee
    // a ce moment-la plutot que de le garder en memoire entre-temps. ---

    // --- Clonage : voir ClonageElegoo.kt pour le detail et le choix de securite (pourquoi on ne
    // touche qu'aux pages 0x03-0x27, jamais 0x28+ qui sont de la configuration de puce). ---
    private var dernierDumpBrut: ByteArray? = null
    private var enAttenteTagCible = false
    private var ecrasementConfirme = false

    // --- Effacement : voir ClonageElegoo.kt (pagesAEffacer/zoneEffacee) - meme plage de pages
    // que le clonage, jamais 0x28+. Contrairement au clonage, ne depend d'aucune lecture
    // prealable : disponible des le lancement de l'appli. ---
    private var enAttenteTagEffacement = false

    // --- Avertissement preventif avant effacement (ajoute le 09/10/2026) : lit les verrous AVANT
    // de tenter l'ecriture, pour prevenir plutot que de laisser echouer silencieusement - voir
    // effacerTagCible. Un verrou a 00 00 ne garantit pas que l'effacement reussira (voir le mystere
    // non resolu sur la page 0x03, ClonageElegoo.kt), donc cet avertissement reste imparfait : il
    // previent seulement quand un verrou NON nul est effectivement detecte. ---
    private var effacementConfirmeMalgreVerrou = false

    // --- Clonage par lot (ajoute le 09/10/2026 a la demande de Tomyn) : importe plusieurs dumps
    // d'un coup, puis les ecrit les uns apres les autres sur autant de tags vierges, sans ressaisir
    // ni rescanner a chaque fois. Reutilise executerClonage (meme coeur que le clonage simple). ---
    private var enAttenteTagLot = false
    private var lotAClone: List<Pair<String, ByteArray>> = emptyList()
    private var indexLotCourant = 0
    /** @param detail raison de l'echec (null si reussi) - ajoute en v0.24 pour permettre
     * l'export CSV du resultat complet d'un lot (voir terminerLot). */
    private data class ResultatLotLigne(val nom: String, val reussi: Boolean, val detail: String?)
    private val resultatsLot = mutableListOf<ResultatLotLigne>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contenuAExporter = savedInstanceState?.getString("contenuAExporter")
        setContentView(R.layout.activity_main)

        imgNfc = findViewById(R.id.imgNfc)
        vuCouleur = findViewById(R.id.vuCouleur)
        txtStatut = findViewById(R.id.txtStatut)
        layoutLignesInfo = findViewById(R.id.layoutLignesInfo)
        btnReglagesNfc = findViewById(R.id.btnReglagesNfc)

        btnCloner = findViewById(R.id.btnCloner)
        btnAnnulerClonage = findViewById(R.id.btnAnnulerClonage)
        btnEffacer = findViewById(R.id.btnEffacer)
        btnAjouterPlanche = findViewById(R.id.btnAjouterPlanche)
        btnGenererPdfPlanche = findViewById(R.id.btnGenererPdfPlanche)
        btnClonerLot = findViewById(R.id.btnClonerLot)

        btnReglagesNfc.setOnClickListener { ouvrirReglagesNfc() }
        btnCloner.setOnClickListener { demarrerModeClonage() }
        btnAnnulerClonage.setOnClickListener { if (enAttenteTagLot) annulerLot() else annulerModeAttente() }
        btnEffacer.setOnClickListener { demarrerModeEffacement() }
        findViewById<Button>(R.id.btnClonerLot).setOnClickListener { demarrerImportLot() }
        btnAjouterPlanche.setOnClickListener { ajouterEtiquetteAPlanche() }
        btnGenererPdfPlanche.setOnClickListener { genererPdfPlanche() }
        findViewById<Button>(R.id.btnCreerTag).setOnClickListener {
            startActivityForResult(Intent(this, CreationTagActivity::class.java), CODE_CREATION)
        }
        findViewById<Button>(R.id.btnImporterBase).setOnClickListener {
            startActivityForResult(Intent(this, ImportBaseDonneesActivity::class.java), CODE_IMPORT_BASE)
        }
        findViewById<Button>(R.id.btnExporter).setOnClickListener { exporterDump() }
        findViewById<Button>(R.id.btnImporterDump).apply {
            setOnClickListener { importerDump() }
            // Appui long = coller un dump depuis le presse-papier (v0.27), symetrique de l'appui
            // long "Copier" -> "copier le dump" ajoute en v0.26 - voir collerDumpDepuisPressePapier().
            setOnLongClickListener { collerDumpDepuisPressePapier(); true }
        }
        findViewById<Button>(R.id.btnScannerQr).setOnClickListener {
            startActivityForResult(Intent(this, ScanQrActivity::class.java), CODE_SCAN_QR)
        }
        findViewById<Button>(R.id.btnCopier).apply {
            setOnClickListener { copierResume() }
            // Appui long = copier le dump brut en hexa (v0.26) plutot que le resume - voir
            // copierDump(). Pas de nouveau bouton pour ne pas surcharger la rangee existante.
            setOnLongClickListener { copierDump(); true }
        }
        findViewById<Button>(R.id.btnPartager).setOnClickListener { partagerResume() }
        findViewById<Button>(R.id.btnHistorique).setOnClickListener { afficherHistorique() }
        findViewById<Button>(R.id.btnRapportCompat).apply {
            setOnClickListener { copierRapportCompatibilite() }
            // Appui long = partager directement (v0.28), meme principe que les appuis longs
            // "Copier" -> dump (v0.26) et "Importer" -> coller (v0.27) : pas de bouton
            // supplementaire pour un ecran deja charge.
            setOnLongClickListener { partagerRapportCompatibilite(); true }
        }
        findViewById<ImageButton>(R.id.btnParametres).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        NfcAdapter.getDefaultAdapter(this)?.let { nfcAdapter = it }

        actualiserBoutonPlanche()
        restaurerAffichageResultat(savedInstanceState)
        traiterIntentEventuel(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!::nfcAdapter.isInitialized) return

        if (!nfcAdapter.isEnabled) {
            btnReglagesNfc.visibility = View.VISIBLE
            txtStatut.text = "Le NFC est désactivé sur ce téléphone."
            return
        }
        if (btnReglagesNfc.visibility == View.VISIBLE) {
            btnReglagesNfc.visibility = View.GONE
            txtStatut.text = "Approche une bobine Elegoo du dos du téléphone..."
        }

        nfcAdapter.enableReaderMode(
            this,
            NfcAdapter.ReaderCallback { tag -> runOnUiThread { lireTag(tag) } },
            NfcAdapter.FLAG_READER_NFC_A,
            null
        )
        // Ecran maintenu allume pendant qu'une lecture est possible (ajoute en v0.23 a la demande
        // de Tomyn) : surtout utile pendant un clonage par lot, ou le telephone peut sinon
        // s'eteindre entre deux tags et obliger a deverrouiller en plein milieu de la manip.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::nfcAdapter.isInitialized) nfcAdapter.disableReaderMode(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        traiterIntentEventuel(intent)
    }

    private fun traiterIntentEventuel(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW) {
            traiterLienEtiquette(intent.data)
            return
        }
        if (intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) return
        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        lireTag(tag)
    }

    /**
     * Reception du lien "elegoorfid://dump/<hex>" encode dans le QR des etiquettes (v0.21, voir
     * PlancheEtiquettes.lienQrPourDump et l'intent-filter dans AndroidManifest.xml) : permet de
     * consulter une bobine (et de la cloner si besoin) a partir de l'etiquette papier scannee par
     * n'importe quelle appli de QR, SANS avoir le tag NFC a portee - utile si le tag est
     * abime/illisible mais l'etiquette existe encore.
     *
     * enregistrerHistorique=false : ce n'est pas une vraie lecture NFC, pas d'entree d'historique
     * ni de sauvegarde automatique pour une simple consultation - par contre dernierDumpBrut est
     * bien renseigne par afficherResultats, donc "Cloner" fonctionne normalement juste apres.
     */
    private fun traiterLienEtiquette(uri: android.net.Uri?) {
        if (uri == null || uri.scheme != "elegoorfid" || uri.host != "dump") return
        val hex = uri.lastPathSegment
        if (hex.isNullOrBlank() || hex.length % 2 != 0 || !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            Toast.makeText(this, "Étiquette illisible (lien invalide).", Toast.LENGTH_LONG).show()
            return
        }
        val dump = try {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Étiquette illisible (lien invalide).", Toast.LENGTH_LONG).show()
            return
        }
        val info = DecodeurElegoo.decoder(dump)
        if (info.headerValide != true) {
            Toast.makeText(this, "Étiquette reconnue mais le contenu ne correspond pas à un dump Elegoo valide.", Toast.LENGTH_LONG).show()
            return
        }
        afficherResultats(info, dump, statutTexte = "Bobine (depuis l'étiquette, sans NFC)", enregistrerHistorique = false, origine = PlancheEtiquettes.ORIGINE_QR)
    }

    private fun lireTag(tag: Tag) {
        if (enAttenteTagCible) {
            traiterTagCibleClonage(tag)
            return
        }
        if (enAttenteTagEffacement) {
            effacerTagCible(tag)
            return
        }
        if (enAttenteTagLot) {
            traiterTagLot(tag)
            return
        }

        layoutLignesInfo.removeAllViews()
        dernieresLignesInfo.clear()
        vuCouleur.visibility = View.GONE
        imgNfc.visibility = View.VISIBLE
        btnCloner.visibility = View.GONE
        btnAjouterPlanche.visibility = View.GONE
        txtStatut.text = "Lecture en cours..."

        val nfcA = NfcA.get(tag)
        if (nfcA == null) {
            txtStatut.text = "Ce tag n'est pas compatible NFC-A"
            dernierScanReussi = false
            jouerSonResultat(false)
            if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
            return
        }

        try {
            nfcA.connect()
            // Le decodeur a besoin jusqu'a l'octet 39 (date) : on lit large, jusqu'a la page 11
            // (0x2C, fin de la zone documentee), par blocs de 4 pages (16 octets).
            val dump = lireDumpBrut(nfcA, 40)
            val info = DecodeurElegoo.decoder(dump)

            if (info.headerValide != true) {
                txtStatut.text = "Tag lu, mais l'en-tête attendu (0x36) est absent — pas au format Elegoo reconnu, ou doc non conforme à ce tag"
                dernierScanReussi = false
                dernierDumpTexte = "--- DUMP BRUT ---\n${formaterDumpHex(dump)}"
                jouerSonResultat(false)
                if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
                return
            }

            afficherResultats(info, dump)
        } catch (e: Exception) {
            txtStatut.text = "Erreur de lecture : ${e.message}"
            dernierScanReussi = false
            jouerSonResultat(false)
            if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
        } finally {
            try { nfcA.close() } catch (e: Exception) { /* rien a faire */ }
        }
    }

    /** Lit les pages 0 a dernierePageIncluse (arrondi au multiple de 4 superieur) via READ (0x30). */
    private fun lireDumpBrut(nfcA: NfcA, dernierePageIncluse: Int): ByteArray {
        val tampon = ByteArrayOutputStream()
        var page = 0
        while (page <= dernierePageIncluse) {
            tampon.write(nfcA.transceive(byteArrayOf(0x30, page.toByte())))
            page += 4
        }
        return tampon.toByteArray()
    }

    /**
     * Ecrit une page (commande WRITE, 0xA2) et VERIFIE la reponse, avec quelques tentatives en
     * cas d'echec passager.
     *
     * Ajoute le 08/10/2026, suite a un bug terrain (pascal_lb) : l'effacement signalait "la
     * relecture ne confirme pas un effacement complet" alors que le tag etait bien devenu
     * inutilisable (en-tete Elegoo absent, reecriture possible) - signe que l'ecriture avait
     * fonctionne pour la plupart des pages mais pas toutes. La cause : ni l'effacement ni le
     * clonage ne regardaient jamais la reponse de la commande WRITE. Or le protocole NFC Forum
     * Type 2 Tag prevoit qu'une ecriture reussie renvoie un ACK (un seul octet 0x0A) - une
     * ecriture qui echoue silencieusement (tag eloigne un instant, page refusee...) peut renvoyer
     * autre chose, ou rien d'exploitable, sans forcement lever d'exception. En ignorant cette
     * reponse, le code pouvait croire avoir tout ecrit alors qu'une ou plusieurs pages n'avaient
     * pas pris - exactement ce qui explique le cas remonte.
     */
    private fun ecrirePageAvecVerif(nfcA: NfcA, page: Int, octets: ByteArray): Boolean {
        repeat(3) {
            try {
                val reponse = nfcA.transceive(
                    byteArrayOf(0xA2.toByte(), page.toByte(), octets[0], octets[1], octets[2], octets[3])
                )
                if (reponse.size == 1 && reponse[0] == 0x0A.toByte()) {
                    // Delai apres l'ACK (ajoute le 09/10/2026, suite au retour terrain de
                    // pascal_lb : effacement toujours incomplet meme avec l'ACK verifie - page
                    // 0x10 specifiquement, qui change d'un essai a l'autre selon lui). Hypothese :
                    // l'ACK confirme la reception de la commande, pas la fin reelle du cycle
                    // d'ecriture EEPROM (quelques ms) ; enchainer immediatement sur la page
                    // suivante (ou sur la relecture finale) pourrait devancer cette fin de cycle.
                    // Ce delai ne resout pas forcement tout (un verrou materiel sur une page
                    // precise resterait un verrou), mais elimine cette hypothese de course sans
                    // rien risquer d'autre.
                    try { Thread.sleep(10) } catch (e: InterruptedException) { /* rien a faire */ }
                    return true
                }
            } catch (e: Exception) {
                // Tag eloigne un instant pendant l'ecriture : on retente avant d'abandonner cette page.
            }
        }
        return false
    }

    // ============================== CLONAGE ==============================
    // Voir ClonageElegoo.kt pour le detail des plages de pages et le raisonnement de securite
    // (pourquoi on ne touche jamais aux pages 0x28+ de configuration de la puce).

    private fun demarrerModeClonage() {
        val source = dernierDumpBrut
        if (source == null || source.size < ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) {
            Toast.makeText(this, "Scanne d'abord une bobine Elegoo valide avant de cloner.", Toast.LENGTH_SHORT).show()
            return
        }
        enAttenteTagCible = true
        btnCloner.visibility = View.GONE
        btnEffacer.visibility = View.GONE
        btnAjouterPlanche.visibility = View.GONE
        btnClonerLot.visibility = View.GONE
        btnAnnulerClonage.visibility = View.VISIBLE
        btnAnnulerClonage.text = "Annuler le clonage"
        layoutLignesInfo.removeAllViews()
        dernieresLignesInfo.clear()
        vuCouleur.visibility = View.GONE
        imgNfc.visibility = View.VISIBLE
        txtStatut.text = "Approche maintenant la bobine VIERGE à écrire (NTAG213/215)..."
    }

    /**
     * Effacement (ajoute le 08/10/2026) : remet a zero la zone de donnees (0x03-0x27, voir
     * ClonageElegoo) d'un tag quelconque - independant de toute lecture prealable, contrairement
     * au clonage. Utile pour reinitialiser un tag deja ecrit (test precedent, ancien clonage)
     * sans dependre d'un outil externe (NFC Tools) dont l'effacement generique s'est revele
     * incomplet dans ce cas precis sur retour terrain (pascal_lb, forum, 08/10/2026).
     */
    private fun demarrerModeEffacement() {
        AlertDialog.Builder(this)
            .setTitle("Effacer un tag")
            .setMessage("Le prochain tag approché sera entièrement effacé (données produit remises à zéro), que ce soit une bobine Elegoo, un clone, ou un tag de test. Irréversible. Continuer ?")
            .setPositiveButton("Approcher un tag") { _, _ ->
                enAttenteTagEffacement = true
                effacementConfirmeMalgreVerrou = false
                btnCloner.visibility = View.GONE
                btnEffacer.visibility = View.GONE
                btnAjouterPlanche.visibility = View.GONE
                btnClonerLot.visibility = View.GONE
                btnAnnulerClonage.visibility = View.VISIBLE
                btnAnnulerClonage.text = "Annuler l'effacement"
                layoutLignesInfo.removeAllViews()
                dernieresLignesInfo.clear()
                vuCouleur.visibility = View.GONE
                imgNfc.visibility = View.VISIBLE
                txtStatut.text = "Approche maintenant le tag à effacer..."
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun effacerTagCible(tag: Tag) {
        if (effacementConfirmeMalgreVerrou) {
            effacementConfirmeMalgreVerrou = false
            effectuerEffacement(tag)
            return
        }

        val nfcA = NfcA.get(tag)
        if (nfcA == null) {
            Toast.makeText(this, "Ce tag n'est pas compatible NFC-A, essaie un autre tag.", Toast.LENGTH_SHORT).show()
            return
        }

        // Lecture des verrous AVANT toute ecriture - voir le commentaire pres de la declaration de
        // effacementConfirmeMalgreVerrou. Connexion separee de celle de l'effacement lui-meme (dans
        // effectuerEffacement), meme principe que traiterTagCibleClonage/ecrireEtVerifierClone :
        // lire d'abord, decider, et si besoin redemander un scan apres une popup.
        try {
            nfcA.connect()
            val dumpAvant = lireDumpBrut(nfcA, ClonageElegoo.DERNIERE_PAGE_DIAGNOSTIC)
            nfcA.close()
            val verrousDyn = ClonageElegoo.verrousDynamiquesHex(dumpAvant)
            val verrousStat = ClonageElegoo.verrousStatiquesHex(dumpAvant)
            val verrouDetecte = (verrousDyn != null && verrousDyn != "00 00") || (verrousStat != null && verrousStat != "00 00")
            if (verrouDetecte) {
                runOnUiThread {
                    txtStatut.text = "Verrou détecté - confirmation demandée..."
                    AlertDialog.Builder(this)
                        .setTitle("Ce tag semble verrouillé")
                        .setMessage(
                            "Verrou dynamique (0x28) : ${verrousDyn ?: "?"} — verrou statique (0x02) : ${verrousStat ?: "?"}.\n\n" +
                                "L'effacement risque d'échouer sur une ou plusieurs pages à cause de ce verrou. Continuer quand même ?"
                        )
                        .setPositiveButton("Effacer quand même") { _, _ ->
                            // Meme raison que pour le clonage : le tag a pu quitter le champ NFC
                            // pendant l'affichage de la popup, on redemande un scan plutot que de
                            // reutiliser l'objet Tag.
                            effacementConfirmeMalgreVerrou = true
                            txtStatut.text = "Confirmé : rapproche à nouveau le tag pour l'effacer..."
                        }
                        .setNegativeButton("Annuler") { _, _ -> annulerModeAttente() }
                        .setOnCancelListener { annulerModeAttente() }
                        .show()
                }
                return
            }
        } catch (e: Exception) {
            try { nfcA.close() } catch (e2: Exception) { /* rien a faire */ }
            runOnUiThread { txtStatut.text = "Erreur de lecture avant effacement : ${e.message}" }
            return
        }

        effectuerEffacement(tag)
    }

    /** Efface puis verifie - voir effacerTagCible pour la verification preventive des verrous en amont. */
    private fun effectuerEffacement(tag: Tag) {
        val nfcA = NfcA.get(tag)
        if (nfcA == null) {
            Toast.makeText(this, "Ce tag n'est pas compatible NFC-A, essaie un autre tag.", Toast.LENGTH_SHORT).show()
            return
        }

        runOnUiThread { txtStatut.text = "Effacement en cours, ne retire pas le tag..." }

        try {
            nfcA.connect()
            var pageEnEchec: Int? = null
            for (page in ClonageElegoo.pagesAEffacer()) {
                if (!ecrirePageAvecVerif(nfcA, page, ClonageElegoo.OCTETS_PAGE_VIDE)) {
                    pageEnEchec = page
                    break
                }
            }
            // Lecture diagnostique poussee jusqu'a 0x28 (octets de verrouillage dynamique) pour
            // pouvoir expliquer un echec eventuel - jamais ecrite, voir ClonageElegoo.kt.
            val dumpRelu = lireDumpBrut(nfcA, ClonageElegoo.DERNIERE_PAGE_DIAGNOSTIC)
            nfcA.close()

            enAttenteTagEffacement = false
            val reussi = pageEnEchec == null && ClonageElegoo.zoneEffacee(dumpRelu)
            runOnUiThread {
                btnAnnulerClonage.visibility = View.GONE
                btnEffacer.visibility = View.VISIBLE
                btnClonerLot.visibility = View.VISIBLE
                if (dernierScanReussi) { btnCloner.visibility = View.VISIBLE; btnAjouterPlanche.visibility = View.VISIBLE }
                txtStatut.text = when {
                    reussi -> "Tag effacé et vérifié ✓ — prêt pour un nouveau clonage"
                    pageEnEchec != null -> "Écriture interrompue (page 0x%02X non confirmée) — repose le tag bien à plat sans le bouger et réessaie.".format(pageEnEchec)
                    else -> {
                        val pagesRestantes = ClonageElegoo.pagesNonEffacees(dumpRelu)
                        val listePages = pagesRestantes.joinToString(", ") { "0x%02X".format(it) }
                        val verrousDyn = ClonageElegoo.verrousDynamiquesHex(dumpRelu)
                        val verrousStat = ClonageElegoo.verrousStatiquesHex(dumpRelu)
                        "Écriture confirmée par le tag (ACK) mais relecture non vide : page(s) $listePages encore non nulle(s)" +
                            (verrousDyn?.let { " — verrou dynamique (0x28) : $it" } ?: "") +
                            (verrousStat?.let { " — verrou statique (0x02) : $it" } ?: "") +
                            ". Réessaie ; si ça persiste sur la même page, elle est peut-être verrouillée."
                    }
                }
                if (!reussi && GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
            }
        } catch (e: Exception) {
            try { nfcA.close() } catch (e2: Exception) { /* rien a faire */ }
            enAttenteTagEffacement = false
            runOnUiThread {
                btnAnnulerClonage.visibility = View.GONE
                btnEffacer.visibility = View.VISIBLE
                btnClonerLot.visibility = View.VISIBLE
                if (dernierScanReussi) { btnCloner.visibility = View.VISIBLE; btnAjouterPlanche.visibility = View.VISIBLE }
                txtStatut.text = "Erreur d'effacement : ${e.message} — le tag est peut-être verrouillé ou n'est pas un NTAG213/215."
                if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
            }
        }
    }

    private fun annulerModeAttente() {
        val effacementEnCours = enAttenteTagEffacement
        enAttenteTagCible = false
        enAttenteTagEffacement = false
        ecrasementConfirme = false
        effacementConfirmeMalgreVerrou = false
        btnAnnulerClonage.visibility = View.GONE
        btnEffacer.visibility = View.VISIBLE
        btnClonerLot.visibility = View.VISIBLE
        if (dernierScanReussi) { btnCloner.visibility = View.VISIBLE; btnAjouterPlanche.visibility = View.VISIBLE }
        txtStatut.text = if (effacementEnCours) {
            "Effacement annulé. Approche une bobine Elegoo du dos du téléphone..."
        } else {
            "Clonage annulé. Approche une bobine Elegoo du dos du téléphone..."
        }
    }

    private fun traiterTagCibleClonage(tag: Tag) {
        val dumpSource = dernierDumpBrut
        if (dumpSource == null || dumpSource.size < ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) {
            // Le dump source a disparu entretemps (ex. rotation d'ecran sans sauvegarde du
            // tableau d'octets) - on ne peut pas continuer en securite, on annule proprement.
            annulerModeAttente()
            Toast.makeText(this, "Le modèle source a été perdu, relance le clonage depuis une nouvelle lecture.", Toast.LENGTH_LONG).show()
            return
        }

        // Ecrasement deja confirme lors d'un scan precedent de CE MEME tag (voir plus bas) :
        // on ecrit directement, sans relire - le tag approche maintenant est celui que
        // l'utilisateur vient de rapprocher expres apres avoir confirme la popup.
        if (ecrasementConfirme) {
            ecrasementConfirme = false
            ecrireEtVerifierClone(tag, dumpSource)
            return
        }

        val nfcA = NfcA.get(tag)
        if (nfcA == null) {
            Toast.makeText(this, "Ce tag n'est pas compatible NFC-A, essaie un autre tag.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            nfcA.connect()
            // Verifie d'abord si le tag cible contient deja des donnees Elegoo valides, pour ne
            // jamais ecraser silencieusement une bobine originale par erreur de manipulation.
            val dumpExistant = lireDumpBrut(nfcA, 40)
            val infoExistante = DecodeurElegoo.decoder(dumpExistant)
            nfcA.close()

            if (infoExistante.headerValide == true) {
                runOnUiThread {
                    txtStatut.text = "Tag deja ecrit detecte - confirmation demandee..."
                    AlertDialog.Builder(this)
                        .setTitle("Ce tag contient déjà une bobine")
                        .setMessage(
                            "Ce tag semble être une bobine Elegoo existante" +
                                (infoExistante.couleurHex?.let { " (couleur #$it)" } ?: "") +
                                ", pas un tag vierge.\n\nL'écraser avec le clone effacera définitivement ses données actuelles. Continuer quand même ?"
                        )
                        .setPositiveButton("Écraser quand même") { _, _ ->
                            // On ne reutilise jamais un objet Tag apres une popup (le tag a eu
                            // le temps de quitter le champ NFC pendant la lecture de la popup) -
                            // on redemande un scan, que le flag ci-dessus laissera passer direct.
                            ecrasementConfirme = true
                            txtStatut.text = "Confirmé : rapproche à nouveau la MÊME bobine pour l'écraser..."
                        }
                        .setNegativeButton("Annuler") { _, _ -> annulerModeAttente() }
                        .setOnCancelListener { annulerModeAttente() }
                        .show()
                }
                return
            }
            ecrireEtVerifierClone(tag, dumpSource)
        } catch (e: Exception) {
            try { nfcA.close() } catch (e2: Exception) { /* rien a faire */ }
            runOnUiThread { txtStatut.text = "Erreur de lecture du tag cible : ${e.message}" }
        }
    }

    /**
     * Resultat brut d'une tentative de clonage sur un tag, sans aucun effet de bord UI - voir
     * executerClonage. reussi=false avec pageEnEchec=null et erreurMessage=null veut dire
     * "ecriture terminee mais la relecture ne correspond pas" (cas distinct d'une page qui refuse
     * carrement l'ecriture).
     */
    private data class ResultatClonage(val reussi: Boolean, val pageEnEchec: Int?, val erreurMessage: String?)

    /**
     * Coeur du clonage (ecrit les pages 0x03-0x27 depuis dumpSource sur tag, puis relit pour
     * verifier) - AUCUN effet de bord sur l'UI, pour pouvoir servir aussi bien au clonage simple
     * (ecrireEtVerifierClone) qu'au clonage par lot (traiterTagLot), qui ont des besoins d'affichage
     * differents apres coup (le lot doit enchainer sur le fichier suivant plutot que de revenir a
     * l'etat de repos). Gere elle-meme connect/close du tag.
     */
    private fun executerClonage(tag: Tag, dumpSource: ByteArray): ResultatClonage {
        val nfcA = NfcA.get(tag)
            ?: return ResultatClonage(false, null, "Ce tag n'est pas compatible NFC-A, essaie un autre tag.")
        return try {
            nfcA.connect()
            var pageEnEchec: Int? = null
            for ((page, octets) in ClonageElegoo.pagesAEcrire(dumpSource)) {
                // Commande WRITE (NFC Forum Type 2 Tag) : 0xA2, numero de page, 4 octets de donnee.
                // Verifiee via ecrirePageAvecVerif (voir plus haut) depuis le 08/10/2026.
                if (!ecrirePageAvecVerif(nfcA, page, octets)) {
                    pageEnEchec = page
                    break
                }
            }
            val dumpRelu = lireDumpBrut(nfcA, ClonageElegoo.DERNIERE_PAGE_DONNEES)
            nfcA.close()
            val reussi = pageEnEchec == null && ClonageElegoo.zoneCloneeIdentique(dumpSource, dumpRelu)
            ResultatClonage(reussi, pageEnEchec, null)
        } catch (e: Exception) {
            try { nfcA.close() } catch (e2: Exception) { /* rien a faire */ }
            ResultatClonage(false, null, e.message)
        }
    }

    /** Clonage simple (bouton "Cloner sur une bobine vierge") : ecrit puis remet l'UI au repos. */
    private fun ecrireEtVerifierClone(tag: Tag, dumpSource: ByteArray) {
        runOnUiThread { txtStatut.text = "Écriture en cours, ne retire pas le tag..." }
        val resultat = executerClonage(tag, dumpSource)
        enAttenteTagCible = false
        runOnUiThread {
            btnAnnulerClonage.visibility = View.GONE
            btnEffacer.visibility = View.VISIBLE
            btnClonerLot.visibility = View.VISIBLE
            if (dernierScanReussi) { btnCloner.visibility = View.VISIBLE; btnAjouterPlanche.visibility = View.VISIBLE }
            when {
                resultat.reussi -> {
                    txtStatut.text = "Clonage réussi et vérifié ✓"
                    if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerConfirmation()
                }
                resultat.pageEnEchec != null -> {
                    txtStatut.text = "Écriture interrompue (page 0x%02X non confirmée) — repose le tag bien à plat sans le bouger et réessaie.".format(resultat.pageEnEchec)
                    if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
                }
                resultat.erreurMessage != null -> {
                    txtStatut.text = "Erreur d'écriture : ${resultat.erreurMessage} — le tag cible est peut-être verrouillé ou n'est pas un NTAG213/215 vierge."
                    if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
                }
                else -> {
                    txtStatut.text = "Écriture terminée mais la relecture ne correspond pas - clonage probablement incomplet. Réessaie."
                    if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
                }
            }
        }
    }

    // ============================== CLONAGE PAR LOT ==============================
    // Importe plusieurs dumps d'un coup (meme logique de parsing que l'import simple, voir
    // extraireDumpDepuisImport), puis les ecrit les uns apres les autres - un tag par fichier -
    // en enchainant automatiquement sur le fichier suivant apres chaque succes.

    private fun demarrerImportLot() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, CODE_IMPORT_LOT)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Nom de fichier lisible a partir d'un Uri (sans dependre de la query du ContentResolver, pas stubee partout) - repli sur le dernier segment de chemin. */
    private fun nomDepuisUri(uri: android.net.Uri): String =
        uri.toString().substringAfterLast('/').substringBefore('?').ifBlank { "fichier" }

    /** Lance effectivement le lot une fois les fichiers valides connus (apres l'avertissement
     * en-tete eventuel, voir CODE_IMPORT_LOT ci-dessus). */
    private fun demarrerLotAvecValides(valides: List<Pair<String, ByteArray>>) {
        lotAClone = valides
        indexLotCourant = 0
        resultatsLot.clear()
        demarrerLotTagSuivant()
    }

    private fun demarrerLotTagSuivant() {
        enAttenteTagLot = true
        btnCloner.visibility = View.GONE
        btnEffacer.visibility = View.GONE
        btnAjouterPlanche.visibility = View.GONE
        btnClonerLot.visibility = View.GONE
        btnAnnulerClonage.visibility = View.VISIBLE
        btnAnnulerClonage.text = "Annuler le lot"
        layoutLignesInfo.removeAllViews()
        dernieresLignesInfo.clear()
        vuCouleur.visibility = View.GONE
        imgNfc.visibility = View.VISIBLE
        val (nom, _) = lotAClone[indexLotCourant]
        txtStatut.text = "Approche le tag vierge pour \"$nom\" (${indexLotCourant + 1}/${lotAClone.size})..."
    }

    private fun traiterTagLot(tag: Tag) {
        val (nom, dump) = lotAClone[indexLotCourant]
        runOnUiThread { txtStatut.text = "Écriture en cours (${indexLotCourant + 1}/${lotAClone.size}) : $nom..." }
        val resultat = executerClonage(tag, dump)
        runOnUiThread {
            if (resultat.reussi) {
                resultatsLot.add(ResultatLotLigne(nom, true, null))
                indexLotCourant++
                if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerConfirmation()
                avancerOuTerminerLot()
            } else {
                val raison = when {
                    resultat.pageEnEchec != null -> "page 0x%02X non confirmée".format(resultat.pageEnEchec)
                    resultat.erreurMessage != null -> resultat.erreurMessage
                    else -> "relecture non conforme"
                }
                if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerEchec()
                AlertDialog.Builder(this)
                    .setTitle("Échec sur \"$nom\"")
                    .setMessage("$raison\n\nRéessayer ce fichier (sur le même tag ou un autre), passer au suivant, ou annuler le lot ?")
                    .setPositiveButton("Réessayer") { _, _ ->
                        txtStatut.text = "Approche un tag pour réessayer \"$nom\" (${indexLotCourant + 1}/${lotAClone.size})..."
                    }
                    .setNeutralButton("Passer au suivant") { _, _ ->
                        resultatsLot.add(ResultatLotLigne(nom, false, raison))
                        indexLotCourant++
                        avancerOuTerminerLot()
                    }
                    .setNegativeButton("Annuler le lot") { _, _ -> annulerLot() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun avancerOuTerminerLot() {
        if (indexLotCourant >= lotAClone.size) {
            terminerLot()
        } else {
            val (nom, _) = lotAClone[indexLotCourant]
            txtStatut.text = "Approche le tag vierge pour \"$nom\" (${indexLotCourant + 1}/${lotAClone.size})..."
        }
    }

    private fun terminerLot() {
        val reussis = resultatsLot.count { it.reussi }
        val total = resultatsLot.size
        val echecs = resultatsLot.filter { !it.reussi }.map { it.nom }
        val detail = if (echecs.isNotEmpty()) "\n\nNon clonés : ${echecs.joinToString(", ")}" else ""
        // Snapshot avant reinitialiserEtatLot() (qui vide resultatsLot juste apres l'appel a
        // show(), lequel ne bloque pas) - sinon "Exporter" n'aurait plus rien a exporter une fois
        // tape (ajoute en v0.24 a la demande de Tomyn, pour garder une trace apres un gros lot).
        val lignesSnapshot = resultatsLot.toList()
        AlertDialog.Builder(this)
            .setTitle("Lot terminé")
            .setMessage("$reussis/$total tag(s) clonés avec succès.$detail")
            .setPositiveButton("OK", null)
            .setNeutralButton("Exporter le résultat") { _, _ -> exporterResultatLot(lignesSnapshot) }
            .show()
        reinitialiserEtatLot()
        txtStatut.text = "Lot terminé. Approche une bobine Elegoo du dos du téléphone..."
    }

    private fun exporterResultatLot(lignes: List<ResultatLotLigne>) {
        val csv = StringBuilder("Fichier;Résultat;Détail\n")
        for (l in lignes) {
            val resultatTexte = if (l.reussi) "Réussi" else "Échec"
            csv.append(listOf(l.nom, resultatTexte, l.detail ?: "").joinToString(";") { it.replace(";", ",") })
            csv.append("\n")
        }
        exporterVers("resultat_lot_elegoo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".csv", csv.toString(), "text/csv")
    }

    private fun annulerLot() {
        reinitialiserEtatLot()
        txtStatut.text = "Lot annulé. Approche une bobine Elegoo du dos du téléphone..."
    }

    private fun reinitialiserEtatLot() {
        enAttenteTagLot = false
        lotAClone = emptyList()
        resultatsLot.clear()
        indexLotCourant = 0
        btnAnnulerClonage.visibility = View.GONE
        btnAnnulerClonage.text = "Annuler le clonage"
        btnEffacer.visibility = View.VISIBLE
        btnClonerLot.visibility = View.VISIBLE
        if (dernierScanReussi) { btnCloner.visibility = View.VISIBLE; btnAjouterPlanche.visibility = View.VISIBLE }
    }

    private fun formaterDumpHex(dump: ByteArray): String {
        val sb = StringBuilder()
        for (page in dump.indices step 4) {
            val fin = minOf(page + 4, dump.size)
            val octets = dump.copyOfRange(page, fin).joinToString(" ") { "%02X".format(it) }
            sb.appendLine("Page %02X : %s".format(page / 4, octets))
        }
        return sb.toString().trim()
    }

    private fun ajouterLigneInfo(icone: Int, texte: String) {
        dernieresLignesInfo.add(icone to texte)
        val ligne = LinearLayout(this)
        ligne.orientation = LinearLayout.HORIZONTAL
        ligne.gravity = Gravity.CENTER_VERTICAL
        val paddingPx = (6 * resources.displayMetrics.density).toInt()
        ligne.setPadding(0, paddingPx, 0, paddingPx)

        val img = ImageView(this)
        img.setImageResource(icone)
        val tailleIcone = (20 * resources.displayMetrics.density).toInt()
        val paramsImg = LinearLayout.LayoutParams(tailleIcone, tailleIcone)
        paramsImg.marginEnd = (10 * resources.displayMetrics.density).toInt()
        img.layoutParams = paramsImg

        val txt = TextView(this)
        txt.text = texte
        txt.setTextColor(resources.getColor(R.color.texte_principal, theme))
        txt.textSize = 14f

        ligne.addView(img)
        ligne.addView(txt)
        layoutLignesInfo.addView(ligne)

        val animation = AnimationUtils.loadAnimation(this, R.anim.apparition_ligne)
        animation.startOffset = (layoutLignesInfo.childCount - 1) * 90L
        ligne.startAnimation(animation)
    }

    /**
     * @param statutTexte permet de distinguer une vraie lecture ("Bobine identifiée") d'un tag
     *   cree a la main (CreationTagActivity, voir onActivityResult/CODE_CREATION) - le reste de
     *   l'affichage (champs, boutons Cloner/Exporter/Planche) est identique dans les deux cas.
     * @param enregistrerHistorique false pour un tag cree a la main : l'historique ne doit
     *   refleter que des bobines physiquement scannees, pas des creations.
     */
    private fun afficherResultats(
        info: DecodeurElegoo.InfoBobine,
        dump: ByteArray,
        statutTexte: String = "Bobine identifiée",
        enregistrerHistorique: Boolean = true,
        origine: String = PlancheEtiquettes.ORIGINE_SCAN_NFC
    ) {
        dernierScanReussi = true
        dernierDumpBrut = dump
        dernierInfoBobine = info
        derniereOrigineBobine = origine
        btnCloner.visibility = if (dump.size >= ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) View.VISIBLE else View.GONE
        btnAjouterPlanche.visibility = View.VISIBLE
        txtStatut.text = statutTexte

        if (info.couleurHex != null) {
            try {
                val argb = Color.parseColor("#${info.couleurHex}")
                vuCouleur.backgroundTintList = ColorStateList.valueOf(argb)
                vuCouleur.visibility = View.VISIBLE
                imgNfc.visibility = View.GONE
            } catch (e: Exception) { /* hex invalide : on garde l'icone NFC */ }
        }

        info.matiereTexte?.let { ajouterLigneInfo(R.drawable.ic_bobine, "Matière : $it") }
        info.sousTypeTexte?.let { ajouterLigneInfo(R.drawable.ic_bobine, "Sous-type : $it") }
        info.couleurHex?.let { ajouterLigneInfo(R.drawable.ic_couleur, "Couleur : #$it") }
        info.poidsGrammes?.let { ajouterLigneInfo(R.drawable.ic_materiau, "Poids bobine : ${it}g") }
        info.diametreMm?.let { ajouterLigneInfo(R.drawable.ic_temperature, "Diamètre : ${it}mm") }
        if (info.tempMinC != null && info.tempMaxC != null) {
            ajouterLigneInfo(R.drawable.ic_temperature, "Température buse : ${info.tempMinC}-${info.tempMaxC}°C")
        }
        info.dateFabricationTexte?.let { ajouterLigneInfo(R.drawable.ic_bobine, "Date de fabrication : $it") }

        val resume = StringBuilder()
        info.matiereTexte?.let { resume.appendLine("Matière : $it") }
        info.sousTypeTexte?.let { resume.appendLine("Sous-type : $it") }
        info.couleurHex?.let { resume.appendLine("Couleur : #$it") }
        info.poidsGrammes?.let { resume.appendLine("Poids : ${it}g") }
        if (info.tempMinC != null && info.tempMaxC != null) {
            resume.appendLine("Température buse : ${info.tempMinC}-${info.tempMaxC}°C")
        }
        info.diametreMm?.let { resume.appendLine("Diamètre : ${it}mm") }
        info.dateFabricationTexte?.let { resume.appendLine("Date de fabrication : $it") }
        info.codeFabricant?.let { resume.appendLine("Code fabricant : $it") }
        dernierResume = resume.toString().trim()
        dernierDumpTexte = dernierResume + "\n\n--- DUMP BRUT (pour analyse) ---\n" + formaterDumpHex(dump)

        if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerConfirmation()
        jouerSonResultat(true)
        if (enregistrerHistorique) {
            enregistrerDansHistorique(
                info.codeFabricant ?: "?",
                info.couleurHex ?: "",
                info.matiereTexte,
                info.sousTypeTexte,
                info.poidsGrammes
            )
            sauvegarderDumpAuto(dernierDumpTexte)
        }
    }

    /**
     * Sauvegarde automatique du dump complet de CHAQUE lecture reussie (ajoute le 09/10/2026 a la
     * demande de Tomyn), dans un sous-dossier horodate - independant du bouton "Exporter le
     * dernier dump" (qui ecrase a chaque fois et exige un clic). But : ne plus perdre un dump
     * faute d'avoir pense a l'exporter avant de scanner autre chose (deja arrive avec pascal_lb,
     * ou on a du lui redemander un export apres coup). Silencieux : aucune erreur affichee si
     * l'ecriture echoue, exactement comme enregistrerDansHistorique juste au-dessus.
     */
    private fun sauvegarderDumpAuto(dumpTexte: String) {
        try {
            val dossier = File(getExternalFilesDir(null), "dumps_auto")
            if (!dossier.exists()) dossier.mkdirs()
            val nomFichier = "dump_elegoo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".txt"
            File(dossier, nomFichier).writeText(dumpTexte)
        } catch (e: Exception) { /* pas grave si la sauvegarde automatique echoue */ }
    }

    private fun vibrerConfirmation() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) { /* pas grave si la vibration echoue */ }
    }

    /**
     * Vibration d'echec (ajoutee en v0.26 a la demande de Tomyn, en miroir du son different
     * succes/echec deja present depuis la v0.21) : jusqu'ici la vibration etait identique quel
     * que soit le resultat (voire absente en cas d'echec), seul le son changeait. Un motif en
     * deux pulsations courtes, nettement different du simple "bip" de vibrerConfirmation(), pour
     * distinguer les deux sans avoir a regarder l'ecran. Meme reglage que vibrerConfirmation()
     * (GestionnaireParametres.lireVibrationFinLecture) - pas de nouveau parametre separe.
     */
    private fun vibrerEchec() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 90, 70), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 70, 90, 70), -1)
            }
        } catch (e: Exception) { /* pas grave si la vibration echoue */ }
    }

    /**
     * Bip de confirmation en fin de lecture NFC (ajoute en v0.21 a la demande de Tomyn, en plus
     * de la vibration deja reglable) : un son different succes/erreur, utile pour scanner vite
     * sans regarder l'ecran a chaque bobine. ToneGenerator plutot qu'un fichier audio : aucune
     * ressource a embarquer, deux tonalites standard suffisent (ACK = double bip aigu "ok", NACK
     * = bip grave "erreur").
     */
    private fun jouerSonResultat(succes: Boolean) {
        if (!GestionnaireParametres.lireSonFinLecture(this)) return
        try {
            val tonalite = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
            val type = if (succes) android.media.ToneGenerator.TONE_PROP_ACK else android.media.ToneGenerator.TONE_PROP_NACK
            tonalite.startTone(type, 150)
            // Le son joue de facon asynchrone cote systeme ; on relache apres un court delai
            // plutot qu'immediatement, sinon le ToneGenerator peut couper le bip avant qu'il ait
            // eu le temps de sortir.
            Thread {
                try { Thread.sleep(200) } catch (e: InterruptedException) { /* rien a faire */ }
                tonalite.release()
            }.start()
        } catch (e: Exception) { /* pas grave si le son echoue (pas de haut-parleur, etc.) */ }
    }

    private fun copierResume() {
        if (dernierResume.isEmpty()) {
            Toast.makeText(this, "Rien à copier pour l'instant, scanne d'abord un tag.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Résultat Elegoo RFID", dernierResume))
        Toast.makeText(this, "Copié dans le presse-papier.", Toast.LENGTH_SHORT).show()
    }

    /**
     * Copie uniquement le dump brut en hexadecimal (ajoute en v0.26 a la demande de Tomyn) : utile
     * pour coller directement un dump sur le forum lesimprimantes3d.fr en cas de tag mal reconnu,
     * sans passer par "Exporter" + rouvrir le fichier pour en recuperer le contenu. Different de
     * copierResume() qui copie le resume lisible (matiere/couleur/poids), pas le dump - accessible
     * par un appui long sur "Copier" pour ne pas ajouter de bouton supplementaire a l'ecran.
     */
    private fun copierDump() {
        if (dernierDumpTexte.isEmpty()) {
            Toast.makeText(this, "Rien à copier pour l'instant, scanne d'abord un tag.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Dump Elegoo RFID", dernierDumpTexte))
        Toast.makeText(this, "Dump brut copié dans le presse-papier.", Toast.LENGTH_SHORT).show()
    }

    private fun partagerResume() {
        if (dernierResume.isEmpty()) {
            Toast.makeText(this, "Rien à partager pour l'instant, scanne d'abord un tag.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, dernierResume)
        startActivity(Intent.createChooser(intent, "Partager le résultat"))
    }

    private fun exporterDump() {
        if (dernierDumpTexte.isEmpty()) {
            Toast.makeText(this, "Aucun dump à exporter pour l'instant, scanne d'abord un tag.", Toast.LENGTH_SHORT).show()
            return
        }
        val nomFichier = "dump_elegoo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".txt"
        exporterVers(nomFichier, dernierDumpTexte, "text/plain")
    }

    /**
     * Importe un dump exporte precedemment pour pouvoir cloner sans avoir la bobine source
     * physique sous la main au moment du clonage. Ajoute le 08/10/2026 a la demande de Tomyn.
     *
     * Accepte trois formats (voir extraireDumpDepuisImport) : le .txt que exporterDump() produit,
     * un .bin brut ou un .hex (chaine hexadecimale brute) venant d'un editeur externe comme
     * elegoo-rfid-editor - utile par exemple pour le filament recycle au Lyman, ou le fournisseur
     * d'origine n'a jamais pose de tag RFID.
     */
    private fun importerDump() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, CODE_IMPORT)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Accepte definitivement un dump importe (apres validation ou confirmation malgre l'avertissement) comme source de clonage. */
    private fun accepterDumpImporte(dump: ByteArray) {
        dernierDumpBrut = dump
        dernierScanReussi = true
        btnCloner.visibility = View.VISIBLE
        txtStatut.text = "Dump importé (${dump.size} octets) - prêt à cloner sur un tag vierge."
        Toast.makeText(this, "Dump importé, appuie sur \"Cloner sur une bobine vierge\".", Toast.LENGTH_LONG).show()
    }

    /**
     * Extrait et valide un dump importe (meme logique pour un fichier ou un texte colle depuis le
     * presse-papier, voir CODE_IMPORT et collerDumpDepuisPressePapier) - factorise en v0.26 pour
     * ne pas dupliquer la verification d'en-tete (avertissement ajoute en v0.23).
     */
    private fun traiterDumpImporte(octetsBruts: ByteArray, messageEchecFormat: String) {
        val dump = extraireDumpDepuisImport(octetsBruts)
        if (dump == null || dump.size < ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) {
            Toast.makeText(this, messageEchecFormat, Toast.LENGTH_LONG).show()
            return
        }
        if (DecodeurElegoo.decoder(dump).headerValide != true) {
            AlertDialog.Builder(this)
                .setTitle("Dump suspect")
                .setMessage("La taille est correcte, mais l'en-tête attendu (0x36) pour un dump Elegoo est absent. Le dump est peut-être corrompu ou dans un autre format.\n\nContinuer quand même avant de cloner ?")
                .setPositiveButton("Continuer quand même") { _, _ -> accepterDumpImporte(dump) }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }
        accepterDumpImporte(dump)
    }

    /**
     * Colle un dump depuis le presse-papier (ajoute en v0.27 a la demande de Tomyn, en miroir du
     * "copier le dump" de la v0.26) : pratique si quelqu'un partage un dump en texte (collé dans
     * un message du forum lesimprimantes3d.fr, par exemple) plutot qu'en fichier - evite de devoir
     * d'abord l'enregistrer dans un .txt pour pouvoir l'importer. Accepte les memes formats que
     * l'import fichier (voir extraireDumpDepuisImport) puisque le texte colle est traite exactement
     * comme le contenu d'un fichier texte.
     */
    private fun collerDumpDepuisPressePapier() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val texte = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (texte.isNullOrBlank()) {
            Toast.makeText(this, "Le presse-papier est vide.", Toast.LENGTH_SHORT).show()
            return
        }
        traiterDumpImporte(texte.toByteArray(Charsets.UTF_8), "Presse-papier non reconnu : ni un dump exporté par cette appli, ni un .hex valide.")
    }

    /**
     * Relit le texte d'un fichier importe et en extrait le dump brut, au format produit par
     * formaterDumpHex() (lignes "Page XX : AA BB CC DD", a partir de la page 0). Les eventuelles
     * lignes de resume avant/apres (couleur, poids...) sont ignorees - seules les lignes "Page "
     * comptent. Retourne null si le fichier ne contient aucune ligne de dump reconnaissable.
     */
    private fun parserDumpHex(texte: String): ByteArray? {
        val pages = mutableListOf<ByteArray>()
        for (ligneBrute in texte.lines()) {
            val ligne = ligneBrute.trim()
            if (!ligne.startsWith("Page ")) continue
            val parties = ligne.split(":")
            if (parties.size != 2) return null
            val octetsHex = parties[1].trim().split(" ").filter { it.isNotBlank() }
            if (octetsHex.size != 4) return null
            try {
                pages.add(ByteArray(4) { i -> octetsHex[i].toInt(16).toByte() })
            } catch (e: NumberFormatException) {
                return null
            }
        }
        if (pages.isEmpty()) return null
        val resultat = ByteArray(pages.size * 4)
        pages.forEachIndexed { index, octets -> octets.copyInto(resultat, index * 4) }
        return resultat
    }

    /**
     * Extrait un dump exploitable depuis les octets bruts d'un fichier importe, en essayant
     * plusieurs formats dans l'ordre (le premier qui correspond gagne) :
     *   1. Notre propre export .txt (lignes "Page XX : AA BB CC DD") - voir parserDumpHex.
     *   2. Un .hex brut : une seule chaine de caracteres hexadecimaux (espaces/retours a la ligne
     *      ignores), format "Hex" de l'editeur elegoo-rfid-editor.
     *   3. Un .bin brut : les octets du fichier SONT directement le dump (format "Binary" du
     *      meme editeur), aucun parsing necessaire.
     * Retourne null si aucun des trois ne donne un dump assez long pour cloner.
     */
    private fun extraireDumpDepuisImport(octetsBruts: ByteArray): ByteArray? {
        val texte = octetsBruts.toString(Charsets.UTF_8)

        parserDumpHex(texte)?.let { return it }

        val hexNettoye = texte.filter { !it.isWhitespace() }
        if (hexNettoye.isNotEmpty() && hexNettoye.length % 2 == 0 &&
            hexNettoye.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        ) {
            try {
                return ByteArray(hexNettoye.length / 2) { i ->
                    hexNettoye.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (e: NumberFormatException) {
                // Ressemblait a de l'hexa mais ne s'est pas decode proprement : on continue avec
                // le fichier binaire brut ci-dessous plutot que d'abandonner tout de suite.
            }
        }

        if (octetsBruts.size >= ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) return octetsBruts

        return null
    }

    private fun exporterVers(nomSuggere: String, contenu: String, typeMime: String) {
        contenuAExporter = contenu
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = typeMime
            putExtra(Intent.EXTRA_TITLE, nomSuggere)
        }
        try {
            startActivityForResult(intent, CODE_EXPORT)
        } catch (e: Exception) {
            contenuAExporter = null
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CODE_EXPORT -> {
                val contenu = contenuAExporter
                contenuAExporter = null
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                if (contenu == null) {
                    Toast.makeText(this, "Export interrompu (l'appli a été relancée), recommence.", Toast.LENGTH_LONG).show()
                    return
                }
                try {
                    val flux = contentResolver.openOutputStream(uri) ?: throw IOException("fichier inaccessible")
                    flux.use { it.write(contenu.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this, "Fichier enregistré.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erreur export : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            CODE_IMPORT -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                try {
                    val octetsBruts = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("fichier inaccessible")
                    traiterDumpImporte(octetsBruts, "Fichier non reconnu : ni un dump exporté par cette appli, ni un .bin/.hex valide.")
                } catch (e: Exception) {
                    Toast.makeText(this, "Erreur d'import : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            CODE_CREATION -> {
                if (resultCode != RESULT_OK) return
                @Suppress("DEPRECATION")
                val dump = data?.getByteArrayExtra("dump")
                if (dump == null) {
                    Toast.makeText(this, "Erreur : le tag créé n'a pas pu être récupéré.", Toast.LENGTH_LONG).show()
                    return
                }
                val info = DecodeurElegoo.decoder(dump)
                layoutLignesInfo.removeAllViews()
                dernieresLignesInfo.clear()
                vuCouleur.visibility = View.GONE
                imgNfc.visibility = View.GONE
                afficherResultats(info, dump, statutTexte = "Tag personnalisé créé — prêt à cloner sur une bobine vierge", enregistrerHistorique = false, origine = PlancheEtiquettes.ORIGINE_CREATION)
                Toast.makeText(this, "Tag créé. Appuie sur \"Cloner sur une bobine vierge\".", Toast.LENGTH_LONG).show()
            }
            CODE_IMPORT_BASE -> {
                if (resultCode != RESULT_OK) return
                // Voir ImportBaseDonneesActivity.dumpsGeneres : passe par un champ statique
                // plutot que par les extras de l'Intent (simple liste de (nom, ByteArray), pas
                // besoin de Parcelable). Reutilise tel quel le flux de clonage par lot
                // (demarrerLotAvecValides) - fonctionne aussi bien pour un seul tag que pour
                // plusieurs couleurs generees d'un coup.
                val valides = ImportBaseDonneesActivity.dumpsGeneres
                ImportBaseDonneesActivity.dumpsGeneres = emptyList()
                if (valides.isEmpty()) {
                    Toast.makeText(this, "Import interrompu (l'appli a été relancée), recommence.", Toast.LENGTH_LONG).show()
                    return
                }
                demarrerLotAvecValides(valides)
            }
            CODE_IMPORT_LOT -> {
                if (resultCode != RESULT_OK) return
                val uris = mutableListOf<android.net.Uri>()
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
                val valides = mutableListOf<Pair<String, ByteArray>>()
                val ignores = mutableListOf<String>()
                // Ajoute en v0.23 a la demande de Tomyn : un fichier qui a la bonne taille mais
                // pas l'en-tete Elegoo (0x36) est garde dans "valides" (meme comportement
                // qu'avant) mais signale a part - on previent avant de lancer le lot plutot que
                // de cloner silencieusement un fichier peut-etre corrompu sur un tag vierge.
                val sansEnteteValide = mutableListOf<String>()
                for (uri in uris) {
                    val nom = nomDepuisUri(uri)
                    try {
                        val octetsBruts = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val dump = octetsBruts?.let { extraireDumpDepuisImport(it) }
                        if (dump != null && dump.size >= ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) {
                            valides.add(nom to dump)
                            if (DecodeurElegoo.decoder(dump).headerValide != true) sansEnteteValide.add(nom)
                        } else {
                            ignores.add(nom)
                        }
                    } catch (e: Exception) {
                        ignores.add(nom)
                    }
                }
                if (valides.isEmpty()) {
                    Toast.makeText(this, "Aucun fichier reconnu parmi la sélection (ni dump exporté par cette appli, ni .bin/.hex valide).", Toast.LENGTH_LONG).show()
                    return
                }
                if (ignores.isNotEmpty()) {
                    Toast.makeText(this, "${ignores.size} fichier(s) ignoré(s) (non reconnus) : ${ignores.joinToString(", ")}", Toast.LENGTH_LONG).show()
                }
                if (sansEnteteValide.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Fichiers suspects dans le lot")
                        .setMessage(
                            "${sansEnteteValide.size} fichier(s) sur ${valides.size} n'ont pas l'en-tête Elegoo attendu (0x36) - peut-être corrompus ou dans un autre format : ${sansEnteteValide.joinToString(", ")}.\n\n" +
                                "Continuer le clonage du lot entier malgré tout ?"
                        )
                        .setPositiveButton("Continuer le lot") { _, _ -> demarrerLotAvecValides(valides) }
                        .setNegativeButton("Annuler", null)
                        .show()
                } else {
                    demarrerLotAvecValides(valides)
                }
            }
            CODE_SCAN_QR -> {
                if (resultCode != RESULT_OK) return
                val lien = data?.getStringExtra(ScanQrActivity.EXTRA_LIEN)
                traiterLienEtiquette(lien?.let { android.net.Uri.parse(it) })
            }
            CODE_EXPORT_PLANCHE -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                // Regenere le PDF a cet instant plutot que de garder un document deja construit en
                // memoire entre le clic et le retour du selecteur de fichiers (meme logique que
                // contenuAExporter, mais le contenu est un PdfDocument, pas un texte - pas besoin
                // de le faire survivre a une rotation d'ecran, le pire qui arrive est de recliquer).
                val document = PlancheEtiquettes.genererPdf(PlancheEtiquettes.lister(this), this)
                try {
                    val flux = contentResolver.openOutputStream(uri) ?: throw IOException("fichier inaccessible")
                    flux.use { document.writeTo(it) }
                    Toast.makeText(this, "PDF enregistré.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erreur export PDF : ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    document.close()
                }
            }
        }
    }

    /**
     * @param matiereTexte @param sousTypeTexte @param poidsGrammes ajoutes en v0.21 (en plus de
     * codeFabricant/couleurHex deja presents) pour permettre un filtre utile dans l'historique
     * (voir afficherHistorique) - avant ca, impossible de retrouver "les bobines PETG" ou "les
     * bobines de 1kg" sans rouvrir chaque ligne a l'oeil.
     */
    private fun enregistrerDansHistorique(
        codeFabricant: String,
        couleurHex: String,
        matiereTexte: String?,
        sousTypeTexte: String?,
        poidsGrammes: Int?
    ) {
        try {
            val fichier = File(getExternalFilesDir(null), "historique_scans.csv")
            val ligne = listOf(
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date()),
                codeFabricant,
                couleurHex,
                matiereTexte ?: "",
                sousTypeTexte ?: "",
                poidsGrammes?.toString() ?: ""
            ).joinToString(";") + "\n"
            fichier.appendText(ligne)
        } catch (e: Exception) { /* pas grave si l'ecriture de l'historique echoue */ }
    }

    /** Une ligne de l'historique, parsee et prete a afficher ou a filtrer. Les lignes ecrites par
     * une version anterieure a l'ajout du filtre (v0.21 et avant) n'ont que 3 champs - matiere,
     * sousType et poids restent alors a null, pas de plantage (voir getOrNull ci-dessous). */
    private data class LigneHistorique(
        val date: String,
        val codeFabricant: String,
        val couleurHex: String,
        val matiereTexte: String?,
        val sousTypeTexte: String?,
        val poidsGrammes: String?,
        val texteBrut: String
    )

    private fun parserLigneHistorique(ligne: String): LigneHistorique? {
        val parts = ligne.split(";")
        if (parts.size < 3) return null
        return LigneHistorique(
            date = parts[0],
            codeFabricant = parts[1],
            couleurHex = parts[2],
            matiereTexte = parts.getOrNull(3)?.ifBlank { null },
            sousTypeTexte = parts.getOrNull(4)?.ifBlank { null },
            poidsGrammes = parts.getOrNull(5)?.ifBlank { null },
            texteBrut = ligne
        )
    }

    private fun texteAfficheLigne(l: LigneHistorique): String {
        val titre = listOfNotNull(l.matiereTexte, l.sousTypeTexte).joinToString(" ").ifBlank { "Matière inconnue" }
        val details = listOfNotNull(
            "#${l.couleurHex}".takeIf { l.couleurHex.isNotBlank() },
            l.poidsGrammes?.let { "${it}g" }
        ).joinToString(" · ")
        return "${l.date}\n$titre" + (if (details.isNotBlank()) " ($details)" else "")
    }

    /**
     * @param filtre si non vide, ne garde que les lignes dont un champ (matiere, sous-type,
     * couleur, code fabricant, date) contient ce texte (insensible a la casse/accents simples) -
     * ajoute en v0.21 a la demande de Tomyn, l'historique pouvant grossir avec le temps.
     * @param dateDebut/dateFin si non nuls, ne garde que les lignes dont la date (JJ/MM/AAAA, voir
     * enregistrerDansHistorique) tombe dans cette plage, bornes incluses - ajoute en v0.27, en
     * complement du filtre texte (utile pour retrouver les scans d'une session de tri precise
     * plutot qu'une matiere/couleur). Les deux filtres ne se combinent pas (voir
     * demanderFiltreDateHistorique) - rester simple plutot que de gerer toutes les combinaisons.
     */
    private fun afficherHistorique(filtre: String? = null, dateDebut: Date? = null, dateFin: Date? = null) {
        try {
            val fichier = File(getExternalFilesDir(null), "historique_scans.csv")
            if (!fichier.exists() || fichier.readText().isBlank()) {
                AlertDialog.Builder(this)
                    .setTitle("Historique des scans")
                    .setMessage("Aucun scan enregistré pour l'instant.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            val toutesLesLignes = fichier.readLines().reversed().mapNotNull { parserLigneHistorique(it) }
            val filtreNettoye = filtre?.trim()?.lowercase(Locale.FRANCE)
            val formatDateHeure = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
            val lignes = when {
                dateDebut != null || dateFin != null -> toutesLesLignes.filter { ligne ->
                    val dateLigne = try { formatDateHeure.parse(ligne.date) } catch (e: Exception) { null } ?: return@filter false
                    (dateDebut == null || !dateLigne.before(dateDebut)) && (dateFin == null || !dateLigne.after(dateFin))
                }
                filtreNettoye.isNullOrBlank() -> toutesLesLignes
                else -> toutesLesLignes.filter { it.texteBrut.lowercase(Locale.FRANCE).contains(filtreNettoye) }
            }
            val descriptionFiltre = when {
                dateDebut != null || dateFin != null -> {
                    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                    "du ${dateDebut?.let { fmt.format(it) } ?: "début"} au ${dateFin?.let { fmt.format(it) } ?: "aujourd'hui"}"
                }
                !filtreNettoye.isNullOrBlank() -> "« $filtre »"
                else -> null
            }

            if (lignes.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Historique des scans")
                    .setMessage("Aucun résultat pour $descriptionFiltre sur ${toutesLesLignes.size} scan(s) enregistré(s).")
                    .setPositiveButton("Nouveau filtre") { _, _ -> demanderFiltreHistorique() }
                    .setNegativeButton("Fermer", null)
                    .show()
                return
            }

            // Affichage limite aux scans les plus recents (ajoute en v0.28 - sans ca, un
            // historique qui grossit avec le temps finit dans un seul setMessage() gigantesque,
            // illisible et potentiellement lourd a afficher). "lignes" est deja du plus recent au
            // plus ancien (voir "reversed()" plus haut), donc take() garde bien les plus recents.
            val lignesAffichees = lignes.take(LIMITE_AFFICHAGE_HISTORIQUE)
            val texteAffiche = lignesAffichees.joinToString("\n\n") { texteAfficheLigne(it) } +
                if (lignes.size > LIMITE_AFFICHAGE_HISTORIQUE) {
                    "\n\n— ${lignes.size - LIMITE_AFFICHAGE_HISTORIQUE} scan(s) plus ancien(s) non affiché(s) : affine ton filtre pour les retrouver, ou utilise \"Exporter/Partager\" pour tout récupérer. —"
                } else ""
            val compteurAffiche = if (lignesAffichees.size < lignes.size) "${lignesAffichees.size}/${lignes.size}" else "${lignes.size}"
            val titre = if (descriptionFiltre == null) {
                "Historique des scans ($compteurAffiche)"
            } else {
                "Historique des scans ($compteurAffiche, filtré sur ${toutesLesLignes.size})"
            }
            // setItems aurait remplace le message (voir imprimerPlanche/genererPdfPlanche pour la
            // meme limite d'AlertDialog) : ici on garde setMessage pour le texte des scans et on
            // se limite donc aux 3 emplacements de boutons existants - "Filtrer" remplace "Fermer"
            // (fermer la boite se fait en tapant en dehors, comportement standard du dialogue).
            AlertDialog.Builder(this)
                .setTitle(titre)
                .setMessage(texteAffiche)
                .setPositiveButton("Filtrer") { _, _ -> demanderFiltreHistorique() }
                .setNeutralButton("Exporter/Partager") { _, _ -> demanderExportOuPartageHistorique(fichier.readText()) }
                .setNegativeButton("Vider l'historique") { _, _ ->
                    fichier.delete()
                    Toast.makeText(this, "Historique effacé.", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lecture historique : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun demanderFiltreHistorique() {
        val champ = EditText(this).apply {
            hint = "matière, couleur, code fabricant..."
        }
        AlertDialog.Builder(this)
            .setTitle("Filtrer l'historique")
            .setView(champ)
            .setPositiveButton("Filtrer") { _, _ -> afficherHistorique(champ.text.toString()) }
            .setNegativeButton("Tout afficher") { _, _ -> afficherHistorique() }
            .setNeutralButton("Par date") { _, _ -> demanderFiltreDateHistorique() }
            .show()
    }

    /**
     * Filtre par plage de dates (ajoute en v0.27 a la demande de Tomyn, en complement du filtre
     * texte de la v0.21) : deux champs JJ/MM/AAAA (l'un ou l'autre peut rester vide pour ne pas
     * borner ce cote-la) plutot qu'un vrai selecteur de date Android (DatePickerDialog) - pas de
     * stub existant pour ca, et une saisie texte directe reste cohérente avec le reste de
     * l'appli (champs de la grille d'etiquettes, filtre texte...).
     */
    private fun demanderFiltreDateHistorique() {
        val formatDate = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val champDebut = EditText(this).apply { hint = "Du : JJ/MM/AAAA (optionnel)" }
        val champFin = EditText(this).apply { hint = "Au : JJ/MM/AAAA (optionnel)" }
        val conteneur = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(champDebut)
            addView(champFin)
        }
        AlertDialog.Builder(this)
            .setTitle("Filtrer par date")
            .setView(conteneur)
            .setPositiveButton("Filtrer") { _, _ ->
                val texteDebut = champDebut.text.toString().trim()
                val texteFin = champFin.text.toString().trim()
                try {
                    val dateDebut = texteDebut.takeIf { it.isNotBlank() }?.let { formatDate.parse(it) }
                    val dateFin = texteFin.takeIf { it.isNotBlank() }?.let { formatDate.parse(it) }
                    if (dateDebut == null && dateFin == null) {
                        Toast.makeText(this, "Indique au moins une date.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    // Borne de fin incluse jusqu'a la fin de la journee (23:59), sinon une date de
                    // fin exclurait les scans faits ce jour-la (ils ont une heure, pas seulement
                    // une date - voir enregistrerDansHistorique).
                    val dateFinIncluse = dateFin?.let { Date(it.time + 24 * 60 * 60 * 1000 - 1) }
                    afficherHistorique(dateDebut = dateDebut, dateFin = dateFinIncluse)
                } catch (e: Exception) {
                    Toast.makeText(this, "Date invalide, utilise le format JJ/MM/AAAA.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * Choix entre enregistrer l'historique dans un fichier (comportement d'origine, via le
     * selecteur de fichiers Android) et le partager directement (mail, Drive... - ajoute en v0.26
     * a la demande de Tomyn, en reprenant le mecanisme FileProvider deja utilise pour le partage
     * du PDF des etiquettes en v0.24). setMessage()+setItems() etant incompatibles sur un vrai
     * AlertDialog (voir le bug de la planche d'etiquettes corrige en v0.23), ce choix passe par
     * une boite dediee, separee de celle qui affiche l'historique.
     */
    private fun demanderExportOuPartageHistorique(contenuCsv: String) {
        AlertDialog.Builder(this)
            .setTitle("Historique : exporter ou partager")
            .setItems(arrayOf("Enregistrer dans un fichier", "Partager (mail, Drive...)")) { _, index ->
                when (index) {
                    0 -> exporterVers("historique_scans_elegoo.csv", contenuCsv, "text/csv")
                    1 -> partagerHistoriqueCsv(contenuCsv)
                }
            }
            .show()
    }

    /**
     * Partage direct du CSV de l'historique (v0.26), meme principe que partagerPlanchePdf() pour
     * le PDF des etiquettes : ecrit dans un sous-dossier dedie du cache, expose via FileProvider
     * (un Uri file:// direct est refuse par Android 7+), puis ouvre le selecteur de partage
     * standard.
     */
    private fun partagerHistoriqueCsv(contenuCsv: String) {
        try {
            val dossier = File(cacheDir, "csv_partages")
            if (!dossier.exists()) dossier.mkdirs()
            val fichier = File(dossier, "historique_scans_elegoo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".csv")
            fichier.writeText(contenuCsv)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", fichier)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Partager l'historique des scans"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur de partage : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ============================== PLANCHE D'ETIQUETTES ==============================
    // Voir PlancheEtiquettes.kt pour le detail (grille generique 3x8 = 24/page A4, stockage,
    // generation du PDF). Meme principe que celui deja en place sur l'autre appli de lecture RFID
    // de Tomyn (Bambu) : ajoute le 09/10/2026 a sa demande.

    private fun actualiserBoutonPlanche() {
        val n = PlancheEtiquettes.nombre(this)
        btnGenererPdfPlanche.text = if (n == 0) "Planche d'étiquettes (vide)" else "Planche d'étiquettes ($n)"
    }

    private fun ajouterEtiquetteAPlanche() {
        val info = dernierInfoBobine
        val dump = dernierDumpBrut
        if (info == null || dump == null) {
            Toast.makeText(this, "Scanne d'abord une bobine Elegoo avant d'ajouter une étiquette.", Toast.LENGTH_SHORT).show()
            return
        }
        PlancheEtiquettes.ajouter(this, info, dump, derniereOrigineBobine)
        actualiserBoutonPlanche()
        Toast.makeText(this, "Étiquette ajoutée à la planche.", Toast.LENGTH_SHORT).show()
    }

    private fun genererPdfPlanche() {
        val etiquettes = PlancheEtiquettes.lister(this)
        if (etiquettes.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Planche d'étiquettes")
                .setMessage("Aucune étiquette ajoutée pour l'instant. Scanne une bobine puis appuie sur \"Ajouter à la planche d'étiquettes\".")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val nbPages = PlancheEtiquettes.nombrePages(etiquettes, this)
        // setItems plutot que les 3 emplacements de boutons habituels (positif/neutre/negatif) :
        // ca permet d'ajouter "Imprimer" (v0.20) sans retirer "Générer le PDF" ni "Vider la
        // planche" - AlertDialog ne propose que 3 boutons fixes, setItems n'a pas cette limite.
        //
        // CORRIGE v0.23 : setMessage() et setItems() ne peuvent PAS cohabiter sur un vrai
        // AlertDialog - les deux se disputent la meme zone de contenu, et le message gagnait
        // silencieusement, faisant disparaitre toute la liste d'actions (bug remonte par Tomyn :
        // plus que le titre, le message et "FERMER", aucune des 3 actions). L'info de pagination
        // passe donc dans le TITRE, qui lui cohabite sans probleme avec setItems.
        val options = arrayOf("Imprimer", "Partager le PDF", "Générer le PDF", "Vider la planche")
        AlertDialog.Builder(this)
            .setTitle("Planche (${etiquettes.size} étiquettes, $nbPages page(s) A4 de ${PlancheEtiquettes.etiquettesParPage(this)})")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> imprimerPlanche(etiquettes)
                    1 -> partagerPlanchePdf(etiquettes)
                    2 -> exporterPlanchePdf()
                    3 -> {
                        PlancheEtiquettes.vider(this)
                        actualiserBoutonPlanche()
                        Toast.makeText(this, "Planche vidée.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    /**
     * Partage direct du PDF de la planche (v0.24, demande de Tomyn : "générer le PDF" + aller le
     * rechercher dans le gestionnaire de fichiers pour l'envoyer etait juge fastidieux). Ecrit le
     * PDF dans un sous-dossier dedie du cache, expose via FileProvider (un Uri file:// direct est
     * refuse par Android 7+, voir AndroidManifest.xml et res/xml/file_paths.xml), puis ouvre le
     * selecteur de partage standard (mail, Drive, service d'impression en ligne...) - meme
     * principe que partagerResume() pour le texte.
     */
    private fun partagerPlanchePdf(etiquettes: List<PlancheEtiquettes.Etiquette>) {
        try {
            val dossier = File(cacheDir, "pdfs_partages")
            if (!dossier.exists()) dossier.mkdirs()
            val fichier = File(dossier, "etiquettes_elegoo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".pdf")
            val document = PlancheEtiquettes.genererPdf(etiquettes, this)
            try {
                FileOutputStream(fichier).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", fichier)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Partager la planche d'étiquettes"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur de partage : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exporterPlanchePdf() {
        val nomFichier = "etiquettes_elegoo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, nomFichier)
        }
        try {
            startActivityForResult(intent, CODE_EXPORT_PLANCHE)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir le sélecteur de fichiers : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Impression directe via la fenetre d'impression Android (ajoute en v0.20 a la demande de
     * Tomyn, en plus du PDF : "ouvre la fenêtre d'impression Android pour imprimer sur une
     * imprimante Wi-Fi directement"). PrintManager.print() ouvre la fenetre systeme standard, qui
     * liste elle-meme les imprimantes Wi-Fi/reseau deja configurees (plugin du fabricant, ou
     * Mopria/service d'impression par defaut) - voir ImpressionPlanche.kt pour le detail de
     * l'adaptateur qui fournit le PDF a cette fenetre.
     */
    private fun imprimerPlanche(etiquettes: List<PlancheEtiquettes.Etiquette>) {
        try {
            val gestionnaireImpression = getSystemService(Context.PRINT_SERVICE) as PrintManager
            val nomTache = "Étiquettes Elegoo " + SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.FRANCE).format(Date())
            gestionnaireImpression.print(nomTache, ImpressionPlanche(this, etiquettes), PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir l'impression : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun construireRapportCompatibilite(): String {
        val versionAppli = try {
            val infoPaquet = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) infoPaquet.longVersionCode else infoPaquet.versionCode.toLong()
            "${infoPaquet.versionName} (build $build)"
        } catch (e: Exception) { "?" }
        val nfcActif = if (::nfcAdapter.isInitialized) (if (nfcAdapter.isEnabled) "oui" else "non") else "pas de puce NFC"

        val r = StringBuilder()
        r.append("=== Rapport de compatibilité - ElegooRFID (décodeur non encore validé sur un vrai tag) ===\n")
        r.append("Appli : v$versionAppli\n")
        r.append("Téléphone : ${Build.MANUFACTURER} ${Build.MODEL}\n")
        r.append("Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        r.append("NFC actif : $nfcActif\n")
        r.append("Date : ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())}\n\n")
        r.append("--- Dernier scan ---\n")
        if (dernierDumpTexte.isEmpty()) {
            // Corrige un bug reel (remonte par pascal_lb sur le forum, premier vrai test) : avant
            // ce correctif, ce rapport disait a tort "aucun scan effectue" apres un scan qui avait
            // bien eu lieu mais ou l'en-tete attendu n'avait pas ete reconnu - la seule situation
            // ou une vraie donnee de diagnostic etait justement necessaire.
            r.append("Aucun scan effectué depuis l'ouverture de l'appli.\n")
        } else if (dernierScanReussi) {
            r.append("Lecture réussie\n")
            r.append(dernierResume).append("\n")
        } else {
            r.append("Tag lu mais en-tête 0x36 non reconnu (voir dump brut ci-dessous)\n\n")
            r.append(dernierDumpTexte).append("\n")
        }
        return r.toString()
    }

    private fun copierRapportCompatibilite() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Rapport compatibilité ElegooRFID", construireRapportCompatibilite()))
        Toast.makeText(this, "Rapport copié : colle-le sur le forum.", Toast.LENGTH_SHORT).show()
    }

    /**
     * Partage direct du rapport de compatibilite (ajoute en v0.28 a la demande de Tomyn) : jusque
     * la, seul le copier-coller manuel etait possible. Accessible par un appui long sur le bouton
     * (plutot qu'un bouton supplementaire) - meme texte brut que copierRapportCompatibilite(),
     * juste un autre moyen de le faire sortir de l'appli.
     */
    private fun partagerRapportCompatibilite() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, construireRapportCompatibilite())
        }
        startActivity(Intent.createChooser(intent, "Partager le rapport de compatibilité"))
    }

    private fun ouvrirReglagesNfc() {
        try {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir les réglages NFC.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        contenuAExporter?.let { outState.putString("contenuAExporter", it) }
        outState.putString("dernierDumpTexte", dernierDumpTexte)
        outState.putString("dernierResume", dernierResume)
        outState.putBoolean("dernierScanReussi", dernierScanReussi)
        outState.putString("txtStatutTexte", txtStatut.text.toString())
        val couleurVisible = vuCouleur.visibility == View.VISIBLE
        outState.putBoolean("vuCouleurVisible", couleurVisible)
        if (couleurVisible) outState.putInt("vuCouleurArgb", vuCouleur.backgroundTintList?.defaultColor ?: Color.TRANSPARENT)
        outState.putIntArray("lignesInfoIcones", dernieresLignesInfo.map { it.first }.toIntArray())
        outState.putStringArray("lignesInfoTextes", dernieresLignesInfo.map { it.second }.toTypedArray())
    }

    private fun restaurerAffichageResultat(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        savedInstanceState.getString("dernierDumpTexte")?.let { dernierDumpTexte = it }
        savedInstanceState.getString("dernierResume")?.let { dernierResume = it }
        dernierScanReussi = savedInstanceState.getBoolean("dernierScanReussi")
        savedInstanceState.getString("txtStatutTexte")?.let { txtStatut.text = it }

        if (savedInstanceState.getBoolean("vuCouleurVisible")) {
            vuCouleur.backgroundTintList = ColorStateList.valueOf(savedInstanceState.getInt("vuCouleurArgb"))
            vuCouleur.visibility = View.VISIBLE
            imgNfc.visibility = View.GONE
        } else {
            vuCouleur.visibility = View.GONE
            imgNfc.visibility = View.VISIBLE
        }

        val icones = savedInstanceState.getIntArray("lignesInfoIcones") ?: IntArray(0)
        val textes = savedInstanceState.getStringArray("lignesInfoTextes") ?: emptyArray()
        for (i in icones.indices) ajouterLigneInfo(icones[i], textes.getOrElse(i) { "" })
    }

    companion object {
        const val CODE_EXPORT = 4711
        const val CODE_IMPORT = 4712
        const val CODE_EXPORT_PLANCHE = 4713
        const val CODE_CREATION = 4714
        const val CODE_IMPORT_LOT = 4715
        const val CODE_SCAN_QR = 4716
        const val CODE_IMPORT_BASE = 4717
        // Affichage de l'historique limite aux N scans les plus recents (v0.28, voir
        // afficherHistorique) - "Exporter/Partager" reste le moyen de tout recuperer au-dela.
        const val LIMITE_AFFICHAGE_HISTORIQUE = 50
    }
}
