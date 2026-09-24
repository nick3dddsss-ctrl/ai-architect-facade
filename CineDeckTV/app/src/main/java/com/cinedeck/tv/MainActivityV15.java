package com.cinedeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityV15 extends Activity {
    private static final int BG = Color.rgb(6, 9, 14);
    private static final int PANEL = Color.rgb(17, 23, 32);
    private static final int PANEL2 = Color.rgb(27, 35, 48);
    private static final int FOCUS = Color.rgb(57, 105, 166);
    private static final int PRIMARY = Color.rgb(86, 164, 255);
    private static final int MUTED = Color.rgb(155, 170, 190);
    private static final int GOOD = Color.rgb(115, 220, 165);

    private final ProviderEngine engine = new ProviderEngine();
    private final ExecutorService io = Executors.newFixedThreadPool(7);
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private Runnable backAction;
    private ExoPlayer player;
    private Player.Listener activeListener;
    private ProviderEngine.Media currentMedia;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showHome();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (player != null) {
            ProviderEngine.Media m = currentMedia;
            releasePlayer();
            if (m != null) showDetails(m); else showHome();
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
        currentMedia = null;
        backAction = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(10), dp(26), dp(18));
        root.setBackgroundColor(BG);
        setContentView(root);
        addTopBar();
    }

    private void addTopBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("▶ CineDeck RU 1.5", 24, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(0, dp(58), 1));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("Провайдеры", v -> showSources()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
    }

    private void showHome() {
        base();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = col();
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout hero = panel();
        hero.addView(text("MULTI-PROVIDER ENGINE", 13, PRIMARY, true));
        hero.addView(text("Выбрал фильм → CineDeck сам ищет и запускает поток", 28, Color.WHITE, true));
        hero.addView(text("Прямые MP4 / HLS / DASH / WebM, несколько вариантов качества, субтитры, автопереход на резерв и сохранение позиции просмотра. Без WebView.", 15, Color.LTGRAY, false));
        content.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        content.addView(space(12));

        addSectionAsync(content, "Смотреть сразу", "Открытые и лицензированные фильмы с прямыми файлами", () -> engine.watchNow());
        addSectionAsync(content, "Популярные фильмы", "Карточка автоматически проверяется у нескольких провайдеров", () -> engine.homeMovies());
        addSectionAsync(content, "Сериалы", "Единый каталог + отдельный поиск источников", () -> engine.homeSeries());
    }

    private void addSectionAsync(LinearLayout content, String title, String subtitle, Callable<List<ProviderEngine.Media>> loader) {
        LinearLayout box = col();
        box.addView(text(title, 23, Color.WHITE, true));
        box.addView(text(subtitle, 13, MUTED, false));
        ProgressBar p = new ProgressBar(this);
        box.addView(p, new LinearLayout.LayoutParams(dp(38), dp(38)));
        content.addView(box);
        content.addView(space(18));
        io.execute(() -> {
            List<ProviderEngine.Media> items = new ArrayList<>();
            String error = "";
            try { items = loader.call(); } catch (Exception e) { error = e.getClass().getSimpleName(); }
            final List<ProviderEngine.Media> ready = items;
            final String err = error;
            main.post(() -> {
                box.removeView(p);
                if (ready.isEmpty()) box.addView(text("Источник недоступен" + (err.isEmpty() ? "" : " • " + err), 14, Color.rgb(255, 130, 130), false));
                else box.addView(rail(ready));
            });
        });
    }

    private void showSearch() {
        base();
        backAction = this::showHome;
        root.addView(text("Поиск", 29, Color.WHITE, true));
        root.addView(text("CineDeck объединяет каталог и открытые источники, а после выбора отдельно ищет поток.", 14, MUTED, false));
        root.addView(space(8));
        LinearLayout line = row();
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint("Название фильма или сериала");
        q.setTextColor(Color.WHITE);
        q.setHintTextColor(MUTED);
        q.setTextSize(18);
        q.setBackgroundColor(PANEL);
        q.setPadding(dp(14), 0, dp(14), 0);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        line.addView(q, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button go = button("Найти", null);
        line.addView(go, new LinearLayout.LayoutParams(dp(150), dp(56)));
        root.addView(line);
        root.addView(space(10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = col();
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Runnable search = () -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty()) return;
            results.removeAllViews();
            ProgressBar p = new ProgressBar(this);
            results.addView(p, new LinearLayout.LayoutParams(dp(40), dp(40)));
            results.addView(text("Ищу…", 15, MUTED, false));
            io.execute(() -> {
                List<ProviderEngine.Media> found = engine.search(query);
                main.post(() -> {
                    results.removeAllViews();
                    if (found.isEmpty()) results.addView(text("Ничего не найдено.", 17, MUTED, false));
                    else for (ProviderEngine.Media m : found) results.addView(searchRow(m));
                });
            });
        };
        go.setOnClickListener(v -> search.run());
        q.setOnEditorActionListener((v, actionId, event) -> { search.run(); return true; });
        q.requestFocus();
    }

    private void showSources() {
        base();
        backAction = this::showHome;
        root.addView(text("Провайдеры 1.5", 29, Color.WHITE, true));
        root.addView(text("Настройка пользователем не требуется.", 14, MUTED, false));
        root.addView(space(14));
        LinearLayout list = col();
        list.addView(sourceBox("Cinemeta", "Карточки, IMDb ID, постеры, фильмы и сериалы", "Каталог"));
        list.addView(sourceBox("Internet Archive", "Автопоиск по названию, несколько файлов качества, SRT/VTT", "Прямой поток"));
        list.addView(sourceBox("Wikimedia Commons", "Свободно лицензированные видеофайлы", "Прямой поток"));
        list.addView(sourceBox("Stremio Public Domain", "Совместимый /stream-провайдер для public-domain контента", "Потоки"));
        list.addView(sourceBox("WatchHub", "Официальные приложения и сервисы, если прямого файла нет", "Fallback"));
        list.addView(sourceBox("ExoPlayer", "Автовыбор лучшего потока + резерв + встроенные субтитры", "Плеер"));
        root.addView(list);
        root.addView(space(12));
        root.addView(button("Проверить соединения", v -> runDiagnostics()), new LinearLayout.LayoutParams(dp(260), dp(58)));
    }

    private View sourceBox(String name, String desc, String tag) {
        LinearLayout p = panel();
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(name, 19, Color.WHITE, true), new LinearLayout.LayoutParams(0, dp(34), 1));
        line.addView(text(tag, 12, GOOD, true));
        p.addView(line);
        p.addView(text(desc, 14, MUTED, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
        lp.setMargins(0, 0, 0, dp(8));
        p.setLayoutParams(lp);
        return p;
    }

    private void runDiagnostics() {
        base();
        backAction = this::showSources;
        root.addView(text("Диагностика", 29, Color.WHITE, true));
        TextView out = text("Проверяю…", 16, MUTED, false);
        root.addView(out);
        io.execute(() -> {
            boolean c = engine.probe("https://v3-cinemeta.strem.io/manifest.json");
            boolean a = engine.probe("https://archive.org/advancedsearch.php?q=mediatype%3Amovies&rows=1&output=json");
            boolean m = engine.probe("https://commons.wikimedia.org/w/api.php?action=query&meta=siteinfo&format=json");
            boolean p = engine.probe("https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json");
            boolean w = engine.probe("https://watchhub.strem.io/manifest.json");
            String result = "Cinemeta: " + ok(c) + "\nInternet Archive: " + ok(a) + "\nWikimedia Commons: " + ok(m) + "\nPublic Domain: " + ok(p) + "\nWatchHub: " + ok(w) + "\n\nWebView: НЕТ\nРучная настройка источников: НЕТ";
            main.post(() -> out.setText(result));
        });
    }

    private String ok(boolean v) { return v ? "OK" : "НЕДОСТУПЕН"; }

    private void showDetails(ProviderEngine.Media m) {
        base();
        currentMedia = m;
        backAction = this::showHome;
        LinearLayout line = row();
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(PANEL2);
        line.addView(poster, new LinearLayout.LayoutParams(dp(225), dp(320)));
        loadImage(poster, m.poster);

        LinearLayout info = col();
        info.setPadding(dp(22), 0, 0, 0);
        info.addView(text(m.name, 31, Color.WHITE, true));
        String meta = m.year;
        if (!m.rating.isEmpty()) meta += (meta.isEmpty() ? "" : " • ") + "★ " + m.rating;
        if (!m.genres.isEmpty()) meta += (meta.isEmpty() ? "" : " • ") + m.genres;
        info.addView(text(meta, 14, MUTED, false));
        info.addView(space(10));
        TextView d = text(m.description.isEmpty() ? "Описание пока не загружено." : m.description, 16, Color.LTGRAY, false);
        d.setMaxLines(8);
        info.addView(d);
        long resume = getResume(m);
        if (resume > 30000) {
            info.addView(space(7));
            info.addView(text("Продолжить с " + time(resume), 14, GOOD, true));
        }
        info.addView(space(14));
        LinearLayout buttons = row();
        Button watch = button(resume > 30000 ? "▶  Продолжить" : "▶  Смотреть", v -> resolveAndPlay(m, false));
        watch.setTextSize(18);
        buttons.addView(watch, new LinearLayout.LayoutParams(dp(250), dp(62)));
        Button sources = button("Источники", v -> resolveAndPlay(m, true));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(190), dp(62));
        slp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(sources, slp);
        info.addView(buttons);
        info.addView(space(9));
        info.addView(text("Каталог: " + m.provider + (m.imdbId.isEmpty() ? "" : " • " + m.imdbId), 13, MUTED, false));
        line.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        watch.requestFocus();
    }

    private void resolveAndPlay(ProviderEngine.Media m, boolean picker) {
        Toast.makeText(this, "Проверяю провайдеры…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            List<ProviderEngine.StreamOption> options = engine.resolve(m);
            main.post(() -> {
                if (options.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Нет доступного потока")
                            .setMessage("Для этой позиции встроенные легальные провайдеры сейчас не нашли прямой файл или официальную ссылку.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                if (picker) { showStreamPicker(m, options); return; }
                List<ProviderEngine.StreamOption> direct = directOnly(options);
                if (!direct.isEmpty()) { playDirectWithFallback(m, direct, 0); return; }
                if (options.size() == 1) openStreamOption(options.get(0));
                else showStreamPicker(m, options);
            });
        });
    }

    private List<ProviderEngine.StreamOption> directOnly(List<ProviderEngine.StreamOption> all) {
        List<ProviderEngine.StreamOption> out = new ArrayList<>();
        for (ProviderEngine.StreamOption s : all) if (s.direct()) out.add(s);
        return out;
    }

    private void showStreamPicker(ProviderEngine.Media m, List<ProviderEngine.StreamOption> options) {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = (options.get(i).direct() ? "▶ " : "↗ ") + options.get(i).label();
        new AlertDialog.Builder(this).setTitle("Источники • " + m.name).setItems(labels, (dialog, which) -> {
            ProviderEngine.StreamOption chosen = options.get(which);
            if (chosen.direct()) {
                List<ProviderEngine.StreamOption> direct = new ArrayList<>();
                direct.add(chosen);
                for (ProviderEngine.StreamOption s : options) if (s.direct() && s != chosen) direct.add(s);
                playDirectWithFallback(m, direct, 0);
            } else openStreamOption(chosen);
        }).setNegativeButton("Отмена", null).show();
    }

    private void playDirectWithFallback(ProviderEngine.Media m, List<ProviderEngine.StreamOption> direct, int index) {
        if (index >= direct.size()) {
            Toast.makeText(this, "Все прямые потоки ответили ошибкой", Toast.LENGTH_LONG).show();
            showDetails(m);
            return;
        }
        currentMedia = m;
        ProviderEngine.StreamOption s = direct.get(index);
        if (player == null) buildPlayerUi(m);

        TextView source = findTaggedText(root, "source");
        if (source != null) source.setText(s.label() + (index > 0 ? " • резерв " + (index + 1) : ""));

        if (activeListener != null) player.removeListener(activeListener);
        MediaItem.Builder builder = new MediaItem.Builder().setUri(s.url);
        List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
        boolean defaultSet = false;
        for (ProviderEngine.SubtitleOption sub : s.subtitles) {
            int flags = 0;
            if (!defaultSet && "ru".equals(sub.language)) { flags = C.SELECTION_FLAG_DEFAULT; defaultSet = true; }
            MediaItem.SubtitleConfiguration cfg = new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(sub.mime).setLanguage(sub.language).setLabel(sub.label).setSelectionFlags(flags).build();
            subs.add(cfg);
        }
        if (!subs.isEmpty()) builder.setSubtitleConfigurations(subs);

        player.stop();
        player.clearMediaItems();
        player.setMediaItem(builder.build());
        long resume = getResume(m);
        if (resume > 0) player.seekTo(resume);
        activeListener = new Player.Listener() {
            private boolean handled;
            @Override public void onPlayerError(PlaybackException error) {
                if (handled) return;
                handled = true;
                saveResume();
                Toast.makeText(MainActivityV15.this, "Поток не ответил — пробую следующий", Toast.LENGTH_SHORT).show();
                main.postDelayed(() -> playDirectWithFallback(m, direct, index + 1), 450);
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (!isPlaying) saveResume();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) clearResume(m);
            }
        };
        player.addListener(activeListener);
        player.prepare();
        player.play();
    }

    private void buildPlayerUi(ProviderEngine.Media m) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        LinearLayout bar = row();
        bar.setPadding(dp(18), dp(6), dp(18), dp(6));
        bar.addView(text(m.name, 19, Color.WHITE, true), new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView source = text("", 13, MUTED, false);
        source.setTag("source");
        bar.addView(source, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        PlayerView pv = new PlayerView(this);
        pv.setUseController(true);
        pv.setBackgroundColor(Color.BLACK);
        root.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        player = new ExoPlayer.Builder(this).build();
        pv.setPlayer(player);
    }

    private void openStreamOption(ProviderEngine.StreamOption s) {
        if (!s.youtubeId.isEmpty()) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + s.youtubeId))); return; }
            catch (ActivityNotFoundException ignored) { openExternal("https://www.youtube.com/watch?v=" + s.youtubeId); return; }
        }
        if (!s.externalUrl.isEmpty()) openExternal(s.externalUrl);
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Не найдено официальное приложение для этого источника", Toast.LENGTH_LONG).show(); }
    }

    private View rail(List<ProviderEngine.Media> items) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        int count = 0;
        for (ProviderEngine.Media m : items) { line.addView(card(m)); if (++count >= 42) break; }
        hs.addView(line);
        hs.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));
        return hs;
    }

    private View card(ProviderEngine.Media m) {
        LinearLayout c = col();
        c.setFocusable(true); c.setClickable(true); c.setPadding(dp(6), dp(6), dp(6), dp(6)); c.setBackgroundColor(PANEL);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(PANEL2);
        c.addView(image, new LinearLayout.LayoutParams(dp(158), dp(220))); loadImage(image, m.poster);
        TextView name = text(m.name, 14, Color.WHITE, true); name.setMaxLines(1); c.addView(name, new LinearLayout.LayoutParams(dp(158), dp(31)));
        String foot = m.year; if (!m.rating.isEmpty()) foot += (foot.isEmpty() ? "" : " • ") + "★ " + m.rating;
        c.addView(text(foot, 12, MUTED, false));
        c.setOnClickListener(v -> showDetails(m));
        c.setOnFocusChangeListener((v, f) -> { c.setBackgroundColor(f ? FOCUS : PANEL); c.setScaleX(f ? 1.045f : 1f); c.setScaleY(f ? 1.045f : 1f); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(174), dp(292)); lp.setMargins(0, 0, dp(12), 0); c.setLayoutParams(lp);
        return c;
    }

    private View searchRow(ProviderEngine.Media m) {
        LinearLayout r = row(); r.setFocusable(true); r.setClickable(true); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(8), dp(6), dp(10), dp(6)); r.setBackgroundColor(PANEL);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(PANEL2); r.addView(image, new LinearLayout.LayoutParams(dp(72), dp(98))); loadImage(image, m.poster);
        LinearLayout info = col(); info.setPadding(dp(12), 0, 0, 0); info.addView(text(m.name, 18, Color.WHITE, true));
        info.addView(text((m.year.isEmpty() ? "" : m.year + " • ") + m.provider, 13, MUTED, false)); r.addView(info, new LinearLayout.LayoutParams(0, dp(98), 1));
        r.setOnClickListener(v -> showDetails(m)); r.setOnFocusChangeListener((v, f) -> r.setBackgroundColor(f ? FOCUS : PANEL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)); lp.setMargins(0, 0, 0, dp(6)); r.setLayoutParams(lp); return r;
    }

    private void loadImage(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        io.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setConnectTimeout(7000); c.setReadTimeout(11000); c.setInstanceFollowRedirects(true); c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) CineDeck/1.5"); c.connect();
                if (c.getResponseCode() >= 200 && c.getResponseCode() < 300) { BitmapFactory.Options o = new BitmapFactory.Options(); o.inPreferredConfig = Bitmap.Config.RGB_565; InputStream in = c.getInputStream(); b = BitmapFactory.decodeStream(in, null, o); in.close(); }
                c.disconnect();
            } catch (Exception ignored) {}
            Bitmap ready = b; if (ready != null) main.post(() -> view.setImageBitmap(ready));
        });
    }

    private TextView findTaggedText(ViewGroup group, String tag) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i); if (tag.equals(v.getTag()) && v instanceof TextView) return (TextView) v;
            if (v instanceof ViewGroup) { TextView found = findTaggedText((ViewGroup) v, tag); if (found != null) return found; }
        }
        return null;
    }

    private SharedPreferences prefs() { return getSharedPreferences("cinedeck_progress", MODE_PRIVATE); }
    private String mediaKey(ProviderEngine.Media m) { String id = !m.imdbId.isEmpty() ? m.imdbId : (!m.id.isEmpty() ? m.id : m.name + "|" + m.year); return "pos_" + id.hashCode(); }
    private long getResume(ProviderEngine.Media m) { return prefs().getLong(mediaKey(m), 0L); }
    private void clearResume(ProviderEngine.Media m) { prefs().edit().remove(mediaKey(m)).apply(); }
    private void saveResume() { if (player != null && currentMedia != null) { long p = player.getCurrentPosition(); if (p > 15000) prefs().edit().putLong(mediaKey(currentMedia), p).apply(); } }
    private String time(long ms) { long s = ms / 1000; return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60); }

    private void releasePlayer() {
        if (player != null) {
            saveResume();
            try { if (activeListener != null) player.removeListener(activeListener); player.release(); } catch (Exception ignored) {}
            player = null; activeListener = null;
        }
    }

    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout col() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout panel() { LinearLayout p = col(); p.setPadding(dp(18), dp(14), dp(18), dp(14)); p.setBackgroundColor(PANEL); return p; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s == null ? "" : s); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private Button nav(String s, View.OnClickListener click) { Button b = button(s, click); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(142), dp(48)); lp.setMargins(dp(6), 0, 0, 0); b.setLayoutParams(lp); return b; }
    private Button button(String s, View.OnClickListener click) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setFocusable(true); b.setBackgroundColor(PANEL2); if (click != null) b.setOnClickListener(click); b.setOnFocusChangeListener((v, f) -> { b.setBackgroundColor(f ? FOCUS : PANEL2); b.setScaleX(f ? 1.035f : 1f); b.setScaleY(f ? 1.035f : 1f); }); return b; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
