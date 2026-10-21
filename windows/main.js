// MUTOPI Pharmacie — application Windows
// Une solution Vsanté by Vsoft · Conception et développement : Ing. Volonté MAYITUKA
const { app, BrowserWindow, Menu, dialog, shell, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');

const CONFIG = require('./config.json');
const fichierReglages = () => path.join(app.getPath('userData'), 'reglages.json');

function lireReglages() {
  try { return JSON.parse(fs.readFileSync(fichierReglages(), 'utf8')); } catch (e) { return {}; }
}
function ecrireReglages(r) {
  try { fs.writeFileSync(fichierReglages(), JSON.stringify(r, null, 2)); } catch (e) {}
}
function normaliser(url) {
  url = String(url || '').trim();
  if (!url) return CONFIG.serveur;
  if (!/^https?:\/\//i.test(url)) url = 'http://' + url;
  if (!url.endsWith('/')) url += '/';
  return url;
}
const serveur = () => normaliser(lireReglages().serveur || CONFIG.serveur);

let fen = null;

function ouvrirAccueil() {
  fen.loadURL(serveur()).catch(() => {});
}

function aPropos() {
  dialog.showMessageBox(fen, {
    type: 'info',
    title: 'À propos',
    message: `${CONFIG.nom} — version ${app.getVersion()}`,
    detail:
      `Logiciel de gestion de pharmacie.\n\n` +
      `Conception et développement : ${CONFIG.auteur}\n` +
      `${CONFIG.societe}\n` +
      `Téléphone / WhatsApp : ${CONFIG.telephone}\n` +
      `E-mail : ${CONFIG.email}\n\n` +
      `Serveur utilisé : ${serveur()}\n\n` +
      `© 2026 ${CONFIG.auteur}. Tous droits réservés.`,
    buttons: ['Fermer'],
  });
}

function changerServeur() {
  const f = new BrowserWindow({
    parent: fen, modal: true, width: 520, height: 300, resizable: false, minimizable: false, maximizable: false,
    title: 'Adresse du serveur', autoHideMenuBar: true, icon: path.join(__dirname, 'icone.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  f.setMenu(null);
  f.loadFile(path.join(__dirname, 'pages', 'serveur.html'), { query: { actuel: serveur(), defaut: CONFIG.serveur } });
}

ipcMain.on('serveur:enregistrer', (e, url) => {
  const r = lireReglages();
  r.serveur = normaliser(url);
  ecrireReglages(r);
  BrowserWindow.fromWebContents(e.sender)?.close();
  ouvrirAccueil();
});
ipcMain.on('serveur:reessayer', () => ouvrirAccueil());
ipcMain.on('serveur:changer', () => changerServeur());

function construireMenu() {
  const modele = [
    {
      label: 'MUTOPI',
      submenu: [
        { label: 'Accueil', accelerator: 'Ctrl+H', click: ouvrirAccueil },
        { label: 'Actualiser', accelerator: 'F5', click: () => fen.webContents.reload() },
        { label: 'Imprimer', accelerator: 'Ctrl+P', click: () => fen.webContents.print() },
        { type: 'separator' },
        { label: 'Adresse du serveur…', click: changerServeur },
        { type: 'separator' },
        { label: 'Quitter', role: 'quit' },
      ],
    },
    {
      label: 'Affichage',
      submenu: [
        { label: 'Précédent', accelerator: 'Alt+Left', click: () => fen.webContents.canGoBack() && fen.webContents.goBack() },
        { label: 'Zoom +', role: 'zoomIn' },
        { label: 'Zoom −', role: 'zoomOut' },
        { label: 'Taille normale', role: 'resetZoom' },
        { label: 'Plein écran', role: 'togglefullscreen' },
      ],
    },
    { label: 'Aide', submenu: [{ label: 'À propos de MUTOPI Pharmacie', click: aPropos }] },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(modele));
}

function creerFenetre() {
  fen = new BrowserWindow({
    width: 1280, height: 800, minWidth: 900, minHeight: 600, show: false,
    title: CONFIG.nom, backgroundColor: '#ffffff', icon: path.join(__dirname, 'icone.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, spellcheck: false },
  });
  fen.once('ready-to-show', () => { fen.maximize(); fen.show(); });

  // Liens externes (WhatsApp, etc.) : ouverts dans le navigateur
  fen.webContents.setWindowOpenHandler(({ url }) => {
    const base = new URL(serveur());
    try {
      if (new URL(url).host === base.host) return { action: 'allow' };
    } catch (e) {}
    shell.openExternal(url);
    return { action: 'deny' };
  });
  fen.webContents.on('will-navigate', (e, url) => {
    let externe = /^(mailto|tel|whatsapp):/i.test(url);
    try { externe = externe || /(^|\.)(wa\.me|whatsapp\.com)$/i.test(new URL(url).hostname); } catch (err) {}
    if (externe) {
      e.preventDefault();
      shell.openExternal(url);
    }
  });

  // Serveur injoignable : page d'aide hors ligne
  fen.webContents.on('did-fail-load', (e, code, desc, url, principal) => {
    if (!principal || code === -3) return;
    fen.loadFile(path.join(__dirname, 'pages', 'horsligne.html'), { query: { serveur: serveur(), erreur: desc } });
  });

  ouvrirAccueil();
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => { if (fen) { if (fen.isMinimized()) fen.restore(); fen.focus(); } });
  app.whenReady().then(() => { construireMenu(); creerFenetre(); });
  app.on('window-all-closed', () => app.quit());
}
