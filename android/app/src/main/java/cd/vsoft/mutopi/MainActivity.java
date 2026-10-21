package cd.vsoft.mutopi;

// MUTOPI Pharmacie — application Android
// Une solution Vsanté by Vsoft · Conception et développement : Ing. Volonté MAYITUKA

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView web;
    private ProgressBar barre;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> choixFichier;
    private ActivityResultLauncher<Intent> lanceurFichier;

    // Script injecté dans chaque page : impression, sauvegarde (formulaire POST) et bouton « i »
    private static final String SCRIPT =
        "(function(){if(window.__mutopi)return;window.__mutopi=1;" +
        "window.print=function(){MutopiAndroid.imprimer(document.title||'MUTOPI');};" +
        "window.close=function(){history.length>1?history.back():null;};" +
        "document.addEventListener('submit',function(e){var f=e.target;" +
        " if(!/sauvegarde\\.php/.test(f.action||location.pathname)||(f.method||'').toLowerCase()!=='post')return;" +
        " e.preventDefault();MutopiAndroid.message('Préparation de la sauvegarde…');" +
        " fetch(f.action||location.href,{method:'POST',body:new FormData(f),credentials:'include'}).then(function(r){" +
        "  var cd=r.headers.get('Content-Disposition')||'';var m=/filename=\"?([^\";]+)/.exec(cd);" +
        "  var nom=m?m[1]:'mutopi-sauvegarde.sql';return r.blob().then(function(b){var fr=new FileReader();" +
        "  fr.onload=function(){MutopiAndroid.enregistrer(nom,String(fr.result).split(',')[1],b.type||'application/sql');};fr.readAsDataURL(b);});" +
        " }).catch(function(){MutopiAndroid.message('Échec de la sauvegarde. Vérifiez la connexion.');});},true);" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mutopi", MODE_PRIVATE);

        FrameLayout racine = new FrameLayout(this);
        web = new WebView(this);
        barre = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barre.setMax(100);
        barre.getProgressDrawable().setTint(0xFF046834);
        racine.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        racine.addView(barre, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8));
        setContentView(racine);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " MutopiAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Pont(), "MutopiAndroid");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
            telecharger(url, userAgent, contentDisposition, mimetype));

        lanceurFichier = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (choixFichier == null) return;
            choixFichier.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.getResultCode(), r.getData()));
            choixFichier = null;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack(); else menu();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else accueil();
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }

    // ---------- Serveur ----------
    private String serveur() {
        return normaliser(prefs.getString("serveur", BuildConfig.SERVEUR));
    }
    private static String normaliser(String u) {
        u = u == null ? "" : u.trim();
        if (u.isEmpty()) u = BuildConfig.SERVEUR;
        if (!u.matches("(?i)^https?://.*")) u = "http://" + u;
        if (!u.endsWith("/")) u += "/";
        return u;
    }
    private void accueil() { web.loadUrl(serveur()); }

    private void changerServeur() {
        EditText champ = new EditText(this);
        champ.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        champ.setText(serveur());
        champ.setSelection(champ.getText().length());
        LinearLayout boite = new LinearLayout(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        boite.setPadding(p, p / 2, p, 0);
        boite.addView(champ, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
            .setTitle("Adresse du serveur")
            .setMessage("En ligne : " + BuildConfig.SERVEUR + "\nEn local : adresse du PC serveur (ex. http://192.168.1.10/mutopi)")
            .setView(boite)
            .setPositiveButton("Enregistrer", (d, w) -> { prefs.edit().putString("serveur", normaliser(champ.getText().toString())).apply(); accueil(); })
            .setNeutralButton("Par défaut", (d, w) -> { prefs.edit().remove("serveur").apply(); accueil(); })
            .setNegativeButton("Annuler", null)
            .show();
    }

    private void aPropos() {
        new AlertDialog.Builder(this)
            .setTitle("À propos")
            .setIcon(R.mipmap.ic_launcher)
            .setMessage("MUTOPI Pharmacie — version " + BuildConfig.VERSION_NAME + "\n\n"
                + "Logiciel de gestion de pharmacie.\n\n"
                + "Conception et développement :\n" + BuildConfig.AUTEUR + "\n"
                + BuildConfig.SOCIETE + "\n\n"
                + "Téléphone / WhatsApp : " + BuildConfig.TELEPHONE + "\n"
                + "E-mail : " + BuildConfig.EMAIL + "\n\n"
                + "Serveur : " + serveur() + "\n\n"
                + "© 2026 " + BuildConfig.AUTEUR + ". Tous droits réservés.")
            .setPositiveButton("Fermer", null)
            .setNeutralButton("WhatsApp", (d, w) -> ouvrirExterne(Uri.parse("https://wa.me/" + BuildConfig.TELEPHONE.replaceAll("[^0-9]", ""))))
            .show();
    }

    private void menu() {
        String[] choix = {"Accueil", "Actualiser", "À propos", "Adresse du serveur", "Quitter"};
        new AlertDialog.Builder(this)
            .setTitle("MUTOPI Pharmacie")
            .setItems(choix, (d, i) -> {
                switch (i) {
                    case 0: accueil(); break;
                    case 1: web.reload(); break;
                    case 2: aPropos(); break;
                    case 3: changerServeur(); break;
                    default: finish();
                }
            })
            .show();
    }

    // ---------- Liens externes ----------
    private boolean estInterne(Uri u) {
        Uri base = Uri.parse(serveur());
        String h = u.getHost();
        return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && h != null && h.equalsIgnoreCase(base.getHost());
    }
    private void ouvrirExterne(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "Aucune application pour ouvrir ce lien.", Toast.LENGTH_SHORT).show(); }
    }

    // ---------- Téléchargements (exports Excel/CSV…) ----------
    private void telecharger(String url, String ua, String cd, String mime) {
        try {
            String nom = URLUtil.guessFileName(url, cd, mime);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            r.addRequestHeader("User-Agent", ua);
            r.setMimeType(mime);
            r.setTitle(nom);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nom);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "Téléchargement : " + nom, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            ouvrirExterne(Uri.parse(url));
        }
    }

    private void enregistrerFichier(String nom, byte[] donnees, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, nom);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("stockage indisponible");
            try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(donnees); }
        } else {
            File dossier = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            try (FileOutputStream o = new FileOutputStream(new File(dossier, nom))) { o.write(donnees); }
        }
    }

    // ---------- Pont JavaScript ----------
    private class Pont {
        @JavascriptInterface public void imprimer(String titre) {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter a = web.createPrintDocumentAdapter("MUTOPI " + titre);
                pm.print("MUTOPI " + titre, a, new PrintAttributes.Builder().build());
            });
        }
        @JavascriptInterface public void enregistrer(String nom, String base64, String mime) {
            try {
                enregistrerFichier(nom, Base64.decode(base64, Base64.DEFAULT), mime);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sauvegarde enregistrée dans Téléchargements : " + nom, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Impossible d'enregistrer la sauvegarde.", Toast.LENGTH_LONG).show());
            }
        }
        @JavascriptInterface public void message(String m) { runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show()); }
        @JavascriptInterface public void reessayer() { runOnUiThread(MainActivity.this::accueil); }
        @JavascriptInterface public void changerServeur() { runOnUiThread(MainActivity.this::changerServeur); }
        @JavascriptInterface public void aPropos() { runOnUiThread(MainActivity.this::aPropos); }
    }

    private class Client extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (estInterne(u)) return false;
            if ("about".equals(u.getScheme()) || "data".equals(u.getScheme())) return false;
            ouvrirExterne(u);
            return true;
        }
        @Override public void onPageStarted(WebView v, String url, Bitmap f) { barre.setVisibility(View.VISIBLE); }
        @Override public void onPageFinished(WebView v, String url) {
            barre.setVisibility(View.GONE);
            v.evaluateJavascript(SCRIPT, null);
            CookieManager.getInstance().flush();
        }
        @Override public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            String html = "<!doctype html><html lang=fr><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#eef7f0;color:#123}"
                + ".b{background:#fff;border-radius:18px;padding:28px 24px;margin:16px;max-width:420px;text-align:center;box-shadow:0 10px 40px #04683422}"
                + "h1{font-size:19px;color:#046834}p{color:#456;line-height:1.5}code{background:#f1f5f3;padding:2px 6px;border-radius:5px;word-break:break-all}"
                + "button{font-size:15px;padding:11px 16px;margin:6px 4px;border-radius:10px;border:1px solid #c9d6cf;background:#fff}"
                + "button.p{background:#046834;color:#fff;border-color:#046834;font-weight:600}</style></head><body><div class=b>"
                + "<h1>Connexion au serveur impossible</h1><p>Vérifiez votre connexion Internet, puis réessayez.</p>"
                + "<p>Serveur : <code>" + serveur().replace("<", "") + "</code></p>"
                + "<button onclick='MutopiAndroid.changerServeur()'>Changer l'adresse</button>"
                + "<button class=p onclick='MutopiAndroid.reessayer()'>Réessayer</button>"
                + "<p><a href='#' onclick='MutopiAndroid.aPropos();return false' style='color:#046834'>À propos</a></p></div></body></html>";
            v.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView v, int p) { barre.setProgress(p); }
        @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (choixFichier != null) choixFichier.onReceiveValue(null);
            choixFichier = cb;
            try { lanceurFichier.launch(params.createIntent()); }
            catch (Exception e) { choixFichier = null; return false; }
            return true;
        }
    }
}
