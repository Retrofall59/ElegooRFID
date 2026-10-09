# Tests — décodage Elegoo

## Lancer

```
cd tests
kotlinc ../app/src/main/java/com/tomyn/elegoorfid/DecodeurElegoo.kt TestDecodeurElegoo.kt \
        -include-runtime -d test.jar
java -jar test.jar
```

## Contenu

- **`TestDecodeurElegoo.kt`** — décode les dumps de `dumps/` et vérifie chaque champ.
- **`dumps/pla_{noir,bleu,jaune,blanc}_pascal_lb.bin`** — 4 **vrais** dumps de bobines Elegoo
  (fournis par pascal_lb sur le forum). Ce sont ces fichiers (et la bobine de Damdam2959, pas
  incluse ici) qui comptent comme "échantillon réel" dans les commentaires de `DecodeurElegoo.kt`
  (ex. pour juger si le champ date/semaine varie réellement entre bobines).
- **`dumps/pla_D3D3D3_editeur_elegoo-rfid-editor.bin`** — fichier généré par l'éditeur externe
  [elegoo-rfid-editor](https://github.com/Savion/elegoo-rfid-editor), **PAS un vrai tag Elegoo**.
  Sert uniquement à vérifier que le décodage d'une date de fabrication valide (mois 1-12)
  fonctionne bien quand le champ est effectivement rempli - cas qu'aucun vrai tag rencontré à ce
  jour n'a présenté (ils ont tous la même valeur `00 36`, un "mois 36" impossible - voir le
  commentaire en tête de `DecodeurElegoo.kt`). Ne jamais le compter comme un échantillon réel.

## Dès qu'une nouvelle vraie bobine est scannée

Exporter son dump (bouton "Exporter le dernier dump" dans l'appli), l'ajouter ici dans `dumps/`,
et ajouter un test qui compare le résultat décodé à ce qui est écrit sur l'étiquette réelle. Si un
champ ne correspond pas, corriger les offsets dans `DecodeurElegoo.kt` à partir du vrai dump.
