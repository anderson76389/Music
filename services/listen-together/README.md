# Serveur d’écoute partagée MAKOFF Music

Serveur WebSocket en Go, fourni avec son protocole dans ce dépôt. Les salons permettent aux participants de synchroniser leur écoute. Aucune image Docker ni aucun serveur du projet d’origine n’est requis.

## Déployer

Suivre [INSTALLATION-MUSIC.txt](INSTALLATION-MUSIC.txt). Les fichiers `Dockerfile`, `compose.yml` et `Caddyfile` construisent et exposent votre serveur depuis les sources locales. Un hébergement et un domaine sont nécessaires ; GitHub Pages ne peut pas héberger ce serveur.

Tous les participants doivent configurer la même adresse sécurisée `wss://votre-domaine/ws` dans l’application. Aucun serveur public n’est imposé. Le service n’est pas déployé automatiquement.

## Vérifier les sources

Avec Go et le compilateur Protocol Buffers installés :

```bash
bash scripts/generate_proto.sh
go test ./...
go build -o makoff-listening-server ./cmd/metroserver
```

Le module est `github.com/anderson76389/Music/services/listen-together`. Le nom interne `cmd/metroserver` est conservé pour la compatibilité du code ; il ne contacte pas de dépôt distant. Les règles d’accès par défaut reconnaissent les identifiants MAKOFF Music. Voir `ua_policy.example.json` si une personnalisation est nécessaire.

## Licence et origine

Voir `LICENSE` et `PROVENANCE.txt` pour la licence et les attributions conservées. Les sources et le protocole sont inclus sans sous-module Git distant.
