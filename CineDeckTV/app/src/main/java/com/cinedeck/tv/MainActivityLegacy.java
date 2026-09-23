package com.cinedeck.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityLegacy extends Activity {
    private static final int BG = Color.rgb(7, 10, 16);
    private static final int PANEL = Color.rgb(19, 25, 35);
    private static final int PANEL2 = Color.rgb(27, 35, 49);
    private static final int FOCUS = Color.rgb(46, 91, 147);
    private static final int PRIMARY = Color.rgb(64, 151, 255);
    private static final int MUTED = Color.rgb(158, 171, 191);
    private static final String PREFS = "cinedeck_online";
    private static final String KEY_M3U = "m3u";
    private static final String FEED_CDN = "https://cdn.jsdelivr.net/gh/nick3dddsss-ctrl/ai-architect-facade@cinedeck-tv-build/catalog-data/catalog.json";
    private static final String FEED_RAW = "https://raw.githubusercontent.com/nick3dddsss-ctrl/ai-architect-facade/cinedeck-tv-build/catalog-data/catalog.json";
    private static final String CINEMETA = "https://v3-cinemeta.strem.io";

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, Bitmap> imageCache = new LinkedHashMap<String, Bitmap>(12, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 10; }
    };
    private final List<Show> catalogCache = new ArrayList<>();

    private LinearLayout root;
    private SharedPreferences prefs;
    private Runnable backAction;
    private VideoView videoView;
    private String lastNetworkError = "";
    private String catalogSource = "";

    private final List<Service> services = Arrays.asList(
            new Service("VK Видео", "https://vkvideo.ru/video?q=%s"),
            new Service("RUTUBE", "https://rutube.ru/search/?query=%s"),
            new Service("Кинопоиск", "https://www.kinopoisk.ru/index.php?kp_query=%s"),
            new Service("Wink", "https://wink.ru/search?query=%s"),
            new Service("Иви", "https://www.ivi.ru/search/?q=%s"),
            new Service("Okko", "https://okko.tv/"),
            new Service("KION", "https://kion.ru/")
    );

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showHome();
    }

    @Override protected void onDestroy() {
        stopVideo();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (backAction != null) {
            Runnable r = backAction;
            backAction = null;
            r.run();
        } else super.onBackPressed();
    }

    private void screen() {
        stopVideo();
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
        TextView logo = text("▶  CineDeck RU", 24, Color.WHITE, true);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(300), dp(58)));
        bar.addView(nav("Главная", v -> showHome()));
        bar.addView(nav("Поиск", v -> showSearch()));
        bar.addView(nav("ТВ / M3U", v -> showChannels()));
        bar.addView(nav("Диагностика", v -> showDiagnostics()));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
    }

    private void showHome() {
        screen();
        LinearLayout hero = panel(PANEL);
        hero.setPadding(dp(24), dp(18), dp(24), dp(16));
        hero.addView(text("CINEDECK RU 1.1.3 ONLINE", 13, PRIMARY, true));
        hero.addView(text("Каталог и обложки — только из интернета", 30, Color.WHITE, true));
        hero.addView(text("В APK нет фильмов, карточек и постеров. Каталог обновляется удалённо без переустановки приложения.", 15, Color.LTGRAY, false));
        LinearLayout a = row();
        a.addView(action("⌕  Поиск", v -> showSearch()));
        a.addView(action("▣  ТВ / M3U", v -> showChannels()));
        a.addView(action("◉  Сеть", v -> showDiagnostics()));
        hero.addView(a);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(188)));
        root.addView(spacer(10));

        ScrollView sc = new ScrollView(this);
        LinearLayout content = column();
        sc.addView(content);
        TextView status = text("Подключаю интернет-каталог…", 16, MUTED, false);
        content.addView(status);
        ProgressBar p = new ProgressBar(this);
        content.addView(p, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        io.execute(() -> {
            List<Show> all = loadCatalog();
            main.post(() -> {
                content.removeAllViews();
                if (all.isEmpty()) {
                    content.addView(text("Не удалось получить каталог из интернета.", 18, Color.rgb(255,120,120), true));
                    content.addView(text("Открой «Диагностика»: приложение проверит GitHub CDN, GitHub Raw и Cinemeta.", 15, MUTED, false));
                    content.addView(action("Открыть диагностику", v -> showDiagnostics()));
                    return;
                }
                content.addView(text("Источник: " + catalogSource + " • " + all.size() + " позиций", 13, MUTED, false));
                List<Show> movies = new ArrayList<>();
                List<Show> series = new ArrayList<>();
                for (Show s : all) {
                    if ("movie".equals(s.type) && movies.size() < 30) movies.add(s);
                    else if (!"movie".equals(s.type) && series.size() < 30) series.add(s);
                }
                if (!movies.isEmpty()) {
                    content.addView(spacer(6));
                    content.addView(text("Фильмы", 24, Color.WHITE, true));
                    content.addView(showRail(movies));
                }
                if (!series.isEmpty()) {
                    content.addView(spacer(8));
                    content.addView(text("Сериалы", 24, Color.WHITE, true));
                    content.addView(showRail(series));
                }
            });
        });
    }

    private void showSearch() {
        screen();
        root.addView(text("Поиск", 32, Color.WHITE, true));
        root.addView(text("Сначала ищем в текущем интернет-каталоге, затем пробуем онлайн-поиск Cinemeta.", 14, MUTED, false));
        root.addView(spacer(8));
        LinearLayout line = row();
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setHint("Название фильма или сериала");
        q.setTextColor(Color.WHITE);
        q.setHintTextColor(MUTED);
        q.setTextSize(18);
        q.setBackground(roundRect(PANEL, 11));
        q.setPadding(dp(14), 0, dp(14), 0);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        line.addView(q, new LinearLayout.LayoutParams(dp(720), dp(56)));
        Button go = action("Найти", null);
        line.addView(go, new LinearLayout.LayoutParams(dp(150), dp(56)));
        root.addView(line);
        root.addView(spacer(10));

        ScrollView sc = new ScrollView(this);
        LinearLayout results = column();
        sc.addView(results);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Runnable search = () -> {
            String s = q.getText().toString().trim();
            if (s.isEmpty()) return;
            results.removeAllViews();
            addServices(results, s);
            results.addView(spacer(8));
            ProgressBar p = new ProgressBar(this);
            results.addView(p, new LinearLayout.LayoutParams(dp(42), dp(42)));
            io.execute(() -> {
                List<Show> found = searchShows(s);
                main.post(() -> {
                    results.removeView(p);
                    if (found.isEmpty()) results.addView(text("В интернет-каталоге ничего не найдено. Российские сервисы доступны выше.", 16, MUTED, false));
                    else for (Show item : found) results.addView(searchRow(item));
                });
            });
        };
        go.setOnClickListener(v -> search.run());
        q.setOnEditorActionListener((v, id, e) -> { search.run(); return true; });
        q.requestFocus();
    }

    private View showRail(List<Show> shows) {
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line = row();
        for (Show s : shows) line.addView(card(s));
        sc.addView(line);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(304)));
        return sc;
    }

    private View card(Show s) {
        LinearLayout c = column();
        c.setFocusable(true); c.setClickable(true);
        c.setPadding(dp(6), dp(6), dp(6), dp(6));
        c.setBackground(roundRect(PANEL, 12));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(roundRect(PANEL2, 9));
        c.addView(image, new LinearLayout.LayoutParams(dp(160), dp(224)));
        loadImageOnline(image, s.image);
        TextView name = text(s.name, 14, Color.WHITE, true); name.setMaxLines(1);
        c.addView(name, new LinearLayout.LayoutParams(dp(160), dp(28)));
        c.addView(text(s.year + (s.rating.isEmpty()?"":" • ★ "+s.rating), 12, MUTED, false));
        c.setOnClickListener(v -> showDetails(s));
        c.setOnFocusChangeListener((v,f) -> {
            c.setBackground(roundRect(f?FOCUS:PANEL,12));
            c.setScaleX(f?1.045f:1f); c.setScaleY(f?1.045f:1f);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176), dp(294));
        lp.setMargins(0,0,dp(12),0); c.setLayoutParams(lp);
        return c;
    }

    private View searchRow(Show s) {
        LinearLayout r = row();
        r.setFocusable(true); r.setClickable(true); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8),dp(7),dp(12),dp(7)); r.setBackground(roundRect(PANEL,10));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        r.addView(image,new LinearLayout.LayoutParams(dp(70),dp(96))); loadImageOnline(image,s.image);
        LinearLayout info=column(); info.setPadding(dp(12),0,0,0);
        info.addView(text(s.name,19,Color.WHITE,true));
        info.addView(text(s.year+(s.rating.isEmpty()?"":" • ★ "+s.rating),13,MUTED,false));
        TextView d=text(s.summary,13,Color.LTGRAY,false); d.setMaxLines(2); info.addView(d);
        r.addView(info,new LinearLayout.LayoutParams(0,dp(96),1));
        r.setOnClickListener(v->showDetails(s));
        r.setOnFocusChangeListener((v,f)->r.setBackground(roundRect(f?FOCUS:PANEL,10)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(110)); lp.setMargins(0,0,0,dp(6)); r.setLayoutParams(lp);
        return r;
    }

    private void showDetails(Show s) {
        screen(); backAction=this::showHome;
        LinearLayout box=panel(PANEL); box.setPadding(dp(20),dp(18),dp(20),dp(18));
        LinearLayout head=row();
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(roundRect(PANEL2,10));
        head.addView(img,new LinearLayout.LayoutParams(dp(205),dp(286))); loadImageOnline(img,s.image);
        LinearLayout info=column(); info.setPadding(dp(20),0,0,0);
        info.addView(text(s.name,32,Color.WHITE,true));
        info.addView(text(s.year+(s.rating.isEmpty()?"":" • ★ "+s.rating)+(s.genres.isEmpty()?"":" • "+s.genres),15,MUTED,false));
        info.addView(spacer(8));
        TextView d=text(s.summary,16,Color.WHITE,false); d.setMaxLines(10); info.addView(d);
        info.addView(spacer(12));
        info.addView(action("⌕  Где смотреть",v->showWhere(s.name)));
        head.addView(info,new LinearLayout.LayoutParams(0,dp(286),1));
        box.addView(head); root.addView(box,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    private void showWhere(String query) {
        screen(); backAction=this::showHome;
        root.addView(text("Где искать",32,Color.WHITE,true));
        root.addView(text(query,20,Color.LTGRAY,false)); root.addView(spacer(10)); addServices(root,query);
    }

    private void addServices(LinearLayout parent,String query) {
        parent.addView(text(query==null?"Российские сервисы":"Поиск в российских сервисах",20,Color.WHITE,true));
        parent.addView(spacer(6));
        HorizontalScrollView sc=new HorizontalScrollView(this); sc.setHorizontalScrollBarEnabled(false);
        LinearLayout line=row();
        for(Service s:services){
            Button b=new Button(this); b.setAllCaps(false); b.setText(s.name); b.setTextColor(Color.WHITE); b.setTextSize(14); style(b,PANEL2);
            b.setOnClickListener(v->openService(s,query));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(170),dp(58)); lp.setMargins(0,0,dp(8),0); line.addView(b,lp);
        }
        sc.addView(line); parent.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(66)));
    }

    private void showChannels(){
        screen(); root.addView(text("ТВ / M3U",32,Color.WHITE,true));
        LinearLayout a=row(); a.addView(action("⚙ Плейлист",v->promptM3u())); a.addView(action("▶ Прямая ссылка",v->promptStream())); root.addView(a); root.addView(spacer(8));
        String u=prefs.getString(KEY_M3U,"");
        if(u==null||u.trim().isEmpty()){root.addView(text("M3U-плейлист ещё не указан.",16,MUTED,false));return;}
        ProgressBar p=new ProgressBar(this); root.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));
        io.execute(()->{List<Channel> list=loadM3u(u);main.post(()->{root.removeView(p);if(list.isEmpty())root.addView(text("Не удалось загрузить M3U.",16,MUTED,false));else{ScrollView sc=new ScrollView(this);LinearLayout c=column();for(Channel ch:list){Button b=new Button(this);b.setAllCaps(false);b.setText("▶  "+ch.name);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);style(b,PANEL);b.setOnClickListener(v->play(ch.name,ch.url));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));lp.setMargins(0,0,0,dp(4));c.addView(b,lp);}sc.addView(c);root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));}});});
    }

    private void promptM3u(){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);e.setText(prefs.getString(KEY_M3U,""));e.setHint("https://.../playlist.m3u");new AlertDialog.Builder(this).setTitle("M3U-плейлист").setView(e).setPositiveButton("Сохранить",(d,w)->{prefs.edit().putString(KEY_M3U,e.getText().toString().trim()).apply();showChannels();}).setNegativeButton("Отмена",null).show();}
    private void promptStream(){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);e.setHint("https://.../video.m3u8 или .mp4");new AlertDialog.Builder(this).setTitle("Открыть поток").setView(e).setPositiveButton("Открыть",(d,w)->{String u=e.getText().toString().trim();if(!u.isEmpty())play("Поток",u);}).setNegativeButton("Отмена",null).show();}
    private void play(String title,String url){screen();backAction=this::showChannels;root.addView(text(title,22,Color.WHITE,true));videoView=new VideoView(this);videoView.setBackgroundColor(Color.BLACK);root.addView(videoView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));try{videoView.setVideoURI(Uri.parse(url));android.widget.MediaController mc=new android.widget.MediaController(this);mc.setAnchorView(videoView);videoView.setMediaController(mc);videoView.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"Поток не поддерживается этой прошивкой",Toast.LENGTH_LONG).show();return true;});videoView.start();}catch(Exception e){Toast.makeText(this,"Не удалось открыть поток",Toast.LENGTH_LONG).show();}}
    private void stopVideo(){if(videoView!=null){try{videoView.stopPlayback();}catch(Exception ignored){}videoView=null;}}

    private void showDiagnostics(){
        screen(); root.addView(text("Диагностика сети",32,Color.WHITE,true));
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);int mem=am==null?0:am.getMemoryClass();
        root.addView(text("Android: "+Build.VERSION.RELEASE+" (SDK "+Build.VERSION.SDK_INT+")",17,Color.WHITE,true));
        root.addView(text("Ядро: "+System.getProperty("os.version","неизвестно"),16,Color.LTGRAY,false));
        root.addView(text("Память приложения: ~"+mem+" МБ",16,Color.LTGRAY,false));
        root.addView(text("Модель: "+Build.MANUFACTURER+" "+Build.MODEL,16,Color.LTGRAY,false));
        root.addView(spacer(10));
        TextView net=text("Проверяю сетевые источники…",17,MUTED,false);root.addView(net);
        ProgressBar p=new ProgressBar(this);root.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));
        io.execute(()->{
            boolean cdn=test(FEED_CDN); boolean raw=test(FEED_RAW); boolean cine=test(CINEMETA+"/catalog/movie/top.json"); boolean img=test("https://images.weserv.nl/?url=www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png"); String err=lastNetworkError;
            main.post(()->{root.removeView(p);net.setText("GitHub CDN: "+ok(cdn)+"\nGitHub Raw: "+ok(raw)+"\nCinemeta: "+ok(cine)+"\nПрокси обложек: "+ok(img)+(err.isEmpty()?"":"\nПоследняя ошибка: "+err));net.setTextColor((cdn||raw||cine)?Color.WHITE:Color.rgb(255,120,120));});
        });
    }
    private String ok(boolean b){return b?"OK":"ОШИБКА";}
    private boolean test(String u){try{String s=httpGet(u,5500,6500);return s!=null&&s.length()>10;}catch(Exception e){lastNetworkError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());return false;}}

    private List<Show> loadCatalog(){
        synchronized(catalogCache){if(!catalogCache.isEmpty())return new ArrayList<>(catalogCache);}
        List<Show> out=new ArrayList<>();
        String[] feeds={FEED_CDN,FEED_RAW};
        for(String feed:feeds){
            try{out=parseRemoteFeed(httpGet(feed,6500,9000));if(!out.isEmpty()){catalogSource=feed.contains("jsdelivr")?"GitHub CDN":"GitHub Raw";break;}}catch(Exception e){lastNetworkError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
        }
        if(out.isEmpty()){
            try{out.addAll(parseCinemeta(httpGet(CINEMETA+"/catalog/movie/top.json",6500,9000),"movie",60));}catch(Exception e){lastNetworkError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
            try{out.addAll(parseCinemeta(httpGet(CINEMETA+"/catalog/series/top.json",6500,9000),"series",60));}catch(Exception e){lastNetworkError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
            if(!out.isEmpty())catalogSource="Cinemeta";
        }
        synchronized(catalogCache){catalogCache.clear();catalogCache.addAll(out);}return out;
    }

    private List<Show> searchShows(String q){
        List<Show> base=loadCatalog(); List<Show> out=new ArrayList<>(); String needle=q.toLowerCase(Locale.ROOT);
        for(Show s:base){if(s.name.toLowerCase(Locale.ROOT).contains(needle)){out.add(s);if(out.size()>=30)break;}}
        if(!out.isEmpty())return out;
        String enc=Uri.encode(q);
        for(String type:new String[]{"movie","series"}){
            try{String raw=httpGet(CINEMETA+"/catalog/"+type+"/top/search="+enc+".json",6500,9000);out.addAll(parseCinemeta(raw,type,20));}catch(Exception ignored){}
        }
        return out;
    }

    private List<Show> parseRemoteFeed(String raw)throws Exception{
        JSONObject root=new JSONObject(raw);JSONArray items=root.optJSONArray("items");List<Show> out=new ArrayList<>();if(items==null)return out;
        for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o==null)continue;Show s=new Show();s.id=o.optString("id","");s.type=o.optString("type","series");s.name=o.optString("name","");if(s.name.isEmpty())continue;s.year=o.optString("year","");s.rating=o.optString("rating","");s.summary=o.optString("description","Описание отсутствует.");s.image=o.optString("posterUrl","");JSONArray g=o.optJSONArray("genres");s.genres=joinGenres(g);out.add(s);}return out;
    }

    private List<Show> parseCinemeta(String raw,String type,int limit)throws Exception{
        JSONObject root=new JSONObject(raw);JSONArray metas=root.optJSONArray("metas");List<Show> out=new ArrayList<>();if(metas==null)return out;
        for(int i=0;i<metas.length()&&out.size()<limit;i++){JSONObject o=metas.optJSONObject(i);if(o==null)continue;Show s=new Show();s.id=o.optString("id","");s.type=type;s.name=o.optString("name","");if(s.name.isEmpty())continue;String rel=o.optString("releaseInfo",o.optString("year",""));s.year=rel.length()>=4?rel.substring(0,4):rel;s.rating=o.optString("imdbRating",o.optString("rating",""));s.summary=clean(o.optString("description","Описание отсутствует."));s.image=o.optString("poster","");s.genres=joinGenres(o.optJSONArray("genres"));out.add(s);}return out;
    }

    private String joinGenres(JSONArray g){if(g==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<g.length()&&i<4;i++){String x=g.optString(i,"");if(x.isEmpty())continue;if(b.length()>0)b.append(", ");b.append(x);}return b.toString();}

    private String httpGet(String address,int connect,int read)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(connect);c.setReadTimeout(read);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 CineDeckRU/1.1.3");c.setRequestProperty("Accept","application/json,text/plain,image/*,*/*");
        int code=c.getResponseCode();if(code<200||code>=400)throw new Exception("HTTP "+code);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new String(out.toByteArray(),Charset.forName("UTF-8"));}finally{c.disconnect();}
    }

    private void loadImageOnline(ImageView target,String url){
        if(url==null||url.isEmpty())return;
        synchronized(imageCache){Bitmap b=imageCache.get(url);if(b!=null){target.setImageBitmap(b);return;}}
        io.execute(()->{
            Bitmap b=null; String clean=url.replace("https://","").replace("http://","");
            String[] tries={url,"https://images.weserv.nl/?url="+Uri.encode(clean),"https://wsrv.nl/?url="+Uri.encode(clean)};
            for(String u:tries){try{b=downloadBitmap(u);if(b!=null)break;}catch(Throwable ignored){}}
            final Bitmap done=b;
            if(done!=null){synchronized(imageCache){imageCache.put(url,done);}main.post(()->{if(!isFinishing())target.setImageBitmap(done);});}
        });
    }

    private Bitmap downloadBitmap(String u)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 CineDeckRU/1.1.3");int code=c.getResponseCode();if(code<200||code>=400){c.disconnect();return null;}BitmapFactory.Options opt=new BitmapFactory.Options();opt.inPreferredConfig=Bitmap.Config.RGB_565;opt.inSampleSize=2;try(InputStream in=c.getInputStream()){return BitmapFactory.decodeStream(in,null,opt);}finally{c.disconnect();}}

    private List<Channel> loadM3u(String url){try{String raw=httpGet(url,8000,12000);String[] lines=raw.replace("\r","").split("\n");List<Channel> out=new ArrayList<>();String name=null;for(String line:lines){line=line.trim();if(line.startsWith("#EXTINF")){int c=line.lastIndexOf(',');name=c>=0?line.substring(c+1).trim():"Канал";}else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){out.add(new Channel(name==null?"Канал "+(out.size()+1):name,line));name=null;if(out.size()>=120)break;}}return out;}catch(Exception e){lastNetworkError=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());return new ArrayList<>();}}

    private void openService(Service s,String query){String q=query==null?"":Uri.encode(query);String u=s.template.contains("%s")?String.format(Locale.ROOT,s.template,q):s.template;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){Toast.makeText(this,"Нет приложения для открытия ссылки",Toast.LENGTH_LONG).show();}}
    private String clean(String s){if(s==null)return"";return s.replaceAll("<[^>]+>"," ").replace("&amp;","&").replace("&quot;","\"").replaceAll("\\s+"," ").trim();}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout panel(int c){LinearLayout v=column();v.setBackground(roundRect(c,14));return v;}
    private TextView text(String s,int sp,int c,boolean bold){TextView v=new TextView(this);v.setText(s==null?"":s);v.setTextSize(sp);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private Button nav(String s,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);style(b,BG);b.setOnClickListener(l);return b;}
    private Button action(String s,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setPadding(dp(12),0,dp(12),0);style(b,PANEL2);if(l!=null)b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(50));lp.setMargins(0,0,dp(8),0);b.setLayoutParams(lp);return b;}
    private void style(View v,int normal){v.setFocusable(true);v.setBackground(roundRect(normal,10));v.setOnFocusChangeListener((x,f)->x.setBackground(roundRect(f?FOCUS:normal,10)));}
    private GradientDrawable roundRect(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private View spacer(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+0.5f);}

    static class Show{String id="",type="series",name="",year="",rating="",summary="",genres="",image="";}
    static class Service{final String name,template;Service(String n,String t){name=n;template=t;}}
    static class Channel{final String name,url;Channel(String n,String u){name=n;url=u;}}
}
