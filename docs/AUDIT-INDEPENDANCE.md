# Audit d’indépendance

Le nom affiché est **Metrolist**, à la demande du propriétaire. Le projet personnel conserve l’identifiant Android `fr.makoff.music`, le dépôt `anderson76389/Music` et son propre canal de mises à jour. Le logo et les sources du moteur sont intégrés localement. La configuration distante du moteur est désactivée et les anciennes adresses Faraday ont été supprimées.

Les fonctions de salons et de traduction IA ont été retirées avec leurs interfaces, clients, ressources, serveur et protocole. Les mentions légales et noms techniques historiques sont conservés. Les colonnes historiques de traduction dans la base ne servent plus à une fonction active ; elles préservent la compatibilité des données sans changer le schéma.

YouTube/Google, les fournisseurs de paroles, Shazam, AutoEQ officiel, Return YouTube Dislike, GitHub et les dépôts de construction restent externes. L’APK publié inclut le SDK propriétaire Google Cast ; il ne s’agit pas d’un ensemble de dépendances entièrement libres. Les autres variantes restent disponibles dans les sources.

Le contrôle recherche les adresses connues dans les modules de production et vérifie le moteur embarqué, le logo et l’absence des composants supprimés :

```bash
python3 scripts/audit_independence.py
python3 scripts/audit_settings.py
```

Voir le [rapport des fonctions](../VERIFICATION-FONCTIONS.txt) pour les résultats de la dernière révision. Un contrôle statique n’est pas une capture exhaustive du trafic ni une validation de toutes les actions sur tous les téléphones.

La [préversion 41](https://github.com/anderson76389/Music/releases/tag/test-41-9844976) a passé la compilation, les tests unitaires, les tests/APIcheck du moteur et les essais Android : démarrage, nom installé, lecteur hors connexion, six catégories, 26 interrupteurs, trois choix de qualité, journal GitHub et trois exports audio. Le contrôle ciblé des 32 fichiers DEX et de la table de ressources de l’APK publié ne trouve plus les classes et libellés des fonctions supprimées. Voir le [rapport détaillé](../VERIFICATION-FONCTIONS.txt). L’essai YouTube anonyme séparé reste non réussi depuis le réseau de CI.
