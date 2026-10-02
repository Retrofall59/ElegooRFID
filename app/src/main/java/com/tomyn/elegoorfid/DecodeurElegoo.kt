package com.tomyn.elegoorfid

/**
 * Decodeur du format EPC-256 des tags RFID Elegoo (NTAG213, memoire utilisateur brute, pas de NDEF).
 *
 * Construit directement a partir de la specification officielle publiee par Elegoo :
 * https://github.com/elegooofficial/ELEGOO-RFID-Tag-Guide (v1.0, 2025-02-10)
 *
 * IMPORTANT - PAS ENCORE VALIDE SUR UN VRAI TAG (contrairement a BambuRfidReader, PrusaTag et
 * AnycubicRFID, qui ont tous ete etablis a partir de vrais dumps). Les offsets ci-dessous
 * reproduisent fidelement le tableau d'allocation en octets de leur doc (section 3.2), verifie
 * coherent avec leur exemple numerique complet (section 4) - mais leur propre tableau
 * d'introduction (section 2) contient des erreurs manifestes sur ces memes champs (l'exemple
 * "Material (Main) = 0x00807665" n'est pas de l'ASCII valide, contrairement a 0x504C4120="PLA "
 * utilise partout ailleurs dans le document), donc une divergence entre la doc et un vrai tag
 * reste possible. A confirmer des qu'un vrai dump est disponible.
 *
 * Champs tasses sans octet de bourrage entre eux (verifie sur l'exemple officiel). Point
 * important corrige pendant l'ecriture : la doc utilise "0x04" etc. a la fois comme numero de
 * PAGE (section 3.1 : "User Memory 0x04~0x27", standard NTAG213) et, dans le tableau d'allocation
 * (section 3.2), comme s'il s'agissait directement d'un OCTET - ces deux sections de leur propre
 * doc ne sont pas cohérentes entre elles. On retient l'interpretation "numero de page" (standard
 * NTAG213, confirmee par ailleurs sur Anycubic qui utilise la meme puce), donc la zone utile
 * commence a l'octet 16 (fin des 4 pages systeme : UID + verrous), pas a l'octet 4. Decalages
 * ci-dessous donnes en octets ABSOLUS depuis le debut du dump (page 0 incluse) :
 *   16          Header (1 octet, attendu 0x36)
 *   17-20       Code fabricant (4 octets)
 *   21-22       Code filament interne (2 octets)
 *   23-26       Nom matiere, ASCII (4 octets, ex. "PLA ")
 *   27-30       Sous-type matiere, ASCII (4 octets, ex. "CF20")
 *   31-33       Couleur RGB888 (3 octets)
 *   34-35       Diametre, centiemes de mm (2 octets)
 *   36-37       Poids, grammes (2 octets)
 *   38-39       Date de fabrication, AAMM en decimal compacte dans un entier (2 octets)
 */
object DecodeurElegoo {

    data class InfoBobine(
        val headerValide: Boolean?,     // true si l'octet d'en-tete vaut bien 0x36 (signature du format)
        val codeFabricant: String?,
        val codeFilament: String?,
        val matiere: String?,           // ex. "PLA"
        val sousType: String?,          // ex. "CF20" (composite carbone), vide si non renseigne
        val couleurHex: String?,        // RGB888 brut, ex. "FF3700" - directement exploitable, pas de table a deviner
        val diametreMm: Double?,
        val poidsGrammes: Int?,
        val anneeFabrication: Int?,
        val moisFabrication: Int?
    )

    private fun u16(d: ByteArray, off: Int): Int? {
        if (off + 1 >= d.size) return null
        return ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
    }

    private fun texteAscii(d: ByteArray, off: Int, longueur: Int): String? {
        if (off + longueur > d.size) return null
        val brut = d.copyOfRange(off, off + longueur)
        val fin = brut.indexOfLast { it != 0.toByte() && it != ' '.code.toByte() }
        if (fin < 0) return null
        return String(brut, 0, fin + 1, Charsets.US_ASCII).trim()
    }

    /**
     * @param dump octets bruts du tag, page 0 (UID) en premier. Doit couvrir au moins jusqu'a
     *             l'octet 0x1B (28 octets) pour obtenir tous les champs ; un dump plus court
     *             renvoie les champs qu'il peut et laisse les autres a null (jamais d'erreur).
     */
    fun decoder(dump: ByteArray): InfoBobine {
        val entete = if (dump.size > 16) (dump[16].toInt() and 0xFF) == 0x36 else null
        val fabricant = if (dump.size >= 21) dump.copyOfRange(17, 21).joinToString(":") { "%02X".format(it) } else null
        val filamentCode = if (dump.size >= 23) dump.copyOfRange(21, 23).joinToString("") { "%02X".format(it) } else null
        val couleur = if (dump.size >= 34) dump.copyOfRange(31, 34).joinToString("") { "%02X".format(it) } else null
        val diametreBrut = u16(dump, 34)
        val poids = u16(dump, 36)
        val date = u16(dump, 38)

        return InfoBobine(
            headerValide = entete,
            codeFabricant = fabricant,
            codeFilament = filamentCode,
            matiere = texteAscii(dump, 23, 4),
            sousType = texteAscii(dump, 27, 4),
            couleurHex = couleur,
            diametreMm = diametreBrut?.let { it / 100.0 },
            poidsGrammes = poids,
            anneeFabrication = date?.let { 2000 + it / 100 },
            moisFabrication = date?.let { it % 100 }
        )
    }
}
