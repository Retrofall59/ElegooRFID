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

    val jaune = DecodeurElegoo.decoder(File("dumps/pla_jaune_pascal_lb.bin").readBytes())
    val blanc = DecodeurElegoo.decoder(File("dumps/pla_blanc_pascal_lb.bin").readBytes())
    check("jaune : couleur #D0C825", jaune.couleurHex == "D0C825", jaune.couleurHex ?: "null")
    check("jaune : poids/diametre corrects", jaune.poidsGrammes == 1000 && jaune.diametreMm == 1.75)
    check("blanc : couleur #FFFFFF", blanc.couleurHex == "FFFFFF", blanc.couleurHex ?: "null")
    check("blanc : poids/diametre corrects", blanc.poidsGrammes == 1000 && blanc.diametreMm == 1.75)

    // Dump tronque : ne doit jamais planter
    val tronque = DecodeurElegoo.decoder(File("dumps/pla_noir_pascal_lb.bin").readBytes().copyOfRange(0, 70))
    check("dump tronque : pas d'erreur, header et fabricant encore lisibles", tronque.headerValide == true && tronque.codeFabricant != null)
    check("dump tronque : couleur absente proprement (hors de portee)", tronque.couleurHex == null)

    // Fichier genere par l'editeur externe elegoo-rfid-editor (PAS un vrai tag Elegoo - a ne
    // jamais compter comme un "echantillon reel" dans les commentaires de DecodeurElegoo.kt, voir
    // la decision du 09/10/2026 sur le champ date/semaine). Utile uniquement pour verifier que le
    // decodage "mois valide" marche bien quand le champ EST rempli - aucun vrai tag vu a ce jour
    // n'a ce cas.
    val editeur = DecodeurElegoo.decoder(File("dumps/pla_D3D3D3_editeur_elegoo-rfid-editor.bin").readBytes())
    check("editeur : matiere PLA", editeur.matiereTexte == "PLA", editeur.matiereTexte ?: "null")
    check("editeur : couleur #D3D3D3", editeur.couleurHex == "D3D3D3", editeur.couleurHex ?: "null")
    check("editeur : date de fabrication 01/2025 (mois valide, cas jamais vu sur un vrai tag)", editeur.dateFabricationTexte == "01/2025", editeur.dateFabricationTexte ?: "null")

    println(if (echecs == 0) "\n=> TOUT PASSE (verifie sur 4 vraies bobines : noir, bleu, jaune, blanc + 1 fichier d'editeur pour la date)" else "\n=> $echecs ECHEC(S)")
    if (echecs > 0) System.exit(1)
}
