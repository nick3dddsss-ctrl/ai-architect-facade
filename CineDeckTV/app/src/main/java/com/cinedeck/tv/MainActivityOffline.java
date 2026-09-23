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
import android.widget.MediaController;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityOffline extends Activity {
    private static final int BG = Color.rgb(7,10,16);
    private static final int PANEL = Color.rgb(18,24,34);
    private static final int PANEL2 = Color.rgb(27,35,49);
    private static final int FOCUS = Color.rgb(43,88,143);
    private static final int PRIMARY = Color.rgb(69,155,255);
    private static final int MUTED = Color.rgb(157,171,191);
    private static final String PREFS = "cinedeck_ru_112";
    private static final String KEY_M3U = "m3u";

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,Bitmap> imageCache = new LinkedHashMap<String,Bitmap>(12,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){ return size()>8; }
    };
    private final List<Item> catalog = new ArrayList<>();
    private LinearLayout root;
    private SharedPreferences prefs;
    private Runnable backAction;
    private VideoView videoView;

    private final List<Service> services = Arrays.asList(
            new Service("VK Видео","https://vkvideo.ru/video?q=%s"),
            new Service("RUTUBE","https://rutube.ru/search/?query=%s"),
            new Service("Кинопоиск","https://www.kinopoisk.ru/index.php?kp_query=%s"),
            new Service("Wink","https://wink.ru/search?query=%s"),
            new Service("Иви","https://www.ivi.ru/search/?q=%s"),
            new Service("Okko","https://okko.tv/"),
            new Service("KION","https://kion.ru/")
    );

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        loadBundledCatalog();
        showHome();
    }

    @Override protected void onDestroy(){ stopVideo(); io.shutdownNow(); super.onDestroy(); }
    @Override public void onBackPressed(){ if(backAction!=null){Runnable r=backAction;backAction=null;r.run();}else super.onBackPressed(); }

    private void screen(){
        stopVideo(); backAction=null;
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28),dp(12),dp(28),dp(18)); root.setBackgroundColor(BG); setContentView(root); topBar();
    }

    private void topBar(){
        LinearLayout bar=row(); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("▶  CineDeck RU",24,Color.WHITE,true); bar.addView(logo,new LinearLayout.LayoutParams(dp(280),dp(58)));
        bar.addView(nav("Главная",v->showHome())); bar.addView(nav("Поиск",v->showSearch())); bar.addView(nav("Сервисы",v->showServices())); bar.addView(nav("ТВ / M3U",v->showChannels())); bar.addView(nav("Диагностика",v->showDiagnostics()));
        root.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(62)));
    }

    private void showHome(){
        screen();
        LinearLayout hero=panel(PANEL); hero.setPadding(dp(24),dp(16),dp(24),dp(16));
        hero.addView(text("CINEDECK RU 1.1.2 • OFFLINE-FIRST",13,PRIMARY,true));
        hero.addView(text("Каталог работает даже без зарубежных API",31,Color.WHITE,true));
        hero.addView(text("Фильмы, сериалы и постеры уже находятся внутри APK. Интернет нужен только для российских сервисов, IPTV и дополнительных ссылок.",15,Color.LTGRAY,false));
        LinearLayout acts=row(); acts.addView(action("⌕  Поиск",v->showSearch())); acts.addView(action("▣  ТВ / M3U",v->showChannels())); acts.addView(action("◉  Диагностика",v->showDiagnostics())); hero.addView(acts);
        root.addView(hero,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(190)));
        root.addView(spacer(10)); addServices(root,null); root.addView(spacer(10));
        ScrollView sc=new ScrollView(this); LinearLayout content=column(); sc.addView(content);
        List<Item> movies=filterType("movie"), series=filterType("series");
        addRail(content,"Фильмы",movies); addRail(content,"Сериалы",series);
        root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    private void addRail(LinearLayout parent,String title,List<Item> list){
        parent.addView(text(title,24,Color.WHITE,true)); parent.addView(spacer(5));
        if(list.isEmpty()){ parent.addView(text("Встроенный каталог пуст — это ошибка сборки.",15,MUTED,false)); return; }
        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); LinearLayout line=row();
        for(Item i:list) line.addView(card(i)); hs.addView(line); parent.addView(hs,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(304))); parent.addView(spacer(12));
    }

    private View card(Item i){
        LinearLayout c=column(); c.setFocusable(true); c.setClickable(true); c.setPadding(dp(6),dp(6),dp(6),dp(6)); c.setBackground(roundRect(PANEL,12));
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(roundRect(PANEL2,9)); c.addView(img,new LinearLayout.LayoutParams(dp(160),dp(224))); loadImage(img,i);
        TextView n=text(i.name,14,Color.WHITE,true); n.setMaxLines(1); c.addView(n,new LinearLayout.LayoutParams(dp(160),dp(27)));
        c.addView(text(i.meta(),12,MUTED,false)); c.setOnClickListener(v->showDetails(i));
        c.setOnFocusChangeListener((v,f)->{c.setBackground(roundRect(f?FOCUS:PANEL,12));float s=f?1.045f:1f;c.setScaleX(s);c.setScaleY(s);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(176),dp(294)); lp.setMargins(0,0,dp(12),0); c.setLayoutParams(lp); return c;
    }

    private void showSearch(){
        screen(); root.addView(text("Поиск",32,Color.WHITE,true)); root.addView(text("Поиск по встроенному каталогу срабатывает без интернета. Для любого названия ниже доступны российские сервисы.",14,MUTED,false)); root.addView(spacer(8));
        LinearLayout line=row(); EditText q=new EditText(this); q.setSingleLine(true); q.setHint("Название фильма или сериала"); q.setTextColor(Color.WHITE); q.setHintTextColor(MUTED); q.setTextSize(18); q.setBackground(roundRect(PANEL,11)); q.setPadding(dp(14),0,dp(14),0); q.setImeOptions(EditorInfo.IME_ACTION_SEARCH); line.addView(q,new LinearLayout.LayoutParams(dp(720),dp(56))); Button go=action("Найти",null); line.addView(go,new LinearLayout.LayoutParams(dp(150),dp(56))); root.addView(line); root.addView(spacer(8));
        ScrollView sc=new ScrollView(this); LinearLayout results=column(); sc.addView(results); root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        Runnable search=()->{String s=q.getText().toString().trim();if(s.isEmpty())return;results.removeAllViews();addServices(results,s);results.addView(spacer(8));List<Item> found=searchLocal(s);if(found.isEmpty())results.addView(text("Во встроенном каталоге совпадений нет. Используй кнопки сервисов выше — они ищут полный каталог сервиса.",16,MUTED,false));else for(Item x:found)results.addView(searchRow(x));};
        go.setOnClickListener(v->search.run()); q.setOnEditorActionListener((v,id,e)->{search.run();return true;}); q.requestFocus();
    }

    private View searchRow(Item i){
        LinearLayout r=row(); r.setFocusable(true); r.setClickable(true); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(8),dp(7),dp(12),dp(7)); r.setBackground(roundRect(PANEL,10));
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); r.addView(img,new LinearLayout.LayoutParams(dp(70),dp(96))); loadImage(img,i);
        LinearLayout info=column(); info.setPadding(dp(12),0,0,0); info.addView(text(i.name,19,Color.WHITE,true)); info.addView(text(i.meta(),13,MUTED,false)); TextView d=text(i.description,13,Color.LTGRAY,false); d.setMaxLines(2); info.addView(d); r.addView(info,new LinearLayout.LayoutParams(0,dp(96),1));
        r.setOnClickListener(v->showDetails(i)); r.setOnFocusChangeListener((v,f)->r.setBackground(roundRect(f?FOCUS:PANEL,10))); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(110)); lp.setMargins(0,0,0,dp(6)); r.setLayoutParams(lp); return r;
    }

    private void showDetails(Item i){
        screen(); backAction=this::showHome; LinearLayout box=panel(PANEL); box.setPadding(dp(20),dp(18),dp(20),dp(18)); LinearLayout head=row();
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setBackground(roundRect(PANEL2,10)); head.addView(img,new LinearLayout.LayoutParams(dp(205),dp(286))); loadImage(img,i);
        LinearLayout info=column(); info.setPadding(dp(20),0,0,0); info.addView(text(i.name,32,Color.WHITE,true)); info.addView(text(i.meta()+(!i.genres.isEmpty()?" • "+i.genres:""),15,MUTED,false)); info.addView(spacer(8)); TextView d=text(i.description,16,Color.WHITE,false); d.setMaxLines(10); info.addView(d); info.addView(spacer(12)); info.addView(action("⌕  Где смотреть",v->showWhere(i.name))); head.addView(info,new LinearLayout.LayoutParams(0,dp(286),1)); box.addView(head); root.addView(box,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
    }

    private void showWhere(String q){ screen(); backAction=this::showHome; root.addView(text("Где искать",32,Color.WHITE,true)); root.addView(text(q,20,Color.LTGRAY,false)); root.addView(spacer(10)); addServices(root,q); }
    private void showServices(){ screen(); root.addView(text("Российские сервисы",32,Color.WHITE,true)); root.addView(text("Кнопки открывают официальный сайт или установленное приложение сервиса.",14,MUTED,false)); root.addView(spacer(10)); addServices(root,null); }

    private void addServices(LinearLayout parent,String query){
        parent.addView(text(query==null?"Российские сервисы":"Искать «"+query+"»",20,Color.WHITE,true)); parent.addView(spacer(6)); HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); LinearLayout line=row();
        for(Service s:services){Button b=new Button(this);b.setAllCaps(false);b.setText(s.name);b.setTextColor(Color.WHITE);b.setTextSize(14);style(b,PANEL2);b.setOnClickListener(v->openService(s,query));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(170),dp(58));lp.setMargins(0,0,dp(8),0);line.addView(b,lp);} hs.addView(line); parent.addView(hs,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(66)));
    }

    private void showChannels(){
        screen(); root.addView(text("ТВ / M3U",32,Color.WHITE,true)); LinearLayout a=row(); a.addView(action("⚙ Плейлист",v->promptM3u())); a.addView(action("▶ Прямая ссылка",v->promptStream())); root.addView(a); root.addView(spacer(8)); String u=prefs.getString(KEY_M3U,""); if(u==null||u.trim().isEmpty()){root.addView(text("M3U-плейлист ещё не указан.",16,MUTED,false));return;} ProgressBar p=new ProgressBar(this); root.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));
        io.execute(()->{List<Channel> list=loadM3u(u);main.post(()->{root.removeView(p);if(list.isEmpty())root.addView(text("Не удалось загрузить M3U. Проверь адрес и доступность сервера.",16,MUTED,false));else{ScrollView sc=new ScrollView(this);LinearLayout c=column();for(Channel ch:list){Button b=new Button(this);b.setAllCaps(false);b.setText("▶  "+ch.name);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);style(b,PANEL);b.setOnClickListener(v->play(ch.name,ch.url));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));lp.setMargins(0,0,0,dp(4));c.addView(b,lp);}sc.addView(c);root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));}});});
    }

    private void promptM3u(){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);e.setText(prefs.getString(KEY_M3U,""));e.setHint("http://.../playlist.m3u");new AlertDialog.Builder(this).setTitle("M3U-плейлист").setView(e).setPositiveButton("Сохранить",(d,w)->{prefs.edit().putString(KEY_M3U,e.getText().toString().trim()).apply();showChannels();}).setNegativeButton("Отмена",null).show();}
    private void promptStream(){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);e.setHint("http://.../video.m3u8 или .mp4");new AlertDialog.Builder(this).setTitle("Открыть поток").setView(e).setPositiveButton("Открыть",(d,w)->{String u=e.getText().toString().trim();if(!u.isEmpty())play("Поток",u);}).setNegativeButton("Отмена",null).show();}
    private void play(String title,String url){screen();backAction=this::showChannels;root.addView(text(title,22,Color.WHITE,true));videoView=new VideoView(this);videoView.setBackgroundColor(Color.BLACK);root.addView(videoView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));try{videoView.setVideoURI(Uri.parse(url));MediaController mc=new MediaController(this);mc.setAnchorView(videoView);videoView.setMediaController(mc);videoView.setOnErrorListener((mp,w,e)->{Toast.makeText(this,"Этот поток не поддерживается системным плеером приставки",Toast.LENGTH_LONG).show();return true;});videoView.start();}catch(Exception e){Toast.makeText(this,"Не удалось открыть поток",Toast.LENGTH_LONG).show();}}
    private void stopVideo(){if(videoView!=null){try{videoView.stopPlayback();}catch(Exception ignored){}videoView=null;}}

    private void showDiagnostics(){
        screen(); root.addView(text("Диагностика",32,Color.WHITE,true)); ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE); int mem=am==null?0:am.getMemoryClass(); root.addView(text("Android: "+Build.VERSION.RELEASE+" (SDK "+Build.VERSION.SDK_INT+")",17,Color.WHITE,true)); root.addView(text("Ядро: "+System.getProperty("os.version","неизвестно"),16,Color.LTGRAY,false)); root.addView(text("Модель: "+Build.MANUFACTURER+" "+Build.MODEL,16,Color.LTGRAY,false)); root.addView(text("Память приложения: ~"+mem+" МБ",16,Color.LTGRAY,false)); root.addView(text("Встроенный каталог: "+catalog.size()+" карточек — "+(catalog.isEmpty()?"ОШИБКА":"OK"),16,catalog.isEmpty()?Color.rgb(255,120,120):Color.rgb(110,220,150),true)); root.addView(spacer(10)); TextView net=text("Проверяю интернет-маршруты…",16,MUTED,false); root.addView(net); ProgressBar p=new ProgressBar(this); root.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));
        io.execute(()->{String a=testHost("https://ya.ru");String b=testHost("https://raw.githubusercontent.com");String c=testHost("https://api.tvmaze.com/shows/1");main.post(()->{root.removeView(p);net.setText("Яндекс: "+a+"\nGitHub CDN: "+b+"\nTVMaze: "+c+"\n\nДаже если TVMaze = ОШИБКА, встроенный каталог должен работать.");net.setTextColor(Color.WHITE);});});
    }

    private String testHost(String url){try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setRequestProperty("User-Agent","Mozilla/5.0 CineDeckRU/1.1.2");int code=c.getResponseCode();c.disconnect();return code>=200&&code<500?"OK ("+code+")":"ОШИБКА ("+code+")";}catch(Exception e){return "ОШИБКА: "+e.getClass().getSimpleName();}}

    private void loadBundledCatalog(){
        catalog.clear(); try(InputStream in=getAssets().open("catalog.json")){String raw=readAll(in);JSONObject root=new JSONObject(raw);JSONArray items=root.optJSONArray("items");if(items==null)return;for(int n=0;n<items.length();n++){JSONObject o=items.optJSONObject(n);if(o==null)continue;Item i=new Item();i.id=o.optString("id","");i.type=o.optString("type","series");i.name=o.optString("name","");i.year=o.optString("year","");i.rating=o.optString("rating","");i.description=o.optString("description","Описание отсутствует.");i.posterAsset=o.optString("posterAsset","");i.posterUrl=o.optString("posterUrl","");JSONArray gs=o.optJSONArray("genres");if(gs!=null){StringBuilder b=new StringBuilder();for(int x=0;x<gs.length()&&x<3;x++){String g=gs.optString(x,"");if(!g.isEmpty()){if(b.length()>0)b.append(", ");b.append(g);}}i.genres=b.toString();}if(!i.name.isEmpty())catalog.add(i);}}catch(Exception e){Toast.makeText(this,"Не найден встроенный каталог",Toast.LENGTH_LONG).show();}
    }

    private List<Item> filterType(String type){List<Item> r=new ArrayList<>();for(Item i:catalog)if(type.equals(i.type))r.add(i);return r;}
    private List<Item> searchLocal(String q){String needle=q.toLowerCase(Locale.ROOT);List<Item> r=new ArrayList<>();for(Item i:catalog){String hay=(i.name+" "+i.year+" "+i.genres).toLowerCase(Locale.ROOT);if(hay.contains(needle))r.add(i);}return r;}

    private void loadImage(ImageView target,Item item){
        final String key=!item.posterAsset.isEmpty()?"asset:"+item.posterAsset:item.posterUrl; if(key==null||key.isEmpty())return; synchronized(imageCache){Bitmap b=imageCache.get(key);if(b!=null){target.setImageBitmap(b);return;}}
        io.execute(()->{try{BitmapFactory.Options opt=new BitmapFactory.Options();opt.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap b=null;if(!item.posterAsset.isEmpty()){try(InputStream in=getAssets().open(item.posterAsset)){b=BitmapFactory.decodeStream(in,null,opt);}}else if(!item.posterUrl.isEmpty()){HttpURLConnection c=(HttpURLConnection)new URL(item.posterUrl).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(6000);try(InputStream in=c.getInputStream()){b=BitmapFactory.decodeStream(in,null,opt);}c.disconnect();}if(b!=null){synchronized(imageCache){imageCache.put(key,b);}Bitmap finalB=b;main.post(()->{if(!isFinishing())target.setImageBitmap(finalB);});}}catch(Throwable ignored){}});
    }

    private List<Channel> loadM3u(String url){try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","CineDeckRU/1.1.2");String raw;try(InputStream in=c.getInputStream()){raw=readAll(in);}c.disconnect();String[] lines=raw.replace("\r","").split("\n");List<Channel> out=new ArrayList<>();String name=null;for(String line:lines){line=line.trim();if(line.startsWith("#EXTINF")){int k=line.lastIndexOf(',');name=k>=0?line.substring(k+1).trim():"Канал";}else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){out.add(new Channel(name==null?"Канал "+(out.size()+1):name,line));name=null;if(out.size()>=100)break;}}return out;}catch(Exception e){return Collections.emptyList();}}
    private String readAll(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new String(out.toByteArray(),Charset.forName("UTF-8"));}

    private void openService(Service s,String query){String q=query==null?"":Uri.encode(query);String u=s.template.contains("%s")?String.format(Locale.ROOT,s.template,q):s.template;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){Toast.makeText(this,"Нет приложения для открытия ссылки",Toast.LENGTH_LONG).show();}}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;} private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;} private LinearLayout panel(int c){LinearLayout v=column();v.setBackground(roundRect(c,14));return v;}
    private TextView text(String s,int sp,int c,boolean bold){TextView v=new TextView(this);v.setText(s==null?"":s);v.setTextSize(sp);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private Button nav(String s,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);style(b,BG);b.setOnClickListener(l);return b;} private Button action(String s,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setPadding(dp(12),0,dp(12),0);style(b,PANEL2);if(l!=null)b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(50));lp.setMargins(0,0,dp(8),0);b.setLayoutParams(lp);return b;}
    private void style(View v,int normal){v.setFocusable(true);v.setBackground(roundRect(normal,10));v.setOnFocusChangeListener((x,f)->x.setBackground(roundRect(f?FOCUS:normal,10)));} private GradientDrawable roundRect(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;} private View spacer(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;} private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}

    private static class Service{final String name,template;Service(String n,String t){name=n;template=t;}}
    private static class Item{String id="",type="series",name="",year="",rating="",description="",genres="",posterAsset="",posterUrl="";String meta(){StringBuilder b=new StringBuilder(type.equals("movie")?"Фильм":"Сериал");if(!year.isEmpty())b.append(" • ").append(year);if(!rating.isEmpty()&&!"0".equals(rating))b.append(" • ★ ").append(rating);return b.toString();}}
    private static class Channel{final String name,url;Channel(String n,String u){name=n;url=u;}}
}
