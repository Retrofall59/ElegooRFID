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
 * MATIERE ET SOUS-TYPE (ajoute le 08/10/2026) : confirmes par comparaison de 5 fichiers generes
 * par l'editeur open-source "elegoo-rfid-editor" (github.com/Savion/elegoo-rfid-editor), tous a
 * la meme couleur, un par matiere (PLA, PETG, ABS, ASA, TPU). Le code matiere (4 octets, page
 * 0x12) et le code sous-type (2 octets, page 0x13) correspondent exactement a la table donnee
 * dans le code source de cet editeur (src/lib/materials.ts) - voir MaterialsElegoo.kt. Le code
 * sous-type du fichier TPU genere par l'utilisateur (0x0302) correspondait meme exactement au nom
 * de fichier qu'il avait choisi ("RAPID_TPU_95A"), confirmation supplementaire que l'offset et la
 * table sont les bons.
 *
 * TEMPERATURE D'EXTRUSION (ajoute le 08/10/2026, meme source que matiere/sous-type) : page 0x15,
 * min sur les 2 premiers octets (0x54-0x55) et max sur les 2 derniers (0x56-0x57). AUCUN champ
 * "temperature plateau" n'existe dans ce format - seule la temperature buse (min/max) est
 * presente.
 *
 * DATE DE FABRICATION (ajoute le 09/10/2026, revu le meme jour) : page trouvee par Damdam2959
 * dans l'editeur hexadecimal de l'editeur elegoo-rfid-editor (qui annote chaque page), interpretee
 * par son code source (ElegooSpool.ts) comme annee+mois en BCD (octet 0x60 = annee, 0x61 = mois).
 * Mais sur la PREMIERE vraie bobine testee (Damdam2959, PLA noir), ca donne 0x60=0x00, 0x61=0x36 -
 * un "mois 36" qui n'existe pas. Hypothese alternative de Damdam2959, bien plus plausible : un
 * code YYWW (annee + numero de semaine), convention tres courante dans l'industrie - 0x36 en BCD
 * fait "36", un numero de semaine tout a fait valide (1-53), alors que 36 est impossible comme
 * mois. Affiche donc "annee/mois" quand l'octet 0x61 decode un mois valide (1-12, comme sur le
 * gabarit de l'editeur), sinon "semaine" quand il decode un numero de semaine valide (1-53) - dans
 * ce second cas, etiquete comme hypothese non confirmee (un seul echantillon reel pour l'instant).
 */
object DecodeurElegoo {

    data class InfoBobine(
        val headerValide: Boolean?,     // true si l'octet d'en-tete vaut bien 0x36 (signature du format)
        val codeFabricant: String?,
        val matiereTexte: String?,      // nom de la famille, ex. "PETG" - null si code non reconnu
        val sousTypeTexte: String?,     // nom precis si different de la famille, ex. "RAPID TPU 95A"
        val couleurHex: String?,        // RGB888 brut, ex. "106DD7" - directement exploitable, pas de table a deviner
        val diametreMm: Double?,
        val poidsGrammes: Int?,
        val tempMinC: Int?,              // temperature d'extrusion (buse) minimale, en degres C
        val tempMaxC: Int?,              // temperature d'extrusion (buse) maximale, en degres C
        val dateFabricationTexte: String?,       // "MM/AAAA", ex. "01/2025" - mois valide (1-12) uniquement
        val semaineFabricationTexte: String?     // "Semaine XX (hypothèse non confirmée)" - repli si pas un mois valide mais une semaine valide (1-53)
    )

    private fun u16(d: ByteArray, off: Int): Int? {
        if (off + 1 >= d.size) return null
        return ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
    }

    /** Decode un octet BCD (ex. 0x25 -> 25) en entier decimal. */
    private fun decoderBCD(octet: Int): Int = ((octet shr 4) and 0x0F) * 10 + (octet and 0x0F)

    private fun u32(d: ByteArray, off: Int): Long? {
        if (off + 3 >= d.size) return null
        var resultat = 0L
        for (i in 0..3) resultat = (resultat shl 8) or (d[off + i].toLong() and 0xFF)
        return resultat
    }

    /**
     * @param dump octets bruts du tag, page 0 (UID) en premier. Doit couvrir au moins jusqu'a
     *             l'octet 95 (fin de la page 0x17) pour obtenir tous les champs ; un dump plus
     *             court renvoie les champs qu'il peut et laisse les autres a null (jamais d'erreur).
     */
    fun decoder(dump: ByteArray): InfoBobine {
        val entete = if (dump.size > 64) (dump[64].toInt() and 0xFF) == 0x36 else null
        val fabricant = if (dump.size >= 69) dump.copyOfRange(65, 69).joinToString(":") { "%02X".format(it) } else null
        val codeMatiere = u32(dump, 72)
        val codeSousType = u16(dump, 76)
        val matiere = MaterialsElegoo.resoudreFamilleMatiere(codeMatiere)
        val sousType = MaterialsElegoo.resoudreSousType(codeMatiere, codeSousType)
        val couleur = if (dump.size >= 83) dump.copyOfRange(80, 83).joinToString("") { "%02X".format(it) } else null
        val diametreBrut = u16(dump, 92)
        val poids = u16(dump, 94)
        val tempMin = u16(dump, 84)
        val tempMax = u16(dump, 86)
        var dateFabrication: String? = null
        var semaineFabrication: String? = null
        if (dump.size >= 98) {
            val annee = decoderBCD(dump[96].toInt() and 0xFF)
            val moisOuSemaine = decoderBCD(dump[97].toInt() and 0xFF)
            if (moisOuSemaine in 1..12) {
                dateFabrication = "%02d/20%02d".format(moisOuSemaine, annee)
            } else if (moisOuSemaine in 1..53) {
                semaineFabrication = "Semaine %02d (hypothèse non confirmée)".format(moisOuSemaine)
            }
        }

        return InfoBobine(
            headerValide = entete,
            codeFabricant = fabricant,
            matiereTexte = matiere,
            sousTypeTexte = sousType,
            couleurHex = couleur,
            diametreMm = diametreBrut?.let { it / 100.0 },
            poidsGrammes = poids,
            tempMinC = tempMin,
            tempMaxC = tempMax,
            dateFabricationTexte = dateFabrication,
            semaineFabricationTexte = semaineFabrication
        )
    }
}
