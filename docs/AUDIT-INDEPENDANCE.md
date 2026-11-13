# Audit d’indépendance

Le nom affiché par défaut est **Apple Music**. Le dépôt conserve des mentions historiques **Metrolist** pour la provenance et la compatibilité des anciennes APK. Ces noms ne constituent pas en eux-mêmes une dépendance réseau. Le projet personnel conserve l’identifiant Android `fr.makoff.music`, le dépôt `anderson76389/Music` et son propre canal de mises à jour. Le logo et les sources du moteur sont intégrés localement. La configuration distante du moteur est désactivée et les anciennes adresses Faraday ont été supprimées.

Les fonctions de salons et de traduction IA ont été retirées avec leurs interfaces, clients, ressources, serveur et protocole. Les mentions légales et noms techniques historiques sont conservés. Les colonnes historiques de traduction dans la base ne servent plus à une fonction active ; elles préservent la compatibilité des données sans changer le schéma.

YouTube/Google, les fournisseurs de paroles, Shazam, AutoEQ officiel, Return YouTube Dislike, GitHub et les dépôts de construction restent externes. L’APK publié inclut le SDK propriétaire Google Cast ; il ne s’agit pas d’un ensemble de dépendances entièrement libres. Les autres variantes restent disponibles dans les sources.

Le contrôle recherche les adresses connues dans les modules de production et vérifie le moteur embarqué, le logo et l’absence des composants supprimés :

```bash
python3 scripts/audit_independence.py
python3 scripts/audit_settings.py
```

Voir le [rapport des fonctions](../VERIFICATION-FONCTIONS.txt) pour les résultats de la dernière révision. Un contrôle statique n’est pas une capture exhaustive du trafic ni une validation de toutes les actions sur tous les téléphones.

## Résultats récents, limites et traçabilité

- **13 novembre 2026 — révision auditée :** `afc594ccee3e4dae855436193556246635d2a351`.
- [GitHub Actions Music Android](https://github.com/anderson76389/Music/actions/runs/43079427853) : audits des dépendances et réglages, tests unitaires, moteur intégré et compatibilité de son API ; APK FOSS et GMS compilées ; installation/démarrage sur Android 11, 15 et 16. Statut **réussi**.
- [Publication APK signée](https://github.com/anderson76389/Music/actions/runs/43083661028) : **réussie** ; [release v13.7.0.116](https://github.com/anderson76389/Music/releases/tag/v13.7.0.116), avec `SIGNATURE.txt`, `SHA256SUMS.txt` et `BUILD.txt`.
- Nouveaux contrôles permanents : aucun lien de service Metrolist dans les ressources de production, aucun artéfact Metrolist dans les dépendances Gradle, moteur local obligatoire, configuration de repli distant désactivée, absence de sous-module Git et téléchargement des APK uniquement depuis le dépôt personnel.

**Conclusion limitée à l’indépendance vis-à-vis de Metrolist :** aucune dépendance opérationnelle au dépôt ni à l’infrastructure Metrolist n'a été trouvée dans les chemins de production et de publication inspectés. Ce constat ne signifie pas une autonomie totale vis-à-vis de YouTube, de GitHub, des bibliothèques externes ou des fournisseurs tiers. Il ne constitue pas une capture de tout le trafic réseau sur un appareil physique.
