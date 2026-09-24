package com.cinedeck.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityAuto extends Activity {
    private static final int BG = Color.rgb(7, 10, 16);
    private static final int PANEL = Color.rgb(19, 25, 35);
    private static final int PANEL2 = Color.rgb(27, 35, 49);
    private static final int FOCUS = Color.rgb(46, 91, 147);
    private static final int PRIMARY = Color.rgb(74, 156, 255);
    private static final int MUTED = Color.rgb(158, 171, 191);

    private static final String FEED = "https://cdn.jsdelivr.net/gh/nick3dddsss-ctrl/ai-architect-facade@cinedeck-tv-build/catalog-data/autoplay.json?v=1";
    private static final String FEED_RAW = "https://raw.githubusercontent.com/nick3dddsss-ctrl/ai-architect-facade/cinedeck-tv-build/catalog-data/autoplay.json?v=1";

    private final ExecutorService io = Executors.newFixedThreadPool(5);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Item> cache = new ArrayList<>();

    private LinearLayout root;
    private Runnable backAction;
    private String selectedSource = "";
    private String lastError = "";

    private ExoPlayer player;
    private WebView webView;
    private FrameLayout webHost;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private Item currentWebItem;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showHome();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        destroyWeb();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (webView != null) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else if (currentWebItem != null) {
                Item i = currentWebItem;
                destroyWeb();
                showDetails(i);
            } else {
                destroyWeb();
                showHome();
            }
            return;
        }
        if (backAction != null) {
            Runnable r = backAction;
            backAction = null;
            r.run();
            return;
        }
        super.onBackPressed();
    }

    private void base() {
        releasePlayer();
        destroyWeb();
        backAction = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(12), dp(28), dp(18));
        root.setBackgroundColor(BG);
        setContentView(root);
        topBar();
    }

    private void topBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = txt("▶ CineDeck RU 1.3 Auto", 24, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(0, dp(58), 1));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("Диагностика", v -> showDiag()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
    }

    private void showHome() {
        base();
        LinearLayout hero = panel();
        hero.addView(txt("БЕЗ НАСТРОЙКИ ИСТОЧНИКОВ", 13, PRIMARY, true));
        hero.addView(txt("Выбрал фильм → нажал «Смотреть сейчас»", 30, Color.WHITE, true));
        hero.addView(txt("На главной показываются только позиции, для которых опубликован официальный бесплатный онлайн-просмотр. Каталог и обложки приходят из интернета.", 15, Color.LTGRAY, false));
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        ScrollView sc = new ScrollView(this);
        LinearLayout content = col();
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ProgressBar p = new ProgressBar(this);
        content.addView(p, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView loading = txt("Получаю фильмы с официальным просмотром…", 16, MUTED, false);
        content.addView(loading);

        io.execute(() -> {
            List<Item> items = loadFeed();
            main.post(() -> {
                content.removeView(p);
                content.removeView(loading);
                if (items.isEmpty()) {
                    content.addView(txt("Не удалось получить список фильмов.", 19, Color.rgb(255, 120, 120), true));
                    content.addView(txt("Открой «Диагностика». Если feed ещё формируется после обновления приложения, он появится автоматически без новой установки APK.", 15, MUTED, false));
                    return;
                }
                content.addView(txt("Смотреть сразу • " + items.size() + " фильмов • " + selectedSource, 14, MUTED, false));
                content.addView(space(8));
                content.addView(rail(items));
            });
        });
    }

    private void showSearch() {
        base();
        backAction = this::showHome;
        root.addView(txt("Поиск", 30, Color.WHITE, true));
        root.addView(txt("Поиск только среди фильмов с готовым официальным просмотром.", 14, MUTED, false));
        root.addView(space(8));

        LinearLayout line = row();
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint("Название фильма");
        q.setTextColor(Color.WHITE);
        q.setHintTextColor(MUTED);
        q.setTextSize(18);
        q.setBackgroundColor(PANEL);
        q.setPadding(dp(14), 0, dp(14), 0);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        line.addView(q, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button go = btn("Найти", null);
        line.addView(go, new LinearLayout.LayoutParams(dp(150), dp(56)));
        root.addView(line);

        ScrollView sc = new ScrollView(this);
        LinearLayout results = col();
        sc.addView(results);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable search = () -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty()) return;
            results.removeAllViews();
            results.addView(txt("Ищу…", 15, MUTED, false));
            io.execute(() -> {
                List<Item> all = loadFeed();
                List<Item> found = new ArrayList<>();
                String needle = norm(query);
                for (Item i : all) {
                    if (norm(i.name).contains(needle)) found.add(i);
                    if (found.size() >= 80) break;
                }
                main.post(() -> {
                    results.removeAllViews();
                    if (found.isEmpty()) results.addView(txt("Ничего не найдено.", 17, MUTED, false));
                    else for (Item i : found) results.addView(searchRow(i));
                });
            });
        };
        go.setOnClickListener(v -> search.run());
        q.setOnEditorActionListener((v, actionId, event) -> { search.run(); return true; });
        q.requestFocus();
    }

    private void showDetails(Item i) {
        base();
        backAction = this::showHome;
        LinearLayout row = row();
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(PANEL2);
        row.addView(image, new LinearLayout.LayoutParams(dp(220), dp(310)));
        loadImage(image, i);

        LinearLayout info = col();
        info.setPadding(dp(22), 0, 0, 0);
        info.addView(txt(i.name, 31, Color.WHITE, true));
        String meta = i.year;
        if (!i.rating.isEmpty()) meta += (meta.isEmpty() ? "" : " • ") + "★ " + i.rating;
        if (!i.genres.isEmpty()) meta += (meta.isEmpty() ? "" : " • ") + i.genres;
        info.addView(txt(meta, 15, MUTED, false));
        info.addView(space(8));
        TextView desc = txt(i.desc, 16, Color.LTGRAY, false);
        desc.setMaxLines(9);
        info.addView(desc);
        info.addView(space(14));
        Button watch = btn("▶  Смотреть сейчас", v -> playItem(i));
        watch.setTextSize(18);
        info.addView(watch, new LinearLayout.LayoutParams(dp(270), dp(62)));
        info.addView(space(8));
        info.addView(txt("Источник: " + (i.provider.isEmpty() ? "официальный" : i.provider), 13, MUTED, false));

        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void playItem(Item i) {
        if (!i.streamUrl.isEmpty()) {
            playDirect(i);
        } else if (!i.pageUrl.isEmpty()) {
            playOfficialWeb(i);
        } else {
            Toast.makeText(this, "У фильма временно нет рабочего официального URL", Toast.LENGTH_LONG).show();
        }
    }

    private void playDirect(Item i) {
        base();
        backAction = () -> showDetails(i);
        root.addView(txt(i.name, 22, Color.WHITE, true));
        PlayerView pv = new PlayerView(this);
        pv.setUseController(true);
        pv.setBackgroundColor(Color.BLACK);
        root.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        player = new ExoPlayer.Builder(this).build();
        pv.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(i.streamUrl));
        player.addListener(new com.google.android.exoplayer2.Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                Toast.makeText(MainActivityAuto.this, "Официальный поток сейчас недоступен", Toast.LENGTH_LONG).show();
            }
        });
        player.prepare();
        player.play();
    }

    private void playOfficialWeb(Item i) {
        releasePlayer();
        destroyWeb();
        currentWebItem = i;

        webHost = new FrameLayout(this);
        webHost.setBackgroundColor(Color.BLACK);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);

        LinearLayout bar = row();
        bar.setPadding(dp(14), dp(6), dp(14), dp(6));
        TextView title = txt("▶ " + i.name + "  •  " + i.provider, 16, Color.WHITE, true);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button external = btn("Открыть снаружи", v -> openExternal(i.pageUrl));
        bar.addView(external, new LinearLayout.LayoutParams(dp(210), dp(48)));
        shell.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        s.setUserAgentString(s.getUserAgentString() + " CineDeckTV/1.3");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                scheduleAutoPlay(view);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webHost.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                shell.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override public void onHideCustomView() {
                hideCustomView();
            }
        });

        shell.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        webHost.addView(shell, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webHost.setTag(shell);
        setContentView(webHost);
        webView.loadUrl(i.pageUrl);
    }

    private void scheduleAutoPlay(WebView view) {
        String js = "(function(){" +
                "var all=[].slice.call(document.querySelectorAll('a,button,[role=button]'));" +
                "var b=all.find(function(e){var t=(e.innerText||e.textContent||'').trim();return /смотреть фильм/i.test(t);});" +
                "if(!b){b=all.find(function(e){var t=(e.innerText||e.textContent||'').trim();return /^смотреть$/i.test(t);});}" +
                "if(b){try{b.scrollIntoView({block:'center'});b.click();}catch(x){}}" +
                "setTimeout(function(){var f=document.querySelector('iframe');if(f){try{f.scrollIntoView({block:'center'});}catch(x){}}},900);" +
                "})();";
        long[] delays = new long[]{700, 1700, 3200, 5200};
        for (long d : delays) {
            main.postDelayed(() -> {
                if (webView == view) view.evaluateJavascript(js, null);
            }, d);
        }
    }

    private void hideCustomView() {
        if (customView == null || webHost == null) return;
        webHost.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        Object tag = webHost.getTag();
        if (tag instanceof View) ((View) tag).setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть официальный сайт", Toast.LENGTH_LONG).show();
        }
    }

    private void showDiag() {
        base();
        backAction = this::showHome;
        root.addView(txt("Диагностика", 30, Color.WHITE, true));
        TextView out = txt("Проверяю…", 16, MUTED, false);
        root.addView(out);
        io.execute(() -> {
            Probe cdn = probe(FEED);
            Probe raw = probe(FEED_RAW);
            Probe mosfilm = probe("https://www.mosfilm.ru/cinema/films/?tags=online");
            List<Item> items = loadFeedFresh();
            String poster = "—";
            if (!items.isEmpty()) {
                Bitmap b = tryBitmap(items.get(0).poster);
                poster = b == null ? "ОШИБКА" : "OK " + b.getWidth() + "×" + b.getHeight();
            }
            final String text =
                    "Autoplay CDN: " + cdn.status + "\n" +
                    "Autoplay Raw: " + raw.status + "\n" +
                    "Мосфильм: " + mosfilm.status + "\n" +
                    "Фильмов с готовым просмотром: " + items.size() + "\n" +
                    "Источник списка: " + (selectedSource.isEmpty() ? "—" : selectedSource) + "\n" +
                    "Тест обложки: " + poster +
                    (lastError.isEmpty() ? "" : "\nПоследняя ошибка: " + lastError);
            main.post(() -> out.setText(text));
        });
    }

    private View rail(List<Item> items) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (Item i : items) line.addView(card(i));
        hs.addView(line);
        hs.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));
        return hs;
    }

    private View card(Item i) {
        LinearLayout c = col();
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(6), dp(6), dp(6), dp(6));
        c.setBackgroundColor(PANEL);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(PANEL2);
        c.addView(image, new LinearLayout.LayoutParams(dp(160), dp(224)));
        loadImage(image, i);
        TextView name = txt(i.name, 14, Color.WHITE, true);
        name.setMaxLines(1);
        c.addView(name, new LinearLayout.LayoutParams(dp(160), dp(30)));
        c.addView(txt(i.year + (i.rating.isEmpty() ? "" : " • ★ " + i.rating), 12, MUTED, false));
        c.setOnClickListener(v -> showDetails(i));
        c.setOnFocusChangeListener((v, focused) -> {
            c.setBackgroundColor(focused ? FOCUS : PANEL);
            c.setScaleX(focused ? 1.045f : 1f);
            c.setScaleY(focused ? 1.045f : 1f);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176), dp(296));
        lp.setMargins(0, 0, dp(12), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private View searchRow(Item i) {
        LinearLayout r = row();
        r.setFocusable(true);
        r.setClickable(true);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(6), dp(10), dp(6));
        r.setBackgroundColor(PANEL);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        r.addView(image, new LinearLayout.LayoutParams(dp(70), dp(96)));
        loadImage(image, i);
        LinearLayout info = col();
        info.setPadding(dp(12), 0, 0, 0);
        info.addView(txt(i.name, 18, Color.WHITE, true));
        info.addView(txt(i.year + " • " + i.provider, 13, MUTED, false));
        r.addView(info, new LinearLayout.LayoutParams(0, dp(96), 1));
        r.setOnClickListener(v -> showDetails(i));
        r.setOnFocusChangeListener((v, f) -> r.setBackgroundColor(f ? FOCUS : PANEL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110));
        lp.setMargins(0, 0, 0, dp(6));
        r.setLayoutParams(lp);
        return r;
    }

    private List<Item> loadFeed() {
        synchronized (cache) {
            if (!cache.isEmpty()) return new ArrayList<>(cache);
        }
        return loadFeedFresh();
    }

    private List<Item> loadFeedFresh() {
        for (String u : new String[]{FEED, FEED_RAW}) {
            try {
                String raw = get(u, 7000, 12000);
                JSONObject root = new JSONObject(raw);
                JSONArray arr = root.optJSONArray("items");
                if (arr == null) continue;
                List<Item> out = new ArrayList<>();
                for (int n = 0; n < arr.length(); n++) {
                    JSONObject o = arr.optJSONObject(n);
                    if (o == null) continue;
                    Item i = new Item();
                    i.id = o.optString("id", "");
                    i.name = o.optString("name", "");
                    i.year = o.optString("year", "");
                    i.rating = o.optString("rating", "");
                    i.genres = o.optString("genres", "");
                    i.desc = o.optString("description", "");
                    i.poster = o.optString("posterUrl", "");
                    i.posterRaw = o.optString("posterRawUrl", "");
                    i.provider = o.optString("provider", "Официальный источник");
                    i.pageUrl = o.optString("pageUrl", "");
                    i.streamUrl = o.optString("streamUrl", "");
                    i.playMode = o.optString("playMode", "web");
                    if (!i.name.isEmpty() && (!i.pageUrl.isEmpty() || !i.streamUrl.isEmpty())) out.add(i);
                }
                if (!out.isEmpty()) {
                    selectedSource = u.contains("jsdelivr") ? "GitHub CDN" : "GitHub Raw";
                    synchronized (cache) {
                        cache.clear();
                        cache.addAll(out);
                    }
                    return out;
                }
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    private void loadImage(ImageView view, Item i) {
        io.execute(() -> {
            Bitmap b = tryBitmap(i.poster);
            if (b == null) b = tryBitmap(i.posterRaw);
            final Bitmap out = b;
            if (out != null) main.post(() -> view.setImageBitmap(out));
        });
    }

    private Bitmap tryBitmap(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) CineDeck/1.3");
            c.connect();
            if (c.getResponseCode() / 100 != 2) {
                c.disconnect();
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            InputStream in = c.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            c.disconnect();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private Probe probe(String url) {
        Probe p = new Probe();
        try {
            String s = get(url, 6000, 9000);
            p.status = "OK (" + s.length() + " байт)";
        } catch (Exception e) {
            p.status = "ОШИБКА: " + e.getClass().getSimpleName();
        }
        return p;
    }

    private String get(String url, int connect, int read) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connect);
        c.setReadTimeout(read);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) CineDeck/1.3");
        c.connect();
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void destroyWeb() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception ignored) {}
        }
        webView = null;
        webHost = null;
        customView = null;
        customViewCallback = null;
        currentWebItem = null;
    }

    private String norm(String s) {
        return (s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^a-zа-я0-9]+", " ").trim());
    }

    private LinearLayout panel() {
        LinearLayout l = col();
        l.setPadding(dp(22), dp(16), dp(22), dp(14));
        l.setBackgroundColor(PANEL);
        return l;
    }

    private Button btn(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackgroundColor(PANEL2);
        if (listener != null) b.setOnClickListener(listener);
        b.setOnFocusChangeListener((v, f) -> b.setBackgroundColor(f ? FOCUS : PANEL2));
        return b;
    }

    private Button nav(String label, View.OnClickListener listener) {
        Button b = btn(label, listener);
        b.setTextSize(14);
        return b;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView txt(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static class Item {
        String id = "";
        String name = "";
        String year = "";
        String rating = "";
        String genres = "";
        String desc = "";
        String poster = "";
        String posterRaw = "";
        String provider = "";
        String playMode = "";
        String pageUrl = "";
        String streamUrl = "";
    }

    static class Probe {
        String status = "";
    }
}
