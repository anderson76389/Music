# InnerTubeX embarqué dans MAKOFF Music

Moteur Kotlin Multiplatform pour les API InnerTube, la résolution locale EJS et les flux SABR/UMP. Cette copie est intégrée aux sources de [MAKOFF Music](https://github.com/anderson76389/Music), qui peut la maintenir indépendamment.

Coordonnée locale : `fr.makoff.music:innertubex`. Le projet Android la remplace par ce dossier au moyen de `includeBuild` dans son `settings.gradle.kts`. Il n’est pas nécessaire de publier cette bibliothèque sur un service Maven ou sur JitPack pour construire l’application.

La configuration distante du lecteur est désactivée par l’hôte Android. Les anciennes adresses Faraday et leurs replis imposés ont été supprimés des classes de compatibilité et des tests. Les règles restantes ciblent uniquement le dépôt Music ; aucune configuration distante n’est activée ni présentée comme disponible. Voir le rapport d’indépendance à la racine du dépôt.

## Vérification

Depuis la racine de MAKOFF Music :

```bash
./gradlew :innertubex:apiCheck
```

Les interfaces publiques, les packages internes, les licences et les crédits d’origine sont conservés. Les changements de nom du groupe Maven ne changent pas les packages Kotlin ni leur contrat d’API. Voir `AGENTS.md`, `LICENSE` et le fichier `PROVENANCE.txt` à la racine.
