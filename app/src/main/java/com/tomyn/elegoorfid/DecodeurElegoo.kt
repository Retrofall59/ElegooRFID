package com.tomyn.elegoorfid

/**
 * Decodeur des tags RFID Elegoo (NTAG213).
 *
 * Reecrit a partir de DEUX VRAIS DUMPS (PLA noir et PLA bleu, fournis par pascal_lb sur le
 * forum), apres que la premiere version - construite depuis la doc officielle EPC-256
 * (https://github.com/elegooofficial/ELEGOO-RFID-Tag-Guide) - se soit revelee fausse sur un
 * vrai tag : la doc annonce les donnees a partir de la page 0x04, alors qu'en realite cette
 * zone contient un enregistrement NDEF standard (lien "https://www.elegoo.com", rien a voir
 * avec la bobine) et les vraies donnees ne commencent qu'a la page 0x10 (decalage de 48 octets
 * que rien dans la doc ne laissait deviner).
 *
 * Comparaison octet par octet des deux dumps reels (pages 0x10 a 0x18) : une SEULE page differe
 * entre les deux bobines, la page 0x14 - exactement les 3 premiers octets, qui donnent #000000
 * pour le noir et #106DD7 pour le bleu (les deux coherents avec l'etiquette physique). Tout le
 * reste est identique a l'octet pres entre les deux echantillons.
 *
 * CE QUI EST CONFIRME (par les deux dumps reels) :
 *   page 0x10, octet 0     Header (0x36 dans les deux cas)
 *   page 0x10-0x11         Code fabricant (EEEEEEEE dans les deux cas, correspond a la doc)
 *   page 0x14, octets 0-2  Couleur RGB888 (seule chose qui change entre les deux echantillons)
 *   page 0x17, octets 1-2  Diametre, centiemes de mm (00AF = 1.75mm dans les deux cas)
 *   page 0x17-0x18         Poids, grammes (03E8 = 1000g dans les deux cas)
 *
 * CE QUI N'EST PAS ENCORE CONFIRME : matiere, sous-type et date de fabrication. Les deux
 * echantillons etant tous les deux du PLA (meme matiere), rien ne permet de distinguer "la bonne
 * position pour la matiere" d'un simple bloc constant sans rapport avec elle - les 11 octets
 * entre le code fabricant et la couleur sont identiques sur les deux dumps (00 00 00 00 80 76 65
 * 00 00 00 00), sans correspondre a du texte ASCII lisible. Il faudrait un dump d'une AUTRE
 * matiere (PETG, ABS...) pour verifier par comparaison, exactement comme pour la couleur. En
 * attendant, ces champs renvoient null plutot qu'une valeur devinee.
 */
object DecodeurElegoo {

    data class InfoBobine(
        val headerValide: Boolean?,     // true si l'octet d'en-tete vaut bien 0x36 (signature du format)
        val codeFabricant: String?,
        val couleurHex: String?,        // RGB888 brut, ex. "106DD7" - directement exploitable, pas de table a deviner
        val diametreMm: Double?,
        val poidsGrammes: Int?
    )

    private fun u16(d: ByteArray, off: Int): Int? {
        if (off + 1 >= d.size) return null
        return ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
    }

    /**
     * @param dump octets bruts du tag, page 0 (UID) en premier. Doit couvrir au moins jusqu'a
     *             l'octet 95 (fin de la page 0x17) pour obtenir tous les champs ; un dump plus
     *             court renvoie les champs qu'il peut et laisse les autres a null (jamais d'erreur).
     */
    fun decoder(dump: ByteArray): InfoBobine {
        val entete = if (dump.size > 64) (dump[64].toInt() and 0xFF) == 0x36 else null
        val fabricant = if (dump.size >= 69) dump.copyOfRange(65, 69).joinToString(":") { "%02X".format(it) } else null
        val couleur = if (dump.size >= 83) dump.copyOfRange(80, 83).joinToString("") { "%02X".format(it) } else null
        val diametreBrut = u16(dump, 92)
        val poids = u16(dump, 94)

        return InfoBobine(
            headerValide = entete,
            codeFabricant = fabricant,
            couleurHex = couleur,
            diametreMm = diametreBrut?.let { it / 100.0 },
            poidsGrammes = poids
        )
    }
}
