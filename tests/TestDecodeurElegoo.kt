import com.tomyn.elegoorfid.DecodeurElegoo

/**
 * Test du decodeur sur l'exemple NUMERIQUE COMPLET fourni par la doc officielle Elegoo
 * (section 4 du guide : PLA-CF, 1.75mm, 1000g, Rouge #FF3700, fevrier 2025).
 *
 * ATTENTION : ceci valide que le decodeur applique correctement la specification telle
 * qu'ecrite, PAS que la specification correspond a un vrai tag physique (aucun dump reel
 * disponible a ce jour). A completer des qu'une vraie bobine est scannee.
 *
 *   kotlinc ../app/src/main/java/com/tomyn/elegoorfid/DecodeurElegoo.kt TestDecodeurElegoo.kt \
 *           -include-runtime -d test.jar
 *   java -jar test.jar
 */
var echecs = 0
fun check(nom: String, ok: Boolean, detail: String = "") {
    println((if (ok) "  OK   " else "  ECHEC") + " $nom" + if (detail.isNotEmpty()) "  [$detail]" else "")
    if (!ok) echecs++
}

fun main() {
    // UID factice (4 pages = 16 octets, peu importe leur valeur) + les 24 octets de l'exemple officiel
    val uidFactice = ByteArray(16)
    val exemple = ("36" + "EEEEEEEE" + "0001" + "504C4120" + "43463230" + "FF3700" + "00AF" + "03E8" + "09C6")
        .let { hex -> ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }
    val dump = uidFactice + exemple

    val info = DecodeurElegoo.decoder(dump)
    println("Decode : $info\n")

    check("en-tete valide (0x36)", info.headerValide == true)
    check("code fabricant = EE:EE:EE:EE", info.codeFabricant == "EE:EE:EE:EE", info.codeFabricant ?: "null")
    check("matiere = PLA", info.matiere == "PLA", info.matiere ?: "null")
    check("sous-type = CF20", info.sousType == "CF20", info.sousType ?: "null")
    check("couleur = FF3700 (rouge)", info.couleurHex == "FF3700", info.couleurHex ?: "null")
    check("diametre = 1.75mm", info.diametreMm == 1.75, info.diametreMm.toString())
    check("poids = 1000g", info.poidsGrammes == 1000, info.poidsGrammes.toString())
    check("annee fabrication = 2025", info.anneeFabrication == 2025, info.anneeFabrication.toString())
    check("mois fabrication = 2 (fevrier)", info.moisFabrication == 2, info.moisFabrication.toString())

    // Dump tronque : ne doit jamais planter (coupe juste apres la matiere, avant le poids)
    val tronque = DecodeurElegoo.decoder(dump.copyOfRange(0, 27))
    check("dump tronque : pas d'erreur, matiere encore lisible", tronque.matiere == "PLA")
    check("dump tronque : poids absent proprement (hors de portee)", tronque.poidsGrammes == null)

    println(if (echecs == 0) "\n=> TOUT PASSE (conforme a la doc officielle - pas encore a un vrai tag)" else "\n=> $echecs ECHEC(S)")
    if (echecs > 0) System.exit(1)
}
