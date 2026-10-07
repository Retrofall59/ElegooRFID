package com.tomyn.elegoorfid

/**
 * Logique de clonage d'un tag Elegoo (hors acces NFC lui-meme, qui reste dans MainActivity pour
 * rester coherent avec lireTag - voir ecrireEtVerifierClone la-bas).
 *
 * CHOIX DE SECURITE IMPORTANT - lu avant de toucher a ce fichier :
 *
 * Les 4 dumps reels fournis par pascal_lb (PLA blanc/bleu/jaune/noir) font chacun 176 octets,
 * soit 44 pages (0x00 a 0x2B). Confirme par la doc officielle NXP (NTAG213_215_216.pdf) :
 *   - Pages 0x00-0x02 : UID + lock bytes (figes en usine, jamais reinscriptibles sur un tag standard)
 *   - Page  0x03      : Capability Container (E1 10 12 00) - zone libre, sans risque
 *   - Pages 0x04-0x27 : zone utilisateur libre (144 octets) - sans risque, c'est la ou vivent le
 *                       lien NDEF "elegoo.com", le code fabricant, la couleur, le poids, le diametre
 *   - Pages 0x28-0x2C : CONFIGURATION de la puce (dynamic lock bytes, MIRROR/AUTH0, ACCESS, PWD,
 *                       PACK) - PAS de la donnee produit
 *
 * Nos 4 echantillons ont des valeurs NON NULLES et IDENTIQUES entre eux sur les pages 0x28-0x2B,
 * avec des octets a des positions normalement RFUI (reserve, attendu a 0) qui ne le sont pas
 * (0xBD en page 0x28, 0x05 en page 0x2A) - signe qu'Elegoo applique un reglage de verrouillage
 * personnalise en usine, qu'on ne comprend pas avec certitude. Ecrire ces 4 pages sur un tag
 * neuf du commerce pourrait verrouiller des pages de facon irreversible ou changer un
 * comportement de protection de maniere imprevisible selon le modele exact de puce du tag cible.
 *
 * DECISION : le clonage ne touche QUE les pages 0x03 a 0x27 (zone confirmee sans risque par la
 * doc officielle + les 4 echantillons reels). Ca suffit a reproduire toutes les informations
 * produit connues (fabricant, couleur, poids, diametre) - largement assez pour qu'un lecteur qui
 * se contente de lire le contenu de la bobine (sans verifier l'UID ni les 4 dernieres pages, qui
 * sont de toute facon identiques sur tous les tags Elegoo et ne distinguent donc aucune bobine
 * d'une autre) accepte le clone. Si un test reel montre que ce n'est pas suffisant, il faudra
 * d'abord comprendre precisement le role des pages 0x28-0x2B (idealement via la doc officielle
 * EPC-256, ou un test controle avec un tag sacrifiable) avant d'envisager de les copier aussi.
 */
object ClonageElegoo {

    const val PREMIERE_PAGE_DONNEES = 0x03
    const val DERNIERE_PAGE_DONNEES = 0x27

    /** Nombre d'octets du dump source necessaires pour pouvoir cloner (jusqu'a la fin de 0x27 incluse). */
    const val TAILLE_MIN_DUMP_SOURCE = (DERNIERE_PAGE_DONNEES + 1) * 4

    /**
     * Decoupe le dump source en pages pretes a ecrire (numero de page, 4 octets), de
     * PREMIERE_PAGE_DONNEES a DERNIERE_PAGE_DONNEES inclus.
     * @return liste vide si le dump est trop court pour couvrir toute la plage.
     */
    fun pagesAEcrire(dumpSource: ByteArray): List<Pair<Int, ByteArray>> {
        if (dumpSource.size < TAILLE_MIN_DUMP_SOURCE) return emptyList()
        val resultat = mutableListOf<Pair<Int, ByteArray>>()
        for (page in PREMIERE_PAGE_DONNEES..DERNIERE_PAGE_DONNEES) {
            val offset = page * 4
            resultat.add(page to dumpSource.copyOfRange(offset, offset + 4))
        }
        return resultat
    }

    /**
     * Compare la zone clonee (0x03-0x27) d'un dump relu sur le tag cible avec le dump source,
     * pour confirmer que l'ecriture a bien pris. Ignore tout ce qui est avant 0x03 (UID, jamais
     * identique entre deux tags physiques differents - normal) et apres 0x27 (non clone).
     */
    fun zoneCloneeIdentique(dumpSource: ByteArray, dumpCibleRelu: ByteArray): Boolean {
        if (dumpSource.size < TAILLE_MIN_DUMP_SOURCE || dumpCibleRelu.size < TAILLE_MIN_DUMP_SOURCE) return false
        val debut = PREMIERE_PAGE_DONNEES * 4
        val fin = (DERNIERE_PAGE_DONNEES + 1) * 4
        return dumpSource.copyOfRange(debut, fin).contentEquals(dumpCibleRelu.copyOfRange(debut, fin))
    }
}
