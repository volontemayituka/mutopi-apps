# MUTOPI Pharmacie — applications Android et Windows

Une solution **Vsanté by Vsoft** · Conception et développement : **Ing. Volonté MAYITUKA** (+243 994 942 076)

Ces deux applications ouvrent le site MUTOPI (`https://mutopi.rf.gd/`) en plein écran, avec impression des tickets,
téléchargement des sauvegardes et exports, page « À propos » et adresse du serveur modifiable
(pour travailler en réseau local, ex. `http://192.168.1.10/mutopi`).

- `config.json` : nom, version, adresse du serveur, auteur (commun aux deux applications)
- `android/` : application Android (WebView)
- `windows/` : application Windows (Electron)

## Publier une nouvelle version
1. Modifier `version` dans `config.json` et `windows/package.json`.
2. Envoyer les changements sur `main`.
3. GitHub construit l'APK et les EXE, puis les publie dans **Releases**.
