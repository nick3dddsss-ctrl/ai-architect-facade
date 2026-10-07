package com.danyusha.beautyrun;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;

public class SmokeInstrumentation extends Instrumentation {
    private static Object get(Object o,String n)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static void call(Object o,String n)throws Exception {Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    private static void tick(Object o,int count)throws Exception {Method m=o.getClass().getDeclaredMethod("update",float.class);m.setAccessible(true);for(int i=0;i<count;i++)m.invoke(o,1f/60f);}
    private static void check(boolean value,String name) {if(!value)throw new AssertionError(name);}
    private void shot(BeautyRunView v,String name)throws Exception {
        Bitmap b=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();final Throwable[] failure={null};
        runOnMainSync(()->{try{
            BeautyRunView v=new BeautyRunView(getTargetContext());v.layout(0,0,1280,720);
            shot(v,"title.png");
            v.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_CENTER));
            check((Integer)get(v,"state")==1,"OK starts game");
            Object p=get(v,"player");tick(v,2);
            check((Boolean)get(p,"onGround"),"Player settles on ground");
            v.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_RIGHT));tick(v,25);
            v.onKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(1,KeyEvent.KEYCODE_DPAD_RIGHT));
            check((Float)get(p,"x")>190f,"D-pad movement");
            check((Integer)get(v,"collected")>=1,"Brush pickup");
            v.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_CENTER));tick(v,3);
            check((Float)get(p,"vy")<0,"Jump from OK");
            v.pauseGame();check((Integer)get(v,"state")==2,"Pause");check(!(Boolean)get(v,"rightHeld"),"Pause clears controls");
            v.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_CENTER));
            set(p,"checkpointX",1720f);set(p,"x",1800f);set(p,"y",1000f);tick(v,1);
            check((Integer)get(v,"lives")==2,"Fall consumes one life");check(Math.abs((Float)get(p,"x")-1720f)<1,"Checkpoint respawn");
            check((Float)get(v,"invincible")>0,"Respawn protection");
            set(p,"x",6220f);set(p,"y",496f);tick(v,1);check((Integer)get(v,"state")==3,"Finish reached");
            call(v,"startGame");check((Float)get(p,"checkpointX")==120f,"Restart resets checkpoint");
            set(p,"x",280f);set(p,"y",496f);set(p,"onGround",true);set(p,"vx",280f);set(v,"runClock",1.0f);shot(v,"level.png");
            set(p,"x",500f);set(p,"y",350f);set(p,"onGround",false);set(p,"vy",-200f);shot(v,"jump.png");
            call(v,"loseLife");call(v,"loseLife");call(v,"loseLife");check((Integer)get(v,"state")==4,"Game over");
        }catch(Throwable t){failure[0]=t;}});
        if(failure[0]!=null){result.putString("stream","DANYUSHA_SMOKE_FAILED: "+failure[0]);finish(0,result);}
        else {result.putString("stream","DANYUSHA_SMOKE_PASS: assets, render, start, movement, pickup, jump, pause, checkpoint, protection, finish, restart, game over");finish(-1,result);}
    }
}