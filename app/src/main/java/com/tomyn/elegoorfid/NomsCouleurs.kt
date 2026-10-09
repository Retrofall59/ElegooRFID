package com.tomyn.elegoorfid

/**
 * Nom de couleur approche en francais a partir d'un hex RGB brut (ajoute le 09/10/2026 a la
 * demande de Tomyn : le code hex seul ("Couleur : #106DD7") ne veut rien dire pour un utilisateur
 * lambda, qui veut un nom lisible ("Bleu"). Un tag Elegoo n'a qu'une seule couleur RGB888 exacte
 * (voir DecodeurElegoo.kt) et pas de nom associe dans le format - il n'y a donc aucune table
 * officielle a lire ici, juste une palette de reference et la couleur nommee la plus proche
 * (distance euclidienne ponderee, approximation perceptuelle usuelle).
 *
 * Volontairement approximatif et affiche entre parentheses a cote du hex exact plutot qu'a sa
 * place ("Couleur : #106DD7 (Bleu)") : le nom aide a se reperer d'un coup d'oeil, le hex reste la
 * valeur exacte et verifiable.
 */
object NomsCouleurs {

    // Palette de reference : noms francais usuels de couleurs de filament, avec leur hex le plus
    // representatif. Pas besoin d'etre exhaustif (pas de table officielle a egaler) - juste assez
    // large pour qu'un filament courant tombe pres d'un nom pertinent plutot que sur un voisin
    // trompeur.
    private data class Entree(val nom: String, val r: Int, val g: Int, val b: Int)

    private val palette: List<Entree> = listOf(
        Entree("Noir", 0x00, 0x00, 0x00),
        Entree("Blanc", 0xFF, 0xFF, 0xFF),
        Entree("Gris clair", 0xD3, 0xD3, 0xD3),
        Entree("Gris", 0x80, 0x80, 0x80),
        Entree("Gris foncé", 0x40, 0x40, 0x40),
        Entree("Rouge", 0xFF, 0x00, 0x00),
        Entree("Rouge bordeaux", 0x6D, 0x07, 0x1A),
        Entree("Rose", 0xFF, 0xC0, 0xCB),
        Entree("Rose vif", 0xFF, 0x14, 0x93),
        Entree("Magenta", 0xFF, 0x00, 0xFF),
        Entree("Violet", 0x80, 0x00, 0x80),
        Entree("Mauve", 0xB7, 0x84, 0xA7),
        Entree("Lavande", 0xE6, 0xE6, 0xFA),
        Entree("Indigo", 0x4B, 0x00, 0x82),
        Entree("Bleu", 0x00, 0x00, 0xFF),
        Entree("Bleu azur", 0x1E, 0x90, 0xFF),
        Entree("Bleu roi", 0x41, 0x69, 0xE1),
        Entree("Bleu marine", 0x00, 0x00, 0x80),
        Entree("Bleu ciel", 0x87, 0xCE, 0xEB),
        Entree("Bleu clair", 0xAD, 0xD8, 0xE6),
        Entree("Cyan", 0x00, 0xFF, 0xFF),
        Entree("Turquoise", 0x40, 0xE0, 0xD0),
        Entree("Sarcelle", 0x00, 0x80, 0x80),
        Entree("Vert", 0x00, 0x80, 0x00),
        Entree("Vert clair", 0x90, 0xEE, 0x90),
        Entree("Vert foncé", 0x00, 0x64, 0x00),
        Entree("Vert menthe", 0x98, 0xFF, 0x98),
        Entree("Vert olive", 0x80, 0x80, 0x00),
        Entree("Vert fluo", 0x00, 0xFF, 0x00),
        Entree("Jaune", 0xFF, 0xFF, 0x00),
        Entree("Jaune citron", 0xFF, 0xF4, 0x4F),
        Entree("Jaune moutarde", 0xD4, 0xAC, 0x0D),
        Entree("Orange", 0xFF, 0xA5, 0x00),
        Entree("Orange foncé", 0xFF, 0x8C, 0x00),
        Entree("Marron", 0x8B, 0x45, 0x13),
        Entree("Chocolat", 0x7B, 0x3F, 0x00),
        Entree("Beige", 0xF5, 0xF5, 0xDC),
        Entree("Crème", 0xFF, 0xFD, 0xD0),
        Entree("Ivoire", 0xFF, 0xFF, 0xF0),
        Entree("Doré", 0xFF, 0xD7, 0x00),
        Entree("Argenté", 0xC0, 0xC0, 0xC0),
        Entree("Bronze", 0xCD, 0x7F, 0x32),
        Entree("Cuivre", 0xB8, 0x73, 0x33),
        Entree("Kaki", 0xC3, 0xB0, 0x91),
        Entree("Corail", 0xFF, 0x7F, 0x50),
        Entree("Saumon", 0xFA, 0x80, 0x72),
        Entree("Pêche", 0xFF, 0xE5, 0xB4),
        Entree("Prune", 0x8E, 0x45, 0x85),
        Entree("Lilas", 0xC8, 0xA2, 0xC8)
    )

    /**
     * @param hex RGB888 sans "#", ex. "106DD7" - format toujours utilise par les dumps Elegoo
     * (voir DecodeurElegoo/EncodeurElegoo).
     * @return le nom le plus proche dans la palette, ou null si le hex n'est pas exploitable
     * (format invalide - ne devrait pas arriver sur un dump reel).
     */
    fun nomApproche(hex: String): String? {
        val nettoye = hex.trim().removePrefix("#")
        if (nettoye.length != 6 || nettoye.any { it !in "0123456789abcdefABCDEF" }) return null
        val r = nettoye.substring(0, 2).toInt(16)
        val g = nettoye.substring(2, 4).toInt(16)
        val b = nettoye.substring(4, 6).toInt(16)

        // Distance euclidienne simple (composantes non ponderees) : une ponderation "luminance
        // percue" (0.3/0.59/0.11) a ete essayee puis abandonnee - elle rend le poids du bleu
        // presque nul et fait par exemple matcher un bleu Elegoo reel (#106DD7) sur "Sarcelle"
        // plutot que "Bleu" (verifie par tests/TestNomsCouleurs.kt). Ici on compare des teintes,
        // pas des luminosites : les trois canaux comptent pareil.
        return palette.minByOrNull { entree ->
            val dr = r - entree.r
            val dg = g - entree.g
            val db = b - entree.b
            dr * dr + dg * dg + db * db
        }?.nom
    }
}
