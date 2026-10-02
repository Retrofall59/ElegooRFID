# ElegooRFID

Appli Android pour lire les tags RFID des bobines de filament Elegoo, et afficher matière,
couleur, poids, diamètre et date de fabrication.

**Projet séparé de BambuRfidReader, PrusaTag et AnycubicRFID** : quatrième technologie NFC du
lot. Comme Anycubic, c'est un NTAG213 en lecture libre, sans chiffrement, mémoire utilisateur
brute (pas de NDEF).

## ⚠️ Statut : décodeur construit depuis la doc officielle, pas encore validé sur un vrai tag

Contrairement aux trois autres projets (tous établis à partir de vrais dumps de bobines),
**Elegoo publie elle-même la structure complète de ses tags** :
https://github.com/elegooofficial/ELEGOO-RFID-Tag-Guide

Le décodeur (`DecodeurElegoo.kt`) a été écrit directement à partir de cette doc, et **testé avec
succès contre l'exemple numérique complet qu'elle fournit** (PLA-CF, 1,75 mm, 1000 g, rouge
`#FF3700`, février 2025) — voir `tests/`. Mais aucune vraie bobine n'a encore été scannée : leur
propre documentation contient des incohérences internes (un tableau d'introduction donne des
exemples ASCII invalides pour le nom de matière, contredits par le tableau d'allocation détaillé
et par l'exemple complet, qui eux sont cohérents entre eux — c'est cette seconde version qui a été
retenue). Une divergence entre la doc et un vrai tag physique reste possible tant que personne ne
l'a confirmé en vrai.

**Dès qu'une vraie bobine est scannée** : comparer le dump exporté (bouton "Exporter le dernier
dump") aux valeurs réelles de l'étiquette, et signaler tout écart.

## Format du tag (EPC-256)

- **Couleur** : RGB888 brut, directement lisible (pas de code à deviner comme chez Anycubic)
- **Matière et sous-type** : texte ASCII brut (ex. "PLA", "CF20" pour un composite carbone)
- **Poids, diamètre, date de fabrication** : champs numériques simples

## Fonctionnalités

Lecture, copier/partager, export du dump brut, historique des scans, rapport de compatibilité,
gestion du NFC désactivé, réglage de la vibration. L'impression d'étiquettes n'a volontairement
pas été ajoutée tant que le décodeur n'est pas confirmé sur un vrai tag — imprimer une étiquette
avec des données potentiellement fausses serait pire que de ne rien afficher.

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
