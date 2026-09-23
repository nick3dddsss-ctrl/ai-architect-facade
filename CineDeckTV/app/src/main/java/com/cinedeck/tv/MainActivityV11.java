package com.cinedeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
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

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivityV11 extends Activity {
    private static final int BG = Color.rgb(6, 9, 14);
    private static final int PANEL = Color.rgb(17, 23, 33);
    private static final int PANEL2 = Color.rgb(25, 33, 46);
    private static final int FOCUS = Color.rgb(40, 83, 136);
    private static final int PRIMARY = Color.rgb(62, 148, 255);
    private static final int MUTED = Color.rgb(160, 173, 193);
    private static final int GOOD = Color.rgb(80, 203, 132);

    private static final String PREFS = "cinedeck_ru_11";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_M3U = "m3u";
    private static final String CINEMETA = "https://v3-cinemeta.strem.io";
    private static final String WIKI = "https://ru.wikipedia.org/w/api.php";

    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> images = new LruCache<>(60);

    private SharedPreferences prefs;
    private LinearLayout root;
    private ExoPlayer player;
    private Runnable backAction;

    private final List<Service> services = Arrays.asList(
            new Service("VK Видео", "https://vkvideo.ru/video?q=%s", "Бесплатное видео"),
            new Service("RUTUBE", "https://rutube.ru/search/?query=%s", "Видео • кино • эфиры"),
            new Service("Кинопоиск", "https://www.kinopoisk.ru/index.php?kp_query=%s", "Кино и сериалы"),
            new Service("Wink", "https://wink.ru/search?query=%s", "Кино • ТВ • спорт"),
            new Service("Иви", "https://www.ivi.ru/search/?q=%s", "Кино • сериалы • ТВ"),
            new Service("Okko", "https://okko.tv/", "Кино • сериалы • спорт"),
            new Service("KION", "https://kion.ru/", "Кино • сериалы • ТВ")
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showHome();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (backAction != null) {
            Runnable action = backAction;
            backAction = null;
            action.run();
        } else {
            super.onBackPressed();
        }
    }

    private void prepareScreen() {
        releasePlayer();
        backAction = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(34), dp(12), dp(34), dp(22));
        setContentView(root);
        addTopBar();
    }

    private void addTopBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("▶  CineDeck RU", 25, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(250), dp(58)));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Каталог", v -> showDiscover("movie", "", "")));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("Сервисы", v -> showServices()));
        bar.addView(nav("ТВ / M3U", v -> showChannels()));
        bar.addView(nav("Избранное", v -> showFavorites()));
        bar.addView(nav("Настройки", v -> showSettings()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
    }

    private void showHome() {
        prepareScreen();

        LinearLayout hero = panel(PANEL);
        hero.setPadding(dp(28), dp(18), dp(28), dp(18));
        hero.addView(text("CINEDECK RU 1.1 • ANDROID TV / AOSP", 13, PRIMARY, true));
        hero.addView(text("Кино, сериалы и ТВ — одним пультом", 37, Color.WHITE, true));
        hero.addView(text("Каталог фильмов и сериалов без обязательного аккаунта, поиск по российским сервисам, избранное, M3U/IPTV и собственный HLS/DASH/MP4-плеер.", 16, Color.LTGRAY, false));
        hero.addView(spacer(10));
        LinearLayout actions = row();
        actions.addView(action("⌕  Найти", v -> showSearch()));
        actions.addView(action("▦  Каталог", v -> showDiscover("movie", "", "")));
        actions.addView(action("▣  Мои каналы", v -> showChannels()));
        actions.addView(action("▶  Ссылка", v -> promptDirectStream()));
        hero.addView(actions);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(198)));

        root.addView(spacer(12));
        addServiceStrip(root, null);
        root.addView(spacer(14));

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        scroll.addView(content);
        addHistory(content);
        addCatalogRow(content, "Популярные фильмы", "movie", "");
        addCatalogRow(content, "Популярные сериалы", "series", "");
        addGenreRail(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void addGenreRail(LinearLayout parent) {
        parent.addView(text("Жанры", 24, Color.WHITE, true));
        parent.addView(spacer(7));
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        String[][] genres = {
                {"Боевики", "Action"}, {"Комедии", "Comedy"}, {"Драмы", "Drama"},
                {"Криминал", "Crime"}, {"Фантастика", "Sci-Fi"}, {"Триллеры", "Thriller"},
                {"Семейные", "Family"}, {"Анимация", "Animation"}, {"Документальные", "Documentary"}
        };
        for (String[] g : genres) {
            Button b = chip(g[0]);
            b.setOnClickListener(v -> showDiscover("movie", g[1], ""));
            line.addView(b);
        }
        sc.addView(line);
        parent.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        parent.addView(spacer(16));
    }

    private void addServiceStrip(LinearLayout parent, String query) {
        parent.addView(text(query == null ? "Российские сервисы" : "Где искать «" + query + "»", 21, Color.WHITE, true));
        parent.addView(spacer(6));
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (Service service : services) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(service.name + "\n" + service.note);
            b.setTextSize(13);
            b.setTextColor(Color.WHITE);
            b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            b.setPadding(dp(14), dp(6), dp(14), dp(6));
            styleFocusable(b, PANEL2);
            b.setOnClickListener(v -> openService(service, query));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(190), dp(72));
            lp.setMargins(0, 0, dp(9), 0);
            line.addView(b, lp);
        }
        sc.addView(line);
        parent.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
    }

    private void addCatalogRow(LinearLayout parent, String title, String type, String genre) {
        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text(title, 24, Color.WHITE, true), new LinearLayout.LayoutParams(0, dp(45), 1));
        Button all = chip("Смотреть все  ›");
        all.setOnClickListener(v -> showDiscover(type, genre, ""));
        titleRow.addView(all);
        parent.addView(titleRow);

        ProgressBar progress = new ProgressBar(this);
        parent.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout holder = column();
        parent.addView(holder);
        parent.addView(spacer(14));

        io.execute(() -> {
            List<Media> list = fetchCatalog(type, genre, "", 18);
            main.post(() -> {
                parent.removeView(progress);
                if (list.isEmpty()) holder.addView(text("Каталог временно недоступен.", 15, MUTED, false));
                else holder.addView(mediaRail(list));
            });
        });
    }

    private View mediaRail(List<Media> list) {
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (Media item : list) line.addView(mediaCard(item));
        sc.addView(line);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(315)));
        return sc;
    }

    private View mediaCard(Media item) {
        LinearLayout card = column();
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setBackground(roundRect(PANEL, 13));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(roundRect(PANEL2, 10));
        card.addView(poster, new LinearLayout.LayoutParams(dp(164), dp(230)));
        loadImage(poster, item.poster);

        TextView name = text(item.name, 14, Color.WHITE, true);
        name.setMaxLines(1);
        card.addView(name, new LinearLayout.LayoutParams(dp(164), dp(28)));
        card.addView(text(item.shortMeta(), 12, MUTED, false), new LinearLayout.LayoutParams(dp(164), dp(24)));
        card.setOnClickListener(v -> showDetails(item));
        card.setOnFocusChangeListener((v, focus) -> {
            card.setBackground(roundRect(focus ? FOCUS : PANEL, 13));
            float s = focus ? 1.055f : 1f;
            card.animate().scaleX(s).scaleY(s).setDuration(90).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), dp(300));
        lp.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void showDiscover(String initialType, String initialGenre, String initialYear) {
        prepareScreen();
        root.addView(text("Каталог", 34, Color.WHITE, true));
        root.addView(text("Фильтры работают без регистрации. Год фильтруется по текущей выдаче каталога.", 14, MUTED, false));
        root.addView(spacer(8));

        final String[] type = {initialType};
        final String[] genre = {initialGenre};
        final String[] year = {initialYear};
        LinearLayout controls = column();
        root.addView(controls);
        LinearLayout results = column();
        ScrollView resultScroll = new ScrollView(this);
        resultScroll.addView(results);
        root.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable[] reload = new Runnable[1];
        Runnable renderControls = () -> {
            controls.removeAllViews();
            LinearLayout types = row();
            types.addView(filterChip("Фильмы", type[0].equals("movie"), v -> { type[0] = "movie"; reload[0].run(); }));
            types.addView(filterChip("Сериалы", type[0].equals("series"), v -> { type[0] = "series"; reload[0].run(); }));
            String[][] gs = {{"Все жанры", ""}, {"Боевики", "Action"}, {"Комедии", "Comedy"}, {"Драмы", "Drama"}, {"Криминал", "Crime"}, {"Фантастика", "Sci-Fi"}, {"Триллер", "Thriller"}, {"Анимация", "Animation"}};
            for (String[] g : gs) types.addView(filterChip(g[0], genre[0].equals(g[1]), v -> { genre[0] = g[1]; reload[0].run(); }));
            types.addView(filterChip(year[0].isEmpty() ? "Год" : "Год: " + year[0], !year[0].isEmpty(), v -> promptYear(year, reload[0])));
            HorizontalScrollView sc = new HorizontalScrollView(this);
            sc.setHorizontalScrollBarEnabled(false);
            sc.addView(types);
            controls.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        };

        reload[0] = () -> {
            renderControls.run();
            results.removeAllViews();
            ProgressBar p = new ProgressBar(this);
            results.addView(p, new LinearLayout.LayoutParams(dp(44), dp(44)));
            io.execute(() -> {
                List<Media> list = fetchCatalog(type[0], genre[0], year[0], 60);
                main.post(() -> {
                    results.removeAllViews();
                    if (list.isEmpty()) {
                        results.addView(text("Ничего не найдено для выбранных фильтров.", 18, MUTED, false));
                        return;
                    }
                    for (int i = 0; i < list.size(); i += 6) {
                        LinearLayout row = row();
                        for (int j = i; j < Math.min(i + 6, list.size()); j++) row.addView(mediaCard(list.get(j)));
                        results.addView(row);
                        results.addView(spacer(8));
                    }
                });
            });
        };
        reload[0].run();
    }

    private void promptYear(String[] year, Runnable reload) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint("Например 2025; пусто = любой");
        e.setText(year[0]);
        new AlertDialog.Builder(this).setTitle("Фильтр по году").setView(e)
                .setPositiveButton("Применить", (d, w) -> { year[0] = e.getText().toString().trim(); reload.run(); })
                .setNegativeButton("Сбросить", (d, w) -> { year[0] = ""; reload.run(); }).show();
    }

    private void showSearch() {
        prepareScreen();
        root.addView(text("Единый поиск", 34, Color.WHITE, true));
        root.addView(text("Ищет одновременно фильмы и сериалы. Из карточки можно сразу перейти к поиску на российских сервисах.", 14, MUTED, false));
        root.addView(spacer(10));

        LinearLayout srow = row();
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setTextColor(Color.WHITE);
        q.setHintTextColor(MUTED);
        q.setHint("Название фильма или сериала");
        q.setTextSize(18);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setBackground(roundRect(PANEL, 12));
        q.setPadding(dp(14), 0, dp(14), 0);
        srow.addView(q, new LinearLayout.LayoutParams(dp(720), dp(56)));
        Button go = action("Найти", null);
        srow.addView(go, new LinearLayout.LayoutParams(dp(150), dp(56)));
        root.addView(srow);
        root.addView(spacer(10));

        ScrollView sc = new ScrollView(this);
        LinearLayout results = column();
        sc.addView(results);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable search = () -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty()) return;
            results.removeAllViews();
            ProgressBar p = new ProgressBar(this);
            results.addView(p, new LinearLayout.LayoutParams(dp(44), dp(44)));
            io.execute(() -> {
                List<Media> all = new ArrayList<>();
                all.addAll(fetchSearch("movie", query));
                all.addAll(fetchSearch("series", query));
                main.post(() -> {
                    results.removeAllViews();
                    addServiceStrip(results, query);
                    results.addView(spacer(8));
                    if (all.isEmpty()) results.addView(text("Ничего не найдено.", 18, MUTED, false));
                    else for (Media item : all) results.addView(searchRow(item));
                });
            });
        };
        go.setOnClickListener(v -> search.run());
        q.setOnEditorActionListener((v, actionId, event) -> { search.run(); return true; });
        q.requestFocus();
    }

    private View searchRow(Media item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(9), dp(7), dp(14), dp(7));
        row.setBackground(roundRect(PANEL, 11));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(poster, new LinearLayout.LayoutParams(dp(74), dp(104)));
        loadImage(poster, item.poster);
        LinearLayout info = column();
        info.setPadding(dp(14), 0, 0, 0);
        info.addView(text(item.name, 19, Color.WHITE, true));
        info.addView(text(item.metaLine(), 13, MUTED, false));
        TextView desc = text(item.description, 13, Color.LTGRAY, false);
        desc.setMaxLines(2);
        info.addView(desc);
        row.addView(info, new LinearLayout.LayoutParams(0, dp(104), 1));
        row.setOnClickListener(v -> showDetails(item));
        row.setOnFocusChangeListener((v, f) -> row.setBackground(roundRect(f ? FOCUS : PANEL, 11)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
        lp.setMargins(0, 0, 0, dp(7));
        row.setLayoutParams(lp);
        return row;
    }

    private void showDetails(Media summary) {
        prepareScreen();
        backAction = this::showHome;
        FrameLayout stage = new FrameLayout(this);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.30f);
        stage.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadImage(backdrop, summary.background.isEmpty() ? summary.poster : summary.background);

        ScrollView sc = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(26), dp(22), dp(26), dp(20));
        sc.addView(content);
        stage.addView(sc, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ProgressBar p = new ProgressBar(this);
        content.addView(p, new LinearLayout.LayoutParams(dp(44), dp(44)));
        io.execute(() -> {
            Media full = fetchMeta(summary.type, summary.id);
            if (full == null) full = summary;
            Media finalFull = full;
            WikiInfo wiki = fetchRussianInfo(finalFull);
            main.post(() -> {
                content.removeAllViews();
                renderDetails(content, finalFull, wiki);
            });
        });
    }

    private void renderDetails(LinearLayout content, Media item, WikiInfo wiki) {
        addHistory(item);
        LinearLayout head = row();
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(roundRect(PANEL2, 12));
        head.addView(poster, new LinearLayout.LayoutParams(dp(210), dp(296)));
        loadImage(poster, item.poster);

        LinearLayout info = column();
        info.setPadding(dp(22), 0, 0, 0);
        String shownTitle = wiki.title.isEmpty() ? item.name : wiki.title;
        info.addView(text(shownTitle, 35, Color.WHITE, true));
        if (!shownTitle.equals(item.name)) info.addView(text(item.name, 17, MUTED, false));
        info.addView(text(item.metaLine(), 16, Color.LTGRAY, false));
        info.addView(spacer(8));
        TextView desc = text(wiki.extract.isEmpty() ? item.description : wiki.extract, 16, Color.WHITE, false);
        desc.setMaxLines(8);
        info.addView(desc);
        info.addView(spacer(12));
        LinearLayout actions = row();
        boolean fav = isFavorite(item.key());
        actions.addView(action(fav ? "★  В избранном" : "☆  В избранное", v -> { toggleFavorite(item.key()); showDetails(item); }));
        actions.addView(action("⌕  Где смотреть", v -> showWhereToWatch(item)));
        info.addView(actions);
        head.addView(info, new LinearLayout.LayoutParams(0, dp(296), 1));
        content.addView(head);
        content.addView(spacer(16));

        if (item.type.equals("series") && !item.episodes.isEmpty()) {
            content.addView(text("Сезоны и серии", 24, Color.WHITE, true));
            content.addView(text(item.episodeSummary(), 14, MUTED, false));
            content.addView(spacer(8));
            HorizontalScrollView epScroll = new HorizontalScrollView(this);
            LinearLayout eps = row();
            int count = Math.min(24, item.episodes.size());
            for (int i = 0; i < count; i++) {
                Episode ep = item.episodes.get(i);
                Button b = chip("S" + ep.season + " E" + ep.number + "\n" + ep.name);
                b.setOnClickListener(v -> showWhereToWatch(item));
                eps.addView(b, new LinearLayout.LayoutParams(dp(190), dp(64)));
            }
            epScroll.addView(eps);
            content.addView(epScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));
            content.addView(spacer(14));
        }
        addServiceStrip(content, item.name);
    }

    private void showWhereToWatch(Media item) {
        prepareScreen();
        backAction = () -> showDetails(item);
        root.addView(text("Где искать", 34, Color.WHITE, true));
        root.addView(text(item.name, 22, Color.LTGRAY, false));
        root.addView(spacer(12));
        addServiceStrip(root, item.name);
        root.addView(spacer(12));
        root.addView(text("CineDeck не извлекает защищённые DRM-потоки из онлайн-кинотеатров. Кнопки открывают официальный сервис или его поиск; наличие конкретного фильма зависит от подписки и каталога сервиса.", 15, MUTED, false));
    }

    private void showServices() {
        prepareScreen();
        root.addView(text("Российские видеосервисы", 34, Color.WHITE, true));
        root.addView(text("Сервисы открываются официальными ссылками. Если приложение сервиса установлено и перехватывает ссылку, Android TV предложит открыть его.", 14, MUTED, false));
        root.addView(spacer(12));
        addServiceStrip(root, null);
    }

    private void showFavorites() {
        prepareScreen();
        root.addView(text("Избранное", 34, Color.WHITE, true));
        Set<String> keys = prefs.getStringSet(KEY_FAVORITES, Collections.emptySet());
        if (keys.isEmpty()) {
            root.addView(text("Пока пусто. Добавляй фильмы и сериалы из карточек.", 17, MUTED, false));
            return;
        }
        ProgressBar p = new ProgressBar(this);
        root.addView(p, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ScrollView sc = new ScrollView(this);
        LinearLayout holder = column();
        sc.addView(holder);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        io.execute(() -> {
            List<Media> list = new ArrayList<>();
            for (String key : keys) {
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    Media m = fetchMeta(parts[0], parts[1]);
                    if (m != null) list.add(m);
                }
            }
            main.post(() -> {
                root.removeView(p);
                if (list.isEmpty()) holder.addView(text("Не удалось загрузить избранное.", 16, MUTED, false));
                else holder.addView(mediaRail(list));
            });
        });
    }

    private void addHistory(LinearLayout parent) {
        List<String> keys = historyKeys();
        if (keys.isEmpty()) return;
        parent.addView(text("Продолжить просмотр", 24, Color.WHITE, true));
        ProgressBar p = new ProgressBar(this);
        parent.addView(p, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout holder = column();
        parent.addView(holder);
        parent.addView(spacer(12));
        io.execute(() -> {
            List<Media> list = new ArrayList<>();
            for (String key : keys.subList(0, Math.min(10, keys.size()))) {
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    Media m = fetchMeta(parts[0], parts[1]);
                    if (m != null) list.add(m);
                }
            }
            main.post(() -> {
                parent.removeView(p);
                if (!list.isEmpty()) holder.addView(mediaRail(list));
            });
        });
    }

    private void addHistory(Media item) {
        List<String> list = historyKeys();
        list.remove(item.key());
        list.add(0, item.key());
        if (list.size() > 20) list = new ArrayList<>(list.subList(0, 20));
        prefs.edit().putString(KEY_HISTORY, String.join("|", list)).apply();
    }

    private List<String> historyKeys() {
        String raw = prefs.getString(KEY_HISTORY, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\|")));
    }

    private boolean isFavorite(String key) {
        return prefs.getStringSet(KEY_FAVORITES, Collections.emptySet()).contains(key);
    }

    private void toggleFavorite(String key) {
        Set<String> copy = new LinkedHashSet<>(prefs.getStringSet(KEY_FAVORITES, Collections.emptySet()));
        if (!copy.add(key)) copy.remove(key);
        prefs.edit().putStringSet(KEY_FAVORITES, copy).apply();
    }

    private void showChannels() {
        prepareScreen();
        root.addView(text("ТВ / M3U", 34, Color.WHITE, true));
        root.addView(text("Добавь свой легальный M3U/M3U8-плейлист. CineDeck сохранит адрес только на этом устройстве.", 14, MUTED, false));
        root.addView(spacer(10));
        LinearLayout actions = row();
        actions.addView(action("⚙  Адрес плейлиста", v -> promptM3u()));
        actions.addView(action("↻  Обновить", v -> loadM3uIntoScreen()));
        actions.addView(action("▶  Прямая ссылка", v -> promptDirectStream()));
        root.addView(actions);
        root.addView(spacer(12));
        loadM3uIntoScreen();
    }

    private void promptM3u() {
        EditText e = new EditText(this);
        e.setText(prefs.getString(KEY_M3U, ""));
        e.setHint("https://.../playlist.m3u");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("M3U-плейлист").setView(e)
                .setPositiveButton("Сохранить", (d, w) -> { prefs.edit().putString(KEY_M3U, e.getText().toString().trim()).apply(); showChannels(); })
                .setNegativeButton("Отмена", null).show();
    }

    private void loadM3uIntoScreen() {
        String url = prefs.getString(KEY_M3U, "");
        if (url == null || url.isEmpty()) {
            root.addView(text("Плейлист ещё не настроен.", 17, MUTED, false));
            return;
        }
        ProgressBar p = new ProgressBar(this);
        root.addView(p, new LinearLayout.LayoutParams(dp(44), dp(44)));
        io.execute(() -> {
            List<Channel> channels = fetchM3u(url);
            main.post(() -> {
                root.removeView(p);
                if (channels.isEmpty()) {
                    root.addView(text("Не удалось загрузить каналы. Проверь URL и доступность плейлиста.", 16, MUTED, false));
                    return;
                }
                ScrollView sc = new ScrollView(this);
                LinearLayout list = column();
                for (Channel c : channels) {
                    Button b = new Button(this);
                    b.setAllCaps(false);
                    b.setText("▶  " + c.name);
                    b.setTextColor(Color.WHITE);
                    b.setTextSize(16);
                    b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    styleFocusable(b, PANEL);
                    b.setOnClickListener(v -> playStream(c.name, c.url));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
                    lp.setMargins(0, 0, 0, dp(5));
                    list.addView(b, lp);
                }
                sc.addView(list);
                root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            });
        });
    }

    private void promptDirectStream() {
        EditText e = new EditText(this);
        e.setHint("https://.../video.m3u8 / .mpd / .mp4");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Открыть медиассылку").setView(e)
                .setPositiveButton("Воспроизвести", (d, w) -> {
                    String url = e.getText().toString().trim();
                    if (!url.isEmpty()) playStream("Медиассылка", url);
                }).setNegativeButton("Отмена", null).show();
    }

    private void playStream(String title, String url) {
        prepareScreen();
        backAction = this::showChannels;
        root.addView(text(title, 25, Color.WHITE, true));
        PlayerView view = new PlayerView(this);
        view.setUseController(true);
        view.setBackgroundColor(Color.BLACK);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        try {
            player = new ExoPlayer.Builder(this).build();
            view.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть поток", Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void showSettings() {
        prepareScreen();
        root.addView(text("Настройки", 34, Color.WHITE, true));
        root.addView(spacer(8));
        root.addView(text("Версия 1.1.0 • Android TV / AOSP • Android 6.0+", 17, Color.WHITE, true));
        root.addView(text("Каталог: Cinemeta. Русское название и краткое описание карточки по возможности уточняются через русскую Википедию. Никаких API-ключей пользователя не требуется.", 14, MUTED, false));
        root.addView(spacer(12));
        root.addView(action("Очистить историю", v -> { prefs.edit().remove(KEY_HISTORY).apply(); Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show(); }));
        root.addView(action("Очистить избранное", v -> { prefs.edit().remove(KEY_FAVORITES).apply(); Toast.makeText(this, "Избранное очищено", Toast.LENGTH_SHORT).show(); }));
        root.addView(action("Настроить M3U", v -> promptM3u()));
        root.addView(spacer(10));
        root.addView(text("Важно: CineDeck показывает метаданные и открывает официальные сервисы. Он не обходит DRM, подписку или территориальные ограничения сервисов.", 14, MUTED, false));
    }

    private List<Media> fetchCatalog(String type, String genre, String year, int limit) {
        try {
            String url = CINEMETA + "/catalog/" + type + "/top" + (genre.isEmpty() ? "" : "/genre=" + Uri.encode(genre)) + ".json";
            JSONObject obj = new JSONObject(http(url));
            JSONArray metas = obj.optJSONArray("metas");
            if (metas == null) return Collections.emptyList();
            List<Media> out = new ArrayList<>();
            for (int i = 0; i < metas.length() && out.size() < limit; i++) {
                Media m = parseMedia(metas.optJSONObject(i), type);
                if (m == null) continue;
                if (!year.isEmpty() && !m.year.startsWith(year)) continue;
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Media> fetchSearch(String type, String query) {
        try {
            String url = CINEMETA + "/catalog/" + type + "/top/search=" + Uri.encode(query) + ".json";
            JSONObject obj = new JSONObject(http(url));
            JSONArray metas = obj.optJSONArray("metas");
            if (metas == null) return Collections.emptyList();
            List<Media> out = new ArrayList<>();
            for (int i = 0; i < metas.length() && i < 15; i++) {
                Media m = parseMedia(metas.optJSONObject(i), type);
                if (m != null) out.add(m);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Media fetchMeta(String type, String id) {
        try {
            JSONObject obj = new JSONObject(http(CINEMETA + "/meta/" + type + "/" + Uri.encode(id) + ".json"));
            JSONObject meta = obj.optJSONObject("meta");
            Media m = parseMedia(meta, type);
            if (m == null) return null;
            JSONArray videos = meta.optJSONArray("videos");
            if (videos != null) {
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.optJSONObject(i);
                    if (v == null) continue;
                    int season = v.optInt("season", 0);
                    int episode = v.optInt("episode", 0);
                    if (season > 0 && episode > 0) m.episodes.add(new Episode(season, episode, v.optString("name", "Серия " + episode)));
                }
            }
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    private Media parseMedia(JSONObject o, String fallbackType) {
        if (o == null) return null;
        String id = o.optString("id", "");
        String name = o.optString("name", "");
        if (id.isEmpty() || name.isEmpty()) return null;
        Media m = new Media();
        m.id = id;
        m.type = o.optString("type", fallbackType);
        m.name = name;
        m.poster = o.optString("poster", "");
        m.background = o.optString("background", "");
        m.description = clean(o.optString("description", "Описание пока отсутствует."));
        m.rating = o.optString("imdbRating", o.optString("rating", ""));
        m.year = o.optString("releaseInfo", o.optString("year", ""));
        if (m.year.contains("–")) m.year = m.year.substring(0, m.year.indexOf("–"));
        JSONArray gs = o.optJSONArray("genres");
        if (gs != null) for (int i = 0; i < gs.length(); i++) m.genres.add(gs.optString(i));
        return m;
    }

    private WikiInfo fetchRussianInfo(Media m) {
        WikiInfo info = new WikiInfo();
        try {
            String kind = m.type.equals("movie") ? " фильм" : " сериал";
            String query = m.name + kind + (m.year.isEmpty() ? "" : " " + m.year);
            String url = WIKI + "?action=query&generator=search&gsrsearch=" + Uri.encode(query) + "&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&exsentences=6&format=json&formatversion=2";
            JSONObject obj = new JSONObject(http(url));
            JSONArray pages = obj.optJSONObject("query") == null ? null : obj.optJSONObject("query").optJSONArray("pages");
            if (pages != null && pages.length() > 0) {
                JSONObject p = pages.optJSONObject(0);
                info.title = p.optString("title", "");
                info.extract = clean(p.optString("extract", ""));
            }
        } catch (Exception ignored) {}
        return info;
    }

    private List<Channel> fetchM3u(String url) {
        try {
            String raw = http(url);
            String[] lines = raw.replace("\r", "").split("\n");
            List<Channel> out = new ArrayList<>();
            String pending = null;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    int comma = line.lastIndexOf(',');
                    pending = comma >= 0 ? line.substring(comma + 1).trim() : "Канал";
                } else if (!line.isEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    out.add(new Channel(pending == null || pending.isEmpty() ? "Канал " + (out.size() + 1) : pending, line));
                    pending = null;
                    if (out.size() >= 100) break;
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String http(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(14000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "CineDeckRU/1.1 (Android TV)");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private void loadImage(ImageView target, String url) {
        if (url == null || url.isEmpty()) return;
        Bitmap cached = images.get(url);
        if (cached != null) { target.setImageBitmap(cached); return; }
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(9000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "CineDeckRU/1.1");
                Bitmap b;
                try (InputStream in = c.getInputStream()) { b = BitmapFactory.decodeStream(in); }
                c.disconnect();
                if (b != null) {
                    images.put(url, b);
                    main.post(() -> target.setImageBitmap(b));
                }
            } catch (Exception ignored) {}
        });
    }

    private void openService(Service s, String query) {
        String q = query == null ? "" : Uri.encode(query);
        String url = s.urlTemplate.contains("%s") ? String.format(Locale.ROOT, s.urlTemplate, q) : s.urlTemplate;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "На устройстве нет приложения для открытия ссылки", Toast.LENGTH_LONG).show();
        }
    }

    private String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("<[^>]+>", " ").replace("&amp;", "&").replace("&quot;", "\"").replaceAll("\\s+", " ").trim();
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout panel(int color) {
        LinearLayout v = column();
        v.setBackground(roundRect(color, 16));
        return v;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value == null ? "" : value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private Button nav(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(8), 0, dp(8), 0);
        styleFocusable(b, BG);
        b.setOnClickListener(listener);
        return b;
    }

    private Button action(String label) { return action(label, null); }

    private Button action(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(13), 0, dp(13), 0);
        styleFocusable(b, PANEL2);
        if (listener != null) b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button chip(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(12), 0, dp(12), 0);
        styleFocusable(b, PANEL2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
        lp.setMargins(0, 0, dp(7), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button filterChip(String label, boolean selected, View.OnClickListener click) {
        Button b = chip(label);
        b.setBackground(roundRect(selected ? FOCUS : PANEL2, 12));
        b.setOnClickListener(click);
        b.setOnFocusChangeListener((v, f) -> b.setBackground(roundRect(f ? PRIMARY : (selected ? FOCUS : PANEL2), 12)));
        return b;
    }

    private void styleFocusable(View v, int normal) {
        v.setFocusable(true);
        v.setBackground(roundRect(normal, 11));
        v.setOnFocusChangeListener((view, focus) -> view.setBackground(roundRect(focus ? FOCUS : normal, 11)));
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static class Service {
        final String name, urlTemplate, note;
        Service(String name, String urlTemplate, String note) { this.name = name; this.urlTemplate = urlTemplate; this.note = note; }
    }

    private static class Media {
        String id = "", type = "movie", name = "", poster = "", background = "", description = "", rating = "", year = "";
        final List<String> genres = new ArrayList<>();
        final List<Episode> episodes = new ArrayList<>();
        String key() { return type + ":" + id; }
        String shortMeta() { return (type.equals("movie") ? "Фильм" : "Сериал") + (year.isEmpty() ? "" : " • " + year) + (rating.isEmpty() ? "" : " • ★ " + rating); }
        String metaLine() {
            StringBuilder s = new StringBuilder(type.equals("movie") ? "Фильм" : "Сериал");
            if (!year.isEmpty()) s.append(" • ").append(year);
            if (!rating.isEmpty()) s.append(" • IMDb ★ ").append(rating);
            if (!genres.isEmpty()) s.append(" • ").append(String.join(", ", genres.subList(0, Math.min(3, genres.size()))));
            return s.toString();
        }
        String episodeSummary() {
            int maxSeason = 0;
            for (Episode e : episodes) maxSeason = Math.max(maxSeason, e.season);
            return maxSeason + " сезон(ов) • " + episodes.size() + " эпизодов в каталоге";
        }
    }

    private static class Episode {
        final int season, number; final String name;
        Episode(int season, int number, String name) { this.season = season; this.number = number; this.name = name == null ? "" : name; }
    }

    private static class WikiInfo { String title = "", extract = ""; }
    private static class Channel { final String name, url; Channel(String n, String u) { name = n; url = u; } }
}
