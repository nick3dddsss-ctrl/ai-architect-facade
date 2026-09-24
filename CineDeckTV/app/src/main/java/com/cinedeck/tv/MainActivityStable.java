package com.cinedeck.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

public class MainActivityStable extends Activity {
    private static final int BG = Color.rgb(7,10,16);
    private static final int PANEL = Color.rgb(19,25,35);
    private static final int FOCUS = Color.rgb(44,78,120);
    private static final int MUTED = Color.rgb(155,169,190);
    private static final String FEED = "https://cdn.jsdelivr.net/gh/nick3dddsss-ctrl/ai-architect-facade@cinedeck-tv-build/catalog-data/catalog.json";
    private static final String FEED_RAW = "https://raw.githubusercontent.com/nick3dddsss-ctrl/ai-architect-facade/cinedeck-tv-build/catalog-data/catalog.json";

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Item> cache = new ArrayList<>();
    private LinearLayout root;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); showHome(); }
    @Override protected void onDestroy(){ io.shutdownNow(); super.onDestroy(); }

    private void base(){
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28),dp(14),dp(28),dp(18)); root.setBackgroundColor(BG); setContentView(root);
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = txt("▶ CineDeck RU 1.1.5 Stable",24,Color.WHITE,true); bar.addView(logo,new LinearLayout.LayoutParams(0,dp(58),1));
        bar.addView(nav("Главная",v->showHome())); bar.addView(nav("Диагностика",v->showDiag())); root.addView(bar);
    }

    private void showHome(){
        base();
        TextView h = txt("Каталог из интернета",30,Color.WHITE,true); root.addView(h);
        root.addView(txt("Каталог и обложки загружаются через GitHub CDN. В APK контента нет.",15,MUTED,false));
        root.addView(space(12));
        ProgressBar p = new ProgressBar(this); root.addView(p,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView status = txt("Загружаю каталог…",16,MUTED,false); root.addView(status);
        ScrollView sc = new ScrollView(this); LinearLayout content = col(); sc.addView(content); root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        io.execute(() -> {
            List<Item> items = loadFeed();
            main.post(() -> {
                root.removeView(p); root.removeView(status);
                if(items.isEmpty()){
                    content.addView(txt("Каталог не загрузился.",20,Color.rgb(255,120,120),true));
                    content.addView(txt("Открой «Диагностика» — там будет результат проверки самого JSON и тестовой обложки.",15,MUTED,false));
                    return;
                }
                content.addView(txt("Получено: "+items.size()+" позиций",14,MUTED,false));
                List<Item> movies=new ArrayList<>(), series=new ArrayList<>();
                for(Item i:items){ if("movie".equals(i.type)&&movies.size()<24) movies.add(i); else if(!"movie".equals(i.type)&&series.size()<24) series.add(i); }
                if(!movies.isEmpty()){ content.addView(txt("Фильмы",24,Color.WHITE,true)); content.addView(rail(movies)); }
                if(!series.isEmpty()){ content.addView(txt("Сериалы",24,Color.WHITE,true)); content.addView(rail(series)); }
            });
        });
    }

    private void showDiag(){
        base(); root.addView(txt("Диагностика",30,Color.WHITE,true));
        TextView out=txt("Проверяю…",17,MUTED,false); root.addView(out);
        io.execute(() -> {
            String a = testText(FEED); String b = testText(FEED_RAW);
            String image = "нет URL";
            List<Item> items = loadFeed();
            if(!items.isEmpty() && items.get(0).poster!=null && !items.get(0).poster.isEmpty()) image = testImage(items.get(0).poster);
            final String res = "GitHub CDN JSON: "+a+"\nGitHub Raw JSON: "+b+"\nКаталог распознан: "+items.size()+"\nТест обложки: "+image;
            main.post(()->out.setText(res));
        });
    }

    private List<Item> loadFeed(){
        synchronized(cache){ if(!cache.isEmpty()) return new ArrayList<>(cache); }
        for(String u:new String[]{FEED,FEED_RAW}){
            try{
                String raw=get(u,7000,10000); JSONObject root=new JSONObject(raw); JSONArray arr=root.optJSONArray("items"); if(arr==null) continue;
                List<Item> list=new ArrayList<>();
                for(int x=0;x<arr.length();x++){
                    JSONObject o=arr.optJSONObject(x); if(o==null) continue; String name=o.optString("name",""); if(name.isEmpty()) continue;
                    Item i=new Item(); i.name=name; i.year=o.optString("year",""); i.rating=o.optString("rating",""); i.type=o.optString("type","series"); i.desc=o.optString("description",""); i.poster=o.optString("posterUrl",""); i.posterRaw=o.optString("posterRawUrl",""); list.add(i);
                }
                if(!list.isEmpty()){ synchronized(cache){ cache.clear(); cache.addAll(list);} return list; }
            }catch(Exception ignored){}
        }
        return new ArrayList<>();
    }

    private View rail(List<Item> items){
        HorizontalScrollView hs=new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL);
        for(Item i:items) line.addView(card(i)); hs.addView(line); hs.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(306))); return hs;
    }

    private View card(Item i){
        LinearLayout c=col(); c.setFocusable(true); c.setClickable(true); c.setPadding(dp(6),dp(6),dp(6),dp(6)); c.setBackgroundColor(PANEL);
        ImageView im=new ImageView(this); im.setScaleType(ImageView.ScaleType.CENTER_CROP); im.setBackgroundColor(Color.rgb(28,36,48)); c.addView(im,new LinearLayout.LayoutParams(dp(160),dp(224))); loadImage(im,i);
        TextView n=txt(i.name,14,Color.WHITE,true); n.setMaxLines(1); c.addView(n,new LinearLayout.LayoutParams(dp(160),dp(30))); c.addView(txt(i.year+(i.rating.isEmpty()?"":" • ★ "+i.rating),12,MUTED,false));
        c.setOnClickListener(v->showDetails(i)); c.setOnFocusChangeListener((v,f)->{c.setBackgroundColor(f?FOCUS:PANEL);c.setScaleX(f?1.04f:1f);c.setScaleY(f?1.04f:1f);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(176),dp(294)); lp.setMargins(0,0,dp(12),0); c.setLayoutParams(lp); return c;
    }

    private void showDetails(Item i){
        base(); LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); ImageView im=new ImageView(this); im.setScaleType(ImageView.ScaleType.CENTER_CROP); row.addView(im,new LinearLayout.LayoutParams(dp(220),dp(310))); loadImage(im,i);
        LinearLayout info=col(); info.setPadding(dp(20),0,0,0); info.addView(txt(i.name,30,Color.WHITE,true)); info.addView(txt(i.year+(i.rating.isEmpty()?"":" • ★ "+i.rating),15,MUTED,false)); info.addView(space(10)); TextView d=txt(i.desc,16,Color.LTGRAY,false); d.setMaxLines(12); info.addView(d); row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); root.addView(row);
    }

    private void loadImage(ImageView view, Item item){
        io.execute(() -> {
            Bitmap b=tryBitmap(item.poster); if(b==null) b=tryBitmap(item.posterRaw); final Bitmap out=b; if(out!=null) main.post(()->view.setImageBitmap(out));
        });
    }

    private Bitmap tryBitmap(String u){
        if(u==null||u.isEmpty()) return null;
        try{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(6500); c.setReadTimeout(9000); c.setInstanceFollowRedirects(true); c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV) CineDeck/1.1.5"); c.connect(); if(c.getResponseCode()/100!=2){c.disconnect();return null;} InputStream in=c.getInputStream(); BitmapFactory.Options o=new BitmapFactory.Options(); o.inPreferredConfig=Bitmap.Config.RGB_565; Bitmap b=BitmapFactory.decodeStream(in,null,o); in.close(); c.disconnect(); return b; }catch(Exception e){ return null; }
    }

    private String get(String u,int ct,int rt)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(ct); c.setReadTimeout(rt); c.setInstanceFollowRedirects(true); c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV) CineDeck/1.1.5"); c.connect(); int code=c.getResponseCode(); if(code/100!=2) throw new Exception("HTTP "+code); InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) out.write(buf,0,n); in.close(); c.disconnect(); return new String(out.toByteArray(), StandardCharsets.UTF_8); }
    private String testText(String u){ try{String s=get(u,5500,7500);return "OK ("+s.length()+" байт)";}catch(Exception e){return "ОШИБКА: "+e.getClass().getSimpleName();} }
    private String testImage(String u){ Bitmap b=tryBitmap(u); return b==null?"ОШИБКА":"OK "+b.getWidth()+"×"+b.getHeight(); }

    private Button nav(String s, View.OnClickListener l){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setOnClickListener(l); return b; }
    private LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView txt(String s,int sp,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private View space(int h){ View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h))); return v; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    static class Item{ String name,year,rating,type,desc,poster,posterRaw; }
}
