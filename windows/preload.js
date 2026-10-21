const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('mutopiApp', {
  plateforme: 'windows',
  enregistrerServeur: (url) => ipcRenderer.send('serveur:enregistrer', url),
  reessayer: () => ipcRenderer.send('serveur:reessayer'),
  changerServeur: () => ipcRenderer.send('serveur:changer'),
});
