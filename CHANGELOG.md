# Changelog

## v0.3-partiellement-verifie (build 3)

**Décodeur réécrit à partir de 2 vraies bobines** (PLA noir et PLA bleu, dumps fournis par pascal_lb sur le forum). La doc officielle Elegoo s'est révélée fausse sur plusieurs points :

- Les vraies données ne commencent pas à la page 0x04 comme annoncé, mais à la page 0x10 (décalage de 48 octets). Les pages 0x04-0x09 contiennent en réalité un simple lien NDEF standard vers `https://www.elegoo.com`, sans rapport avec la bobine.
- **Confirmé par comparaison des deux dumps** (une seule page diffère entre les deux bobines) : couleur (RGB888 direct), poids, diamètre, code fabricant. Tous exacts.
- **Pas encore localisé** : matière et date de fabrication. Les deux échantillons étant tous les deux du PLA, impossible de distinguer "le bon champ matière" d'un simple bloc constant sans rapport avec elle - il faudrait un dump d'une autre matière (PETG, ABS...) pour trancher par comparaison, comme pour la couleur. Ces champs ont été retirés de l'appli plutôt que d'afficher une valeur devinée et potentiellement fausse.
- Le bandeau d'avertissement reflète maintenant précisément ce qui est confirmé et ce qui ne l'est pas, au lieu d'un simple "pas encore vérifié" général.

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
