package com.mabat.media;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.animation.AccelerateDecelerateInterpolator;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    LinearLayout home;
    FrameLayout viewer;
    ImageView picture;
    TextureView movie;
    TextView like, share, save, count, status, speed, timeText;
    SeekBar seekBar;
    Runnable progressUpdater;
    ArrayList<Uri> roots = new ArrayList<>(), items = new ArrayList<>();
    int pos = 0;
    float downX, downY;
    long downTime;
    boolean moved = false, longPressing = false, doubleTapPending = false;
    Handler handler = new Handler(Looper.getMainLooper());
    MediaPlayer player;
    SharedPreferences likes;
    boolean scanning = false;\n    boolean speedLocked=false;\n    SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        setContentView(R.layout.activity_main);

        home=findViewById(R.id.home); viewer=findViewById(R.id.viewer);
        picture=findViewById(R.id.picture); movie=findViewById(R.id.movie);
        like=findViewById(R.id.like); share=findViewById(R.id.share); save=findViewById(R.id.save);
        count=findViewById(R.id.count); status=findViewById(R.id.status); speed=findViewById(R.id.speed);
        seekBar=findViewById(R.id.seekBar); timeText=findViewById(R.id.timeText);
        likes=getSharedPreferences("likes",0);\n        prefs=getSharedPreferences("settings",0);
        movie.setSurfaceTextureListener(this);

        loadRoots();
        findViewById(R.id.add).setOnClickListener(v->pick());
        findViewById(R.id.start).setOnClickListener(v->startScan());\n        findViewById(R.id.settings).setOnClickListener(v->showSettings());
        findViewById(R.id.back).setOnClickListener(v->closeViewer());
        like.setOnClickListener(v->toggleLike());
        share.setOnClickListener(v->share());
        save.setOnClickListener(v->save());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                if(fromUser && player!=null && player.getDuration()>0) player.seekTo((int)((long)p*player.getDuration()/1000L));
                updateTime();
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });

        movie.setOnTouchListener((v,e)->handleTouch(e));
        picture.setOnTouchListener((v,e)->handleTouch(e));
        viewer.setOnTouchListener((v,e)->handleTouch(e));
    }

    boolean handleTouch(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            downX=e.getX(); downY=e.getY(); downTime=System.currentTimeMillis(); moved=false; longPressing=false;
            if(isVideo() && prefs.getBoolean("longSpeed",true)) handler.postDelayed(()->{
                if(!moved && isVideo()){ longPressing=true; setSpeed(2f); }
            },320);
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            if(Math.abs(e.getX()-downX)>35||Math.abs(e.getY()-downY)>35)moved=true;
            if(longPressing && prefs.getBoolean("speedLock",true) && e.getY()-downY>90){
                speedLocked=true; speed.setText("כפול 2 • נעול"); speed.setVisibility(View.VISIBLE);
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP){
            handler.removeCallbacksAndMessages(null);
            if(longPressing){
                if(!speedLocked)setSpeed(1f);
                longPressing=false; scheduleControlsHide(); return true;
            }
            if(moved){
                float d=e.getY()-downY;
                if(Math.abs(d)>90){if(d<0)next();else prev();}
                return true;
            }
            if(System.currentTimeMillis()-downTime<300){
                if(doubleTapPending){
                    doubleTapPending=false;
                    if(prefs.getBoolean("gestureDoubleTap",true)){
                        float third=viewer.getWidth()/3f,x=e.getX();
                        if(x<third)seekBy(-10000);
                        else if(x>third*2f)seekBy(10000);
                        else toggleLike();
                    }else togglePlayback();
                }else{
                    doubleTapPending=true;
                    handler.postDelayed(()->{if(doubleTapPending){doubleTapPending=false;togglePlayback();}},240);
                }
            }
            return true;
        }
        return true;
    }}