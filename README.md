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
- **Date de fabrication** : année + mois, encodés en BCD (pas de jour)

## Fonctionnalités

Lecture, copier/partager, export **et import** du dump brut, historique des scans, rapport de
compatibilité, gestion du NFC désactivé, réglage de la vibration. L'impression d'étiquettes n'a
pas encore été ajoutée (tous les champs connus sont désormais confirmés, donc ce n'est plus une
question de données manquantes — juste pas encore demandée/construite).

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
