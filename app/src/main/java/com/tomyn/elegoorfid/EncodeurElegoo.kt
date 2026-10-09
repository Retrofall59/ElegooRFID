package com.tomyn.elegoorfid

/**
 * Construit un dump Elegoo pret a cloner, a partir de champs saisis a la main (CreationTagActivity)
 * - l'inverse de DecodeurElegoo. Ajoute le 09/10/2026 a la demande de Tomyn, pour creer directement
 * un tag de bobine (notamment pour son futur filament recycle Lyman) sans passer par un editeur
 * externe.
 *
 * Construit un tableau de 160 octets (pages 0x00 a 0x27 - voir ClonageElegoo.TAILLE_MIN_DUMP_SOURCE,
 * exactement ce qu'exige le clonage), en recopiant telles quelles les zones communes a TOUS les
 * echantillons reels vus a ce jour (Capability Container, enregistrement NDEF "elegoo.com") et en
 * ecrivant les champs connus dans la zone produit (page 0x10 et suivantes - voir DecodeurElegoo.kt
 * pour le detail des offsets).
 *
 * Champs volontairement laisses a zero plutot que devines :
 *   - pages 0x00-0x02 (UID) : jamais copiees par le clonage (ClonageElegoo ne touche qu'a partir de
 *     0x03), leur valeur ici n'a donc aucune importance.
 *   - page 0x16 (0x58-0x5B) : zone dont le role reste inconnu sur tous les echantillons reels.
 *   - page 0x18 (date de fabrication) : volontairement a zero - voir la decision du 09/10/2026 dans
 *     DecodeurElegoo.kt (tous les echantillons reels montrent la meme valeur `00 36`, d'origine
 *     inconnue - on evite de la reproduire sans savoir ce qu'elle signifie).
 */
object EncodeurElegoo {

    // Capability Container (page 0x03) - identique sur tous les echantillons reels.
    private val CC = byteArrayOf(0xE1.toByte(), 0x10, 0x12, 0x00)

    // Enregistrement NDEF standard pointant vers "https://www.elegoo.com" (pages 0x04-0x09) -
    // identique sur tous les echantillons reels, aucun rapport avec les donnees de la bobine.
    private val NDEF = byteArrayOf(
        0x01, 0x03, 0xA0.toByte(), 0x0C, 0x34, 0x03, 0x0F, 0xD1.toByte(),
        0x01, 0x0B, 0x55, 0x02, 0x65, 0x6C, 0x65, 0x67,
        0x6F, 0x6F, 0x2E, 0x63, 0x6F, 0x6D, 0xFE.toByte(), 0x00
    )

    private const val CODE_FABRICANT = 0xEE.toByte()

    private fun ecrireU16(d: ByteArray, off: Int, valeur: Int) {
        d[off] = ((valeur shr 8) and 0xFF).toByte()
        d[off + 1] = (valeur and 0xFF).toByte()
    }

    private fun ecrireU32(d: ByteArray, off: Int, valeur: Long) {
        for (i in 0..3) d[off + i] = ((valeur shr ((3 - i) * 8)) and 0xFF).toByte()
    }

    /**
     * @param couleurHex 6 caracteres hexadecimaux (RRGGBB), sans '#'.
     * @param diametreMm ex. 1.75 - converti en centiemes de mm (meme unite que le format du tag).
     * @return un dump de 160 octets, pret pour ClonageElegoo.pagesAEcrire (clonage) ou
     *         DecodeurElegoo.decoder (verification/apercu avant clonage).
     */
    fun construireDump(
        codeMatiere: Long,
        codeSousType: Int,
        couleurHex: String,
        poidsGrammes: Int,
        diametreMm: Double,
        tempMinC: Int,
        tempMaxC: Int
    ): ByteArray {
        val d = ByteArray(160)

        CC.copyInto(d, 0x03 * 4)
        NDEF.copyInto(d, 0x04 * 4)
        // Pages 0x0A-0x0F deja a zero (ByteArray initialise a zero par defaut).

        d[0x10 * 4] = 0x36 // en-tete
        repeat(4) { d[0x10 * 4 + 1 + it] = CODE_FABRICANT } // deborde volontairement sur page 0x11
        // (code fabricant = offsets 65-68, soit le dernier octet de la page 0x10 + les 3 premiers
        // de la page 0x11 - voir DecodeurElegoo.decoder, copyOfRange(65, 69))

        ecrireU32(d, 0x12 * 4, codeMatiere)
        ecrireU16(d, 0x13 * 4, codeSousType)

        val rgb = ByteArray(3) { i -> couleurHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        rgb.copyInto(d, 0x14 * 4)
        d[0x14 * 4 + 3] = 0xFF.toByte() // "modificateur" de couleur, constant sur tous les echantillons reels

        ecrireU16(d, 0x15 * 4, tempMinC)
        ecrireU16(d, 0x15 * 4 + 2, tempMaxC)

        ecrireU16(d, 0x17 * 4, Math.round(diametreMm * 100).toInt())
        ecrireU16(d, 0x17 * 4 + 2, poidsGrammes)

        // Page 0x18 (date) et pages 0x19-0x27 restent a zero - voir le commentaire en tete de fichier.

        return d
    }
}
