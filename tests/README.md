# Tests — décodage Elegoo

## Lancer

```
cd tests
kotlinc ../app/src/main/java/com/tomyn/elegoorfid/DecodeurElegoo.kt TestDecodeurElegoo.kt \
        -include-runtime -d test.jar
java -jar test.jar
```

## Contenu

- **`TestDecodeurElegoo.kt`** — décode l'exemple numérique complet publié dans la documentation
  officielle Elegoo (PLA-CF, 1,75 mm, 1000 g, rouge `#FF3700`, février 2025) et vérifie chaque
  champ. **Valide que le décodeur applique correctement la spec telle qu'écrite — pas qu'elle
  correspond à un vrai tag physique**, aucun dump réel n'étant encore disponible.

## Dès qu'une vraie bobine est scannée

Exporter son dump (bouton "Exporter le dernier dump" dans l'appli), l'ajouter ici dans un dossier
`dumps/`, et ajouter un test qui compare le résultat décodé à ce qui est écrit sur l'étiquette
réelle. Si tout correspond, retirer l'avertissement "pas encore validé" du README et de l'appli
(bandeau dans `activity_main.xml`) et bumper la version vers 1.0. Si ça ne correspond pas, corriger
les offsets dans `DecodeurElegoo.kt` à partir du vrai dump plutôt que de la doc.
