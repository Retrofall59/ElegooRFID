import com.tomyn.elegoorfid.DecodeurElegoo
import java.io.File

/**
 * Test du decodeur sur DEUX VRAIS DUMPS (PLA noir et PLA bleu Elegoo, fournis par pascal_lb sur
 * le forum, octobre 2026) : seuls le header, le code fabricant, la couleur, le diametre et le
 * poids sont verifies ici, car ce sont les seuls champs confirmes par comparaison des deux
 * echantillons reels. La matiere/sous-type/date restent a confirmer (necessitent un dump d'une
 * matiere differente).
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
    val noir = DecodeurElegoo.decoder(File("dumps/pla_noir_pascal_lb.bin").readBytes())
    val bleu = DecodeurElegoo.decoder(File("dumps/pla_bleu_pascal_lb.bin").readBytes())

    println("Noir : $noir")
    println("Bleu : $bleu\n")

    check("noir : en-tete valide", noir.headerValide == true)
    check("noir : fabricant EE:EE:EE:EE", noir.codeFabricant == "EE:EE:EE:EE", noir.codeFabricant ?: "null")
    check("noir : couleur #000000", noir.couleurHex == "000000", noir.couleurHex ?: "null")
    check("noir : diametre 1.75mm", noir.diametreMm == 1.75, noir.diametreMm.toString())
    check("noir : poids 1000g", noir.poidsGrammes == 1000, noir.poidsGrammes.toString())

    check("bleu : en-tete valide", bleu.headerValide == true)
    check("bleu : fabricant EE:EE:EE:EE", bleu.codeFabricant == "EE:EE:EE:EE", bleu.codeFabricant ?: "null")
    check("bleu : couleur #106DD7", bleu.couleurHex == "106DD7", bleu.couleurHex ?: "null")
    check("bleu : diametre 1.75mm", bleu.diametreMm == 1.75, bleu.diametreMm.toString())
    check("bleu : poids 1000g", bleu.poidsGrammes == 1000, bleu.poidsGrammes.toString())

    // Dump tronque : ne doit jamais planter
    val tronque = DecodeurElegoo.decoder(File("dumps/pla_noir_pascal_lb.bin").readBytes().copyOfRange(0, 70))
    check("dump tronque : pas d'erreur, header et fabricant encore lisibles", tronque.headerValide == true && tronque.codeFabricant != null)
    check("dump tronque : couleur absente proprement (hors de portee)", tronque.couleurHex == null)

    println(if (echecs == 0) "\n=> TOUT PASSE (verifie sur 2 vraies bobines)" else "\n=> $echecs ECHEC(S)")
    if (echecs > 0) System.exit(1)
}
