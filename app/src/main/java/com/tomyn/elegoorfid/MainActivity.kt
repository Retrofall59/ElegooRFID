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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.File
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
    private lateinit var nfcAdapter: NfcAdapter

    private val dernieresLignesInfo = mutableListOf<Pair<Int, String>>()
    private var dernierDumpTexte: String = ""
    private var dernierResume: String = ""
    private var dernierScanReussi = false
    private var contenuAExporter: String? = null

    // --- Clonage : voir ClonageElegoo.kt pour le detail et le choix de securite (pourquoi on ne
    // touche qu'aux pages 0x03-0x27, jamais 0x28+ qui sont de la configuration de puce). ---
    private var dernierDumpBrut: ByteArray? = null
    private var enAttenteTagCible = false
    private var ecrasementConfirme = false

    // --- Effacement : voir ClonageElegoo.kt (pagesAEffacer/zoneEffacee) - meme plage de pages
    // que le clonage, jamais 0x28+. Contrairement au clonage, ne depend d'aucune lecture
    // prealable : disponible des le lancement de l'appli. ---
    private var enAttenteTagEffacement = false

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

        btnReglagesNfc.setOnClickListener { ouvrirReglagesNfc() }
        btnCloner.setOnClickListener { demarrerModeClonage() }
        btnAnnulerClonage.setOnClickListener { annulerModeAttente() }
        btnEffacer.setOnClickListener { demarrerModeEffacement() }
        findViewById<Button>(R.id.btnExporter).setOnClickListener { exporterDump() }
        findViewById<Button>(R.id.btnImporterDump).setOnClickListener { importerDump() }
        findViewById<Button>(R.id.btnCopier).setOnClickListener { copierResume() }
        findViewById<Button>(R.id.btnPartager).setOnClickListener { partagerResume() }
        findViewById<Button>(R.id.btnHistorique).setOnClickListener { afficherHistorique() }
        findViewById<Button>(R.id.btnRapportCompat).setOnClickListener { copierRapportCompatibilite() }
        findViewById<ImageButton>(R.id.btnParametres).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        NfcAdapter.getDefaultAdapter(this)?.let { nfcAdapter = it }

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
    }

    override fun onPause() {
        super.onPause()
        if (::nfcAdapter.isInitialized) nfcAdapter.disableReaderMode(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        traiterIntentEventuel(intent)
    }

    private fun traiterIntentEventuel(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) return
        @Suppress("DEPRECATION")
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        lireTag(tag)
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

        layoutLignesInfo.removeAllViews()
        dernieresLignesInfo.clear()
        vuCouleur.visibility = View.GONE
        imgNfc.visibility = View.VISIBLE
        btnCloner.visibility = View.GONE
        txtStatut.text = "Lecture en cours..."

        val nfcA = NfcA.get(tag)
        if (nfcA == null) {
            txtStatut.text = "Ce tag n'est pas compatible NFC-A"
            dernierScanReussi = false
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
                return
            }

            afficherResultats(info, dump)
        } catch (e: Exception) {
            txtStatut.text = "Erreur de lecture : ${e.message}"
            dernierScanReussi = false
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
                if (reponse.size == 1 && reponse[0] == 0x0A.toByte()) return true
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
                btnCloner.visibility = View.GONE
                btnEffacer.visibility = View.GONE
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
            val dumpRelu = lireDumpBrut(nfcA, ClonageElegoo.DERNIERE_PAGE_DONNEES)
            nfcA.close()

            enAttenteTagEffacement = false
            val reussi = pageEnEchec == null && ClonageElegoo.zoneEffacee(dumpRelu)
            runOnUiThread {
                btnAnnulerClonage.visibility = View.GONE
                btnEffacer.visibility = View.VISIBLE
                if (dernierScanReussi) btnCloner.visibility = View.VISIBLE
                txtStatut.text = when {
                    reussi -> "Tag effacé et vérifié ✓ — prêt pour un nouveau clonage"
                    pageEnEchec != null -> "Écriture interrompue (page 0x%02X non confirmée) — repose le tag bien à plat sans le bouger et réessaie.".format(pageEnEchec)
                    else -> "Écriture terminée mais la relecture ne confirme pas un effacement complet - réessaie."
                }
            }
        } catch (e: Exception) {
            try { nfcA.close() } catch (e2: Exception) { /* rien a faire */ }
            enAttenteTagEffacement = false
            runOnUiThread {
                btnAnnulerClonage.visibility = View.GONE
                btnEffacer.visibility = View.VISIBLE
                if (dernierScanReussi) btnCloner.visibility = View.VISIBLE
                txtStatut.text = "Erreur d'effacement : ${e.message} — le tag est peut-être verrouillé ou n'est pas un NTAG213/215."
            }
        }
    }

    private fun annulerModeAttente() {
        val effacementEnCours = enAttenteTagEffacement
        enAttenteTagCible = false
        enAttenteTagEffacement = false
        ecrasementConfirme = false
        btnAnnulerClonage.visibility = View.GONE
        btnEffacer.visibility = View.VISIBLE
        if (dernierScanReussi) btnCloner.visibility = View.VISIBLE
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

    /** Ecrit les pages 0x03-0x27 depuis dumpSource sur tag, puis relit pour verifier. Gere elle-meme connect/close. */
    private fun ecrireEtVerifierClone(tag: Tag, dumpSource: ByteArray) {
        val nfcA = NfcA.get(tag)
        if (nfcA == null) {
            runOnUiThread { Toast.makeText(this, "Ce tag n'est pas compatible NFC-A, essaie un autre tag.", Toast.LENGTH_SHORT).show() }
            return
        }

        runOnUiThread { txtStatut.text = "Écriture en cours, ne retire pas le tag..." }

        try {
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

            enAttenteTagCible = false
            val reussi = pageEnEchec == null && ClonageElegoo.zoneCloneeIdentique(dumpSource, dumpRelu)
            runOnUiThread {
                btnAnnulerClonage.visibility = View.GONE
                btnEffacer.visibility = View.VISIBLE
                if (dernierScanReussi) btnCloner.visibility = View.VISIBLE
                if (reussi) {
                    txtStatut.text = "Clonage réussi et vérifié ✓"
                    if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerConfirmation()
                } else if (pageEnEchec != null) {
                    txtStatut.text = "Écriture interrompue (page 0x%02X non confirmée) — repose le tag bien à plat sans le bouger et réessaie.".format(pageEnEchec)
                } else {
                    txtStatut.text = "Écriture terminée mais la relecture ne correspond pas - clonage probablement incomplet. Réessaie."
                }
            }
        } catch (e: Exception) {
            try { nfcA.close() } catch (e2: Exception) { /* rien a faire */ }
            enAttenteTagCible = false
            runOnUiThread {
                btnAnnulerClonage.visibility = View.GONE
                btnEffacer.visibility = View.VISIBLE
                if (dernierScanReussi) btnCloner.visibility = View.VISIBLE
                txtStatut.text = "Erreur d'écriture : ${e.message} — le tag cible est peut-être verrouillé ou n'est pas un NTAG213/215 vierge."
            }
        }
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

    private fun afficherResultats(info: DecodeurElegoo.InfoBobine, dump: ByteArray) {
        dernierScanReussi = true
        dernierDumpBrut = dump
        btnCloner.visibility = if (dump.size >= ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) View.VISIBLE else View.GONE
        txtStatut.text = "Bobine identifiée"

        if (info.couleurHex != null) {
            try {
                val argb = Color.parseColor("#${info.couleurHex}")
                vuCouleur.backgroundTintList = ColorStateList.valueOf(argb)
                vuCouleur.visibility = View.VISIBLE
                imgNfc.visibility = View.GONE
            } catch (e: Exception) { /* hex invalide : on garde l'icone NFC */ }
        }

        // Matiere et date de fabrication pas encore localisees avec certitude (voir le commentaire
        // en tete de DecodeurElegoo.kt) : on ne les affiche pas plutot que d'inventer une valeur.
        info.couleurHex?.let { ajouterLigneInfo(R.drawable.ic_couleur, "Couleur : #$it") }
        info.poidsGrammes?.let { ajouterLigneInfo(R.drawable.ic_materiau, "Poids bobine : ${it}g") }
        info.diametreMm?.let { ajouterLigneInfo(R.drawable.ic_temperature, "Diamètre : ${it}mm") }

        val resume = StringBuilder()
        info.couleurHex?.let { resume.appendLine("Couleur : #$it") }
        info.poidsGrammes?.let { resume.appendLine("Poids : ${it}g") }
        info.diametreMm?.let { resume.appendLine("Diamètre : ${it}mm") }
        info.codeFabricant?.let { resume.appendLine("Code fabricant : $it") }
        dernierResume = resume.toString().trim()
        dernierDumpTexte = dernierResume + "\n\n--- DUMP BRUT (pour analyse) ---\n" + formaterDumpHex(dump)

        if (GestionnaireParametres.lireVibrationFinLecture(this)) vibrerConfirmation()
        enregistrerDansHistorique(info.codeFabricant ?: "?", info.couleurHex ?: "")
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

    private fun copierResume() {
        if (dernierResume.isEmpty()) {
            Toast.makeText(this, "Rien à copier pour l'instant, scanne d'abord un tag.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Résultat Elegoo RFID", dernierResume))
        Toast.makeText(this, "Copié dans le presse-papier.", Toast.LENGTH_SHORT).show()
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
     * Importe un dump exporte precedemment (le meme fichier .txt que exporterDump() produit, sur
     * CET appareil ou un autre) pour pouvoir cloner sans avoir la bobine source physique sous la
     * main au moment du clonage. Ajoute le 08/10/2026 a la demande de Tomyn.
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
                    val texte = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IOException("fichier inaccessible")
                    val dump = parserDumpHex(texte)
                    if (dump == null || dump.size < ClonageElegoo.TAILLE_MIN_DUMP_SOURCE) {
                        Toast.makeText(this, "Fichier non reconnu : ce n'est pas un dump exporté par cette appli, ou il est incomplet.", Toast.LENGTH_LONG).show()
                        return
                    }
                    dernierDumpBrut = dump
                    dernierScanReussi = true
                    btnCloner.visibility = View.VISIBLE
                    txtStatut.text = "Dump importé (${dump.size} octets) - prêt à cloner sur un tag vierge."
                    Toast.makeText(this, "Dump importé, appuie sur \"Cloner sur une bobine vierge\".", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erreur d'import : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun enregistrerDansHistorique(codeFabricant: String, couleurHex: String) {
        try {
            val fichier = File(getExternalFilesDir(null), "historique_scans.csv")
            val ligne = "${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())};$codeFabricant;$couleurHex\n"
            fichier.appendText(ligne)
        } catch (e: Exception) { /* pas grave si l'ecriture de l'historique echoue */ }
    }

    private fun afficherHistorique() {
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
            val lignes = fichier.readLines().reversed()
            val texteAffiche = lignes.joinToString("\n\n") { ligne ->
                val parts = ligne.split(";")
                if (parts.size >= 3) "${parts[0]}\n${parts[2]}" else ligne
            }
            AlertDialog.Builder(this)
                .setTitle("Historique des scans (${lignes.size})")
                .setMessage(texteAffiche)
                .setPositiveButton("Fermer", null)
                .setNeutralButton("Exporter") { _, _ -> exporterVers("historique_scans_elegoo.csv", fichier.readText(), "text/csv") }
                .setNegativeButton("Vider l'historique") { _, _ ->
                    fichier.delete()
                    Toast.makeText(this, "Historique effacé.", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lecture historique : ${e.message}", Toast.LENGTH_LONG).show()
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
    }
}
