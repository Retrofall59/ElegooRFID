package com.tomyn.elegoorfid

/**
 * Table de correspondance matiere/sous-type pour les tags Elegoo.
 *
 * Portee depuis le projet open-source "elegoo-rfid-editor" (github.com/Savion/elegoo-rfid-editor,
 * fichier src/lib/materials.ts), qui liste directement dans son code source les codes numeriques
 * utilises par Elegoo - bien plus fiable que la doc officielle EPC-256, qui s'est deja reveles
 * fausse sur les offsets de page (voir le commentaire en tete de DecodeurElegoo.kt).
 *
 * Verifie le 08/10/2026 par comparaison octet par octet de 5 fichiers generes par cet editeur
 * (PLA, PETG, ABS, ASA, TPU, tous a la meme couleur) : les deux champs tombent exactement aux
 * offsets indiques dans le README de cet editeur (0x48 = page 0x12, 0x4C = page 0x13), et les
 * valeurs decodees correspondent exactement aux noms de fichiers generes (ex. TPU avec sous-type
 * 0x0302 = "RAPID TPU 95A", qui etait bien le nom du fichier genere par l'utilisateur).
 */
object MaterialsElegoo {

    /** Code matiere (4 octets, grand-boutiste) -> nom de la famille de matiere. */
    val CODES_MATIERE: Map<Long, String> = mapOf(
        0x00807665L to "PLA",
        0x80698471L to "PETG",
        0x00656683L to "ABS",
        0x00848085L to "TPU",
        0x00008065L to "PA",
        0x00678069L to "CPE",
        0x00008067L to "PC",
        0x00808665L to "PVA",
        0x00658365L to "ASA",
        0x42564F48L to "BVOH",
        0x00455641L to "EVA",
        0x48495053L to "HIPS",
        0x00005050L to "PP",
        0x00505041L to "PPA",
        0x00505053L to "PPS",
    )

    /** Code sous-type (2 octets, grand-boutiste) -> nom precis (ex. "RAPID TPU 95A", "PLA Matte"). */
    val CODES_SOUS_TYPE: Map<Int, String> = mapOf(
        // Famille PLA (0x00XX)
        0x0000 to "PLA",
        0x0001 to "PLA+",
        0x0002 to "PLA Pro",
        0x0003 to "PLA Silk",
        0x0004 to "PLA-CF",
        0x0005 to "PLA Carbon",
        0x0006 to "PLA Matte",
        0x0007 to "PLA Fluo",
        0x0008 to "PLA Wood",
        0x0009 to "PLA Basic",
        0x000A to "RAPID PLA+",
        0x000B to "PLA Marble",
        0x000C to "PLA Galaxy",
        0x000D to "PLA Red Copper",
        0x000E to "PLA Sparkle",
        // Famille PETG (0x01XX)
        0x0100 to "PETG",
        0x0101 to "PETG-CF",
        0x0102 to "PETG-GF",
        0x0103 to "PETG Pro",
        0x0104 to "PETG Translucent",
        0x0105 to "RAPID PETG",
        // Famille ABS (0x02XX)
        0x0200 to "ABS",
        0x0201 to "ABS-GF",
        // Famille TPU (0x03XX)
        0x0300 to "TPU",
        0x0301 to "TPU 95A",
        0x0302 to "RAPID TPU 95A",
        // Famille PA (0x04XX)
        0x0400 to "PA",
        0x0401 to "PA-CF",
        0x0403 to "PAHT-CF",
        0x0404 to "PA6",
        0x0405 to "PA6-CF",
        0x0406 to "PA12",
        0x0407 to "PA12-CF",
        // Autres matieres
        0x0500 to "CPE",
        0x0600 to "PC",
        0x0601 to "PCTG",
        0x0602 to "PC-FR",
        0x0700 to "PVA",
        0x0800 to "ASA",
        0x0900 to "BVOH",
        0x0A00 to "EVA",
        0x0B00 to "HIPS",
        0x0C00 to "PP",
        0x0C01 to "PP-CF",
        0x0C02 to "PP-GF",
        0x0D00 to "PPA",
        0x0D01 to "PPA-CF",
        0x0D02 to "PPA-GF",
        0x0E00 to "PPS",
        0x0E02 to "PPS-CF",
    )

    /**
     * Resout le texte matiere a afficher a partir des deux codes bruts. Prefere le sous-type
     * (plus precis, ex. "RAPID TPU 95A") ; si le sous-type n'est pas dans la table, retombe sur
     * le nom de la famille de matiere seule ; si meme ca n'est pas reconnu, retourne null plutot
     * que d'inventer un nom (meme choix que pour les autres champs non confirmes).
     */
    fun resoudreTexteMatiere(codeMatiere: Long?, codeSousType: Int?): String? {
        codeSousType?.let { CODES_SOUS_TYPE[it]?.let { nom -> return nom } }
        codeMatiere?.let { CODES_MATIERE[it]?.let { nom -> return nom } }
        return null
    }
}
