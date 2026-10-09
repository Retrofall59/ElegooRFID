# Changelog

## v0.34-fix-qr-etiquette (build 34)

Deux demandes de Damdam2959 après avoir testé la v0.33 avec une vraie impression :

**1. Nom de couleur sur l'étiquette imprimée aussi** (`PlancheEtiquettes.dessinerEtiquette`) : la
v0.33 avait ajouté le nom approché partout à l'écran, mais pas sur l'étiquette papier elle-même, où
seul le hex (et la pastille de couleur) restait visible. Ajouté en dessous du titre, même palette
(`NomsCouleurs.kt`).

**2. Le QR de l'étiquette ne scannait pas du tout** avec l'appareil photo - confirmé par Damdam2959
sur une vraie impression. Deux causes réelles identifiées dans `PlancheEtiquettes.dessinerQr`,
cumulatives :
- `EncodeHintType.MARGIN` était forcé à `0`, ce qui supprime la "quiet zone" (marge blanche) que la
  norme QR exige autour du code - sans elle, beaucoup de détecteurs (dont celui des appareils
  photo de téléphone) ne reconnaissent même pas qu'il y a un QR à cet endroit. Retiré : zxing
  applique maintenant sa marge standard.
- Le lien encodé (~330 caractères, dump complet en hexa) force un encodage QR en mode "byte" (le
  moins dense) à cause du `elegoorfid://` en minuscules - obligatoire pour que "Ouvrir avec
  ElegooRFID" continue à être proposé par les autres applis de scan (comparaison sensible à la
  casse côté Android sur le schéma/l'hôte déclarés dans le manifeste, aucune optimisation
  d'encodage possible sans casser ça). Modules du coup très petits (~0.3mm) pour la taille
  imprimée d'avant. QR agrandi de 56pt à 80pt sur l'étiquette pour des modules nettement plus gros
  à densité égale.

Effet de bord traité au passage : avec un QR plus grand, un nom de matière/sous-type un peu long
pouvait passer dessous et le rendre illisible une fois imprimé (aucune troncature avant cette
version). Les lignes de texte de l'étiquette sont maintenant tronquées avec une ellipse si elles
dépasseraient dans la zone du QR (`tronquerPourLargeur`, nouveau stub `Paint.measureText` pour la
vérification locale par compilation).

Vérifié par compilation réelle (zéro erreur) et par les trois suites de tests unitaires existantes
(décodeur, base de données JSON, noms de couleurs - zéro régression, aucune n'exerce le dessin PDF
lui-même). **Toujours pas vérifié avec une vraie impression/scan** (pas d'imprimante disponible
ici) - les deux causes identifiées sont cohérentes avec un QR totalement indétectable, mais à
confirmer par Damdam2959 sur sa prochaine planche imprimée.

## v0.33-nom-couleur (build 33)

Nom de couleur approché affiché à côté du code hex ("Couleur : #106DD7 (Bleu roi)") partout où la
couleur d'une bobine est affichée (lecture NFC, tag créé à la main, consultation via QR, texte
exporté/partagé, détail de l'historique) - demandé par Damdam2959 : le code hex seul ne veut rien
dire pour un utilisateur lambda, qui a besoin d'un nom lisible.

Pas de table officielle à lire (un tag Elegoo ne stocke qu'une couleur RGB888 brute, aucun nom -
voir `DecodeurElegoo.kt`), donc nouveau fichier `NomsCouleurs.kt` : une petite palette de référence
(~45 noms de couleurs usuels pour du filament) et le nom le plus proche par distance euclidienne
simple sur les trois composantes RGB. Le hex exact reste toujours affiché en premier, le nom entre
parenthèses n'est qu'une approximation pour se repérer d'un coup d'œil. Testé sur les couleurs de
base et sur deux couleurs de vraies bobines Elegoo (`tests/TestNomsCouleurs.kt`) - une première
version pondérée par luminosité perçue (habituelle pour comparer des gris) faisait tomber un bleu
assez saturé sur "Sarcelle" à cause du poids quasi nul donné au bleu dans cette pondération ;
revenu à une distance simple, complétée par quelques teintes de bleu supplémentaires ("Bleu azur",
"Bleu roi") pour mieux couvrir les bleus clairs/saturés qu'on trouve réellement sur les bobines.

## v0.32-quantite-creation-tag (build 32)

Nouveau champ "Quantité (tags identiques)" sur l'écran "Créer un tag personnalisé"
(`CreationTagActivity.kt`) - proposée par Claude en discutant du futur filament recyclé (Lyman) que
Damdam2959 pourrait vendre avec de vrais tags Elegoo sur la bobine, puis explicitement demandée par
Damdam2959 ("tu peux le coder maintenant"). Permet de générer d'un coup N copies identiques du tag
en cours de création (même matière, couleur, poids, diamètre, températures) - utile pour tagger un
lot de bobines identiques sans repasser tout le formulaire à chaque fois.

Quantité laissée à 1 (comportement inchangé) ou vide : aucun changement, un seul tag est renvoyé et
affiché directement comme avant. Quantité > 1 : réutilise exactement le même circuit que l'import de
base de données (v0.30) - un champ statique `CreationTagActivity.dumpsGeneres` (liste de
(nom, dump)) lu par `MainActivity.onActivityResult` et transmis directement à
`demarrerLotAvecValides`, le même flux d'écriture guidée bobine par bobine déjà utilisé pour le
clonage par lot. Quantité plafonnée à 500 (garde-fou contre une faute de frappe, pas une limite
technique du format).

## v0.31-fix-libelles-gammes (build 31)

Correction d'un bug remonté par Damdam2959 en testant la v0.30 (capture d'écran à l'appui) : dans
le menu déroulant "Gamme" de l'import de base de données, la plupart des gammes s'affichaient comme
"..." indiscernables les uns des autres. Cause : la grande majorité des fichiers JSON ont un patron
`"name"` qui vaut EXACTEMENT `"{color_name}"` (rien d'autre autour) - une fois la couleur retirée
pour l'aperçu de gamme, il ne restait plus rien à afficher. `FilamentDatabase.nomGammeAffiche`
utilise maintenant la matière en repli (`"{color_name}"` + PLA → `"PLA"`), et l'ajoute entre
parenthèses quand le patron a un vrai nom qui ne la mentionne pas déjà (`"Silk {color_name}"` + PLA
→ `"Silk (PLA)"`, `"DuraPro - {color_name}"` + ABS → `"DuraPro (ABS)"`). Testé en régression sur le
fichier 3DJAKE (celui de la capture d'écran) dans `tests/TestFilamentDatabase.kt`.

## v0.30-import-base-filaments (build 30)

Nouvel écran "Importer depuis une base de données" (`ImportBaseDonneesActivity.kt`, nouveau bouton
sur l'écran principal) - proposée par Damdam2959, qui a trouvé en ligne 36 fichiers JSON de bases
de filaments (un par fabricant : Elegoo, eSun, Overture, Hatchbox, Inland, Extrudr, Fusion...) et
demandé si l'appli pouvait en générer des tags directement, sans tout ressaisir à la main dans
"Créer un tag personnalisé".

Fonctionnement : Damdam2959 choisit un ou plusieurs fichiers JSON (sélecteur multi-fichiers
standard Android - ouvrir son dossier et tout sélectionner d'un coup fonctionne aussi bien que
choisir un seul fichier à la fois) ; les fichiers choisis sont mémorisés
(`GestionnaireParametres.lireSourcesBaseJson`/`ecrireSourcesBaseJson`) pour ne pas avoir à les
reselectionner à chaque lancement, une nouvelle sélection s'ajoutant aux précédentes plutôt que de
les remplacer. Ensuite : fabricant → gamme → couleurs à cocher, génération en lot via le même
circuit que le clonage par lot existant (`MainActivity.demarrerLotAvecValides`).

Trois incompatibilités réelles entre ce format JSON communautaire et le format de tag Elegoo
(une seule couleur RGB, pas de "température plateau", une liste fixe de matières/sous-types), gérées
en grisant l'information plutôt qu'en l'inventant :
- **matière sans équivalent Elegoo** (`MaterialsJsonMapping.kt`) : rapprochement en deux niveaux -
  exact quand le texte JSON correspond pile à un nom de la table (`PA6-CF`, `PCTG`, `PPS-CF`...),
  approximatif quand c'est une variante proche d'une famille connue mais sans sous-type exact
  (`ABS+`, `EASYASA`, `TPU-85A`..., alors ramenée au sous-type générique de sa famille, toujours
  signalé "approx." à l'écran) - le reste (`PCPBT`, `GREENTEC`, `FLAX`, `PEARL`, `BIOFUSION`...)
  n'a aucun équivalent et la gamme entière est grisée plutôt que de deviner.
- **couleur en dégradé** (`"hexes"` + `multi_color_direction`, un tag Elegoo n'a qu'un seul champ
  couleur) : cette couleur précise est grisée individuellement, même dans une gamme par ailleurs
  supportée.
- **température/poids/diamètre absent du fichier** (ex. NTH Grillon, qui n'a aucun champ
  température) : gamme grisée plutôt que d'écrire 0 sur le tag.

Parseur JSON écrit à la main (`MiniJson.kt`) plutôt que d'utiliser `org.json` : cette dernière fait
partie du framework Android (toujours présente sur un vrai téléphone) mais pas du JDK standard,
donc indisponible pour les tests unitaires locaux de ce projet (`kotlinc` hors Android, voir
`tests/TestFilamentDatabase.kt`, qui tourne sur 10 des 36 fichiers réels couvrant chaque cas
particulier : température simple vs plage, hex à 8 caractères avec alpha, dégradé mêlé à des
couleurs normales dans la même gamme, fichier sans aucune température).

## v0.29-scan-qr-camera (build 29)

Scan du QR des étiquettes directement depuis l'appareil photo de l'appli (`ScanQrActivity.kt`,
nouveau bouton "Scanner le QR d'une étiquette") - proposée par Claude, validée par Damdam2959.
Jusqu'ici, le lien `elegoorfid://dump/<hex>` encodé dans le QR des étiquettes (v0.21, voir
`PlancheEtiquettes.lienQrPourDump`) ne pouvait être ouvert que via une appli de scan QR externe (ou
le détecteur intégré à l'appareil photo de certains téléphones). CameraX pour la prévisualisation
et l'analyse d'image, ZXing pour le décodage - ZXing est déjà une dépendance du projet depuis la
v0.18 mais uniquement pour l'**encodage** du QR (`QRCodeWriter`) ; cette version réutilise la même
bibliothèque pour le **décodage** (`MultiFormatReader` + `PlanarYUVLuminanceSource`), aucune
dépendance supplémentaire pour cette partie. Permission caméra demandée à l'exécution au premier
lancement (permission "dangereuse" depuis Android 6, contrairement à NFC/VIBRATE qui sont
accordées automatiquement à l'installation) ; `android.hardware.camera` déclaré non obligatoire
(`required="false"`) pour que l'appli reste installable sans caméra, le NFC restant le moyen
principal.

**Mise en garde cash et franc, plus franche que d'habitude sur celle-ci** : CameraX est une API
nettement plus large et plus complexe que tout ce qui a été stubbé jusqu'ici dans ce projet
(`ProcessCameraProvider`, `ImageAnalysis.Analyzer`, `ImageProxy`, les génériques de
`ListenableFuture`...). Trois vraies erreurs de compilation réelles ont déjà été ratées cette
session par la vérification `kotlinc` contre des stubs écrits à la main
(`ParcelFileDescriptor` en v0.22, `EditText.text`/`Editable` en v0.25, `AlertDialog`
`setMessage`+`setItems` en v0.23) précisément quand le stub et le code réel partageaient la même
hypothèse fausse sur une API Android. Le risque que ça se reproduise ici est plus élevé que pour
les versions précédentes, simplement parce que la surface stubbée d'un coup (une dizaine de
nouvelles classes CameraX/Guava/ZXing) est bien plus grande. **Si la compilation GitHub Actions
échoue sur cette version, commence par regarder `ScanQrActivity.kt` et les imports
`androidx.camera.*`/`com.google.zxing.*` en premier.**

Vérifié par compilation réelle (zéro erreur contre les nouveaux stubs - voir la mise en garde
ci-dessus sur leur fiabilité), par les 23 fichiers XML passés dans un vrai parseur XML (dont le
nouveau `activity_scan_qr.xml`), et par les tests unitaires du décodeur (zéro régression, aucun
rapport avec ce changement). **Non testé en conditions réelles** : absolument tout le
fonctionnement de cet écran (démarrage de la caméra, cadrage, decodage effectif d'un QR, fermeture
propre de la caméra) - c'est la fonctionnalité la moins éprouvée livrée cette session.

## v0.28-limite-historique-partage-rapport (build 28)

Deux améliorations proposées par Claude, validées par Damdam2959 ("1-2") :

**1. Affichage de l'historique limité aux 50 scans les plus récents** (`MainActivity.
afficherHistorique`, `LIMITE_AFFICHAGE_HISTORIQUE`) : jusqu'ici, tout l'historique (filtré ou pas)
partait dans un seul `setMessage()` sans aucune limite - avec un usage intensif, ça finirait tôt
ou tard par un dialogue illisible, voire lourd à afficher. Recommandation faite cash et franc
plutôt qu'attendue : c'est un vrai problème de robustesse, pas un gadget. Au-delà de 50, une note
indique combien de scans plus anciens ne sont pas affichés et renvoie vers "Exporter/Partager"
(v0.26) pour tout récupérer, ou vers les filtres (texte v0.21, date v0.27) pour les retrouver.

**2. Partager directement le rapport de compatibilité** (`MainActivity.
partagerRapportCompatibilite`, appui long sur "Copier le rapport de compatibilité") : jusqu'ici,
seul le copier-coller manuel (`copierRapportCompatibilite`, bouton existant) était possible. Même
principe que les autres appuis longs déjà en place (copier le dump en v0.26, coller un dump en
v0.27) plutôt qu'un bouton supplémentaire.

Vérifié par compilation réelle (zéro erreur, aucun nouveau stub nécessaire), par les 22 fichiers
XML passés dans un vrai parseur XML, et par les tests unitaires du décodeur (zéro régression,
aucun rapport avec ces deux changements). **Non testé en conditions réelles** : le rendu visuel du
dialogue d'historique tronqué (coupure propre, pas de texte coupé en plein milieu d'une ligne), et
le sélecteur de partage pour le rapport de compatibilité.

## v0.27-backup-reglages-coller-dump-filtre-date (build 27)

Trois améliorations proposées par Claude, validées par Damdam2959 ("ok pour les trois") :

**1. Sauvegarde/restauration des réglages** (`GestionnaireParametres.exporterReglages`/
`importerReglages`, écran Paramètres) : export/import d'un petit fichier texte (vibration, son,
grille d'étiquettes) via le sélecteur de fichiers Android, pour ne pas tout reconfigurer à la main
en cas de changement de téléphone ou de réinstallation. Format volontairement simple
(`clé=valeur`, une ligne par réglage) plutôt que du JSON - aucune dépendance supplémentaire, et
relisible à l'œil si besoin. L'import ignore les lignes non reconnues plutôt que de tout rejeter
(tolérant à une sauvegarde partielle ou faite par une version antérieure).

**2. Coller un dump hex depuis le presse-papier** (`MainActivity.collerDumpDepuisPressePapier`,
appui long sur "Importer un dump pour cloner") : symétrique du "copier le dump" de la v0.26.
Pratique si quelqu'un partage un dump en texte sur le forum lesimprimantes3d.fr plutôt qu'en
fichier - évite de devoir l'enregistrer dans un .txt avant de pouvoir l'importer. Même validation
d'en-tête (avertissement si suspect) que l'import fichier - la logique de validation a été
factorisée (`traiterDumpImporte`) entre les deux chemins plutôt que dupliquée.

**3. Filtrer l'historique par plage de dates** (`MainActivity.demanderFiltreDateHistorique`) : en
complément du filtre texte de la v0.21 (matière, couleur, code fabricant...), un filtre "Du ... au
..." (JJ/MM/AAAA, l'un des deux bornes pouvant rester vide) pour retrouver les scans d'une session
de tri précise. Saisie texte plutôt qu'un vrai sélecteur de date Android (`DatePickerDialog`) -
plus simple, cohérent avec le reste de l'appli, et pas de nouveau stub lourd à maintenir pour cette
vérification.

Vérifié par compilation réelle (zéro erreur ; stubs `ClipboardManager.primaryClip`,
`ClipData.Item.text` ajoutés), par les 22 fichiers XML passés dans un vrai parseur XML, et par les
tests unitaires du décodeur (zéro régression). **Non testé en conditions réelles** : le
comportement de l'appui long sur "Importer un dump" sur un vrai `Button` (pas de conflit avec
l'appui simple), et le bon fonctionnement du sélecteur de fichiers pour la sauvegarde des réglages
sur un vrai téléphone.

## v0.26-vibration-partage-historique-copie-dump (build 26)

Trois améliorations proposées par Claude, validées par Damdam2959 ("ok pour les trois") :

**1. Vibration différenciée succès/échec** (`MainActivity.vibrerEchec`) : le son différenciait déjà
succès/échec depuis la 0.21, mais la vibration restait soit identique, soit carrément absente en
cas d'échec (aucune vibration sur un échec de clonage/effacement, par exemple). Nouveau motif en
deux pulsations courtes (`VibrationEffect.createWaveform`, repli `vibrate(pattern, repeat)` pour
Android < 8), ajouté à tous les points d'échec existants (lecture NFC-A incompatible, en-tête
absent, erreur de lecture, clonage interrompu/erreur/relecture incohérente, effacement interrompu/
incohérent/erreur, échec sur un fichier d'un lot). Même réglage que la vibration de succès
(`GestionnaireParametres.lireVibrationFinLecture`) - pas de nouveau paramètre séparé.

**2. Partager l'historique des scans** (`MainActivity.partagerHistoriqueCsv`,
`demanderExportOuPartageHistorique`) : le bouton "Exporter tout" de l'historique ouvrait
seulement le sélecteur de fichiers Android (enregistrer puis aller chercher le fichier pour
l'envoyer) - il est remplacé par "Exporter/Partager", qui propose maintenant aussi l'envoi direct
par mail/Drive/etc., en réutilisant le mécanisme `FileProvider` déjà en place pour le partage du
PDF des étiquettes (v0.24). Nouveau sous-dossier `csv_partages/` déclaré dans
`res/xml/file_paths.xml`, à côté de `pdfs_partages/`.

**3. Copier le dump brut en hexadécimal** (`MainActivity.copierDump`) : un appui long sur le
bouton "Copier" (plutôt qu'un bouton supplémentaire, l'écran principal étant déjà chargé) copie
maintenant le dump brut dans le presse-papier, séparément du résumé lisible que l'appui simple
copie déjà. Pratique pour coller directement un dump sur le forum lesimprimantes3d.fr en cas de
tag mal reconnu, sans passer par "Exporter" puis rouvrir le fichier.

Vérifié par compilation réelle (zéro erreur ; stubs `VibrationEffect.createWaveform`,
`Vibrator.vibrate(LongArray, Int)` et `View.setOnLongClickListener` ajoutés/complétés), par les 22
fichiers XML passés dans un vrai parseur XML, et par les tests unitaires du décodeur (zéro
régression - aucun rapport avec le décodage). **Non testé en conditions réelles** : la sensation
du motif de vibration sur un vrai téléphone, et le bon fonctionnement de l'appui long sur
"Copier" (pas de conflit avec l'appui simple) sur un vrai `Button` Android.

## v0.25-fix-settings-text (build 25)

**Correctif d'un vrai bug remonté par Tomyn en testant la 0.23/0.24** (erreur de compilation
Gradle, build bloqué avant même l'APK) :

```
e: SettingsActivity.kt:35:30 Type mismatch: inferred type is String but Editable! was expected
e: SettingsActivity.kt:36:28 Type mismatch: inferred type is String but Editable! was expected
```

**Root cause, cash et franc** : dans le vrai SDK Android, `EditText` redéclare `getText()` pour
retourner `Editable!` et non `CharSequence` - du coup la propriété Kotlin `text` qu'on récupère
sur un `EditText` est elle aussi typée `Editable!`, et on ne peut pas lui assigner une `String`
brute avec `champ.text = "..."`. Il faut passer par la méthode `setText(CharSequence)`, qui elle
accepte bien n'importe quel `CharSequence` (dont une `String`).

C'est moi qui ai introduit ce bug en v0.23, et c'est entièrement ma faute de raisonnement, pas un
manque de vérification : j'avais **d'abord** écrit correctement `champColonnes.setText(...)`, j'ai
buté sur une erreur de compilation ("accidental override") qui venait en réalité d'un défaut de
mon stub de test (mon faux `EditText` déclarait un `setText` qui entrait en collision avec le
getter/setter auto-généré par la propriété `text` de `TextView`), et au lieu de corriger le stub
je me suis convaincu - à tort - que la "vraie" API ne proposait pas `setText` séparément et que la
syntaxe `.text = ...` était la bonne. Mon outil de vérification (`kotlinc` contre des stubs écrits
à la main) ne pouvait pas détecter l'erreur, puisque stub et code réel partageaient la même
hypothèse fausse sur l'API d'`EditText` - exactement le même type de trou que le bug
`ParcelFileDescriptor` de la 0.22.

**Corrigé** : les deux lignes de `SettingsActivity.kt` utilisent maintenant
`champColonnes.setText(...)` / `champLignes.setText(...)`. Le stub `EditText`/`TextView` a aussi
été corrigé pour que `kotlinc` distingue bien, comme le vrai SDK, la propriété `text` (lecture/
écriture simple, utilisée partout ailleurs dans l'appli pour `txtStatut.text = ...` etc., toujours
valide) de la méthode `setText(CharSequence)` (déclarée séparément, sans collision de signature
JVM cette fois). Un grep sur tout le code a confirmé qu'il n'y avait que ces deux lignes qui
assignaient `.text = ` sur un `EditText` - toutes les autres occurrences de `.text = ` dans le
projet sont sur des `TextView`/`Button` (type `CharSequence` dans le vrai SDK, donc correctes).

Vérifié par compilation réelle du stub corrigé (zéro erreur), par les 22 fichiers XML passés dans
un vrai parseur XML, et par les tests unitaires du décodeur (zéro régression - ce bug n'a aucun
rapport avec le décodage). Aucun changement fonctionnel, juste le correctif.

## v0.24-partage-export-origine (build 24)

Trois améliorations proposées par Claude, validées par Damdam2959 :

**1. Partager le PDF de la planche directement** (`MainActivity.partagerPlanchePdf`, nouvelle
option dans le menu "Planche d'étiquettes") : écrit le PDF dans un sous-dossier dédié du cache,
exposé via `FileProvider` (un `Uri file://` direct est refusé par Android 7+ -
`FileUriExposedException`), puis ouvre le sélecteur de partage standard (mail, Drive, service
d'impression en ligne...) - plus besoin de passer par "Générer le PDF" puis aller le rechercher
dans le gestionnaire de fichiers. Déclaration `<provider>` + `res/xml/file_paths.xml` ajoutés dans
le manifeste, limités au seul sous-dossier concerné.

**2. Export du résultat d'un clonage par lot** (`MainActivity.exporterResultatLot`, bouton
"Exporter le résultat" dans le récap de fin de lot) : CSV avec, pour chaque fichier du lot, le
résultat (Réussi/Échec) et le détail de l'échec le cas échéant (page non confirmée, message
d'erreur) - avant, cette information n'existait qu'à l'écran et disparaissait à la fermeture du
récap.

**3. Distinction visuelle de l'origine d'une bobine sur les étiquettes** (`PlancheEtiquettes.
ORIGINE_SCAN_NFC`/`ORIGINE_CREATION`/`ORIGINE_QR`, `MainActivity.afficherResultats`) : un tag créé
à la main (formulaire) ou simplement consulté via le QR d'une étiquette (sans re-scan NFC) porte
maintenant un petit badge d'avertissement sur l'étiquette imprimée ("⚠ Créé manuellement" / "⚠ Vu
via QR"), pour ne pas le confondre plus tard avec une vraie bobine Elegoo physiquement scannée.
Rien n'apparaît pour un vrai scan NFC (cas largement majoritaire). Les étiquettes déjà
enregistrées par une version antérieure à la v0.24 n'ont pas cette info (champ absent) et
n'affichent donc aucun badge - pas de faux positif rétroactif.

Vérifié par compilation réelle (zéro erreur, nouveaux stubs `FileProvider`/`Intent.EXTRA_STREAM`
etc.), par les 21 (+1 nouveau : `file_paths.xml`) fichiers XML passés dans un vrai parseur XML, et
par les tests unitaires du décodeur (zéro régression). **Non testé en conditions réelles** : que
le sélecteur de partage Android propose bien les applis attendues pour un PDF, et le rendu visuel
du badge d'origine sur une étiquette imprimée.

## v0.23-fix-planche-grille (build 23)

**Correctif d'un vrai bug remonté par Tomyn en testant la 0.22** : le menu "Planche d'étiquettes"
n'affichait plus que le titre, le message et "FERMER" - plus aucune des 3 actions (Imprimer,
Générer le PDF, Vider la planche).

**Cause, cash et franc** : `setMessage()` et `setItems()` se disputent la même zone de contenu sur
un vrai `AlertDialog` Android - impossible de les cumuler. Le message gagnait silencieusement et
toute la liste d'actions disparaissait, sans aucune erreur ni avertissement nulle part (ni à la
compilation, ni à l'exécution). C'est passé inaperçu ici pour la même raison que le bug
`ParcelFileDescriptor` de la 0.22 : mon stub `AlertDialog` acceptait les deux appels sans se
plaindre, donc rien ne pouvait le détecter avant un vrai test sur téléphone - exactement ce que
Tomyn vient de faire. **Corrigé** en déplaçant l'info de pagination dans le titre du dialogue (qui
cohabite sans problème avec `setItems`) plutôt que dans un message séparé. J'ai aussi ajouté une
vérification à l'exécution dans mon propre stub `AlertDialog` (`check()` si les deux sont appelés
ensemble) pour qu'un futur test manuel de ce genre de code la révèle tout de suite plutôt que de
rester invisible.

**Trois améliorations supplémentaires**, proposées par Claude pendant la compilation de la 0.22 et
validées par Damdam2959 :

**1. Écran maintenu allumé pendant une lecture** (`onResume`/`onPause`, `FLAG_KEEP_SCREEN_ON`) :
surtout utile pendant un clonage par lot, où le téléphone pouvait sinon s'éteindre entre deux tags
et obliger à déverrouiller en plein milieu de la manip.

**2. Vérification de l'en-tête avant de cloner un dump importé** (`CODE_IMPORT`/`CODE_IMPORT_LOT`
dans `MainActivity`) : avant, seule la taille du fichier était vérifiée. Un fichier de la bonne
taille mais sans l'en-tête Elegoo (0x36) déclenche maintenant un avertissement ("fichier suspect,
peut-être corrompu ou dans un autre format") avec confirmation requise avant de continuer - pour
l'import simple comme pour le clonage par lot (liste des fichiers concernés dans
l'avertissement).

**3. Grille d'étiquettes réglable** (`GestionnaireParametres.lireColonnesEtiquettes`/
`lireLignesEtiquettes`, nouvelle section dans les Paramètres) : 3×8 par défaut comme avant, mais
modifiable sans recompiler si Tomyn change un jour de planche autocollante. `PlancheEtiquettes`
(génération PDF, impression directe, pagination) et `ImpressionPlanche` prennent maintenant le
`Context` pour lire ce réglage.

Vérifié par compilation réelle (zéro erreur après correction des deux conflits de stub rencontrés
en cours de route - voir ci-dessus), par les 21 fichiers XML (manifeste inchangé, layout
paramètres modifié - toujours zéro erreur), et par les tests unitaires du décodeur (zéro
régression). **Non testé en conditions réelles** : le nouveau menu "Planche" corrigé, et la grille
réglable avec un nombre de colonnes/lignes différent de 3×8.

## v0.22-fix-impression (build 22)

**Correctif d'un vrai bug de compilation signalé par Damdam2959** (build GitHub Actions, v0.20) :

```
e: ImpressionPlanche.kt:29:1 Class 'ImpressionPlanche' is not abstract and does not implement
abstract base class member public abstract fun onWrite(... destination: ParcelFileDescriptor! ...)
e: ImpressionPlanche.kt:54:5 'onWrite' overrides nothing
```

**Cause, cash et franc** : en écrivant `ImpressionPlanche.kt` (v0.20, impression directe), j'ai
utilisé `FileDescriptor` pour le paramètre `destination` de `onWrite`, en me basant sur ma mémoire
de l'API Android plutôt qu'en la vérifiant - le vrai type attendu par `PrintDocumentAdapter` est
`ParcelFileDescriptor`. Ma vérification par `kotlinc` ne l'a pas vu : j'avais écrit le stub
`PrintDocumentAdapter` ET l'implémentation avec la même erreur, donc ils correspondaient entre eux
sans jamais être comparés à la vraie signature Android. C'est une limite structurelle de cette
méthode de vérification (compiler contre des stubs écrits à la main plutôt que le vrai SDK
Android, qui n'est pas installé ici) : elle ne peut pas rattraper une erreur que je commets de
façon cohérente des deux côtés.

**Corrigé** : `onWrite` prend maintenant un `ParcelFileDescriptor`, et le flux d'écriture passe
par `ParcelFileDescriptor.AutoCloseOutputStream` (classe réelle prévue pour ça, qui ferme aussi le
descripteur). Le stub `PrintDocumentAdapter` a été corrigé pour coller à la vraie signature, et
les autres méthodes de `android.print.*` utilisées (`onLayout`, `PrintManager.print`,
`PrintDocumentInfo.Builder`, `PageRange`) ont été recomparées une à une à l'API réelle plutôt que
de refaire confiance à mes propres stubs.

Revérifié par compilation réelle (zéro erreur après correction), par les 21 fichiers XML (aucun
touché cette fois, toujours zéro erreur) et par les tests unitaires du décodeur (zéro régression).
**Point qui reste hors de portée d'ici** : un vrai test d'impression sur une imprimante Wi-Fi,
faute de matériel disponible - la logique PDF elle-même (`PlancheEtiquettes.genererPdf`) est
inchangée et déjà utilisée sans souci par l'export PDF classique.

## v0.21-filtre-qr-son (build 21)

Trois améliorations proposées par Claude, validées par Damdam2959 (3-4-5 de la liste) :

**1. Filtre dans l'historique des scans** (`MainActivity.afficherHistorique`/`demanderFiltreHistorique`) :
le bouton "Filtrer" ouvre un champ de recherche et ne garde que les scans dont un champ (matière,
sous-type, couleur, code fabricant, date) contient le texte saisi. L'historique enregistre
maintenant aussi la matière, le sous-type et le poids (avant : seulement date, code fabricant et
couleur) - sans ça il n'y avait rien de pertinent à filtrer. Les lignes écrites par une version
antérieure (3 champs) restent lisibles, juste sans ces nouveaux champs (`parserLigneHistorique`,
compatibilité par `getOrNull`).

**2. QR des étiquettes → consultation sans NFC** (`PlancheEtiquettes.lienQrPourDump`,
`MainActivity.traiterLienEtiquette`, intent-filter dans `AndroidManifest.xml`) : le QR codait
déjà le dump en hexa depuis la v0.18, mais rien ne consommait ce texte scanné - cette partie de la
fonctionnalité n'avait jamais été câblée jusqu'au bout (signalé "non testé" dans le CHANGELOG
v0.18, en réalité incomplet). Corrigé en encodant un vrai lien `elegoorfid://dump/<hex>` : une
appli de scan QR (ou le détecteur QR intégré à la plupart des appareils photo Android) propose
directement "Ouvrir avec ElegooRFID", qui affiche alors la bobine exactement comme après une
lecture NFC - utile si le tag est abîmé/illisible mais l'étiquette papier existe encore. Le bouton
"Cloner" fonctionne ensuite normalement (le dump est bien chargé), ce qui couvre aussi l'usage
"recloner sans retrouver le fichier d'origine" visé dès la v0.18. Pas d'entrée d'historique ni de
sauvegarde automatique pour une simple consultation (`enregistrerHistorique=false`) - seule une
vraie lecture NFC compte comme un scan.

**3. Son de fin de lecture** (`MainActivity.jouerSonResultat`, nouveau réglage "Son en fin de
lecture" dans les paramètres, activé par défaut) : un bip différent succès/erreur via
`ToneGenerator` (aucun fichier audio à embarquer), pour scanner sans avoir à regarder l'écran à
chaque bobine. Scopé à la lecture NFC elle-même (tag non compatible, en-tête absent, erreur de
lecture, et le succès dans `afficherResultats`) - pas au clonage/effacement, qui ont déjà leurs
propres retours.

Vérifié par compilation réelle (kotlinc, nouveaux stubs `android.net.Uri`/`android.media.*`/
`AlertDialog.setView`/`setItems`/`EditText.hint`), par les 21 fichiers XML passés dans un vrai
parseur XML (voir v0.19 - manifest et layout paramètres modifiés cette fois, toujours zéro
erreur), par un aller-retour hex→URI→hex hors appli confirmant que l'encodage du lien QR ne perd
aucun octet, et par les tests unitaires du décodeur (zéro régression, aucune logique de
décodage/encodage touchée). **Non testé en conditions réelles** : qu'un vrai scanner QR/appareil
photo propose bien "Ouvrir avec ElegooRFID" pour ce lien (dépend du détecteur QR du téléphone, pas
de cette appli), et le bip du `ToneGenerator` sur un vrai haut-parleur.

## v0.20-impression-directe (build 20)

**Impression directe demandée par Damdam2959** : générer le PDF puis devoir aller le chercher
dans une appli séparée pour l'imprimer était jugé trop lourd. Le menu "Planche d'étiquettes"
propose maintenant une option **"Imprimer"** en plus de "Générer le PDF" et "Vider la planche" -
elle ouvre directement la fenêtre d'impression standard d'Android (`PrintManager`/`PrintManager`),
qui liste elle-même toutes les imprimantes Wi-Fi/réseau déjà configurées sur le téléphone (service
du fabricant, ou Mopria/service d'impression par défaut) : aucune config réseau ni modèle
d'imprimante à gérer côté appli, c'est le travail du framework d'impression Android.

Implémentation (`ImpressionPlanche.kt`, nouveau) : un `PrintDocumentAdapter` qui réutilise tel
quel `PlancheEtiquettes.genererPdf()` (même mise en page, même grille 3×8) - `onLayout` annonce le
nombre de pages, `onWrite` génère le PDF à cet instant et l'écrit sur le descripteur fourni par le
système. Le PDF exporté en fichier (bouton existant) et le PDF imprimé partagent donc exactement
le même code de mise en page - un seul endroit à ajuster si Tomyn passe un jour à une planche de
références précises.

Vérifié par compilation réelle (kotlinc, nouveaux stubs `android.print.*`) **et** par un passage
de tous les fichiers `.xml` du projet dans un vrai parseur XML (voir v0.19) - aucun fichier layout
touché cette fois, donc rien de neuf à signaler côté XML. Tests unitaires existants (décodeur) :
zéro régression (aucune logique de décodage/encodage touchée). **Non testé avec une vraie
imprimante Wi-Fi** (aucune disponible ici) - la fenêtre d'impression Android elle-même est un
composant système standard, donc le point à vérifier en pratique est seulement que l'imprimante
de Tomyn apparaît bien dans cette liste (dépend de son propre support réseau/Mopria, pas de cette
appli).

## v0.19-fix-xml-creation-tag (build 19)

**Correctif d'un vrai bug de compilation signalé par Damdam2959** (build GitHub Actions, log
fourni, testé deux fois avec la même erreur) :

```
[Fatal Error] activity_creation_tag.xml:46:76: Element type "TextView" must be followed by
either attribute specifications, ">" or "/>".
> Task :app:packageDebugResources FAILED
```

**Cause** : dans `activity_creation_tag.xml` (ajouté en v0.17), l'attribut `android:text` du
texte d'intro contenait des guillemets échappés à la façon Kotlin - `\"Créer le tag\"` - au lieu
de l'entité XML `&quot;`. Le backslash n'a aucun sens en XML : le parseur termine la valeur de
l'attribut au premier guillemet littéral (juste avant "Créer"), puis essaie d'interpréter la
suite comme une nouvelle balise, d'où l'erreur "Element type must be followed by attribute
specifications" exactement à la position signalée. Corrigé en remplaçant par `&quot;...&quot;`.

**Ce qui n'allait pas dans ma vérification, pour que ça n'arrive plus** : les CHANGELOG v0.17 et
v0.18 annonçaient "vérifié par compilation réelle, zéro erreur" - c'était vrai seulement pour les
fichiers `.kt` (compilés avec `kotlinc` contre des stubs Android). Cette vérification ne passait
jamais les fichiers `.xml` (layouts) dans un vrai parseur XML, donc une erreur de syntaxe XML
pourtant immédiatement fatale à la compilation réelle est passée inaperçue dans deux versions
d'affilée. Les 21 fichiers XML du projet sont maintenant tous validés avec un vrai parseur XML
(`xml.dom.minidom`) en plus de la compilation Kotlin, et c'est désormais systématique avant
d'annoncer une version "vérifiée".

## v0.18-sombre-qr-autosave (build 18)

Trois idées supplémentaires de Claude, validées par Damdam2959 :

**1. Sauvegarde automatique de chaque dump lu** (`MainActivity.sauvegarderDumpAuto`) : chaque
lecture réussie (pas les créations manuelles) écrit maintenant son dump complet dans
`dumps_auto/dump_elegoo_<horodatage>.txt`, sans action requise. Complète le bouton "Exporter le
dernier dump" (qui écrase à chaque fois et exige un clic) - but : ne plus jamais perdre un dump
faute d'avoir pensé à l'exporter avant de scanner autre chose (déjà arrivé avec pascal_lb, où on a
dû lui redemander un export après coup).

**2. QR code de reclonage sur les étiquettes** (`PlancheEtiquettes.kt`, dépendance
`com.google.zxing:core:3.5.3`) : chaque étiquette de la planche (v0.15) porte maintenant un QR
codant le dump complet en hexa - exactement le format accepté par "Importer un dump pour cloner".
Rescanner l'étiquette imprimée permet donc de recloner sans retrouver le fichier d'origine.
**Non testé avec un vrai lecteur QR ni une vraie imprimante** (aucun des deux disponibles ici) : à
56pt pour 320 caractères hexa, la densité peut être limite pour un appareil photo de téléphone
selon la qualité d'impression - voir le commentaire dans `dessinerQr`, à agrandir si ça scanne mal
en pratique.

**3. Mode sombre** (`values-night/colors.xml`, thème basculé vers `Theme.AppCompat.DayNight`) :
suit le réglage système, sans toucher au code ni aux layouts (tout référençait déjà les couleurs
par leur nom). Seul changement de layout nécessaire : le texte des boutons secondaires utilisait la
couleur de marque `elegoo_noir` (fixe, pour les dégradés) au lieu de `texte_principal` (qui doit
varier) - corrigé pour que le texte reste lisible en mode sombre.

Vérifié par compilation réelle (kotlinc + stubs zxing ajoutés) et par les tests unitaires existants
(zéro régression). Non testé visuellement (rendu du mode sombre, lisibilité du QR imprimé) - à
valider par Damdam2959.

## v0.17-creation-lot-prevention (build 17)

Trois améliorations demandées par Damdam2959, une fois l'app jugée "mature" :

**1. Création de tag personnalisé** (`EncodeurElegoo.kt`, `CreationTagActivity.kt`) : nouveau
bouton "Créer un tag personnalisé" - un formulaire (matière, sous-type, couleur, poids, diamètre,
température buse) construit directement un dump Elegoo valide, affiché exactement comme après une
vraie lecture (mêmes boutons Cloner/Exporter/Planche d'étiquettes ensuite). Plus besoin de passer
par l'éditeur externe elegoo-rfid-editor - utile en particulier pour le futur filament recyclé
Lyman de Damdam2959. Les zones inconnues ou non confirmées (date de fabrication, page 0x16) sont
laissées à zéro plutôt que devinées. Vérifié par un test aller-retour encode→decode (PETG-CF,
couleur, poids, diamètre, températures) : tous les champs ressortent identiques après décodage.

**2. Clonage par lot** (`MainActivity.kt`, section CLONAGE PAR LOT) : nouveau bouton "Cloner un lot
de dumps" - sélection multiple de fichiers (.txt/.bin/.hex, même parsing que l'import simple),
puis l'appli guide tag par tag ("Approche le tag vierge pour X (2/5)...") sans ressaisir ni rouvrir
le sélecteur à chaque fois. En cas d'échec sur un fichier : proposition de réessayer, passer au
suivant, ou annuler le lot. Résumé final (réussis/total, liste des échecs).

**3. Avertissement préventif avant effacement** (`MainActivity.effacerTagCible`) : les verrous
(dynamique 0x28, statique 0x02, diagnostics ajoutés en v0.12/v0.13) sont maintenant lus AVANT de
tenter l'effacement plutôt qu'après un échec. Si un verrou non nul est détecté, une confirmation
est demandée avant de continuer. **Avertissement imparfait et assumé comme tel** : le mystère sur
la page 0x03 qui résiste à l'effacement avec des verrous à `00 00` (voir v0.13, retours de
Damdam2959 et pascal_lb) n'est pas résolu - cette fonctionnalité ne détecte que les verrous
effectivement visibles dans ces deux registres, pas cette cause encore inconnue.

Refactorisation interne au passage : le cœur du clonage (écriture + relecture de vérification) est
extrait dans `executerClonage()`, sans effet de bord UI, pour être partagé entre le clonage simple
et le clonage par lot plutôt que dupliqué.

Vérifié par compilation réelle (kotlinc + stubs Android, plusieurs nouveaux stubs ajoutés :
Spinner, AdapterView, ArrayAdapter, EditText, android.R) : zéro erreur. Non testé sur un vrai
téléphone - à valider par Damdam2959, en particulier le scénario de clonage par lot sur plusieurs
tags physiques et le formulaire de création sur un vrai tag vierge.

## v0.16-retrait-semaine (build 16)

**Retrait de l'affichage "Semaine XX"**, ajouté en v0.14. pascal_lb a fourni un 6e échantillon
réel (dump exporté de son propre tag) : page 0x18 = `00 36 C8 00`, **exactement la même valeur**
que sur les 5 échantillons précédents (la bobine de Damdam2959 et les 4 dumps déjà en local).
Six échantillons de bobines/lots a priori différents donnant tous la même valeur, c'est bien plus
cohérent avec une constante fixe ou réservée du format Elegoo qu'avec une vraie date qui varierait
par bobine - afficher "Semaine 36" comme s'il s'agissait d'une info réelle serait trompeur.

- Le champ `semaineFabricationTexte` est retiré de `InfoBobine` et de l'affichage (y compris de la
  planche d'étiquettes, v0.15).
- `dateFabricationTexte` (mois/année) reste inchangé - aucun échantillon réel n'a encore donné de
  mois valide (1-12) de toute façon, donc ce champ n'a jamais rien affiché en pratique jusqu'ici.
- Si un jour un vrai tag montre une valeur différente de `0x0036`, l'hypothèse (semaine ou autre)
  redeviendra testable - voir le commentaire en tête de `DecodeurElegoo.kt`.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.15-planche-etiquettes (build 15)

**Impression d'étiquettes**, demandée par Damdam2959 (même principe que sur son autre appli de
lecture RFID, BambuRfidReader) : après une lecture réussie, un bouton "Ajouter à la planche
d'étiquettes" enregistre l'étiquette de la bobine courante. Un second bouton ("Planche
d'étiquettes (N)") ouvre un résumé (nombre d'étiquettes, nombre de pages) avec deux actions :
générer le PDF (export via "Enregistrer sous", comme le dump), ou vider la planche.

- Grille **générique** 3 colonnes × 8 lignes = 24 étiquettes par page A4 (pas de référence de
  planche autocollante précise fournie pour l'instant - facile à ajuster plus tard dans
  `PlancheEtiquettes.kt` si besoin).
- Chaque étiquette : pastille de couleur, matière + sous-type, poids, diamètre, température buse,
  date/semaine de fabrication si connue.
- Stockage simple en fichier texte (une ligne par étiquette, comme `historique_scans.csv`) -
  persiste entre les lectures et les fermetures de l'appli, jusqu'à "Vider la planche".
- Le PDF est régénéré au moment de l'export (pas gardé en mémoire entre le clic et le retour du
  sélecteur de fichiers) via `android.graphics.pdf.PdfDocument` - aucune dépendance ajoutée.
- Vérifié par compilation réelle (kotlinc + stubs Android, y compris de nouveaux stubs
  Canvas/Paint/RectF/PdfDocument) : zéro erreur. Non testé sur un vrai téléphone (pas de bobine
  Elegoo physique chez Claude) - à valider par Damdam2959.

## v0.14-semaine-fabrication (build 14)

**Date de fabrication revue** : sur la première vraie bobine testée par Damdam2959 (PLA noir),
le champ "mois" décodait en 36 - un mois qui n'existe pas. Hypothèse de Damdam2959, bien plus
logique : un code YYWW (année + numéro de semaine), convention courante dans l'industrie - 36
est un numéro de semaine tout à fait valide, pas un mois. Vérification sur les 4 vrais dumps de
pascal_lb déjà en local (`tests/dumps/`) : les 4 ont exactement la même valeur (`00 36 C8 00`),
malgré 4 couleurs différentes - ne tranche pas (pourrait être le même lot de fabrication, ou une
valeur fixe), donc affiché comme hypothèse non confirmée plutôt que comme fait.

- Nouveau repli : si l'octet ne décode pas un mois valide (1-12) mais un numéro de semaine valide
  (1-53), affiche "Semaine XX (hypothèse non confirmée)" au lieu de ne rien afficher.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.13-verrou-statique (build 13)

**Suite au test de Damdam2959** sur sa propre bobine (PLA noir) : échec d'effacement sur la page
`0x03` (le Capability Container, pas l'en-tête 0x10 comme chez pascal_lb), avec un verrou
dynamique à `00 00` (donc pas de verrou usine comme sur les tags de pascal). Page différente,
cause probablement différente - on ne vérifiait que le verrou dynamique (page 0x28), jamais le
verrou STATIQUE (page 0x02), qui protège justement souvent par défaut la page CC sur un NTAG213 du
commerce.

- Le message d'échec d'effacement affiche maintenant aussi le verrou statique (page 0x02,
  lecture seule, jamais écrite).
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.12-diagnostic-effacement (build 12)

**Suite au retour terrain de pascal_lb (09/10/2026)** : le problème d'effacement incomplet
persiste en v0.6 et v0.7 malgré la vérification d'ACK - "toujours le 0x36 qui pose problème"
(l'octet d'en-tête, page 0x10). Deux changements, sans certitude que ça résout le fond du
problème, mais utiles dans tous les cas :

- **Petit délai (10ms) après chaque ACK d'écriture confirmée**, avant de passer à la page
  suivante. Hypothèse : l'ACK confirme la réception de la commande par le tag, pas forcément la
  fin réelle du cycle d'écriture EEPROM (quelques ms) - enchaîner trop vite pourrait devancer
  cette fin de cycle. Ne coûte presque rien en temps total (36 pages × 10ms ≈ 0,36s).
- **Message d'échec d'effacement bien plus précis** : au lieu de "la relecture ne confirme pas un
  effacement complet", le message liste maintenant exactement quelle(s) page(s) n'ont pas été
  remises à zéro, et affiche la valeur des octets de verrouillage dynamique de la puce (page
  0x28, lecture seule, jamais écrite) - ce champ a déjà des valeurs non standard sur les 4 vrais
  dumps dont on dispose (voir le commentaire en tête de `ClonageElegoo.kt`), donc un verrou usine
  posé par Elegoo sur une page précise reste une hypothèse sérieuse. Si le problème persiste,
  cette info précise (quelle page, quelle valeur de verrou) permettra de trancher avec certitude
  plutôt que de deviner.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.11-date-fabrication (build 11)

**Date de fabrication décodée**, trouvée par Damdam2959 directement dans l'éditeur hexadécimal de
l'éditeur [elegoo-rfid-editor](https://github.com/Savion/elegoo-rfid-editor) (qui annote chaque
page), puis confirmée dans son code source. Page 0x18 : octet `0x60` = année en BCD (ex. `0x25` =
2025), octet `0x61` = mois en BCD (ex. `0x01` = janvier). Pas de jour.

- Nouvelle ligne "Date de fabrication : MM/AAAA".
- Tous les champs documentés par l'éditeur sont désormais confirmés - le bandeau d'avertissement
  de l'écran principal est mis à jour en conséquence.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.10-sous-type-et-temperature (build 10)

**Sous-type affiché séparément + température d'extrusion ajoutée**, à la demande de Damdam2959.

- "Matière" affiche maintenant le nom de la famille (ex. "TPU"), et une nouvelle ligne
  "Sous-type" apparaît en dessous quand il est plus précis (ex. "RAPID TPU 95A") - pas de ligne en
  double quand le sous-type est juste le nom générique de la matière.
- Nouvelle ligne "Température buse : XXX-XXX°C" (min-max), même source que matière/sous-type
  (page 0x15 du tag). **Aucun champ "température plateau" n'existe dans ce format** - vérifié
  dans le code source de l'éditeur, seule la température buse y est présente.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.9-import-bin-hex (build 9)

**Import élargi aux fichiers `.bin` et `.hex` d'éditeurs externes**, à la demande de Damdam2959 :
jusqu'ici "Importer un dump pour cloner" ne relisait que le `.txt` exporté par cette appli. Utile
en particulier pour les tags "maison" qu'il compte créer pour son propre filament recyclé (Lyman)
via [elegoo-rfid-editor](https://github.com/Savion/elegoo-rfid-editor), qui exporte en `.bin` ou
en `.hex` plutôt qu'au format `.txt` de cette appli.

- L'import essaie maintenant trois formats dans l'ordre : notre `.txt` existant, une chaîne
  hexadécimale brute (`.hex`), puis en dernier recours les octets bruts du fichier tels quels
  (`.bin`) — le premier qui correspond est utilisé, sans que l'utilisateur ait à préciser le
  format.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.8-matiere-decodee (build 8)

**Matière et sous-type enfin décodés**, confirmés par Damdam2959 (08/10/2026) : trouvée grâce à
un éditeur open-source de tags Elegoo ([elegoo-rfid-editor](https://github.com/Savion/elegoo-rfid-editor))
qui liste directement la table de correspondance dans son code source. En comparant 6 fichiers
générés par cet outil (PLA, PETG, ABS, ASA, TPU, à couleur identique), les deux champs tombent
exactement aux offsets indiqués par cet éditeur (page 0x12 pour le code matière sur 4 octets,
page 0x13 pour le code sous-type sur 2 octets) — confirmé notamment par le fichier TPU généré par
Damdam2959, dont le sous-type décodé (0x0302 = "RAPID TPU 95A") correspondait exactement au nom de
fichier qu'il avait choisi dans l'éditeur.

- Nouvelle ligne "Matière : ..." affichée dans les résultats (ex. "PETG", "RAPID TPU 95A").
- Table de correspondance complète dans `MaterialsElegoo.kt` : ~15 familles de matière, ~50
  sous-types, directement portée depuis le code source de l'éditeur.
- Reste non confirmé : la date de fabrication.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.7-import-dump (build 7)

**Nouveau bouton "Importer un dump pour cloner"**, idée de Damdam2959 : jusqu'ici le clonage
exigeait d'avoir la bobine source physiquement en main au moment de l'écriture (on scanne la
source, puis on scanne tout de suite le tag vierge). Le fichier exporté par "Exporter le dernier
dump" était seulement un rapport texte lisible, pas réutilisable.

- L'appli sait désormais relire ce même fichier exporté (ou un fichier équivalent) et en extraire
  le dump brut, pour pouvoir cloner plus tard sans la bobine source présente — utile par exemple
  pour scanner une bobine chez quelqu'un d'autre, puis cloner un tag vierge chez soi.
- Aucun nouveau format de fichier : l'export existant fonctionne déjà pour l'import, les deux
  fonctions lisent/écrivent le même format "Page XX : AA BB CC DD".
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur.

## v0.6-ecriture-verifiee (build 6)

**Correctif** suite au retour terrain de pascal_lb (08/10/2026) : effacer un tag déjà cloné par
l'appli affichait "la relecture ne confirme pas un effacement complet", alors que le tag était
bien devenu illisible (en-tête Elegoo absent) et réinscriptible — preuve que l'écriture avait
fonctionné pour la plupart des pages, mais pas toutes.

Cause trouvée : ni l'effacement ni le clonage ne vérifiaient jamais la réponse de la commande
NFC d'écriture (0xA2/WRITE) — ils écrivaient page par page sans regarder si chaque page avait
réellement été acceptée par le tag. Le protocole NFC Forum Type 2 Tag renvoie un ACK (0x0A) en
cas de succès ; une page refusée silencieusement (tag éloigné un instant, par exemple) pouvait
donc passer inaperçue jusqu'à l'échec de la vérification finale, sans message clair sur la page
en cause.

- L'écriture de chaque page (effacement **et** clonage) vérifie maintenant la réponse de la
  commande, avec 3 tentatives avant d'abandonner.
- En cas d'échec, le message indique désormais la page précise non confirmée, et invite à reposer
  le tag bien à plat sans le bouger avant de réessayer, au lieu d'un message générique.
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur. Reste à confirmer sur le
  terrain que ce correctif résout bien le cas remonté par pascal_lb.

## v0.5-clonage-confirme-terrain (build 5)

**Clonage confirmé fonctionnel sur vrai terrain** (pascal_lb, forum, 08/10/2026) : testé avec
succès sur PLA jaune, noir, bleu et blanc, reconnu directement par un Canvas Elegoo comme une
vraie bobine. Le format n'est donc pas spécifique à une référence/couleur en particulier.

**Nouveau bouton "Effacer un tag"**, suite à un retour terrain du même testeur : en réessayant de
cloner sur un tag déjà cloné par l'appli, après un effacement via NFC Tools (outil générique
tiers), l'appli détectait encore "une bobine existante" — l'effacement de NFC Tools s'est révélé
incomplet sur ce cas précis. Plutôt que de dépendre d'un outil externe, l'appli sait maintenant
effacer elle-même :

- Remet à zéro la même plage de pages que le clonage (0x03-0x27, voir `ClonageElegoo.kt`) — jamais
  les pages 0x28+ de configuration de la puce, même choix de sécurité que pour le clonage.
- Disponible à tout moment, sans dépendre d'une lecture préalable (contrairement au clonage, qui a
  besoin d'un modèle source) : utile pour réinitialiser un tag de test ou un ancien clonage avant
  de le recloner proprement.
- Confirmation demandée avant d'effacer (action irréversible), puis relecture automatique de
  vérification (toute la zone doit être retombée à zéro).
- Vérifié par compilation réelle (kotlinc + stubs Android) : zéro erreur. Pas encore testé sur un
  vrai tag physique.

## v0.4-clonage-non-teste-terrain (build 4)

**Nouvelle fonctionnalité : clonage d'une bobine Elegoo sur un tag vierge** (NTAG213/215 du commerce, UID fixe non modifiable). Pas encore testé sur un vrai tag physique — à valider avant toute utilisation sur une bobine qui compte.

- Nouveau bouton "Cloner sur une bobine vierge", qui apparaît après une lecture réussie. Le flux : scanner la bobine source → approcher le tag vierge à écrire → écriture → relecture automatique de vérification.
- **Choix de sécurité important** (voir le commentaire en tête de `ClonageElegoo.kt`) : seules les pages 0x03 à 0x27 sont copiées (Capability Container + zone utilisateur libre, où vivent le code fabricant, la couleur, le poids et le diamètre). Les pages 0x28 à 0x2C (configuration de la puce : dynamic lock bytes, MIRROR/AUTH0, ACCESS, PWD, PACK) ne sont **jamais** écrites, car nos 4 échantillons réels montrent qu'Elegoo y place des octets non standards (hors zone RFUI attendue) dont le rôle exact n'est pas compris avec certitude — les copier sur un tag neuf pourrait le verrouiller de façon irréversible. Si un test réel montre que c'est insuffisant pour qu'un lecteur du commerce accepte le clone, il faudra d'abord comprendre ces pages avant d'envisager de les copier.
- Si le tag approché pour l'écriture contient déjà une bobine Elegoo reconnue, une confirmation est demandée avant d'écraser (et le tag doit être rapproché une seconde fois après confirmation, pour éviter d'écrire sur un objet NFC qui a eu le temps de quitter le champ pendant la popup).
- Un bouton "Annuler le clonage" permet de sortir du mode clonage à tout moment avant l'écriture.
- Vérifiée par compilation réelle (kotlinc + stubs Android complets pour les API NFC/UI utilisées), comme pour les autres parties de l'appli — pas encore vérifiée sur un vrai tag NTAG213/215 vierge.

## v0.3-partiellement-verifie (build 3)

**Décodeur réécrit à partir de 2 vraies bobines** (PLA noir et PLA bleu, dumps fournis par pascal_lb sur le forum). La doc officielle Elegoo s'est révélée fausse sur plusieurs points :

- Les vraies données ne commencent pas à la page 0x04 comme annoncé, mais à la page 0x10 (décalage de 48 octets). Les pages 0x04-0x09 contiennent en réalité un simple lien NDEF standard vers `https://www.elegoo.com`, sans rapport avec la bobine.
- **Confirmé par comparaison des deux dumps** (une seule page diffère entre les deux bobines) : couleur (RGB888 direct), poids, diamètre, code fabricant. Tous exacts.
- **Pas encore localisé** : matière et date de fabrication. Les deux échantillons étant tous les deux du PLA, impossible de distinguer "le bon champ matière" d'un simple bloc constant sans rapport avec elle - il faudrait un dump d'une autre matière (PETG, ABS...) pour trancher par comparaison, comme pour la couleur. Ces champs ont été retirés de l'appli plutôt que d'afficher une valeur devinée et potentiellement fausse.
- Le bandeau d'avertissement reflète maintenant précisément ce qui est confirmé et ce qui ne l'est pas, au lieu d'un simple "pas encore vérifié" général.
- **Mise à jour (même version, pas de changement de code)** : 2 dumps réels supplémentaires (PLA jaune `#D0C825`, PLA blanc `#FFFFFF`), toujours fournis par pascal_lb. Couleur/poids/diamètre confirmés exacts sur 4 bobines au total. La matière reste non localisée : les 4 échantillons disponibles sont tous du PLA, il faudrait une autre matière (PETG, ABS...) pour trancher.

## v0.2-non-verifie (build 2)

**Premier vrai test terrain (pascal_lb, 2 bobines)** : l'en-tête attendu (0x36) n'est pas reconnu — confirme que le décodeur a besoin d'un vrai dump pour être corrigé, pas seulement de la doc officielle.

- Corrige un bug réel trouvé à cette occasion : le rapport de compatibilité affichait à tort "aucun scan effectué" après un scan qui avait bien eu lieu mais dont l'en-tête n'était pas reconnu — exactement la situation où une vraie donnée de diagnostic est nécessaire. Il affiche maintenant le dump brut dans ce cas.
- Le bouton "Exporter le dernier dump" n'était pas concerné par ce bug : il contenait déjà la bonne donnée dès la v0.1.

## v0.1-non-verifie (build 1)

Premier jet, **pas encore testé sur un vrai tag**.

- Décodeur écrit à partir de la documentation officielle Elegoo (format EPC-256), testé avec
  succès contre l'exemple numérique complet fourni par cette doc.
- Un vrai bug d'offset trouvé et corrigé pendant l'écriture (confusion entre numéro de page et
  octet brut — la doc elle-même utilise les deux conventions selon les sections, sans le dire).
- Lecture NFC-A/NTAG213, affichage matière, couleur (pastille directe), poids, diamètre, date de
  fabrication.
- Copier/partager, export du dump brut, historique des scans, rapport de compatibilité, réglage
  de la vibration.
- Volontairement absent : impression d'étiquettes (reportée tant que le décodeur n'est pas
  confirmé sur un vrai tag).
