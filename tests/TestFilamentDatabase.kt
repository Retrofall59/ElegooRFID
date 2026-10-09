import com.tomyn.elegoorfid.FilamentDatabase
import com.tomyn.elegoorfid.MaterialsJsonMapping
import java.io.File

/**
 * Test du parseur JSON (MiniJson/FilamentDatabase) et du rapprochement de matieres
 * (MaterialsJsonMapping) sur un echantillon de 10 vrais fichiers de base de filaments fournis par
 * Tomyn (sur les 36 au total), couvrant les cas particuliers : temperature simple vs plage,
 * couleur multi-ton ("hexes"), hex a 8 caracteres (alpha), filament sans temperature du tout
 * (NTHGrillon), matiere exacte/approximative/non reconnue.
 *
 *   kotlinc ../app/src/main/java/com/tomyn/elegoorfid/MiniJson.kt \
 *           ../app/src/main/java/com/tomyn/elegoorfid/FilamentDatabase.kt \
 *           ../app/src/main/java/com/tomyn/elegoorfid/MaterialsElegoo.kt \
 *           ../app/src/main/java/com/tomyn/elegoorfid/MaterialsJsonMapping.kt \
 *           TestFilamentDatabase.kt -include-runtime -d test.jar
 *   java -jar test.jar
 */
var echecs = 0
fun check(nom: String, ok: Boolean, detail: String = "") {
    println((if (ok) "  OK   " else "  ECHEC") + " $nom" + if (detail.isNotEmpty()) "  [$detail]" else "")
    if (!ok) echecs++
}

fun main() {
    // ----- elegoo.json : fichier "simple", reference -----
    val elegoo = FilamentDatabase.analyser(File("json_samples/elegoo.json").readText())
    check("elegoo : fabricant reconnu", elegoo?.nom == "ELEGOO", elegoo?.nom ?: "null")
    check("elegoo : au moins 1 filament", (elegoo?.filaments?.size ?: 0) > 0)
    val elegooPla = elegoo?.filaments?.find { it.materiauJson == "PLA" }
    check("elegoo : PLA present", elegooPla != null)
    check("elegoo : PLA temp simple -> min=max", elegooPla?.tempMinC != null && elegooPla.tempMinC == elegooPla.tempMaxC)

    // ----- cc3d.json : couleur avec "glow"/"translucent" (pas de hexes), hex standard -----
    val cc3d = FilamentDatabase.analyser(File("json_samples/cc3d.json").readText())
    check("cc3d : fabricant reconnu", cc3d?.nom == "CC3D", cc3d?.nom ?: "null")
    val cc3dPetg = cc3d?.filaments?.find { it.materiauJson == "PETG" }
    check("cc3d : PETG a 2 couleurs", cc3dPetg?.couleurs?.size == 2)
    check(
        "cc3d : couleur translucide non marquee multi-ton (hex simple present)",
        cc3dPetg?.couleurs?.find { it.nom == "Transparent" }?.multiTon == false
    )

    // ----- AmazonBasics.json : hex 8 caracteres (AARRGGBB) a nettoyer -----
    val amazon = FilamentDatabase.analyser(File("json_samples/AmazonBasics.json").readText())
    val amazonPla = amazon?.filaments?.find { it.materiauJson == "PLA" }
    val translucent = amazonPla?.couleurs?.find { it.nom == "Translucent" }
    check("AmazonBasics : hex 8 car. -> 6 car. RGB", translucent?.hexRgb == "FFFFFF", translucent?.hexRgb ?: "null")
    val neonOrange = amazonPla?.couleurs?.find { it.nom == "Neon Orange" }
    check("AmazonBasics : hex 8 car. avec alpha non-FF, alpha quand meme tronque", neonOrange?.hexRgb == "F8A813", neonOrange?.hexRgb ?: "null")

    // ----- overture.json : couleurs multi-ton ("hexes") melangees a des couleurs normales -----
    val overture = FilamentDatabase.analyser(File("json_samples/overture.json").readText())
    val rockPla = overture?.filaments?.find { it.nomTemplate == "Rock PLA {color_name}" }
    check("overture : Rock PLA a 6 couleurs", rockPla?.couleurs?.size == 6)
    check("overture : 'Rock White' (hex simple) pas multi-ton", rockPla?.couleurs?.find { it.nom == "Rock White" }?.multiTon == false)
    check("overture : 'Rock Rainbow' (hexes) marquee multi-ton", rockPla?.couleurs?.find { it.nom == "Rock Rainbow" }?.multiTon == true)
    check("overture : 'Rock Rainbow' sans hex exploitable", rockPla?.couleurs?.find { it.nom == "Rock Rainbow" }?.hexRgb == null)
    // PETG d'overture a une plage de temperature (pas une valeur unique)
    val overturePetg = overture?.filaments?.find { it.materiauJson == "PETG" }
    check("overture : PETG tempMin=230", overturePetg?.tempMinC == 230, overturePetg?.tempMinC.toString())
    check("overture : PETG tempMax=250", overturePetg?.tempMaxC == 250, overturePetg?.tempMaxC.toString())

    // ----- ldo.json : extruder_temp simple ET extruder_temp_range dans le meme fichier -----
    val ldo = FilamentDatabase.analyser(File("json_samples/ldo.json").readText())
    val ldoAbs = ldo?.filaments?.find { it.materiauJson == "ABS" }
    check("ldo : ABS temp simple -> 260/260", ldoAbs?.tempMinC == 260 && ldoAbs.tempMaxC == 260)
    val ldoAsaCf = ldo?.filaments?.find { it.materiauJson == "ASA-CF" }
    check("ldo : ASA-CF plage 230-260", ldoAsaCf?.tempMinC == 230 && ldoAsaCf.tempMaxC == 260)

    // ----- NTHGrillon.json : AUCUN champ temperature -----
    val nth = FilamentDatabase.analyser(File("json_samples/NTHGrillon.json").readText())
    val nthPla = nth?.filaments?.find { it.materiauJson == "PLA" }
    check("NTHGrillon : pas de temperature -> tempMinC null", nthPla?.tempMinC == null)
    check("NTHGrillon : pas de temperature -> tempMaxC null", nthPla?.tempMaxC == null)
    check("NTHGrillon : 24 couleurs quand meme lues", nthPla?.couleurs?.size == 24, nthPla?.couleurs?.size.toString())

    // ----- MaterialsJsonMapping : exact / orthographe / approximatif / non reconnu -----
    fun resoudreCode(m: String) = MaterialsJsonMapping.resoudre(m)

    check("mapping PLA : exact", resoudreCode("PLA")?.let { !it.approximatif } == true)
    check("mapping PLA+ : exact", resoudreCode("PLA+")?.let { !it.approximatif } == true)
    check("mapping PA6-CF : exact", resoudreCode("PA6-CF")?.let { !it.approximatif } == true)
    check("mapping PCTG : exact", resoudreCode("PCTG")?.let { !it.approximatif } == true)
    check("mapping PPS-CF (extrudr.json) : exact", resoudreCode("PPS-CF") != null)

    val tpu95 = resoudreCode("TPU-95A")
    check("mapping TPU-95A (eSun) : reconnu", tpu95 != null)
    check("mapping TPU-95A : pas signale approximatif (meme sous-type, juste l'ecriture)", tpu95?.approximatif == false)

    val absPlus = resoudreCode("ABS+")
    check("mapping ABS+ : reconnu en approximatif (famille ABS)", absPlus != null && absPlus.approximatif)

    check("mapping PCPBT (extrudr.json) : non reconnu", resoudreCode("PCPBT") == null)
    check("mapping GREENTEC (extrudr.json) : non reconnu", resoudreCode("GREENTEC") == null)
    check("mapping FLAX (extrudr.json) : non reconnu", resoudreCode("FLAX") == null)
    check("mapping BIOFUSION (extrudr.json) : non reconnu", resoudreCode("BIOFUSION") == null)
    check("mapping PA12-CF (extrudr.json) : exact", resoudreCode("PA12-CF")?.let { !it.approximatif } == true)

    // ----- nomAffiche / nomGammeAffiche -----
    val filamentTest = FilamentDatabase.Filament("Silk {color_name}", "PLA", 1000, 1.75, 220, 220, emptyList())
    val couleurTest = FilamentDatabase.Couleur("Gold", "D5983E", false)
    check("nomAffiche substitue correctement", FilamentDatabase.nomAffiche(filamentTest, couleurTest) == "Silk Gold")
    check("nomGammeAffiche sans couleur precise", FilamentDatabase.nomGammeAffiche(filamentTest) == "Silk …")

    // ----- fichier non-JSON / sans les bons champs -----
    check("analyser(JSON invalide) -> null", FilamentDatabase.analyser("{ ceci n'est pas du json") == null)
    check("analyser(JSON sans manufacturer) -> null", FilamentDatabase.analyser("{\"filaments\":[]}") == null)

    println()
    if (echecs == 0) println("Tous les tests sont passes.") else println("$echecs test(s) en echec.")
    if (echecs > 0) kotlin.system.exitProcess(1)
}
