from pathlib import Path
import re

root=Path('/tmp/makeup231')

build=root/'app/build.gradle'
s=build.read_text()
s=re.sub(r'versionCode\s+\d+', 'versionCode 231', s)
s=re.sub(r"versionName\s+'[^']+'", "versionName '2.3.1'", s)
build.write_text(s)

main=root/'app/src/main/java/ru/daniafedorina/makeuptv/MainActivity.java'
s=main.read_text()
s=s.replace('root.setBackground(makeGradient(0xff0b0a0a,0xff080808,0));','root.setBackground(makeGradient(0xff0c090a,0xff050405,0));')
s=s.replace('navShell.setBackgroundColor(Color.rgb(17,15,16));root.addView(navShell,new LinearLayout.LayoutParams(dp(236),-1));','navShell.setBackgroundColor(Color.rgb(15,12,14));root.addView(navShell,new LinearLayout.LayoutParams(dp(244),-1));')
s=s.replace('TextView brand=text("ДАНИЯ ФЕДОРИНА",19,0xfff3e4dd);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);navShell.addView(brand,lp(-1,dp(31)));','TextView brand=text("DANIA FEDORINA",18,0xfff3e4dd);brand.setTypeface(Typeface.SERIF,Typeface.BOLD);navShell.addView(brand,lp(-1,dp(29)));')
s=s.replace('TextView sub=text("MAKEUP TV  ·  NATIVE 2.2",10.5f,0xffdda5ad);sub.setLetterSpacing(.07f);navShell.addView(sub,lp(-1,dp(34)));','TextView sub=text("MAKEUP TV  ·  больше, чем макияж",10.5f,0xffe0aeb6);sub.setLetterSpacing(.04f);navShell.addView(sub,lp(-1,dp(34)));')
hero='''    private View buildHero(){
        FrameLayout box=new FrameLayout(this);box.setBackground(makeGradient(0xff27171c,0xff110d0f,22));
        ImageView bg=new ImageView(this);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);bg.setImageResource(R.drawable.hero_glam);box.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        View scrim=new View(this);scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xee0f0b0d,0xd91a1114,0x55140d10}));box.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        box.setPadding(dp(25),dp(19),dp(22),dp(16));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);FrameLayout.LayoutParams lpp=new FrameLayout.LayoutParams(dp(690),-1,Gravity.LEFT);box.addView(left,lpp);
        TextView kicker=text("РЕКОМЕНДУЕМ",11,0xfff0c5cc);kicker.setLetterSpacing(.08f);left.addView(kicker,lp(-1,dp(25)));
        TextView h=text("Уроки макияжа\\nна каждый день",29,0xfffff5f1);h.setTypeface(Typeface.SERIF,Typeface.BOLD);left.addView(h,lp(-1,dp(72)));
        TextView d=text("Пошаговые уроки, подборки по средствам и кистям и быстрый поиск видеоуроков.",13.5f,0xfff2dfe2);d.setMaxLines(2);left.addView(d,lp(-1,dp(44)));
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);left.addView(buttons,lp(-1,dp(45)));
        TextView start=heroButton("СМОТРЕТЬ",true);start.setOnClickListener(v->performSearch("макияж с нуля полный урок для начинающих"));buttons.addView(start,lp(dp(160),dp(42)));
        TextView products=heroButton("СРЕДСТВА",false);products.setOnClickListener(v->showProducts());LinearLayout.LayoutParams pp=lp(dp(150),dp(42));pp.leftMargin=dp(10);buttons.addView(products,pp);
        TextView brushes=heroButton("КИСТИ",false);brushes.setOnClickListener(v->showBrushes());LinearLayout.LayoutParams bp=lp(dp(130),dp(42));bp.leftMargin=dp(10);buttons.addView(brushes,bp);
        continueBtn=heroButton("ПРОДОЛЖИТЬ",false);LinearLayout.LayoutParams cp=lp(dp(170),dp(42));cp.leftMargin=dp(10);buttons.addView(continueBtn,cp);
        TextView right=text("Dania\\nFedorina",26,0xfff0b1bd);right.setTypeface(Typeface.SERIF,Typeface.ITALIC);right.setGravity(Gravity.RIGHT);FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(210),-2,Gravity.RIGHT|Gravity.TOP);rp.topMargin=dp(18);rp.rightMargin=dp(18);box.addView(right,rp);
        return box;
    }

'''
s=re.sub(r'    private View buildHero\(\)\{.*?(?=    private TextView heroButton)',hero,s,flags=re.S)
main.write_text(s)

va=root/'app/src/main/java/ru/daniafedorina/makeuptv/VideoAdapter.java'
s=va.read_text()
old='''        h.monogram.setVisibility(category||v.thumb==null||v.thumb.isEmpty()?View.VISIBLE:View.GONE);h.monogram.setText(category?badge(v):"MAKEUP");
        h.art.setBackground(category?categoryGradient(v):videoPlaceholder());
        if(category){images.clearView(h.img);}else images.load(v.thumb,h.img);
'''
new='''        int categoryRes=category?categoryArt(v):0;
        boolean showMonogram=(category && categoryRes==0)||(!category&&(v.thumb==null||v.thumb.isEmpty()));
        h.monogram.setVisibility(showMonogram?View.VISIBLE:View.GONE);h.monogram.setText(category?badge(v):"MAKEUP");
        h.art.setBackground(category?categoryGradient(v):videoPlaceholder());
        if(category){images.clearView(h.img);if(categoryRes!=0){h.img.setImageResource(categoryRes);h.img.setVisibility(View.VISIBLE);}else h.img.setVisibility(View.INVISIBLE);}else{h.img.setVisibility(View.VISIBLE);images.load(v.thumb,h.img);}
'''
if old not in s:
    raise SystemExit('VideoAdapter bind block not found')
s=s.replace(old,new)
method='''
    private int categoryArt(VideoItem v){
        String t=v.title==null?"":v.title.toLowerCase(Locale.ROOT);String d=v.author==null?"":v.author.toLowerCase(Locale.ROOT);String all=t+" "+d;
        if(all.contains("с нуля")||all.contains("начина")||all.contains("ошибк")||all.contains("курс")||all.contains("день 7"))return R.drawable.cover_makeup_start;
        if(all.contains("дневн")||all.contains("для себя")||all.contains("nude")||all.contains("без макияжа")||all.contains("экспресс")||all.contains("10 минут")||all.contains("день 1"))return R.drawable.cover_day;
        if(all.contains("вечер")||all.contains("креатив")||all.contains("фото")||all.contains("фотосес")||all.contains("день 6"))return R.drawable.cover_evening;
        if(all.contains("свад"))return R.drawable.cover_bride;
        if(all.contains("возраст")||all.contains("зрел")||all.contains("40+")||all.contains("50+"))return R.drawable.cover_age;
        if(all.contains("smok")||all.contains("смоки"))return R.drawable.cover_smokey;
        if(all.contains("стрел"))return R.drawable.cover_arrows;
        if(all.contains("губ")||all.contains("помад")||all.contains("тинт")||all.contains("блеск")||all.contains("карандаш для губ"))return R.drawable.cover_lips;
        if(all.contains("глаз")||all.contains("ресниц")||all.contains("нависш")||all.contains("туш")||all.contains("тени")||all.contains("подводк")||all.contains("бров"))return R.drawable.cover_eyes;
        if(all.contains("кист")||all.contains("спонж")||all.contains("дуофиб")||all.contains("растуш")||all.contains("веер")||all.contains("мыть")||all.contains("инструмент"))return R.drawable.cover_brushes;
        if(all.contains("тон")||all.contains("bb")||all.contains("cc")||all.contains("консил")||all.contains("корректор")||all.contains("пудр")||all.contains("праймер")||all.contains("фиксатор")||all.contains("день 2")||all.contains("день 3"))return R.drawable.cover_tone;
        if(all.contains("средств")||all.contains("косметик")||all.contains("скульп")||all.contains("бронзер")||all.contains("румян")||all.contains("хайлай"))return R.drawable.cover_products;
        return R.drawable.cover_makeup_start;
    }

'''
s=s.replace('    private GradientDrawable categoryGradient(VideoItem v){',method+'    private GradientDrawable categoryGradient(VideoItem v){')
va.write_text(s)
