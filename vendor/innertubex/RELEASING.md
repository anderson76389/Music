# Publication de la bibliothèque embarquée

MAKOFF Music compile cette bibliothèque directement depuis ses sources locales. Aucune publication externe n’est nécessaire pour produire les APK.

Si une publication séparée devient nécessaire, utiliser le groupe `fr.makoff.music`, l’artefact `innertubex` et une destination Maven contrôlée par le propriétaire de MAKOFF Music. Ne pas utiliser les comptes ou procédures du projet d’origine.

Avant toute publication, exécuter les contrôles d’API et les tests de la bibliothèque. Conserver les notices et la licence. La publication des APK est gérée à la racine par `.github/workflows/music-release.yml` et requiert la clé privée du projet.
