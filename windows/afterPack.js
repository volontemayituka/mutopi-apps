// Ajoute l'icône MUTOPI et les informations d'auteur dans MUTOPI Pharmacie.exe
// (en JavaScript pur : fonctionne aussi bien sous Windows que sous Linux, sans Wine)
const fs = require('fs');
const path = require('path');

exports.default = async function (ctx) {
  if (ctx.electronPlatformName !== 'win32') return;
  const ResEdit = await import('resedit');
  const { NtExecutable, NtExecutableResource, Resource, Data } = ResEdit;
  const nomExe = ctx.packager.appInfo.productFilename + '.exe';
  const exe = path.join(ctx.appOutDir, nomExe);
  const ico = path.join(ctx.packager.projectDir, 'build', 'icon.ico');
  const version = ctx.packager.appInfo.version;

  const binaire = NtExecutable.from(fs.readFileSync(exe), { ignoreCert: true });
  const res = NtExecutableResource.from(binaire);

  if (fs.existsSync(ico)) {
    const fichierIco = Data.IconFile.from(fs.readFileSync(ico));
    const groupes = Resource.IconGroupEntry.fromEntries(res.entries);
    const id = groupes.length ? groupes[0].id : 1;
    const lang = groupes.length ? groupes[0].lang : 1033;
    Resource.IconGroupEntry.replaceIconsForResource(res.entries, id, lang, fichierIco.icons.map((i) => i.data));
  }

  const infos = Resource.VersionInfo.fromEntries(res.entries);
  const vi = infos.length ? infos[0] : Resource.VersionInfo.createEmpty();
  const [a, b, c] = version.split('.').map((n) => parseInt(n, 10) || 0);
  vi.setFileVersion(a, b, c, 0, 1033);
  vi.setProductVersion(a, b, c, 0, 1033);
  const lang = { lang: 1033, codepage: 1200 };
  vi.setStringValues(lang, {
    CompanyName: 'Vsoft (Volonté Software) - Ing. Volonté MAYITUKA',
    ProductName: 'MUTOPI Pharmacie',
    FileDescription: 'MUTOPI Pharmacie - une solution Vsanté by Vsoft',
    LegalCopyright: '© 2026 Ing. Volonté MAYITUKA. Tous droits réservés.',
    OriginalFilename: nomExe,
    InternalName: 'MUTOPI Pharmacie',
    FileVersion: version,
    ProductVersion: version,
  });
  vi.outputToResourceEntries(res.entries);

  res.outputResource(binaire);
  fs.writeFileSync(exe, Buffer.from(binaire.generate()));
  console.log('  • MUTOPI : icône et informations d\'auteur ajoutées à ' + nomExe);
};
