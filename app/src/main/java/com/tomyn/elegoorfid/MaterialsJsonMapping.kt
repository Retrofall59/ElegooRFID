package com.tomyn.elegoorfid

/**
 * Rapproche le champ texte libre "material" des fichiers JSON de base de filaments (ex. "PLA+",
 * "PA6-CF", "BIOFUSION"...) avec la table fixe de matieres/sous-types du format de tag Elegoo
 * (MaterialsElegoo.kt). Ajoute le 09/10/2026 a la demande de Tomyn - voir ImportBaseDonneesActivity.
 *
 * Deux niveaux de resultat, pour rester honnete sur ce qui est fiable et ce qui est approximatif :
 *   - correspondance exacte : le texte JSON est EXACTEMENT un nom connu de MaterialsElegoo (famille
 *     ou sous-type), ex. "PETG-CF", "PA6-CF", "PCTG" -> matiere ET sous-type corrects.
 *   - correspondance approximative : le texte JSON est une variante proche d'une famille connue
 *     mais sans sous-type Elegoo correspondant (ex. "ABS+MATTE", "EASYASA") -> matiere correcte,
 *     mais sous-type generique de la famille (approximation signalee a l'ecran, jamais cachee).
 * Tout le reste (PVDF, PCPBT, GREENTEC, FLAX, PEARL, BIOFUSION, HTPET+...) est laisse non reconnu
 * plutot que de deviner une famille au hasard - ces matieres n'existent simplement pas cote Elegoo.
 */
object MaterialsJsonMapping {

    data class Resultat(val codeMatiere: Long, val codeSousType: Int, val approximatif: Boolean)

    // Index "nom (famille ou sous-type), en majuscules" -> (codeMatiere, codeSousType), construit
    // uniquement a partir de l'API publique de MaterialsElegoo (CODES_MATIERE + sousTypesPourFamille)
    // - aucune dependance a ses details internes.
    private val index: Map<String, Pair<Long, Int>> by lazy {
        val m = mutableMapOf<String, Pair<Long, Int>>()
        for ((codeMatiere, famille) in MaterialsElegoo.CODES_MATIERE) {
            val sousTypes = MaterialsElegoo.sousTypesPourFamille(famille)
            if (sousTypes.isEmpty()) {
                m[famille.uppercase()] = codeMatiere to 0
            } else {
                for ((codeSousType, nomSousType) in sousTypes) {
                    m[nomSousType.uppercase()] = codeMatiere to codeSousType
                }
            }
        }
        m
    }

    /**
     * Alias vers le sous-type GENERIQUE d'une famille connue, pour des variantes JSON qui
     * n'ont pas d'equivalent precis dans la table Elegoo - toujours signale "approximatif" a
     * l'ecran (matiere correcte, mais le sous-type affiche sur le tag sera plus generique que ce
     * que dit vraiment le fichier JSON).
     */
    private val aliasApproximatifs: Map<String, String> = mapOf(
        "ABS+" to "ABS", "ABS+MATTE" to "ABS", "ABS+GLOSS" to "ABS",
        "ASA-CF" to "ASA", "ASA-GF" to "ASA", "EASYASA" to "ASA",
        "HTPLA+" to "PLA", "PLAX" to "PLA",
        "TPU-85A" to "TPU", "TPU-90A" to "TPU", "TPU-98A" to "TPU",
    )

    // Variantes d'ECRITURE d'un sous-type qui EXISTE pourtant tel quel dans la table Elegoo
    // (juste avec un tiret au lieu d'un espace, ou l'inverse) - correspondance exacte, pas une
    // approximation : "TPU-95A" (vu dans les fichiers JSON) et "TPU 95A" (nom dans la table)
    // designent la meme chose.
    private val aliasOrthographe: Map<String, String> = mapOf(
        "TPU-95A" to "TPU 95A",
    )

    /** @return null si aucune correspondance, meme approximative, n'existe pour ce texte. */
    fun resoudre(materiauJson: String): Resultat? {
        val norm = materiauJson.trim().uppercase()
        index[norm]?.let { (cm, cs) -> return Resultat(cm, cs, approximatif = false) }
        aliasOrthographe[norm]?.let { canonique ->
            index[canonique.uppercase()]?.let { (cm, cs) -> return Resultat(cm, cs, approximatif = false) }
        }
        val famille = aliasApproximatifs[norm] ?: return null
        val (cm, cs) = index[famille] ?: return null
        return Resultat(cm, cs, approximatif = true)
    }
}
