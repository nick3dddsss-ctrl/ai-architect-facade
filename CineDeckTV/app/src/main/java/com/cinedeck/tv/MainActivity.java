package com.cinedeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 11, 17);
    private static final int PANEL = Color.rgb(18, 25, 36);
    private static final int PANEL_FOCUS = Color.rgb(30, 49, 76);
    private static final int PRIMARY = Color.rgb(43, 140, 255);
    private static final int MUTED = Color.rgb(163, 174, 191);
    private static final String DEMO_STREAM = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4";

    private final List<MediaItem> media = Arrays.asList(
            new MediaItem("Орбита", "2026 • Фантастика • Драма", "4K • HDR", "Экипаж исследовательской станции получает сигнал, который меняет план обычной экспедиции."),
            new MediaItem("Северный ветер", "2025 • Сериал • Триллер", "FULL HD", "Мрачная северная история о расследовании, связывающем несколько городов."),
            new MediaItem("Скорость света", "2026 • Фантастика • Приключения", "4K", "Испытание нового двигателя отправляет команду гораздо дальше от дома, чем планировалось."),
            new MediaItem("Код города", "2026 • Триллер • Детектив", "4K • HDR10", "Архитектор цифровой инфраструктуры замечает закономерность в серии городских сбоев."),
            new MediaItem("Тихий сад", "2024 • Драма", "FULL HD", "История о семье, старом доме и решениях, которые приходится принимать заново."),
            new MediaItem("Нулевой этаж", "2026 • Сериал • Мистика", "4K", "В старом высотном здании появляется этаж, которого нет ни на одном плане.")
    );

    private final LinkedHashMap<String, Boolean> sources = new LinkedHashMap<>();
    private LinearLayout root;

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
        LinearLayout hero = panel();
        hero.setPadding(dp(34), dp(26), dp(34), dp(26));
        hero.addView(text("CINEDECK TV • ANDROID TV", 14, PRIMARY, true));
        hero.addView(text("Орбита", 48, Color.WHITE, true));
        hero.addView(text("2026 • Фантастика • Драма • 4K HDR", 17, MUTED, false));
        hero.addView(spacer(12));
        hero.addView(text("Первая APK-версия CineDeck: интерфейс под пульт, каталог, поиск, источники и встроенный демо-плеер.", 18, Color.LTGRAY, false));
        LinearLayout actions = row();
        Button watch = action("▶ Смотреть демо");
        watch.setOnClickListener(v -> showPlayer(media.get(0)));
        actions.addView(watch);
        Button details = action("Подробнее");
        details.setOnClickListener(v -> showDetails(media.get(0)));
        actions.addView(details);
        hero.addView(spacer(18));
        hero.addView(actions);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270)));
        root.addView(spacer(22));
        addMediaSection("Сейчас популярно", media);
    }

    private void addMediaSection(String title, List<MediaItem> items) {
        root.addView(text(title, 25, Color.WHITE, true));
        root.addView(spacer(10));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (MediaItem item : items) {
            Button card = new Button(this);
            card.setAllCaps(false);
            card.setText(item.title + "\n" + item.meta + "\n" + item.badge);
            card.setTextSize(16);
            card.setTextColor(Color.WHITE);
            card.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(12), dp(18), dp(12));
            styleFocusable(card, PANEL);
            card.setOnClickListener(v -> showDetails(item));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(145));
            lp.setMargins(0, 0, dp(14), 0);
            line.addView(card, lp);
        }
        scroll.addView(line);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
    }

    private void showDetails(MediaItem item) {
        prepareScreen();
        LinearLayout box = panel();
        box.setPadding(dp(42), dp(34), dp(42), dp(34));
        box.addView(text(item.badge, 15, PRIMARY, true));
        box.addView(text(item.title, 50, Color.WHITE, true));
        box.addView(text(item.meta, 18, MUTED, false));
        box.addView(spacer(18));
        box.addView(text(item.description, 21, Color.LTGRAY, false));
        box.addView(spacer(26));
        LinearLayout actions = row();
        Button play = action("▶ Смотреть");
        play.setOnClickListener(v -> showPlayer(item));
        actions.addView(play);
        Button streams = action("Источник / качество");
        streams.setOnClickListener(v -> chooseStream(item));
        actions.addView(streams);
        box.addView(actions);
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
    }

    private void chooseStream(MediaItem item) {
        String[] variants = {"CineDeck Demo • 1080p • Auto", "HTTP/HLS • пользовательский URL", "Jellyfin / Plex • после настройки"};
        new AlertDialog.Builder(this)
                .setTitle("Источник и качество")
                .setItems(variants, (d, which) -> {
                    if (which == 0) showPlayer(item);
                    else Toast.makeText(this, "Настрой источник в разделе «Источники»", Toast.LENGTH_LONG).show();
                }).show();
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
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Runnable refresh = () -> {
            results.removeAllViews();
            String needle = q.getText().toString().trim().toLowerCase();
            for (MediaItem item : media) {
                if (needle.isEmpty() || item.title.toLowerCase().contains(needle) || item.meta.toLowerCase().contains(needle)) {
                    Button b = action(item.title + "   —   " + item.meta);
                    b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    b.setOnClickListener(v -> showDetails(item));
                    results.addView(b, new LinearLayout.LayoutParams(dp(900), dp(62)));
                }
            }
        };
        q.setOnEditorActionListener((v, actionId, event) -> { refresh.run(); return true; });
        q.setOnKeyListener((v, keyCode, event) -> { if (event.getAction() == KeyEvent.ACTION_UP) refresh.run(); return false; });
        refresh.run();
        q.requestFocus();
    }

    private void showSources() {
        prepareScreen();
        LinearLayout head = row();
        head.addView(text("Источники и серверы", 36, Color.WHITE, true));
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
        addSetting("Версия", "0.4.0 APK prototype");
        addSetting("Интерфейс", "Android TV / D-pad");
        addSetting("Плеер", "Android VideoView (demo)");
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
        line.addView(r, new LinearLayout.LayoutParams(dp(520), dp(54)));
        root.addView(line);
    }

    private void showPlayer(MediaItem item) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        VideoView video = new VideoView(this);
        frame.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = text("←  " + item.title + "   •   DEMO STREAM", 17, Color.WHITE, true);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        tlp.gravity = Gravity.TOP | Gravity.LEFT;
        tlp.setMargins(dp(24), dp(20), 0, 0);
        frame.addView(title, tlp);
        setContentView(frame);
        video.setVideoURI(Uri.parse(DEMO_STREAM));
        video.setMediaController(new android.widget.MediaController(this));
        video.setOnPreparedListener(mp -> { mp.setLooping(false); video.start(); });
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Демо-поток недоступен. Нажми Назад.", Toast.LENGTH_LONG).show();
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        showHome();
    }

    private Button nav(String label, View.OnClickListener click) {
        Button b = action(label);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(170), dp(50));
        lp.setMargins(0, 0, dp(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(18), 0, dp(18), 0);
        styleFocusable(b, PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        lp.setMargins(0, 0, dp(12), dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private void styleFocusable(View v, int baseColor) {
        v.setBackground(roundRect(baseColor, 14));
        v.setOnFocusChangeListener((view, hasFocus) -> {
            view.setBackground(roundRect(hasFocus ? PANEL_FOCUS : baseColor, 14));
            view.setScaleX(hasFocus ? 1.035f : 1f);
            view.setScaleY(hasFocus ? 1.035f : 1f);
        });
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(roundRect(PANEL, 22));
        return p;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class MediaItem {
        final String title;
        final String meta;
        final String badge;
        final String description;
        MediaItem(String title, String meta, String badge, String description) {
            this.title = title; this.meta = meta; this.badge = badge; this.description = description;
        }
    }
}
