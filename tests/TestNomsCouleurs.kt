import com.tomyn.elegoorfid.NomsCouleurs

/**
 * Test du rapprochement hex -> nom de couleur approche (NomsCouleurs.kt, ajoute le 09/10/2026 a la
 * demande de Tomyn : le code hex seul ne dit rien a un utilisateur lambda).
 *
 *   kotlinc ../app/src/main/java/com/tomyn/elegoorfid/NomsCouleurs.kt TestNomsCouleurs.kt \
 *           -include-runtime -d test.jar
 *   java -jar test.jar
 */
var echecs = 0
fun check(nom: String, ok: Boolean, detail: String = "") {
    println((if (ok) "  OK   " else "  ECHEC") + " $nom" + if (detail.isNotEmpty()) "  [$detail]" else "")
    if (!ok) echecs++
}

fun main() {
    check("noir pur -> Noir", NomsCouleurs.nomApproche("000000") == "Noir")
    check("blanc pur -> Blanc", NomsCouleurs.nomApproche("FFFFFF") == "Blanc")
    check("rouge pur -> Rouge", NomsCouleurs.nomApproche("FF0000") == "Rouge")
    check("vert pur -> Vert ou Vert fluo", NomsCouleurs.nomApproche("00FF00") in listOf("Vert", "Vert fluo"), NomsCouleurs.nomApproche("00FF00") ?: "null")
    check("bleu pur -> Bleu", NomsCouleurs.nomApproche("0000FF") == "Bleu")
    // Bleu Elegoo reel (vraie bobine, voir tests/dumps) : bleu assez sature et clair (composante
    // verte notable) - doit tomber sur une entree "bleu", jamais sur une teinte franchement
    // differente comme "Sarcelle"/"Vert" malgre sa composante verte.
    check(
        "bleu Elegoo reel (#106DD7) -> une entree Bleu",
        NomsCouleurs.nomApproche("106DD7")?.startsWith("Bleu") == true,
        NomsCouleurs.nomApproche("106DD7") ?: "null"
    )
    check("jaune Elegoo reel (#D0C825) -> Jaune ou proche", NomsCouleurs.nomApproche("D0C825")?.contains("Jaune", ignoreCase = true) == true, NomsCouleurs.nomApproche("D0C825") ?: "null")
    check("minuscules acceptees", NomsCouleurs.nomApproche("ff0000") == "Rouge")
    check("avec # accepte", NomsCouleurs.nomApproche("#FF0000") == "Rouge")
    check("hex invalide (court) -> null", NomsCouleurs.nomApproche("FF00") == null)
    check("hex invalide (caracteres) -> null", NomsCouleurs.nomApproche("ZZZZZZ") == null)
    // Bleu fonce : ne doit pas se faire voler par le noir malgre des valeurs absolues faibles
    // (justifie la ponderation par luminosite percue plutot qu'une distance euclidienne brute).
    check("bleu foncé (#00008B) -> pas Noir", NomsCouleurs.nomApproche("00008B") != "Noir", NomsCouleurs.nomApproche("00008B") ?: "null")

    println()
    if (echecs == 0) println("Tous les tests sont passes.") else println("$echecs test(s) en echec.")
    if (echecs > 0) kotlin.system.exitProcess(1)
}
