# ElegooRFID

Appli Android pour lire les tags RFID des bobines de filament Elegoo, et afficher matière,
couleur, poids, diamètre et date de fabrication.

**Projet séparé de BambuRfidReader, PrusaTag et AnycubicRFID** : quatrième technologie NFC du
lot. Comme Anycubic, c'est un NTAG213 en lecture libre, sans chiffrement, mémoire utilisateur
brute (pas de NDEF).

## ✅ Statut : tous les champs connus sont confirmés

La doc officielle d'Elegoo (EPC-256) s'est révélée fausse sur un point important dès la première
vraie bobine scannée (merci pascal_lb sur le forum lesimprimantes3d.fr) : elle annonce les
données utiles à partir de la page 0x04, alors qu'en réalité cette zone contient un enregistrement
NDEF standard (lien `https://www.elegoo.com`, rien à voir avec la bobine) et les vraies données ne
commencent qu'à la page 0x10. Le décodeur (`DecodeurElegoo.kt`) a donc été entièrement réécrit
depuis de vrais dumps plutôt que depuis la doc.

**Confirmé sur le terrain** : couleur, poids, diamètre, clonage et effacement, tous testés avec
succès sur de vraies bobines Elegoo. **Matière, sous-type, température buse et date de
fabrication** confirmés le 08-09/10/2026 grâce à l'éditeur open-source
[elegoo-rfid-editor](https://github.com/Savion/elegoo-rfid-editor), qui liste directement dans
son code source la table de correspondance et annote chaque page dans son éditeur hexadécimal
(voir `MaterialsElegoo.kt` et `DecodeurElegoo.kt`).

## Format du tag

- **Couleur** : RGB888 brut, directement lisible (pas de code à deviner comme chez Anycubic)
- **Matière et sous-type** : code numérique sur 4 puis 2 octets (pas du texte ASCII malgré ce
  qu'annonçait la doc officielle), voir `MaterialsElegoo.kt` pour la table de correspondance
- **Température d'extrusion (buse)** : min et max, en degrés C. Aucun champ "température plateau"
  dans ce format.
- **Poids, diamètre** : champs numériques simples
- **Date de fabrication** : deux octets en BCD (page 0x18), décodés comme mois (1-12) + année
  quand c'est un mois valide. Sur les 6 vrais échantillons disponibles à ce jour, c'est toujours
  la même valeur impossible comme mois (`00 36`) - plus probablement une constante réservée du
  format qu'une vraie date variable, donc rien n'est affiché pour cette valeur (voir
  `DecodeurElegoo.kt`)

## Fonctionnalités

Lecture (écran maintenu allumé pendant le scan - v0.23), copier/partager, **copier le dump brut en
hexadécimal séparément (appui long sur "Copier") - v0.26**, export **et import** du dump brut
(en-tête vérifié avant clonage, avec avertissement si absent - v0.23, **ou collé directement
depuis le presse-papier par appui long sur "Importer" - v0.27**), historique des scans
**filtrable** (matière, couleur, code fabricant... - v0.21, **ou par plage de dates - v0.27**,
**affichage limité aux 50 plus récents - v0.28**) et **partageable directement par mail/Drive -
v0.26**, rapport de compatibilité **partageable directement (appui long) - v0.28**, gestion du NFC
désactivé, réglage de la vibration **et du son** de fin de lecture (bip **et vibration** différents
succès/erreur - v0.21, vibration d'échec ajoutée en v0.26), **sauvegarde/restauration des réglages
en un fichier - v0.27**, **impression d'étiquettes** (v0.15, grille **réglable** dans les
Paramètres - 3×8 par page A4 par défaut - v0.23, voir `PlancheEtiquettes.kt`), exportable en PDF
**ou imprimable/partageable directement** (fenêtre d'impression Android sur une imprimante Wi-Fi -
v0.20, ou envoi du PDF par mail/Drive/etc. - v0.24, voir `ImpressionPlanche.kt`), avec un QR de
reclonage qui permet aussi de **consulter une bobine sans NFC** en scannant l'étiquette papier
(v0.21, **scanné directement depuis la caméra de l'appli, sans appli externe - v0.29**) et un **badge d'origine** sur les tags créés à la main ou simplement consultés via QR,
pour ne pas les confondre avec une vraie bobine scannée (v0.24), **création de tag personnalisé**
(v0.17, formulaire complet sans passer par un éditeur externe - voir `EncodeurElegoo.kt`),
**clonage par lot** (v0.17, plusieurs dumps → plusieurs tags à la suite, résultat **exportable en
CSV** - v0.24) et **avertissement préventif** si un verrou est détecté avant un effacement (v0.17,
imparfait - voir le CHANGELOG), **sauvegarde automatique** de chaque dump lu et **mode sombre**
(v0.18).

**Importer un dump pour cloner (v0.7, élargi en v0.9)** : jusqu'à présent le clonage exigeait
d'avoir la bobine source physiquement en main au moment de l'écriture. Le bouton "Importer un
dump pour cloner" permet de relire un fichier (sur ce téléphone ou un autre) pour cloner un tag
vierge plus tard, sans la bobine source présente - trois formats acceptés automatiquement : le
`.txt` exporté par cette appli, ou un `.bin`/`.hex` venant d'un éditeur externe comme
[elegoo-rfid-editor](https://github.com/Savion/elegoo-rfid-editor) (pratique pour créer ses
propres tags, par exemple pour du filament recyclé maison).

## Confidentialité

Aucune connexion réseau, permissions NFC et vibration uniquement, toutes les données restent sur
le téléphone.

## Distribution

Comme pour les trois autres projets : zip complet du dépôt à uploader sur GitHub, ce qui déclenche
la compilation automatique de l'APK via GitHub Actions.

## Historique des versions

Voir [CHANGELOG.md](CHANGELOG.md).

## Remerciements

Merci à pascal_lb sur le forum lesimprimantes3d.fr pour avoir trouvé le lien vers la
documentation officielle Elegoo.
