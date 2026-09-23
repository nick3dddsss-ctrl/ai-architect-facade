package com.cinedeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 11, 17);
    private static final int PANEL = Color.rgb(18, 25, 36);
    private static final int PANEL_FOCUS = Color.rgb(30, 49, 76);
    private static final int PRIMARY = Color.rgb(43, 140, 255);
    private static final int MUTED = Color.rgb(163, 174, 191);

    private static final String SAMPLE_BASE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/";

    private final List<MediaItem> media = Arrays.asList(
            new MediaItem(
                    "Орбита",
                    "2026 • Фантастика • Драма",
                    "HD • DEMO",
                    "Экипаж исследовательской станции получает сигнал, который меняет план обычной экспедиции.",
                    SAMPLE_BASE + "images/BigBuckBunny.jpg",
                    SAMPLE_BASE + "BigBuckBunny.mp4",
                    Color.rgb(20, 79, 132)),
            new MediaItem(
                    "Северный ветер",
                    "2025 • Сериал • Триллер",
                    "HD • DEMO",
                    "Мрачная северная история о расследовании, связывающем несколько городов.",
                    SAMPLE_BASE + "images/ElephantsDream.jpg",
                    SAMPLE_BASE + "ElephantsDream.mp4",
                    Color.rgb(37, 67, 88)),
            new MediaItem(
                    "Скорость света",
                    "2026 • Фантастика • Приключения",
                    "HD • DEMO",
                    "Испытание нового двигателя отправляет команду гораздо дальше от дома, чем планировалось.",
                    SAMPLE_BASE + "images/ForBiggerBlazes.jpg",
                    SAMPLE_BASE + "ForBiggerBlazes.mp4",
                    Color.rgb(142, 69, 23)),
            new MediaItem(
                    "Код города",
                    "2026 • Триллер • Детектив",
                    "HD • DEMO",
                    "Архитектор цифровой инфраструктуры замечает закономерность в серии городских сбоев.",
                    SAMPLE_BASE + "images/ForBiggerEscapes.jpg",
                    SAMPLE_BASE + "ForBiggerEscapes.mp4",
                    Color.rgb(34, 96, 87)),
            new MediaItem(
                    "Тихий сад",
                    "2024 • Драма",
                    "HD • DEMO",
                    "История о семье, старом доме и решениях, которые приходится принимать заново.",
                    SAMPLE_BASE + "images/ForBiggerFun.jpg",
                    SAMPLE_BASE + "ForBiggerFun.mp4",
                    Color.rgb(72, 104, 42)),
            new MediaItem(
                    "Нулевой этаж",
                    "2026 • Сериал • Мистика",
                    "HD • DEMO",
                    "В старом высотном здании появляется этаж, которого нет ни на одном плане.",
                    SAMPLE_BASE + "images/ForBiggerJoyrides.jpg",
                    SAMPLE_BASE + "ForBiggerJoyrides.mp4",
                    Color.rgb(91, 43, 113))
    );

    private final LinkedHashMap<String, Boolean> sources = new LinkedHashMap<>();
    private LinearLayout root;
    private boolean playerOpen = false;
    private MediaItem selectedItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        seedSources();
        showHome();
    }

    private void seedSources() {
        sources.put("CineDeck Demo", true);
        sources.put("TMDB metadata", false);
        sources.put("Jellyfin", false);
        sources.put("Emby", false);
        sources.put("Plex Media Server", false);
        sources.put("SMB / Windows Share", false);
        sources.put("WebDAV", false);
        sources.put("DLNA / UPnP", false);
        sources.put("M3U / M3U8", false);
        sources.put("HTTP / HTTPS / HLS", false);
        sources.put("Community Provider Manifest", false);
        sources.put("Custom JSON API", false);
        sources.put("RSS / Atom Media Feed", false);
        sources.put("PeerTube", false);
        sources.put("Internet Archive", false);
        sources.put("Kodi JSON-RPC", false);
        sources.put("Локальное хранилище / USB", false);
        sources.put("Внешний Torrent/P2P адаптер", false);
    }

    private void prepareScreen() {
        playerOpen = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(42), dp(20), dp(42), dp(28));
        setContentView(root);
        addTopBar();
    }

    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("▶  CineDeck", 28, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(250), dp(60)));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("Источники", v -> showSources()));
        bar.addView(nav("Настройки", v -> showSettings()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
    }

    private void showHome() {
        prepareScreen();
        MediaItem featured = media.get(0);

        LinearLayout hero = panel();
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setPadding(dp(34), dp(24), dp(24), dp(24));

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(text("CINEDECK TV • ANDROID TV", 14, PRIMARY, true));
        heroText.addView(text(featured.title, 46, Color.WHITE, true));
        heroText.addView(text(featured.meta + " • " + featured.badge, 17, MUTED, false));
        heroText.addView(spacer(10));
        heroText.addView(text(featured.description, 18, Color.LTGRAY, false));
        heroText.addView(spacer(16));
        LinearLayout actions = row();
        Button watch = action("▶ Смотреть");
        watch.setOnClickListener(v -> showPlayer(featured));
        actions.addView(watch);
        Button details = action("Подробнее");
        details.setOnClickListener(v -> showDetails(featured));
        actions.addView(details);
        heroText.addView(actions);

        hero.addView(heroText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        FrameLayout heroPoster = posterView(featured, true);
        LinearLayout.LayoutParams heroPosterLp = new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.MATCH_PARENT);
        heroPosterLp.setMargins(dp(24), 0, 0, 0);
        hero.addView(heroPoster, heroPosterLp);

        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        root.addView(spacer(20));
        addMediaSection("Сейчас популярно", media);
    }

    private void addMediaSection(String title, List<MediaItem> items) {
        root.addView(text(title, 25, Color.WHITE, true));
        root.addView(spacer(10));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();

        for (MediaItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(5), dp(5), dp(5), dp(8));
            card.setClickable(true);
            card.setFocusable(true);
            styleFocusable(card, PANEL);
            card.setOnClickListener(v -> showDetails(item));

            FrameLayout poster = posterView(item, false);
            card.addView(poster, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));
            TextView titleView = text(item.title, 18, Color.WHITE, true);
            titleView.setSingleLine(true);
            card.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
            TextView metaView = text(item.meta, 13, MUTED, false);
            metaView.setSingleLine(true);
            card.addView(metaView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(280), dp(225));
            lp.setMargins(0, 0, dp(14), 0);
            line.addView(card, lp);
        }

        scroll.addView(line);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
    }

    private FrameLayout posterView(MediaItem item, boolean wide) {
        FrameLayout poster = new FrameLayout(this);
        poster.setClipToOutline(true);
        poster.setBackground(roundRect(item.posterColor, 14));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(item.title);
        poster.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView fallback = text(item.title, wide ? 28 : 20, Color.WHITE, true);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(dp(14), dp(10), dp(14), dp(10));
        fallback.setBackground(roundRect(withAlpha(Color.BLACK, 90), 12));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.BOTTOM;
        flp.setMargins(dp(12), dp(12), dp(12), dp(12));
        poster.addView(fallback, flp);

        loadImage(item.posterUrl, image, fallback);
        return poster;
    }

    private void loadImage(String url, ImageView image, TextView fallback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "CineDeckTV/0.5 AndroidTV");
                connection.connect();
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return;
                try (InputStream in = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            image.setImageBitmap(bitmap);
                            fallback.setVisibility(View.GONE);
                        });
                    }
                }
            } catch (Exception ignored) {
                // The colored local fallback remains visible when a remote poster is unavailable.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showDetails(MediaItem item) {
        selectedItem = item;
        prepareScreen();

        LinearLayout box = panel();
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(dp(30), dp(28), dp(34), dp(28));

        FrameLayout poster = posterView(item, false);
        box.addView(poster, new LinearLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(36), 0, 0, 0);
        info.addView(text(item.badge, 15, PRIMARY, true));
        info.addView(text(item.title, 48, Color.WHITE, true));
        info.addView(text(item.meta, 18, MUTED, false));
        info.addView(spacer(18));
        info.addView(text(item.description, 21, Color.LTGRAY, false));
        info.addView(spacer(24));

        LinearLayout actions = row();
        Button play = action("▶ Смотреть");
        play.setOnClickListener(v -> showPlayer(item));
        actions.addView(play);
        Button streams = action("Источник / качество");
        streams.setOnClickListener(v -> chooseStream(item));
        actions.addView(streams);
        info.addView(actions);

        box.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(470)));
    }

    private void chooseStream(MediaItem item) {
        String[] variants = {
                "CineDeck Demo • отдельный ролик • " + item.badge,
                "HTTP/HTTPS • ввести прямой URL",
                "Jellyfin / Plex • после настройки"
        };
        new AlertDialog.Builder(this)
                .setTitle("Источник и качество")
                .setItems(variants, (d, which) -> {
                    if (which == 0) {
                        showPlayer(item);
                    } else if (which == 1) {
                        promptDirectStream(item);
                    } else {
                        Toast.makeText(this, "Настрой сервер в разделе «Источники»", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void promptDirectStream(MediaItem item) {
        EditText input = new EditText(this);
        input.setHint("https://example.org/video.mp4");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Прямой поток")
                .setMessage("Поддержка зависит от системного VideoView телевизора. Для теста лучше MP4/H.264.")
                .setView(input)
                .setPositiveButton("Открыть", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        showPlayer(item, url, "USER URL");
                    } else {
                        Toast.makeText(this, "Нужен HTTP/HTTPS URL", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showSearch() {
        prepareScreen();
        root.addView(text("Поиск", 38, Color.WHITE, true));

        EditText q = new EditText(this);
        q.setHint("Название фильма или сериала");
        q.setHintTextColor(MUTED);
        q.setTextColor(Color.WHITE);
        q.setSingleLine(true);
        q.setTextSize(20);
        q.setBackground(roundRect(PANEL, 14));
        q.setPadding(dp(18), 0, dp(18), 0);
        root.addView(q, new LinearLayout.LayoutParams(dp(720), dp(62)));
        root.addView(spacer(18));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable refresh = () -> {
            results.removeAllViews();
            String needle = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (MediaItem item : media) {
                if (needle.isEmpty()
                        || item.title.toLowerCase(Locale.ROOT).contains(needle)
                        || item.meta.toLowerCase(Locale.ROOT).contains(needle)) {
                    LinearLayout result = row();
                    result.setGravity(Gravity.CENTER_VERTICAL);
                    result.setClickable(true);
                    result.setFocusable(true);
                    result.setPadding(dp(8), dp(6), dp(16), dp(6));
                    styleFocusable(result, PANEL);
                    result.setOnClickListener(v -> showDetails(item));

                    FrameLayout thumb = posterView(item, false);
                    result.addView(thumb, new LinearLayout.LayoutParams(dp(150), dp(82)));

                    LinearLayout resultText = new LinearLayout(this);
                    resultText.setOrientation(LinearLayout.VERTICAL);
                    resultText.setPadding(dp(18), 0, 0, 0);
                    resultText.addView(text(item.title, 20, Color.WHITE, true));
                    resultText.addView(text(item.meta + " • " + item.badge, 15, MUTED, false));
                    result.addView(resultText, new LinearLayout.LayoutParams(0, dp(82), 1f));

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1050), dp(96));
                    lp.setMargins(0, 0, 0, dp(10));
                    results.addView(result, lp);
                }
            }
        };

        q.setOnEditorActionListener((v, actionId, event) -> {
            refresh.run();
            return true;
        });
        q.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP) refresh.run();
            return false;
        });
        refresh.run();
        q.requestFocus();
    }

    private void showSources() {
        prepareScreen();
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Источники и серверы", 36, Color.WHITE, true), new LinearLayout.LayoutParams(0, dp(70), 1f));
        Button add = action("+ Community URL");
        add.setOnClickListener(v -> promptCommunityUrl());
        head.addView(add);
        root.addView(head);
        root.addView(text("Подключаемые медиасерверы, сетевые папки, IPTV и пользовательские провайдеры.", 15, MUTED, false));
        root.addView(spacer(14));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Map.Entry<String, Boolean> e : sources.entrySet()) {
            CheckBox c = new CheckBox(this);
            c.setText(e.getKey());
            c.setTextColor(Color.WHITE);
            c.setTextSize(17);
            c.setChecked(e.getValue());
            c.setPadding(dp(18), dp(8), dp(18), dp(8));
            c.setButtonTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
            styleFocusable(c, PANEL);
            c.setOnCheckedChangeListener((button, checked) -> sources.put(e.getKey(), checked));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(900), dp(58));
            lp.setMargins(0, 0, 0, dp(8));
            list.addView(c, lp);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void promptCommunityUrl() {
        EditText input = new EditText(this);
        input.setHint("https://example.org/provider.json");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Добавить Community Provider")
                .setMessage("Укажи URL manifest/API провайдера, к которому у тебя есть право доступа.")
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        Toast.makeText(this, "Нужен HTTP/HTTPS URL", Toast.LENGTH_LONG).show();
                    } else {
                        sources.put("Community: " + url, true);
                        Toast.makeText(this, "Провайдер добавлен в текущую сессию", Toast.LENGTH_LONG).show();
                        showSources();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showSettings() {
        prepareScreen();
        root.addView(text("Настройки", 40, Color.WHITE, true));
        root.addView(spacer(18));
        addSetting("Версия", "0.5.0 APK");
        addSetting("Интерфейс", "Android TV / D-pad");
        addSetting("Постеры", "Remote image + local fallback");
        addSetting("Плеер", "Android VideoView • отдельный URL на карточку");
        addSetting("Активные источники", String.valueOf(sources.values().stream().filter(Boolean::booleanValue).count()));
        addSetting("Community providers", "URL / JSON manifest");
        root.addView(spacer(20));
        Button sourcesButton = action("Открыть источники");
        sourcesButton.setOnClickListener(v -> showSources());
        root.addView(sourcesButton, new LinearLayout.LayoutParams(dp(340), dp(62)));
    }

    private void addSetting(String label, String value) {
        LinearLayout line = row();
        TextView l = text(label, 19, Color.WHITE, false);
        TextView r = text(value, 19, MUTED, false);
        line.addView(l, new LinearLayout.LayoutParams(dp(360), dp(54)));
        line.addView(r, new LinearLayout.LayoutParams(dp(700), dp(54)));
        root.addView(line);
    }

    private void showPlayer(MediaItem item) {
        showPlayer(item, item.streamUrl, "CINEDECK DEMO");
    }

    private void showPlayer(MediaItem item, String streamUrl, String sourceLabel) {
        selectedItem = item;
        playerOpen = true;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        VideoView video = new VideoView(this);
        frame.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text("←  " + item.title + "   •   " + sourceLabel, 17, Color.WHITE, true);
        title.setPadding(dp(16), 0, dp(16), 0);
        title.setBackground(roundRect(withAlpha(Color.BLACK, 150), 10));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        tlp.gravity = Gravity.TOP | Gravity.LEFT;
        tlp.setMargins(dp(24), dp(20), 0, 0);
        frame.addView(title, tlp);

        setContentView(frame);

        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setVideoURI(Uri.parse(streamUrl));
        video.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            video.start();
        });
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Этот поток не воспроизводится на системном плеере. Нажми Назад и выбери другой источник.", Toast.LENGTH_LONG).show();
            return true;
        });
        video.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (playerOpen && selectedItem != null) {
            playerOpen = false;
            showDetails(selectedItem);
        } else {
            showHome();
        }
    }

    private Button nav(String label, View.OnClickListener click) {
        Button b = action(label);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(52));
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        styleFocusable(b, PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        lp.setMargins(0, 0, dp(12), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void styleFocusable(View view, int normalColor) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setBackground(roundRect(normalColor, 12));
        view.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(roundRect(focused ? PANEL_FOCUS : normalColor, 12));
            v.setScaleX(focused ? 1.045f : 1f);
            v.setScaleY(focused ? 1.045f : 1f);
        });
    }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(roundRect(PANEL, 18));
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class MediaItem {
        final String title;
        final String meta;
        final String badge;
        final String description;
        final String posterUrl;
        final String streamUrl;
        final int posterColor;

        MediaItem(String title, String meta, String badge, String description, String posterUrl, String streamUrl, int posterColor) {
            this.title = title;
            this.meta = meta;
            this.badge = badge;
            this.description = description;
            this.posterUrl = posterUrl;
            this.streamUrl = streamUrl;
            this.posterColor = posterColor;
        }
    }
}
