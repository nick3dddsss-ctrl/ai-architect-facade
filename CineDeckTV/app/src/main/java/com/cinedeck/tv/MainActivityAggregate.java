package com.cinedeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.view.KeyEvent;
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
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityAggregate extends Activity {
    private static final int BG = Color.rgb(6, 9, 14);
    private static final int PANEL = Color.rgb(17, 23, 32);
    private static final int PANEL2 = Color.rgb(27, 35, 48);
    private static final int FOCUS = Color.rgb(57, 105, 166);
    private static final int PRIMARY = Color.rgb(86, 164, 255);
    private static final int MUTED = Color.rgb(155, 170, 190);
    private static final int GOOD = Color.rgb(115, 220, 165);

    private final ProviderEngine engine = new ProviderEngine();
    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private Runnable backAction;
    private ExoPlayer player;
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
        TextView logo = text("▶ CineDeck RU 1.4", 24, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(0, dp(58), 1));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("Источники", v -> showSources()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
    }

    private void showHome() {
        base();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = col();
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout hero = panel();
        hero.addView(text("АГРЕГАТОР, А НЕ ВЕБ-ОБЁРТКА", 13, PRIMARY, true));
        hero.addView(text("Фильм → поиск потоков → прямое воспроизведение", 29, Color.WHITE, true));
        hero.addView(text("CineDeck сам опрашивает встроенные провайдеры. Прямые MP4/HLS/DASH идут во внутренний плеер; официальные сервисы используются только как запасной вариант.", 15, Color.LTGRAY, false));
        content.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));
        content.addView(space(12));

        addSectionAsync(content, "Смотреть сразу", "Открытые и разрешённые прямые потоки — без перехода на сайт", () -> engine.watchNow());
        addSectionAsync(content, "Популярные фильмы", "Единый каталог; источник подбирается после нажатия «Смотреть»", () -> engine.homeMovies());
        addSectionAsync(content, "Сериалы", "Каталог и поиск источников разделены, как у крупных агрегаторов", () -> engine.homeSeries());
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
                if (ready.isEmpty()) {
                    box.addView(text("Источник временно недоступен" + (err.isEmpty() ? "" : " • " + err), 14, Color.rgb(255, 130, 130), false));
                } else {
                    box.addView(rail(ready));
                }
            });
        });
    }

    private void showSearch() {
        base();
        backAction = this::showHome;
        root.addView(text("Поиск по единому каталогу", 29, Color.WHITE, true));
        root.addView(text("Результаты объединяются из каталога фильмов и источников с прямыми потоками.", 14, MUTED, false));
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
            results.addView(text("Ищу каталог и доступные источники…", 15, MUTED, false));
            io.execute(() -> {
                List<ProviderEngine.Media> found = engine.search(query);
                main.post(() -> {
                    results.removeAllViews();
                    if (found.isEmpty()) {
                        results.addView(text("Ничего не найдено.", 17, MUTED, false));
                    } else {
                        for (ProviderEngine.Media m : found) results.addView(searchRow(m));
                    }
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
        root.addView(text("Встроенные провайдеры", 29, Color.WHITE, true));
        root.addView(text("Ничего вводить и настраивать не нужно.", 14, MUTED, false));
        root.addView(space(14));

        LinearLayout list = col();
        list.addView(sourceBox("Cinemeta", "Метаданные, IMDb ID, постеры, фильмы и сериалы", "Каталог"));
        list.addView(sourceBox("Internet Archive", "Прямые MP4 из открытых/лицензированных коллекций", "Внутренний плеер"));
        list.addView(sourceBox("Stremio Public Domain", "Совместимый /stream-провайдер для произведений public domain", "Потоки"));
        list.addView(sourceBox("WatchHub", "Официальные площадки для конкретного фильма или сериала", "Запасной вариант"));
        list.addView(sourceBox("ExoPlayer", "MP4 • HLS • DASH с автоматическим переходом к следующему прямому потоку при ошибке", "Плеер"));
        root.addView(list);
        root.addView(space(12));

        Button diag = button("Проверить соединения", v -> runDiagnostics());
        root.addView(diag, new LinearLayout.LayoutParams(dp(260), dp(58)));
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
        root.addView(text("Диагностика провайдеров", 29, Color.WHITE, true));
        TextView out = text("Проверяю…", 16, MUTED, false);
        root.addView(out);
        io.execute(() -> {
            boolean c = engine.probe("https://v3-cinemeta.strem.io/manifest.json");
            boolean a = engine.probe("https://archive.org/advancedsearch.php?q=mediatype%3Amovies&rows=1&output=json");
            boolean p = engine.probe("https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json");
            boolean w = engine.probe("https://watchhub.strem.io/manifest.json");
            String result = "Cinemeta: " + ok(c) + "\nInternet Archive: " + ok(a) + "\nPublic Domain: " + ok(p) + "\nWatchHub: " + ok(w) + "\n\nWebView в цепочке воспроизведения: НЕТ";
            main.post(() -> out.setText(result));
        });
    }

    private String ok(boolean value) { return value ? "OK" : "НЕДОСТУПЕН"; }

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
        d.setMaxLines(9);
        info.addView(d);
        info.addView(space(16));

        LinearLayout buttons = row();
        Button watch = button("▶  Смотреть", v -> resolveAndPlay(m, false));
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

    private void resolveAndPlay(ProviderEngine.Media m, boolean forcePicker) {
        Toast.makeText(this, "Ищу рабочие источники…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            List<ProviderEngine.StreamOption> options = engine.resolve(m);
            main.post(() -> {
                if (options.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Поток не найден")
                            .setMessage("Для этой позиции встроенные провайдеры сейчас не вернули ни прямого потока, ни официальной ссылки.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                if (forcePicker) {
                    showStreamPicker(m, options);
                    return;
                }
                List<ProviderEngine.StreamOption> direct = directOnly(options);
                if (!direct.isEmpty()) {
                    playDirectWithFallback(m, direct, 0);
                    return;
                }
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
        for (int i = 0; i < options.size(); i++) {
            ProviderEngine.StreamOption s = options.get(i);
            labels[i] = (s.direct() ? "▶ " : "↗ ") + s.label();
        }
        new AlertDialog.Builder(this)
                .setTitle("Источники • " + m.name)
                .setItems(labels, (dialog, which) -> {
                    ProviderEngine.StreamOption chosen = options.get(which);
                    if (chosen.direct()) {
                        List<ProviderEngine.StreamOption> direct = new ArrayList<>();
                        direct.add(chosen);
                        for (ProviderEngine.StreamOption s : options) if (s.direct() && s != chosen) direct.add(s);
                        playDirectWithFallback(m, direct, 0);
                    } else openStreamOption(chosen);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void playDirectWithFallback(ProviderEngine.Media m, List<ProviderEngine.StreamOption> direct, int index) {
        if (index >= direct.size()) {
            Toast.makeText(this, "Все прямые потоки ответили ошибкой", Toast.LENGTH_LONG).show();
            showDetails(m);
            return;
        }
        currentMedia = m;
        ProviderEngine.StreamOption s = direct.get(index);

        if (player == null) {
            root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.BLACK);
            setContentView(root);

            LinearLayout bar = row();
            bar.setPadding(dp(18), dp(6), dp(18), dp(6));
            TextView title = text(m.name, 19, Color.WHITE, true);
            bar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
            TextView source = text("", 13, MUTED, false);
            source.setTag("source");
            bar.addView(source, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
            root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

            PlayerView pv = new PlayerView(this);
            pv.setUseController(true);
            pv.setBackgroundColor(Color.BLACK);
            pv.setTag("playerView");
            root.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            player = new ExoPlayer.Builder(this).build();
            pv.setPlayer(player);
        }

        TextView source = findTaggedText(root, "source");
        if (source != null) source.setText(s.label() + (index > 0 ? " • резерв " + (index + 1) : ""));

        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(s.url));
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(s.url));
        player.addListener(new Player.Listener() {
            private boolean handled = false;
            @Override public void onPlayerError(PlaybackException error) {
                if (handled) return;
                handled = true;
                try { player.stop(); } catch (Exception ignored) {}
                Toast.makeText(MainActivityAggregate.this, "Поток не ответил — пробую следующий", Toast.LENGTH_SHORT).show();
                main.postDelayed(() -> playDirectWithFallback(m, direct, index + 1), 500);
            }
        });
        player.prepare();
        player.play();
    }

    private TextView findTaggedText(ViewGroup group, String tag) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (tag.equals(v.getTag()) && v instanceof TextView) return (TextView) v;
            if (v instanceof ViewGroup) {
                TextView found = findTaggedText((ViewGroup) v, tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void openStreamOption(ProviderEngine.StreamOption s) {
        if (!s.youtubeId.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + s.youtubeId)));
                return;
            } catch (ActivityNotFoundException ignored) {
                openExternal("https://www.youtube.com/watch?v=" + s.youtubeId);
                return;
            }
        }
        if (!s.externalUrl.isEmpty()) openExternal(s.externalUrl);
    }

    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Не найдено приложение для этого официального сервиса", Toast.LENGTH_LONG).show();
        }
    }

    private View rail(List<ProviderEngine.Media> items) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        int count = 0;
        for (ProviderEngine.Media m : items) {
            line.addView(card(m));
            if (++count >= 42) break;
        }
        hs.addView(line);
        hs.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)));
        return hs;
    }

    private View card(ProviderEngine.Media m) {
        LinearLayout c = col();
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(6), dp(6), dp(6), dp(6));
        c.setBackgroundColor(PANEL);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(PANEL2);
        c.addView(image, new LinearLayout.LayoutParams(dp(158), dp(220)));
        loadImage(image, m.poster);
        TextView name = text(m.name, 14, Color.WHITE, true);
        name.setMaxLines(1);
        c.addView(name, new LinearLayout.LayoutParams(dp(158), dp(31)));
        String foot = m.year;
        if (!m.rating.isEmpty()) foot += (foot.isEmpty() ? "" : " • ") + "★ " + m.rating;
        c.addView(text(foot, 12, MUTED, false));
        c.setOnClickListener(v -> showDetails(m));
        c.setOnFocusChangeListener((v, focused) -> {
            c.setBackgroundColor(focused ? FOCUS : PANEL);
            c.setScaleX(focused ? 1.045f : 1f);
            c.setScaleY(focused ? 1.045f : 1f);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(174), dp(292));
        lp.setMargins(0, 0, dp(12), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private View searchRow(ProviderEngine.Media m) {
        LinearLayout r = row();
        r.setFocusable(true);
        r.setClickable(true);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(6), dp(10), dp(6));
        r.setBackgroundColor(PANEL);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(PANEL2);
        r.addView(image, new LinearLayout.LayoutParams(dp(72), dp(98)));
        loadImage(image, m.poster);
        LinearLayout info = col();
        info.setPadding(dp(12), 0, 0, 0);
        info.addView(text(m.name, 18, Color.WHITE, true));
        String sub = (m.year.isEmpty() ? "" : m.year + " • ") + m.provider;
        info.addView(text(sub, 13, MUTED, false));
        r.addView(info, new LinearLayout.LayoutParams(0, dp(98), 1));
        r.setOnClickListener(v -> showDetails(m));
        r.setOnFocusChangeListener((v, f) -> r.setBackgroundColor(f ? FOCUS : PANEL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        lp.setMargins(0, 0, 0, dp(6));
        r.setLayoutParams(lp);
        return r;
    }

    private void loadImage(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        io.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(7000);
                c.setReadTimeout(11000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) CineDeck/1.4");
                c.connect();
                if (c.getResponseCode() >= 200 && c.getResponseCode() < 300) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    InputStream in = c.getInputStream();
                    b = BitmapFactory.decodeStream(in, null, o);
                    in.close();
                }
                c.disconnect();
            } catch (Exception ignored) {}
            Bitmap ready = b;
            if (ready != null) main.post(() -> view.setImageBitmap(ready));
        });
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private LinearLayout col() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout panel() {
        LinearLayout p = col();
        p.setPadding(dp(18), dp(14), dp(18), dp(14));
        p.setBackgroundColor(PANEL);
        return p;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button nav(String s, View.OnClickListener click) {
        Button b = button(s, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(142), dp(48));
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button button(String s, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackgroundColor(PANEL2);
        if (click != null) b.setOnClickListener(click);
        b.setOnFocusChangeListener((v, f) -> {
            b.setBackgroundColor(f ? FOCUS : PANEL2);
            b.setScaleX(f ? 1.035f : 1f);
            b.setScaleY(f ? 1.035f : 1f);
        });
        return b;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
