package com.cinedeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.InputType;
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

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityPlayer extends Activity {
    private static final int BG = Color.rgb(7,10,16);
    private static final int PANEL = Color.rgb(19,25,35);
    private static final int PANEL2 = Color.rgb(27,35,49);
    private static final int FOCUS = Color.rgb(44,78,120);
    private static final int MUTED = Color.rgb(155,169,190);
    private static final int PRIMARY = Color.rgb(74,156,255);
    private static final int FILE_REQUEST = 51;

    private static final String FEED = "https://cdn.jsdelivr.net/gh/nick3dddsss-ctrl/ai-architect-facade@cinedeck-tv-build/catalog-data/catalog.json?v=3";
    private static final String FEED_RAW = "https://raw.githubusercontent.com/nick3dddsss-ctrl/ai-architect-facade/cinedeck-tv-build/catalog-data/catalog.json?v=3";
    private static final String PREFS = "cinedeck_player";
    private static final String KEY_M3U = "m3u_url";
    private static final String KEY_PROVIDER = "provider_url";

    private final ExecutorService io = Executors.newFixedThreadPool(5);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Item> cache = new ArrayList<>();
    private final List<Service> services = Arrays.asList(
            new Service("VK Видео", "https://vkvideo.ru/video?q=%s"),
            new Service("RUTUBE", "https://rutube.ru/search/?query=%s"),
            new Service("Кинопоиск", "https://www.kinopoisk.ru/index.php?kp_query=%s"),
            new Service("Wink", "https://wink.ru/search?query=%s"),
            new Service("Иви", "https://www.ivi.ru/search/?q=%s"),
            new Service("Okko", "https://okko.tv/"),
            new Service("KION", "https://kion.ru/")
    );

    private LinearLayout root;
    private SharedPreferences prefs;
    private Runnable backAction;
    private Item pendingFileItem;
    private Item playerBackItem;
    private String selectedCatalogSource = "";
    private String lastError = "";

    private ExoPlayer player;
    private PlayerView playerView;
    private Uri currentUri;
    private String currentPositionKey = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showHome();
    }

    @Override protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (backAction != null) {
            Runnable r = backAction;
            backAction = null;
            r.run();
        } else {
            super.onBackPressed();
        }
    }

    private void base() {
        releasePlayer();
        backAction = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28),dp(12),dp(28),dp(18));
        root.setBackgroundColor(BG);
        setContentView(root);
        topBar();
    }

    private void topBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = txt("▶ CineDeck RU 1.2 Player",24,Color.WHITE,true);
        bar.addView(logo,new LinearLayout.LayoutParams(0,dp(58),1));
        bar.addView(nav("Главная",v->showHome()));
        bar.addView(nav("Поиск",v->showSearch()));
        bar.addView(nav("Источники",v->showSources()));
        bar.addView(nav("Диагностика",v->showDiag()));
        root.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(62)));
    }

    private void showHome() {
        base();
        LinearLayout hero = panel();
        hero.addView(txt("CINEDECK RU 1.2",13,PRIMARY,true));
        hero.addView(txt("Выберите фильм — затем источник воспроизведения",29,Color.WHITE,true));
        hero.addView(txt("Каталог используется только для поиска и постеров. Воспроизведение идёт из выбранного файла, M3U, прямой ссылки или подключённого JSON-провайдера.",15,Color.LTGRAY,false));
        root.addView(hero,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(148)));

        ScrollView sc = new ScrollView(this);
        LinearLayout content = col();
        sc.addView(content);
        root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        ProgressBar p = new ProgressBar(this);
        content.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView loading = txt("Загружаю метаданные…",15,MUTED,false);
        content.addView(loading);

        io.execute(() -> {
            List<Item> items = loadFeed();
            main.post(() -> {
                content.removeView(p);
                content.removeView(loading);
                if(items.isEmpty()) {
                    content.addView(txt("Не удалось загрузить метаданные.",18,Color.rgb(255,120,120),true));
                    content.addView(btn("Открыть источники",v->showSources()));
                    return;
                }
                List<Item> movies = new ArrayList<>();
                List<Item> series = new ArrayList<>();
                for(Item i:items) {
                    if("movie".equals(i.type) && movies.size()<30) movies.add(i);
                    else if(!"movie".equals(i.type) && series.size()<30) series.add(i);
                }
                if(!movies.isEmpty()) {
                    content.addView(txt("Фильмы",24,Color.WHITE,true));
                    content.addView(rail(movies));
                }
                if(!series.isEmpty()) {
                    content.addView(txt("Сериалы",24,Color.WHITE,true));
                    content.addView(rail(series));
                }
            });
        });
    }

    private void showSearch() {
        base();
        root.addView(txt("Поиск",30,Color.WHITE,true));
        LinearLayout line = row();
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint("Название фильма или сериала");
        q.setTextColor(Color.WHITE);
        q.setHintTextColor(MUTED);
        q.setTextSize(18);
        q.setBackgroundColor(PANEL);
        q.setPadding(dp(14),0,dp(14),0);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        line.addView(q,new LinearLayout.LayoutParams(0,dp(56),1));
        Button find = btn("Найти",null);
        line.addView(find,new LinearLayout.LayoutParams(dp(150),dp(56)));
        root.addView(line);

        ScrollView sc = new ScrollView(this);
        LinearLayout results = col();
        sc.addView(results);
        root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        Runnable run = () -> {
            String query = q.getText().toString().trim();
            if(query.isEmpty()) return;
            results.removeAllViews();
            results.addView(txt("Ищу…",15,MUTED,false));
            io.execute(() -> {
                List<Item> all = loadFeed();
                List<Item> found = new ArrayList<>();
                String n = norm(query);
                for(Item i:all) {
                    if(norm(i.name).contains(n)) found.add(i);
                    if(found.size()>=50) break;
                }
                main.post(() -> {
                    results.removeAllViews();
                    if(found.isEmpty()) results.addView(txt("Ничего не найдено.",17,MUTED,false));
                    else for(Item i:found) results.addView(searchRow(i));
                });
            });
        };
        find.setOnClickListener(v->run.run());
        q.setOnEditorActionListener((v,id,e)->{run.run();return true;});
        q.requestFocus();
    }

    private void showDetails(Item i) {
        base();
        backAction = this::showHome;
        LinearLayout row = row();
        ImageView im = new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackgroundColor(PANEL2);
        row.addView(im,new LinearLayout.LayoutParams(dp(220),dp(310)));
        loadImage(im,i);

        LinearLayout info = col();
        info.setPadding(dp(22),0,0,0);
        info.addView(txt(i.name,31,Color.WHITE,true));
        info.addView(txt(i.year+(i.rating.isEmpty()?"":" • ★ "+i.rating)+(i.genres.isEmpty()?"":" • "+i.genres),15,MUTED,false));
        info.addView(space(10));
        TextView d = txt(i.desc,16,Color.LTGRAY,false);
        d.setMaxLines(9);
        info.addView(d);
        info.addView(space(14));
        LinearLayout buttons = row();
        buttons.addView(btn("▶ Смотреть",v->showWatch(i)),new LinearLayout.LayoutParams(dp(210),dp(58)));
        buttons.addView(btn("Где смотреть",v->showWhere(i)),new LinearLayout.LayoutParams(dp(210),dp(58)));
        info.addView(buttons);
        row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    private void showWatch(Item i) {
        base();
        backAction = () -> showDetails(i);
        root.addView(txt("Смотреть",30,Color.WHITE,true));
        root.addView(txt(i.name+(i.year.isEmpty()?"":" • "+i.year),19,Color.LTGRAY,false));
        root.addView(space(8));

        LinearLayout quick = row();
        quick.addView(btn("Файл / USB",v->pickFile(i)),new LinearLayout.LayoutParams(dp(190),dp(58)));
        quick.addView(btn("Прямая ссылка",v->promptDirect(i)),new LinearLayout.LayoutParams(dp(190),dp(58)));
        quick.addView(btn("Все M3U",v->showAllM3u(i)),new LinearLayout.LayoutParams(dp(190),dp(58)));
        quick.addView(btn("Настроить источники",v->showSources()),new LinearLayout.LayoutParams(dp(230),dp(58)));
        root.addView(quick);
        root.addView(space(10));

        ScrollView sc = new ScrollView(this);
        LinearLayout box = col();
        sc.addView(box);
        root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        box.addView(txt("Ищу доступные источники…",16,MUTED,false));

        io.execute(() -> {
            List<Stream> streams = new ArrayList<>();
            streams.addAll(loadProvider(i));
            streams.addAll(loadM3u(i,true));
            main.post(() -> {
                box.removeAllViews();
                if(streams.isEmpty()) {
                    box.addView(txt("Автоматических совпадений не найдено.",17,MUTED,false));
                    box.addView(txt("Можно выбрать локальный файл, вставить прямую ссылку или открыть весь M3U-плейлист. Для автоматической выдачи потоков подключите свой JSON-провайдер в разделе «Источники».",14,MUTED,false));
                } else {
                    box.addView(txt("Доступные источники",22,Color.WHITE,true));
                    for(Stream s:streams) box.addView(streamButton(s,i));
                }
                box.addView(space(14));
                box.addView(txt("Официальные сервисы",20,Color.WHITE,true));
                addServices(box,i.name);
            });
        });
    }

    private View streamButton(Stream s, Item back) {
        String label = "▶  "+(s.quality.isEmpty()?"":s.quality+" • ")+s.name;
        Button b = btn(label,v->play(s.url,s.name,back));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56));
        lp.setMargins(0,0,0,dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void showAllM3u(Item back) {
        String m3u = prefs.getString(KEY_M3U,"");
        if(m3u==null || m3u.trim().isEmpty()) {
            Toast.makeText(this,"Сначала укажите M3U в разделе «Источники»",Toast.LENGTH_LONG).show();
            showSources();
            return;
        }
        base();
        backAction = () -> showWatch(back);
        root.addView(txt("M3U-плейлист",28,Color.WHITE,true));
        ScrollView sc = new ScrollView(this);
        LinearLayout list = col();
        sc.addView(list);
        root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        list.addView(txt("Загружаю…",15,MUTED,false));
        io.execute(() -> {
            List<Stream> streams = loadM3u(back,false);
            main.post(() -> {
                list.removeAllViews();
                if(streams.isEmpty()) list.addView(txt("Плейлист пуст или недоступен.",17,MUTED,false));
                else for(Stream s:streams) list.addView(streamButton(s,back));
            });
        });
    }

    private void showSources() {
        base();
        root.addView(txt("Источники воспроизведения",30,Color.WHITE,true));
        root.addView(txt("Здесь настраиваются реальные источники видео. Метаданные и постеры к ним не привязаны.",15,MUTED,false));
        root.addView(space(10));

        String m3u = prefs.getString(KEY_M3U,"");
        String provider = prefs.getString(KEY_PROVIDER,"");
        root.addView(txt("M3U / M3U8",20,Color.WHITE,true));
        root.addView(txt(emptyLabel(m3u),14,MUTED,false));
        root.addView(btn("Изменить M3U",v->promptSetting(KEY_M3U,"M3U / M3U8 URL","https://.../playlist.m3u")));
        root.addView(space(12));
        root.addView(txt("JSON-провайдер потоков",20,Color.WHITE,true));
        root.addView(txt(emptyLabel(provider),14,MUTED,false));
        root.addView(txt("Приложение делает GET-запрос с id, title и year. Ответ: {\"streams\":[{\"name\":\"Server\",\"quality\":\"1080p\",\"url\":\"https://...\"}]}",13,MUTED,false));
        root.addView(btn("Изменить JSON-провайдер",v->promptSetting(KEY_PROVIDER,"JSON-провайдер","https://server/api/streams")));
    }

    private void promptSetting(String key,String title,String hint) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        e.setHint(hint);
        e.setText(prefs.getString(key,""));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(e)
                .setPositiveButton("Сохранить",(d,w)->{prefs.edit().putString(key,e.getText().toString().trim()).apply();showSources();})
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void promptDirect(Item i) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        e.setHint("https://.../video.m3u8, .mp4, .mpd, .mkv");
        new AlertDialog.Builder(this)
                .setTitle("Прямая ссылка")
                .setView(e)
                .setPositiveButton("Смотреть",(d,w)->{
                    String u=e.getText().toString().trim();
                    if(!u.isEmpty()) play(u,i.name,i);
                })
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void pickFile(Item i) {
        pendingFileItem = i;
        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("video/*");
        try {
            startActivityForResult(in,FILE_REQUEST);
        } catch(Exception e) {
            Intent alt = new Intent(Intent.ACTION_GET_CONTENT);
            alt.addCategory(Intent.CATEGORY_OPENABLE);
            alt.setType("video/*");
            try { startActivityForResult(alt,FILE_REQUEST); }
            catch(Exception x) { Toast.makeText(this,"На приставке нет файлового менеджера",Toast.LENGTH_LONG).show(); }
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==FILE_REQUEST && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri,flags);
            } catch(Exception ignored) {}
            Item back = pendingFileItem;
            String title = back==null?"Локальный файл":back.name;
            play(uri.toString(),title,back);
        }
    }

    private void play(String url,String title,Item back) {
        try { showPlayer(Uri.parse(url),title,back); }
        catch(Exception e) { Toast.makeText(this,"Не удалось открыть источник",Toast.LENGTH_LONG).show(); }
    }

    private void showPlayer(Uri uri,String title,Item back) {
        releasePlayer();
        playerBackItem = back;
        currentUri = uri;
        currentPositionKey = "position_"+Integer.toHexString(uri.toString().hashCode());
        backAction = () -> {
            if(playerBackItem!=null) showWatch(playerBackItem); else showHome();
        };

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        LinearLayout head = row();
        head.setPadding(dp(10),dp(6),dp(10),dp(6));
        Button backBtn = btn("← Назад",v->onBackPressed());
        head.addView(backBtn,new LinearLayout.LayoutParams(dp(150),dp(52)));
        TextView name = txt(title,20,Color.WHITE,true);
        head.addView(name,new LinearLayout.LayoutParams(0,dp(52),1));
        Button external = btn("Внешний плеер",v->openExternal(uri));
        head.addView(external,new LinearLayout.LayoutParams(dp(210),dp(52)));
        root.addView(head,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(64)));

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setBackgroundColor(Color.BLACK);
        root.addView(playerView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(uri));
        long saved = prefs.getLong(currentPositionKey,0L);
        if(saved>5000L) player.seekTo(saved);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                Toast.makeText(MainActivityPlayer.this,"Ошибка воспроизведения. Можно открыть источник во внешнем плеере.",Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if(state==Player.STATE_ENDED) prefs.edit().remove(currentPositionKey).apply();
            }
        });
        player.prepare();
        player.play();
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,"video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch(Exception e) {
            Toast.makeText(this,"Не найден внешний видеоплеер",Toast.LENGTH_LONG).show();
        }
    }

    private void savePosition() {
        if(player!=null && !currentPositionKey.isEmpty()) {
            try {
                long p = player.getCurrentPosition();
                if(p>5000L) prefs.edit().putLong(currentPositionKey,p).apply();
            } catch(Exception ignored) {}
        }
    }

    private void releasePlayer() {
        savePosition();
        if(playerView!=null) playerView.setPlayer(null);
        if(player!=null) {
            try { player.release(); } catch(Exception ignored) {}
        }
        player = null;
        playerView = null;
        currentUri = null;
        currentPositionKey = "";
    }

    private List<Stream> loadProvider(Item i) {
        List<Stream> out = new ArrayList<>();
        String base = prefs.getString(KEY_PROVIDER,"");
        if(base==null || base.trim().isEmpty()) return out;
        try {
            String u = buildProviderUrl(base.trim(),i);
            String raw = get(u,8000,12000);
            JSONArray arr;
            if(raw.trim().startsWith("[")) arr = new JSONArray(raw);
            else arr = new JSONObject(raw).optJSONArray("streams");
            if(arr==null) return out;
            for(int x=0;x<arr.length() && out.size()<80;x++) {
                JSONObject o = arr.optJSONObject(x);
                if(o==null) continue;
                String url = first(o.optString("url",""),o.optString("stream",""));
                if(url.isEmpty()) continue;
                Stream s = new Stream();
                s.url = url;
                s.name = first(o.optString("name",""),o.optString("source",""));
                if(s.name.isEmpty()) s.name = "JSON provider";
                s.quality = o.optString("quality","");
                out.add(s);
            }
        } catch(Exception e) {
            lastError = "Provider: "+e.getClass().getSimpleName();
        }
        return out;
    }

    private String buildProviderUrl(String base,Item i) throws Exception {
        String id = enc(i.id);
        String title = enc(i.name);
        String year = enc(i.year);
        if(base.contains("{id}") || base.contains("{title}") || base.contains("{year}")) {
            return base.replace("{id}",id).replace("{title}",title).replace("{year}",year);
        }
        String sep = base.contains("?")?"&":"?";
        return base+sep+"id="+id+"&title="+title+"&year="+year;
    }

    private List<Stream> loadM3u(Item i,boolean matchingOnly) {
        List<Stream> out = new ArrayList<>();
        String u = prefs.getString(KEY_M3U,"");
        if(u==null || u.trim().isEmpty()) return out;
        try {
            String raw = get(u.trim(),8000,14000);
            String pending = "";
            String[] lines = raw.replace("\r","").split("\n");
            for(String original:lines) {
                String line = original.trim();
                if(line.isEmpty()) continue;
                if(line.startsWith("#EXTINF")) {
                    int comma = line.indexOf(',');
                    pending = comma>=0?line.substring(comma+1).trim():"Канал";
                    continue;
                }
                if(line.startsWith("#")) continue;
                String name = pending.isEmpty()?line:pending;
                pending = "";
                if(matchingOnly && !titleMatch(i.name,name)) continue;
                Stream s = new Stream();
                s.name = name;
                s.url = line;
                s.quality = "M3U";
                out.add(s);
                if(out.size() >= (matchingOnly?30:150)) break;
            }
        } catch(Exception e) {
            lastError = "M3U: "+e.getClass().getSimpleName();
        }
        return out;
    }

    private boolean titleMatch(String title,String entry) {
        String a = norm(title);
        String b = norm(entry);
        if(a.length()<3 || b.length()<3) return false;
        if(a.contains(b) || b.contains(a)) return true;
        String[] tokens = a.split(" ");
        int good = 0, useful = 0;
        for(String t:tokens) {
            if(t.length()<4) continue;
            useful++;
            if(b.contains(t)) good++;
        }
        return useful>0 && good>=Math.min(2,useful);
    }

    private void showWhere(Item i) {
        base();
        backAction = () -> showDetails(i);
        root.addView(txt("Где смотреть",30,Color.WHITE,true));
        root.addView(txt(i.name,20,Color.LTGRAY,false));
        root.addView(space(8));
        addServices(root,i.name);
    }

    private void addServices(LinearLayout parent,String query) {
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for(Service s:services) {
            Button b = btn(s.name,v->openService(s,query));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(170),dp(58));
            lp.setMargins(0,0,dp(8),0);
            line.addView(b,lp);
        }
        sc.addView(line);
        parent.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(70)));
    }

    private void openService(Service s,String query) {
        try {
            String url = s.url.contains("%s")?String.format(Locale.ROOT,s.url,Uri.encode(query)):s.url;
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        } catch(Exception e) {
            Toast.makeText(this,"Не удалось открыть сервис",Toast.LENGTH_LONG).show();
        }
    }

    private void showDiag() {
        base();
        root.addView(txt("Диагностика",30,Color.WHITE,true));
        TextView out = txt("Проверяю…",16,MUTED,false);
        root.addView(out);
        io.execute(() -> {
            Probe cdn = probe(FEED);
            Probe raw = probe(FEED_RAW);
            List<Item> items = loadFeedFresh();
            String image = "нет";
            if(!items.isEmpty()) {
                Bitmap b = tryBitmap(items.get(0).poster);
                if(b==null) b = tryBitmap(items.get(0).posterRaw);
                image = b==null?"ОШИБКА":"OK "+b.getWidth()+"×"+b.getHeight();
            }
            String m3u = prefs.getString(KEY_M3U,"");
            String provider = prefs.getString(KEY_PROVIDER,"");
            String res = "GitHub CDN JSON: "+cdn.status+"\n"+
                    "GitHub Raw JSON: "+raw.status+"\n"+
                    "Каталог: "+items.size()+"\n"+
                    "Обложка: "+image+"\n"+
                    "M3U: "+((m3u==null||m3u.trim().isEmpty())?"не настроен":"настроен")+"\n"+
                    "JSON provider: "+((provider==null||provider.trim().isEmpty())?"не настроен":"настроен")+"\n"+
                    "Плеер: ExoPlayer 2.19.1"+
                    (lastError.isEmpty()?"":"\nПоследняя ошибка: "+lastError);
            main.post(()->out.setText(res));
        });
    }

    private List<Item> loadFeed() {
        synchronized(cache) { if(!cache.isEmpty()) return new ArrayList<>(cache); }
        return loadFeedFresh();
    }

    private List<Item> loadFeedFresh() {
        for(String u:new String[]{FEED,FEED_RAW}) {
            try {
                String raw = get(u,7000,10000);
                JSONObject obj = new JSONObject(raw);
                if(obj.optInt("version",0)<3) continue;
                JSONArray arr = obj.optJSONArray("items");
                if(arr==null) continue;
                List<Item> list = new ArrayList<>();
                for(int x=0;x<arr.length();x++) {
                    JSONObject o = arr.optJSONObject(x);
                    if(o==null) continue;
                    String name = o.optString("name","");
                    if(name.isEmpty()) continue;
                    Item i = new Item();
                    i.id = o.optString("id","");
                    i.name = name;
                    i.year = o.optString("year","");
                    i.rating = o.optString("rating","");
                    i.type = o.optString("type","series");
                    i.desc = o.optString("description","");
                    i.poster = o.optString("posterUrl","");
                    i.posterRaw = o.optString("posterRawUrl","");
                    JSONArray g = o.optJSONArray("genres");
                    if(g!=null) {
                        StringBuilder gs = new StringBuilder();
                        for(int y=0;y<g.length();y++) {
                            String v = g.optString(y,"");
                            if(v.isEmpty()) continue;
                            if(gs.length()>0) gs.append(", ");
                            gs.append(v);
                        }
                        i.genres = gs.toString();
                    }
                    list.add(i);
                }
                if(!list.isEmpty()) {
                    selectedCatalogSource = u.contains("jsdelivr")?"GitHub CDN":"GitHub Raw";
                    synchronized(cache) { cache.clear(); cache.addAll(list); }
                    return list;
                }
            } catch(Exception e) {
                lastError = e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    private Probe probe(String u) {
        Probe p = new Probe();
        try {
            String s = get(u,5500,8000);
            p.status = "OK ("+s.length()+" байт)";
        } catch(Exception e) {
            p.status = "ОШИБКА: "+e.getClass().getSimpleName();
        }
        return p;
    }

    private View rail(List<Item> items) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for(Item i:items) line.addView(card(i));
        hs.addView(line);
        hs.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(306)));
        return hs;
    }

    private View card(Item i) {
        LinearLayout c = col();
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(dp(6),dp(6),dp(6),dp(6));
        c.setBackgroundColor(PANEL);
        ImageView im = new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackgroundColor(PANEL2);
        c.addView(im,new LinearLayout.LayoutParams(dp(160),dp(224)));
        loadImage(im,i);
        TextView n = txt(i.name,14,Color.WHITE,true);
        n.setMaxLines(1);
        c.addView(n,new LinearLayout.LayoutParams(dp(160),dp(30)));
        c.addView(txt(i.year+(i.rating.isEmpty()?"":" • ★ "+i.rating),12,MUTED,false));
        c.setOnClickListener(v->showDetails(i));
        c.setOnFocusChangeListener((v,f)->{
            c.setBackgroundColor(f?FOCUS:PANEL);
            c.setScaleX(f?1.04f:1f);
            c.setScaleY(f?1.04f:1f);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176),dp(294));
        lp.setMargins(0,0,dp(12),0);
        c.setLayoutParams(lp);
        return c;
    }

    private View searchRow(Item i) {
        LinearLayout r = row();
        r.setFocusable(true);
        r.setClickable(true);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8),dp(7),dp(12),dp(7));
        r.setBackgroundColor(PANEL);
        ImageView im = new ImageView(this);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        r.addView(im,new LinearLayout.LayoutParams(dp(72),dp(100)));
        loadImage(im,i);
        LinearLayout info = col();
        info.setPadding(dp(12),0,0,0);
        info.addView(txt(i.name,19,Color.WHITE,true));
        info.addView(txt(i.year+(i.rating.isEmpty()?"":" • ★ "+i.rating),13,MUTED,false));
        TextView d = txt(i.desc,13,Color.LTGRAY,false);
        d.setMaxLines(2);
        info.addView(d);
        r.addView(info,new LinearLayout.LayoutParams(0,dp(100),1));
        r.setOnClickListener(v->showDetails(i));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(114));
        lp.setMargins(0,0,0,dp(5));
        r.setLayoutParams(lp);
        return r;
    }

    private void loadImage(ImageView view,Item item) {
        io.execute(() -> {
            Bitmap b = tryBitmap(item.poster);
            if(b==null) b = tryBitmap(item.posterRaw);
            final Bitmap out = b;
            if(out!=null) main.post(()->view.setImageBitmap(out));
        });
    }

    private Bitmap tryBitmap(String u) {
        if(u==null || u.isEmpty()) return null;
        try {
            HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
            c.setConnectTimeout(6500);
            c.setReadTimeout(9000);
            c.setInstanceFollowRedirects(true);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV) CineDeck/1.2");
            c.connect();
            if(c.getResponseCode()/100!=2) { c.disconnect(); return null; }
            InputStream in = c.getInputStream();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeStream(in,null,o);
            in.close();
            c.disconnect();
            return b;
        } catch(Exception e) { return null; }
    }

    private String get(String u,int ct,int rt) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(ct);
        c.setReadTimeout(rt);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control","no-cache");
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV) CineDeck/1.2");
        c.connect();
        int code = c.getResponseCode();
        if(code/100!=2) throw new Exception("HTTP "+code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while((n=in.read(buf))>0) out.write(buf,0,n);
        in.close();
        c.disconnect();
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }

    private String emptyLabel(String s) {
        return s==null || s.trim().isEmpty()?"Не настроен":s;
    }

    private String first(String a,String b) {
        return a!=null && !a.isEmpty()?a:(b==null?"":b);
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s==null?"":s,StandardCharsets.UTF_8.name());
    }

    private String norm(String s) {
        if(s==null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-яё0-9]+"," ").trim();
    }

    private LinearLayout panel() {
        LinearLayout l = col();
        l.setPadding(dp(22),dp(16),dp(22),dp(16));
        l.setBackgroundColor(PANEL);
        return l;
    }

    private Button nav(String s,View.OnClickListener l) {
        Button b = btn(s,l);
        b.setTextSize(14);
        return b;
    }

    private Button btn(String s,View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackgroundColor(PANEL2);
        if(l!=null) b.setOnClickListener(l);
        b.setOnFocusChangeListener((v,f)->b.setBackgroundColor(f?FOCUS:PANEL2));
        return b;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView txt(String s,int sp,int color,boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if(bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    static class Item {
        String id="",name="",year="",rating="",type="",desc="",poster="",posterRaw="",genres="";
    }
    static class Stream {
        String name="",quality="",url="";
    }
    static class Probe {
        String status="";
    }
    static class Service {
        final String name,url;
        Service(String n,String u){name=n;url=u;}
    }
}
