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
import android.text.Html;
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

import androidx.annotation.Nullable;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 16);
    private static final int PANEL = Color.rgb(18, 24, 34);
    private static final int PANEL_2 = Color.rgb(25, 33, 47);
    private static final int FOCUS = Color.rgb(38, 75, 122);
    private static final int PRIMARY = Color.rgb(54, 143, 255);
    private static final int MUTED = Color.rgb(157, 170, 190);
    private static final int GOOD = Color.rgb(68, 196, 122);

    private static final String PREFS = "cinedeck_ru";
    private static final String KEY_M3U = "m3u_url";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final String TVMAZE = "https://api.tvmaze.com";

    private final ExecutorService io = Executors.newFixedThreadPool(5);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> imageCache = new LruCache<>(40);

    private LinearLayout root;
    private SharedPreferences prefs;
    private ExoPlayer activePlayer;
    private Runnable backAction;

    private final List<Service> services = Arrays.asList(
            new Service("VK Видео", "com.vk.tv", "https://vkvideo.ru/", "Бесплатное видео • до 4K"),
            new Service("RUTUBE", "rtb.mobile.android", "https://rutube.ru/", "Видео, кино, ТВ, эфиры"),
            new Service("Кинопоиск", "ru.kinopoisk.tv", "https://www.kinopoisk.ru/", "Кино и сериалы"),
            new Service("Wink", "ru.rt.video.app.tv", "https://wink.ru/", "Кино • ТВ • спорт"),
            new Service("Иви", "ru.ivi.client", "https://www.ivi.ru/", "Кино • сериалы • ТВ"),
            new Service("Okko", "ru.more.play", "https://okko.tv/", "Кино • сериалы • спорт"),
            new Service("KION", "ru.mts.mtstv", "https://kion.ru/", "Кино • сериалы • ТВ")
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

    private void prepareScreen() {
        releasePlayer();
        backAction = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(38), dp(16), dp(38), dp(26));
        setContentView(root);
        addTopBar();
    }

    private void addTopBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("▶  CineDeck RU", 26, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(265), dp(60)));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("Сервисы", v -> showServices()));
        bar.addView(nav("ТВ / M3U", v -> showChannels()));
        bar.addView(nav("Избранное", v -> showFavorites()));
        bar.addView(nav("Настройки", v -> showSettings()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
    }

    private void showHome() {
        prepareScreen();

        LinearLayout hero = panel(PANEL);
        hero.setPadding(dp(30), dp(22), dp(30), dp(22));
        hero.addView(text("CINEDECK RU • ANDROID TV / AOSP", 13, PRIMARY, true));
        hero.addView(text("Всё видео — в одном месте", 39, Color.WHITE, true));
        hero.addView(text("Реальный каталог, российские видеосервисы, M3U/IPTV и прямые HLS/DASH/MP4-потоки. Без обязательной привязки к Google Play.", 17, Color.LTGRAY, false));
        hero.addView(spacer(14));
        LinearLayout actions = row();
        Button search = action("⌕  Найти фильм / сериал");
        search.setOnClickListener(v -> showSearch());
        actions.addView(search);
        Button channels = action("▣  Мои каналы");
        channels.setOnClickListener(v -> showChannels());
        actions.addView(channels);
        Button direct = action("▶  Открыть ссылку");
        direct.setOnClickListener(v -> promptDirectStream());
        actions.addView(direct);
        hero.addView(actions);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        root.addView(spacer(16));
        addServiceStrip();
        root.addView(spacer(18));

        ScrollView vertical = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        vertical.addView(content);

        addHistorySection(content);
        addCatalogSection(content, "Популярные сериалы", Arrays.asList("Fallout", "The Last of Us", "Wednesday", "Sherlock", "Chernobyl", "The Boys", "Dark", "House of the Dragon"));
        addCatalogSection(content, "Для вечернего просмотра", Arrays.asList("Severance", "Silo", "The Bear", "Slow Horses", "Black Mirror", "True Detective", "Fargo", "Foundation"));

        root.addView(vertical, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void addServiceStrip() {
        root.addView(text("Российские сервисы", 22, Color.WHITE, true));
        root.addView(spacer(8));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (Service service : services) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(service.name + "\n" + service.note);
            b.setTextSize(14);
            b.setTextColor(Color.WHITE);
            b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            b.setPadding(dp(16), dp(8), dp(16), dp(8));
            styleFocusable(b, PANEL_2);
            b.setOnClickListener(v -> openService(service, null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(205), dp(78));
            lp.setMargins(0, 0, dp(10), 0);
            line.addView(b, lp);
        }
        scroll.addView(line);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));
    }

    private void addCatalogSection(LinearLayout parent, String title, List<String> queries) {
        parent.addView(text(title, 24, Color.WHITE, true));
        parent.addView(spacer(8));
        ProgressBar progress = new ProgressBar(this);
        parent.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        parent.addView(holder);
        parent.addView(spacer(20));

        io.execute(() -> {
            List<ShowItem> found = new ArrayList<>();
            for (String q : queries) {
                ShowItem item = searchFirstShow(q);
                if (item != null) found.add(item);
            }
            main.post(() -> {
                parent.removeView(progress);
                if (found.isEmpty()) {
                    holder.addView(text("Каталог временно недоступен. Проверь интернет или используй российские сервисы выше.", 16, MUTED, false));
                } else {
                    holder.addView(buildMediaRow(found));
                }
            });
        });
    }

    private void addHistorySection(LinearLayout parent) {
        List<Integer> ids = getHistoryIds();
        if (ids.isEmpty()) return;
        parent.addView(text("Недавно открывали", 24, Color.WHITE, true));
        parent.addView(spacer(8));
        ProgressBar progress = new ProgressBar(this);
        parent.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        parent.addView(holder);
        parent.addView(spacer(20));

        io.execute(() -> {
            List<ShowItem> list = new ArrayList<>();
            for (Integer id : ids.subList(0, Math.min(ids.size(), 8))) {
                ShowItem item = getShow(id);
                if (item != null) list.add(item);
            }
            main.post(() -> {
                parent.removeView(progress);
                if (!list.isEmpty()) holder.addView(buildMediaRow(list));
            });
        });
    }

    private View buildMediaRow(List<ShowItem> items) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (ShowItem item : items) line.addView(mediaCard(item));
        scroll.addView(line);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        return scroll;
    }

    private View mediaCard(ShowItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(7), dp(7), dp(7), dp(8));
        card.setBackground(roundRect(PANEL, 14));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(roundRect(PANEL_2, 12));
        card.addView(poster, new LinearLayout.LayoutParams(dp(178), dp(248)));
        loadImage(poster, item.posterUrl);

        TextView name = text(item.name, 15, Color.WHITE, true);
        name.setMaxLines(1);
        card.addView(name, new LinearLayout.LayoutParams(dp(178), dp(30)));
        String meta = (item.year.isEmpty() ? "" : item.year + " • ") + (item.rating.isEmpty() ? "TV" : "★ " + item.rating);
        card.addView(text(meta, 13, MUTED, false), new LinearLayout.LayoutParams(dp(178), dp(26)));

        card.setOnFocusChangeListener((v, hasFocus) -> {
            card.setBackground(roundRect(hasFocus ? FOCUS : PANEL, 14));
            float s = hasFocus ? 1.055f : 1f;
            card.animate().scaleX(s).scaleY(s).setDuration(100).start();
        });
        card.setOnClickListener(v -> showDetails(item));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(196), dp(318));
        lp.setMargins(0, 0, dp(14), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void showSearch() {
        prepareScreen();
        root.addView(text("Поиск в каталоге", 34, Color.WHITE, true));
        root.addView(text("Названия, постеры и описания загружаются из открытого каталога. Для просмотра выбери российский сервис или свой источник.", 15, MUTED, false));
        root.addView(spacer(12));

        LinearLayout searchRow = row();
        EditText q = new EditText(this);
        q.setHint("Например: Fallout, Шерлок, Чернобыль…");
        q.setHintTextColor(MUTED);
        q.setTextColor(Color.WHITE);
        q.setSingleLine(true);
        q.setTextSize(19);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setBackground(roundRect(PANEL, 12));
        q.setPadding(dp(16), 0, dp(16), 0);
        searchRow.addView(q, new LinearLayout.LayoutParams(dp(690), dp(58)));
        Button go = action("Найти");
        searchRow.addView(go, new LinearLayout.LayoutParams(dp(150), dp(58)));
        root.addView(searchRow);
        root.addView(spacer(14));

        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable runSearch = () -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty()) return;
            progress.setVisibility(View.VISIBLE);
            results.removeAllViews();
            io.execute(() -> {
                List<ShowItem> items = searchShows(query);
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    if (items.isEmpty()) {
                        results.addView(text("Ничего не найдено. Попробуй другое название.", 18, MUTED, false));
                        return;
                    }
                    for (ShowItem item : items) results.addView(searchResultRow(item));
                });
            });
        };
        go.setOnClickListener(v -> runSearch.run());
        q.setOnEditorActionListener((v, actionId, event) -> {
            runSearch.run();
            return true;
        });
        q.requestFocus();
    }

    private View searchResultRow(ShowItem item) {
        LinearLayout resultRow = new LinearLayout(this);
        resultRow.setOrientation(LinearLayout.HORIZONTAL);
        resultRow.setGravity(Gravity.CENTER_VERTICAL);
        resultRow.setFocusable(true);
        resultRow.setClickable(true);
        resultRow.setPadding(dp(10), dp(8), dp(16), dp(8));
        resultRow.setBackground(roundRect(PANEL, 12));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        resultRow.addView(image, new LinearLayout.LayoutParams(dp(82), dp(112)));
        loadImage(image, item.posterUrl);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);
        info.addView(text(item.name, 20, Color.WHITE, true));
        info.addView(text(item.metaLine(), 14, MUTED, false));
        TextView summary = text(item.summary, 14, Color.LTGRAY, false);
        summary.setMaxLines(2);
        info.addView(summary);
        resultRow.addView(info, new LinearLayout.LayoutParams(0, dp(112), 1));
        resultRow.setOnFocusChangeListener((v, focused) -> resultRow.setBackground(roundRect(focused ? FOCUS : PANEL, 12)));
        resultRow.setOnClickListener(v -> showDetails(item));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130));
        lp.setMargins(0, 0, 0, dp(10));
        resultRow.setLayoutParams(lp);
        return resultRow;
    }

    private void showDetails(ShowItem item) {
        prepareScreen();
        addHistory(item.id);
        backAction = this::showHome;

        LinearLayout body = row();
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(roundRect(PANEL, 14));
        body.addView(poster, new LinearLayout.LayoutParams(dp(260), dp(375)));
        loadImage(poster, item.posterUrl);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(28), 0, 0, 0);
        info.addView(text(item.name, 42, Color.WHITE, true));
        info.addView(text(item.metaLine(), 17, MUTED, false));
        info.addView(spacer(12));
        TextView summary = text(item.summary.isEmpty() ? "Описание отсутствует." : item.summary, 18, Color.LTGRAY, false);
        summary.setMaxLines(7);
        info.addView(summary);
        info.addView(spacer(18));

        LinearLayout actions = row();
        Button watch = action("▶ Где смотреть");
        watch.setOnClickListener(v -> chooseService(item.name));
        actions.addView(watch);
        Button episodes = action("Сезоны / серии");
        episodes.setOnClickListener(v -> showEpisodes(item));
        actions.addView(episodes);
        Button favorite = action(isFavorite(item.id) ? "★ В избранном" : "☆ В избранное");
        favorite.setOnClickListener(v -> {
            toggleFavorite(item.id);
            favorite.setText(isFavorite(item.id) ? "★ В избранном" : "☆ В избранное");
        });
        actions.addView(favorite);
        info.addView(actions);

        info.addView(spacer(12));
        info.addView(text("Платный контент открывается в официальном приложении сервиса. Свои M3U/HLS/DASH/MP4 воспроизводятся внутри CineDeck.", 13, MUTED, false));
        body.addView(info, new LinearLayout.LayoutParams(0, dp(390), 1));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400)));
    }

    private void showEpisodes(ShowItem show) {
        prepareScreen();
        backAction = () -> showDetails(show);
        root.addView(text(show.name + " — серии", 32, Color.WHITE, true));
        root.addView(spacer(10));
        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        io.execute(() -> {
            List<EpisodeItem> episodes = getEpisodes(show.id);
            main.post(() -> {
                root.removeView(progress);
                if (episodes.isEmpty()) {
                    list.addView(text("Список серий недоступен.", 18, MUTED, false));
                    return;
                }
                int max = Math.min(episodes.size(), 250);
                for (int i = 0; i < max; i++) {
                    EpisodeItem ep = episodes.get(i);
                    Button b = action("S" + ep.season + "E" + ep.number + "  •  " + ep.name + (ep.airdate.isEmpty() ? "" : "  •  " + ep.airdate));
                    b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    b.setOnClickListener(v -> chooseService(show.name + " " + ep.name));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
                    lp.setMargins(0, 0, 0, dp(7));
                    list.addView(b, lp);
                }
            });
        });
    }

    private void chooseService(String query) {
        String[] names = new String[services.size() + 2];
        for (int i = 0; i < services.size(); i++) names[i] = services.get(i).name;
        names[services.size()] = "Моя ссылка / HLS / DASH / MP4";
        names[services.size() + 1] = "Мои M3U-каналы";
        new AlertDialog.Builder(this)
                .setTitle("Где смотреть")
                .setItems(names, (dialog, which) -> {
                    if (which < services.size()) openService(services.get(which), query);
                    else if (which == services.size()) promptDirectStream();
                    else showChannels();
                })
                .show();
    }

    private void showServices() {
        prepareScreen();
        root.addView(text("Российские видеосервисы", 34, Color.WHITE, true));
        root.addView(text("CineDeck запускает установленное приложение. Если его нет — открывает официальный сайт сервиса.", 15, MUTED, false));
        root.addView(spacer(14));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Service s : services) {
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(text(s.name + "\n" + s.note, 18, Color.WHITE, true), new LinearLayout.LayoutParams(dp(420), dp(74)));
            boolean installed = getPackageManager().getLaunchIntentForPackage(s.packageName) != null;
            line.addView(text(installed ? "● установлено" : "○ открыть сайт", 15, installed ? GOOD : MUTED, false), new LinearLayout.LayoutParams(dp(190), dp(74)));
            Button open = action(installed ? "Открыть" : "Сайт");
            open.setOnClickListener(v -> openService(s, null));
            line.addView(open, new LinearLayout.LayoutParams(dp(180), dp(58)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
            lp.setMargins(0, 0, 0, dp(8));
            list.addView(line, lp);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void openService(Service service, @Nullable String query) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(service.packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                if (query != null && !query.isEmpty()) Toast.makeText(this, "Поиск: " + query, Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(service.webUrl)));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть " + service.name, Toast.LENGTH_LONG).show();
        }
    }

    private void promptDirectStream() {
        EditText input = new EditText(this);
        input.setHint("https://.../video.m3u8, .mpd или .mp4");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Открыть медиассылку")
                .setView(input)
                .setPositiveButton("Воспроизвести", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.startsWith("http://") || url.startsWith("https://")) showPlayer(url, "Медиапоток");
                    else Toast.makeText(this, "Нужна HTTP/HTTPS ссылка", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showChannels() {
        prepareScreen();
        backAction = this::showHome;
        root.addView(text("Мои ТВ-каналы / M3U", 34, Color.WHITE, true));
        String playlistUrl = prefs.getString(KEY_M3U, "");
        if (playlistUrl == null || playlistUrl.trim().isEmpty()) {
            root.addView(text("Добавь URL своего M3U/M3U8-плейлиста. Подойдут ваши легальные IPTV-плейлисты, домашний сервер или операторский плейлист.", 17, MUTED, false));
            root.addView(spacer(16));
            Button setup = action("Настроить M3U");
            setup.setOnClickListener(v -> showSettings());
            root.addView(setup, new LinearLayout.LayoutParams(dp(290), dp(62)));
            return;
        }
        root.addView(text(playlistUrl, 13, MUTED, false));
        root.addView(spacer(10));
        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        String finalPlaylistUrl = playlistUrl;
        io.execute(() -> {
            List<ChannelItem> channels = loadM3u(finalPlaylistUrl);
            main.post(() -> {
                root.removeView(progress);
                if (channels.isEmpty()) {
                    list.addView(text("Не удалось прочитать плейлист. Проверь URL в настройках.", 18, MUTED, false));
                    return;
                }
                int max = Math.min(channels.size(), 500);
                for (int i = 0; i < max; i++) {
                    ChannelItem c = channels.get(i);
                    Button b = action("▶  " + c.name);
                    b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    b.setOnClickListener(v -> showPlayer(c.url, c.name));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
                    lp.setMargins(0, 0, 0, dp(6));
                    list.addView(b, lp);
                }
            });
        });
    }

    private void showPlayer(String url, String title) {
        releasePlayer();
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        PlayerView playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        frame.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView label = text("←  " + title, 16, Color.WHITE, true);
        label.setBackground(roundRect(Color.argb(170, 0, 0, 0), 10));
        label.setPadding(dp(14), 0, dp(14), 0);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        tlp.gravity = Gravity.TOP | Gravity.LEFT;
        tlp.setMargins(dp(18), dp(14), 0, 0);
        frame.addView(label, tlp);
        setContentView(frame);

        activePlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(activePlayer);
        activePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(url)));
        activePlayer.prepare();
        activePlayer.play();
        backAction = this::showHome;
    }

    private void releasePlayer() {
        if (activePlayer != null) {
            activePlayer.release();
            activePlayer = null;
        }
    }

    private void showFavorites() {
        prepareScreen();
        root.addView(text("Избранное", 34, Color.WHITE, true));
        Set<String> fav = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, Collections.emptySet()));
        if (fav.isEmpty()) {
            root.addView(text("Пока пусто. Открой карточку сериала и нажми «В избранное».", 17, MUTED, false));
            return;
        }
        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        root.addView(holder);
        io.execute(() -> {
            List<ShowItem> items = new ArrayList<>();
            for (String s : fav) {
                try {
                    ShowItem item = getShow(Integer.parseInt(s));
                    if (item != null) items.add(item);
                } catch (Exception ignored) { }
            }
            main.post(() -> {
                root.removeView(progress);
                if (items.isEmpty()) holder.addView(text("Не удалось загрузить избранное.", 17, MUTED, false));
                else holder.addView(buildMediaRow(items));
            });
        });
    }

    private void showSettings() {
        prepareScreen();
        root.addView(text("Настройки", 34, Color.WHITE, true));
        root.addView(text("CineDeck RU 1.0 • Android TV и AOSP-приставки, в том числе используемые в России.", 15, MUTED, false));
        root.addView(spacer(16));
        root.addView(text("M3U / IPTV-плейлист", 18, Color.WHITE, true));
        EditText m3u = new EditText(this);
        m3u.setText(prefs.getString(KEY_M3U, ""));
        m3u.setHint("http://.../playlist.m3u");
        m3u.setSingleLine(true);
        m3u.setTextColor(Color.WHITE);
        m3u.setHintTextColor(MUTED);
        m3u.setBackground(roundRect(PANEL, 12));
        m3u.setPadding(dp(14), 0, dp(14), 0);
        root.addView(m3u, new LinearLayout.LayoutParams(dp(850), dp(58)));
        root.addView(spacer(10));
        Button save = action("Сохранить M3U");
        save.setOnClickListener(v -> {
            prefs.edit().putString(KEY_M3U, m3u.getText().toString().trim()).apply();
            Toast.makeText(this, "Плейлист сохранён", Toast.LENGTH_SHORT).show();
        });
        root.addView(save, new LinearLayout.LayoutParams(dp(260), dp(58)));
        root.addView(spacer(20));
        Button clearHistory = action("Очистить историю");
        clearHistory.setOnClickListener(v -> {
            prefs.edit().remove(KEY_HISTORY).apply();
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show();
        });
        root.addView(clearHistory, new LinearLayout.LayoutParams(dp(260), dp(58)));
        root.addView(spacer(18));
        root.addView(text("Плеер: HLS (.m3u8), DASH (.mpd), MP4 и форматы Media3. HTTP разрешён для пользовательских локальных/операторских плейлистов.", 14, MUTED, false));
    }

    private List<ShowItem> searchShows(String query) {
        List<ShowItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(fetchText(TVMAZE + "/search/shows?q=" + Uri.encode(query)));
            for (int i = 0; i < arr.length() && i < 30; i++) {
                ShowItem item = parseShow(arr.getJSONObject(i).optJSONObject("show"));
                if (item != null) out.add(item);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private ShowItem searchFirstShow(String query) {
        List<ShowItem> list = searchShows(query);
        return list.isEmpty() ? null : list.get(0);
    }

    private ShowItem getShow(int id) {
        try { return parseShow(new JSONObject(fetchText(TVMAZE + "/shows/" + id))); }
        catch (Exception e) { return null; }
    }

    private ShowItem parseShow(JSONObject show) {
        if (show == null) return null;
        try {
            ShowItem item = new ShowItem();
            item.id = show.optInt("id", -1);
            item.name = show.optString("name", "Без названия");
            item.premiered = show.optString("premiered", "");
            item.year = item.premiered.length() >= 4 ? item.premiered.substring(0, 4) : "";
            JSONObject rating = show.optJSONObject("rating");
            if (rating != null && !rating.isNull("average")) item.rating = String.valueOf(rating.optDouble("average"));
            JSONArray genres = show.optJSONArray("genres");
            if (genres != null) {
                List<String> gs = new ArrayList<>();
                for (int i = 0; i < genres.length(); i++) gs.add(genres.optString(i));
                item.genres = String.join(" • ", gs);
            }
            JSONObject image = show.optJSONObject("image");
            if (image != null) item.posterUrl = image.optString("medium", "");
            String raw = show.optString("summary", "");
            item.summary = raw.isEmpty() ? "" : Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim();
            JSONObject network = show.optJSONObject("network");
            JSONObject webChannel = show.optJSONObject("webChannel");
            if (network != null) item.network = network.optString("name", "");
            else if (webChannel != null) item.network = webChannel.optString("name", "");
            return item.id < 0 ? null : item;
        } catch (Exception e) { return null; }
    }

    private List<EpisodeItem> getEpisodes(int showId) {
        List<EpisodeItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(fetchText(TVMAZE + "/shows/" + showId + "/episodes"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                EpisodeItem ep = new EpisodeItem();
                ep.name = o.optString("name", "Серия");
                ep.season = o.optInt("season", 0);
                ep.number = o.optInt("number", 0);
                ep.airdate = o.optString("airdate", "");
                out.add(ep);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private List<ChannelItem> loadM3u(String url) {
        List<ChannelItem> out = new ArrayList<>();
        try {
            String text = fetchText(url);
            String pendingName = null;
            for (String rawLine : text.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.startsWith("#EXTINF")) {
                    int comma = line.lastIndexOf(',');
                    pendingName = comma >= 0 ? line.substring(comma + 1).trim() : "Канал";
                } else if (!line.isEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    ChannelItem item = new ChannelItem();
                    item.name = pendingName == null || pendingName.isEmpty() ? "Канал " + (out.size() + 1) : pendingName;
                    item.url = line;
                    out.add(item);
                    pendingName = null;
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private String fetchText(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(9000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "CineDeckRU/1.0 AndroidTV");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { c.disconnect(); }
    }

    private void loadImage(ImageView target, String url) {
        if (url == null || url.isEmpty()) return;
        Bitmap cached = imageCache.get(url);
        if (cached != null) { target.setImageBitmap(cached); return; }
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", "CineDeckRU/1.0");
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) {
                        imageCache.put(url, b);
                        main.post(() -> target.setImageBitmap(b));
                    }
                }
            } catch (Exception ignored) { }
            finally { if (c != null) c.disconnect(); }
        });
    }

    private void addHistory(int id) {
        List<Integer> ids = getHistoryIds();
        ids.remove(Integer.valueOf(id));
        ids.add(0, id);
        if (ids.size() > 20) ids = new ArrayList<>(ids.subList(0, 20));
        StringBuilder sb = new StringBuilder();
        for (Integer value : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(value);
        }
        prefs.edit().putString(KEY_HISTORY, sb.toString()).apply();
    }

    private List<Integer> getHistoryIds() {
        List<Integer> out = new ArrayList<>();
        String raw = prefs.getString(KEY_HISTORY, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(",")) {
            try { out.add(Integer.parseInt(s)); } catch (Exception ignored) { }
        }
        return out;
    }

    private boolean isFavorite(int id) {
        return prefs.getStringSet(KEY_FAVORITES, Collections.emptySet()).contains(String.valueOf(id));
    }

    private void toggleFavorite(int id) {
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, Collections.emptySet()));
        String key = String.valueOf(id);
        if (set.contains(key)) set.remove(key); else set.add(key);
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    @Override
    public void onBackPressed() {
        if (activePlayer != null) {
            releasePlayer();
            showHome();
            return;
        }
        if (backAction != null) {
            Runnable action = backAction;
            backAction = null;
            action.run();
            return;
        }
        showHome();
    }

    private Button nav(String label, View.OnClickListener click) {
        Button b = action(label);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        lp.setMargins(0, 0, dp(6), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(15), 0, dp(15), 0);
        styleFocusable(b, PANEL_2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(54));
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void styleFocusable(View view, int normalColor) {
        view.setFocusable(true);
        view.setBackground(roundRect(normalColor, 12));
        view.setOnFocusChangeListener((v, focused) -> v.setBackground(roundRect(focused ? FOCUS : normalColor, 12)));
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout panel(int color) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(roundRect(color, 18));
        return p;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static class ShowItem {
        int id;
        String name = "";
        String premiered = "";
        String year = "";
        String rating = "";
        String genres = "";
        String posterUrl = "";
        String summary = "";
        String network = "";
        String metaLine() {
            List<String> p = new ArrayList<>();
            if (!year.isEmpty()) p.add(year);
            if (!genres.isEmpty()) p.add(genres);
            if (!rating.isEmpty()) p.add("★ " + rating);
            if (!network.isEmpty()) p.add(network);
            return String.join(" • ", p);
        }
    }

    private static class EpisodeItem {
        String name = "";
        int season;
        int number;
        String airdate = "";
    }

    private static class ChannelItem {
        String name = "";
        String url = "";
    }

    private static class Service {
        final String name;
        final String packageName;
        final String webUrl;
        final String note;
        Service(String name, String packageName, String webUrl, String note) {
            this.name = name;
            this.packageName = packageName;
            this.webUrl = webUrl;
            this.note = note;
        }
    }
}
