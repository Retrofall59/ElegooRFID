package com.tomyn.elegoorfid

/**
 * Modele + parseur pour les fichiers JSON de bases de donnees de filaments (ajoute le 09/10/2026
 * a la demande de Tomyn, suite aux 36 fichiers qu'il a fournis - voir ImportBaseDonneesActivity
 * pour l'ecran qui s'en sert).
 *
 * Format communautaire observe sur les 36 fichiers (un par fabricant) :
 *   { "manufacturer": "...", "filaments": [ { "name", "material", "weights", "diameters",
 *     "extruder_temp" OU "extruder_temp_range", "colors": [ { "name", "hex" } OU
 *     { "name", "hexes", "multi_color_direction" } ] } ] }
 *
 * Trois limites du format de tag Elegoo (voir EncodeurElegoo.kt) face a ce JSON, toutes gerees ici
 * en marquant l'information manquante/incompatible plutot qu'en l'inventant :
 *   - une seule couleur RGB par tag -> une entree "hexes" (couleur multi-ton/dégradée) devient une
 *     Couleur avec hexRgb=null, multiTon=true (voir ImportBaseDonneesActivity, qui la grise).
 *   - pas de "temperature plateau" dans le format Elegoo (confirme par DecodeurElegoo.kt) -> le
 *     champ JSON "bed_temp"/"bed_temp_range" n'est pas lu du tout, il n'y a nulle part ou le mettre.
 *   - une seule matiere/sous-type parmi une liste fixe (MaterialsElegoo.kt) -> le rapprochement
 *     avec le champ JSON "material" est delegue a MaterialsJsonMapping.kt, pas fait ici.
 */
object FilamentDatabase {

    data class Couleur(
        val nom: String,
        val hexRgb: String?, // 6 caracteres hexadecimaux (RRGGBB), ou null si non representable
        val multiTon: Boolean
    )

    data class Filament(
        val nomTemplate: String,  // ex. "Silk {color_name}" - voir nomAffiche()
        val materiauJson: String, // valeur brute du champ "material", ex. "PLA+", "PA6-CF"...
        val poidsGrammes: Int?,
        val diametreMm: Double?,
        val tempMinC: Int?,
        val tempMaxC: Int?,
        val couleurs: List<Couleur>
    )

    data class Fabricant(val nom: String, val filaments: List<Filament>)

    /**
     * @return null si le contenu n'est pas du JSON valide, ou pas un objet avec un champ
     * "manufacturer" (chaine) et un champ "filaments" (tableau) - dans ce cas le fichier est
     * simplement ignore par l'ecran d'import plutot que de faire planter tout le chargement.
     */
    fun analyser(contenuJson: String): Fabricant? {
        val racine = try { MiniJson.parser(contenuJson) } catch (e: Exception) { return null }
        val obj = racine as? JsonValeur.Obj ?: return null
        val nomFabricant = obj.texte("manufacturer")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val filamentsJson = obj.tableau("filaments") ?: return null

        val filaments = filamentsJson.mapNotNull { fv -> analyserFilament(fv) }
        return Fabricant(nomFabricant, filaments)
    }

    private fun analyserFilament(fv: JsonValeur): Filament? {
        val f = fv as? JsonValeur.Obj ?: return null
        val nomTemplate = f.texte("name") ?: return null
        val materiau = f.texte("material") ?: return null

        val poids = (f.tableau("weights")?.firstOrNull() as? JsonValeur.Obj)
            ?.nombre("weight")?.let { Math.round(it).toInt() }

        val diametre = (f.tableau("diameters")?.firstOrNull() as? JsonValeur.Nombre)?.valeur

        var tempMin: Int? = null
        var tempMax: Int? = null
        val tempUnique = f.nombre("extruder_temp")
        if (tempUnique != null) {
            tempMin = Math.round(tempUnique).toInt()
            tempMax = tempMin
        } else {
            val plage = f.tableau("extruder_temp_range")
            if (plage != null && plage.size == 2) {
                tempMin = (plage[0] as? JsonValeur.Nombre)?.valeur?.let { Math.round(it).toInt() }
                tempMax = (plage[1] as? JsonValeur.Nombre)?.valeur?.let { Math.round(it).toInt() }
            }
        }

        val couleurs = (f.tableau("colors") ?: emptyList()).mapNotNull { cv -> analyserCouleur(cv) }

        return Filament(nomTemplate, materiau, poids, diametre, tempMin, tempMax, couleurs)
    }

    private fun analyserCouleur(cv: JsonValeur): Couleur? {
        val c = cv as? JsonValeur.Obj ?: return null
        val nom = c.texte("name") ?: return null
        val estMultiTon = c.tableau("hexes") != null
        val hex = if (estMultiTon) null else c.texte("hex")?.let { normaliserHex(it) }
        return Couleur(nom, hex, estMultiTon || hex == null)
    }

    /**
     * Garde les 6 derniers caracteres (RRGGBB) d'un hex a 8 caracteres (AARRGGBB, utilise par
     * certains fichiers pour les couleurs translucides) - le canal alpha n'a pas d'equivalent
     * dans le format de tag Elegoo (une seule couleur RGB888, voir EncodeurElegoo.kt).
     * @return null si la longueur ne correspond a aucun des deux formats connus.
     */
    private fun normaliserHex(hex: String): String? {
        val nettoye = hex.trim().removePrefix("#")
        if (!nettoye.all { it in "0123456789abcdefABCDEF" }) return null
        return when (nettoye.length) {
            6 -> nettoye.uppercase()
            8 -> nettoye.substring(2).uppercase()
            else -> null
        }
    }

    /** Nom affichable d'un filament pour une couleur donnee (substitue "{color_name}" dans le patron). */
    fun nomAffiche(filament: Filament, couleur: Couleur): String =
        filament.nomTemplate.replace("{color_name}", couleur.nom).trim()

    /**
     * Nom de gamme affichable dans le menu deroulant, sans couleur precise. Corrige le 09/10/2026
     * (bug remonte par Damdam2959, capture d'ecran a l'appui) : la grande majorite des fichiers
     * JSON ont un patron "name" qui est EXACTEMENT "{color_name}" (rien d'autre) - le remplacer
     * par "…" donnait un menu deroulant rempli de "..." indiscernables les uns des autres. On
     * utilise maintenant la matiere comme repli, et on l'ajoute entre parentheses chaque fois que
     * le patron ne la mentionne pas deja (ex. "Silk {color_name}" + materiau "PLA" -> "Silk
     * (PLA)", "{color_name}" + "PLA" -> "PLA", "DuraPro - {color_name}" + "ABS" -> "DuraPro
     * (ABS)").
     */
    fun nomGammeAffiche(filament: Filament): String {
        val motif = filament.nomTemplate
            .replace("{color_name}", "")
            .trim(' ', '-', '|', '/')
            .replace(Regex("\\s+"), " ")
        return when {
            motif.isEmpty() -> filament.materiauJson
            motif.contains(filament.materiauJson, ignoreCase = true) -> motif
            else -> "$motif (${filament.materiauJson})"
        }
    }
}
