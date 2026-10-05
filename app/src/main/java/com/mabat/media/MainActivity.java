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
import android.graphics.*;
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
    TextView share, count, status, speed, timeText;
    ImageView pauseIndicator;
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
    boolean scanning = false;
    boolean speedLocked=false;
    SharedPreferences prefs;
    AlertDialog colorDialog;
    Runnable longPressRunnable;
    boolean returningFromBackground=false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        setContentView(R.layout.activity_main);

        home=findViewById(R.id.home); viewer=findViewById(R.id.viewer);
        picture=findViewById(R.id.picture); movie=findViewById(R.id.movie);
        share=findViewById(R.id.share);
        count=findViewById(R.id.count); status=findViewById(R.id.status); speed=findViewById(R.id.speed);
        pauseIndicator=findViewById(R.id.pauseIndicator);
        seekBar=findViewById(R.id.seekBar); timeText=findViewById(R.id.timeText);
        likes=getSharedPreferences("likes",0);
        prefs=getSharedPreferences("settings",0);
        movie.setSurfaceTextureListener(this);

        loadRoots();
        applyAccent(prefs.getInt("accentColor",0xFF7C4DFF));
        findViewById(R.id.add).setOnClickListener(v->pick());
        findViewById(R.id.start).setOnClickListener(v->startScan());
        findViewById(R.id.settings).setOnClickListener(v->showSettings());
        findViewById(R.id.back).setOnClickListener(v->closeViewer());
        share.setOnClickListener(v->share());
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
        if(!prefs.getBoolean("zoomConfigured",false)) handler.postDelayed(this::showFirstZoomSetup,350);
    }

    boolean handleTouch(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downTime=System.currentTimeMillis();moved=false;longPressing=false;speedLocked=false;
            if(isVideo()&&prefs.getBoolean("longSpeed",true)){ longPressRunnable=()->{if(!moved&&isVideo()){longPressing=true;setSpeed(2f);}}; handler.postDelayed(longPressRunnable,320); } return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>35||Math.abs(e.getY()-downY)>35)moved=true;
            if(longPressing&&prefs.getBoolean("speedLock",true)&&e.getY()-downY>90){speedLocked=true;speed.setText("כפול 2 • נעול");speed.setVisibility(View.VISIBLE);}return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){if(longPressRunnable!=null){handler.removeCallbacks(longPressRunnable);longPressRunnable=null;}if(longPressing){if(!speedLocked)setSpeed(1f);longPressing=false;scheduleControlsHide();return true;}
            if(moved){float d=e.getY()-downY;if(Math.abs(d)>90){if(d<0)next();else prev();}return true;}
            if(System.currentTimeMillis()-downTime<300){if(doubleTapPending){doubleTapPending=false;
                // Double-tap also brings the hidden controls back so the back button is immediately reachable.
                setControlsVisible(true); scheduleControlsHide();
                if(prefs.getBoolean("gestureDoubleTap",true)){float third=viewer.getWidth()/3f,x=e.getX();if(x<third)seekBy(-10000);else if(x>third*2f)seekBy(10000);else togglePlayback();}else togglePlayback();}
                else{doubleTapPending=true;handler.postDelayed(()->{if(doubleTapPending){doubleTapPending=false;togglePlayback();}},240);}}return true;}return true;
    }

    void setControlsVisible(boolean visible){int v=visible?View.VISIBLE:View.GONE;findViewById(R.id.actionColumn).setVisibility(v);findViewById(R.id.bottomInfo).setVisibility(v);count.setVisibility(v);seekBar.setVisibility(v);timeText.setVisibility(v);findViewById(R.id.back).setVisibility(v);}
    void scheduleControlsHide(){if(!prefs.getBoolean("autoHide",true))return;handler.postDelayed(()->{if(player!=null&&player.isPlaying())setControlsVisible(false);},2000);}
    void showFirstZoomSetup(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,8,24,4);
        TextView info=new TextView(this);info.setText("בחר את גודל הסרטון שנוח לך. אפשר לשנות את זה אחר כך בהגדרות.");info.setTextSize(16);info.setPadding(0,0,0,10);box.addView(info);
        DemoPreviewView demo=new DemoPreviewView(this); demo.setLayoutParams(new LinearLayout.LayoutParams(-1,360)); box.addView(demo);
        SeekBar z=new SeekBar(this);z.setMax(20);z.setProgress(8);box.addView(z);
        TextView value=new TextView(this);value.setText("גודל: 96%");value.setGravity(Gravity.CENTER);box.addView(value);
        z.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){value.setText("גודל: "+(88+p)+"%");demo.setScaleFactor((88+p)/100f);}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle("הגדרת גודל הסרטון").setMessage("בפעם הראשונה בלבד").setView(box)
            .setPositiveButton("שמור",(d,w)->prefs.edit().putInt("videoZoom",88+z.getProgress()).putBoolean("zoomConfigured",true).apply())
            .setNegativeButton("ברירת מחדל",(d,w)->prefs.edit().putInt("videoZoom",96).putBoolean("zoomConfigured",true).apply()).setCancelable(false).show();
    }

    void showZoomSetup(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,8,24,4);
        DemoPreviewView demo=new DemoPreviewView(this);
        LinearLayout.LayoutParams demoParams=new LinearLayout.LayoutParams(-1,420);
        demo.setLayoutParams(demoParams);
        box.addView(demo);
        SeekBar z=new SeekBar(this);z.setMax(20);z.setProgress(Math.max(0,Math.min(20,prefs.getInt("videoZoom",96)-88)));
        TextView value=new TextView(this);value.setText("גודל: "+(88+z.getProgress())+"%");value.setGravity(Gravity.CENTER);box.addView(z);box.addView(value);
        z.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){value.setText("גודל: "+(88+p)+"%");demo.setScaleFactor((88+p)/100f);}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle("גודל סרטון").setView(box).setPositiveButton("שמור",(d,w)->{prefs.edit().putInt("videoZoom",88+z.getProgress()).putBoolean("zoomConfigured",true).apply();if(player!=null)fitVideo(player.getVideoWidth(),player.getVideoHeight());}).setNegativeButton("ביטול",null).show();
    }

    class DemoPreviewView extends View {
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float scaleFactor=1f;
        long started=System.currentTimeMillis();
        DemoPreviewView(Context c){super(c);paint.setTypeface(Typeface.create("sans",Typeface.BOLD));}
        void setScaleFactor(float f){scaleFactor=f;invalidate();}
        @Override protected void onDraw(Canvas c){
            c.drawColor(Color.BLACK);
            float w=getWidth(),h=getHeight();
            c.save(); c.translate(w/2f,h/2f); c.scale(scaleFactor,scaleFactor);
            float vw=Math.min(w*.62f,260f), vh=vw*1.78f;
            paint.setColor(Color.rgb(35,35,45));c.drawRoundRect(-vw/2,-vh/2,vw/2,vh/2,24,24,paint);
            float t=((System.currentTimeMillis()-started)%2200)/2200f;
            paint.setColor(Color.rgb(124,77,255));c.drawCircle((float)Math.sin(t*Math.PI*2)*vw*.28f,(float)Math.cos(t*Math.PI*2)*vh*.22f,24,paint);
            paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(22);c.drawText("הדגמת וידאו",0,10,paint);
            paint.setTextSize(13);paint.setColor(Color.LTGRAY);c.drawText("כך ייראה גודל הסרטון",0,35,paint);
            c.restore();postInvalidateDelayed(40);
        }
    }

    void showSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(18,8,18,4);
        TextView hint=new TextView(this);hint.setText("התאם את טיק דוס בדיוק איך שנוח לך");hint.setTextColor(0xFF888896);hint.setTextSize(13);hint.setPadding(4,0,4,10);box.addView(hint);
        CheckBox a=new CheckBox(this);a.setText("הסתרת כפתורים אחרי 2 שניות");a.setChecked(prefs.getBoolean("autoHide",true));
        CheckBox g=new CheckBox(this);g.setText("דאבל־טאפ: אחורה / קדימה");g.setChecked(prefs.getBoolean("gestureDoubleTap",true));
        CheckBox sp=new CheckBox(this);sp.setText("לחיצה ארוכה = כפול 2");sp.setChecked(prefs.getBoolean("longSpeed",true));
        CheckBox l=new CheckBox(this);l.setText("נעילת כפול 2 בהחלקה למטה");l.setChecked(prefs.getBoolean("speedLock",true));
        CheckBox ap=new CheckBox(this);ap.setText("הפעל סרטון אוטומטית במעבר לפריט");ap.setChecked(prefs.getBoolean("autoPlay",true));
        CheckBox tr=new CheckBox(this);tr.setText("אנימציית מעבר בין סרטונים");tr.setChecked(prefs.getBoolean("transitionAnim",true));
        Button zoom=new Button(this);zoom.setText("גודל סרטון: "+prefs.getInt("videoZoom",96)+"%");zoom.setOnClickListener(v->showZoomSetup());
        Button colors=new Button(this);colors.setText("🎨 צבעי האפליקציה");colors.setOnClickListener(v->showColorSettings());
        Button guideBtn=new Button(this);guideBtn.setText("📖 מדריך והוראות");guideBtn.setAllCaps(false);guideBtn.setOnClickListener(v->showGuide());
        box.addView(a);box.addView(g);box.addView(sp);box.addView(l);box.addView(ap);box.addView(tr);box.addView(zoom);box.addView(colors);box.addView(guideBtn);
        new AlertDialog.Builder(this).setTitle("⚙ הגדרות טיק דוס").setView(box)
            .setPositiveButton("שמור",(d,w)->prefs.edit().putBoolean("autoHide",a.isChecked()).putBoolean("gestureDoubleTap",g.isChecked()).putBoolean("longSpeed",sp.isChecked()).putBoolean("speedLock",l.isChecked()).putBoolean("autoPlay",ap.isChecked()).putBoolean("transitionAnim",tr.isChecked()).apply())
            .setNegativeButton("ביטול",null).show();
    }

    int contrastTextColor(int color){
        int a=Color.alpha(color), r=Color.red(color), g=Color.green(color), b=Color.blue(color);
        double lum=(0.299*r+0.587*g+0.114*b)/255.0;
        return (a<150 || lum>0.62) ? Color.BLACK : Color.WHITE;
    }

    void applyAccent(int color){
        prefs.edit().putInt("accentColor",color).apply();
        int fg=contrastTextColor(color);
        int[] ids={R.id.add,R.id.settings,R.id.start};
        for(int id:ids){
            View v=findViewById(id);
            if(v instanceof Button){
                Button b=(Button)v;
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                b.setTextColor(fg);
            }
        }
        TextView title=findViewById(R.id.homeTitle), sub=findViewById(R.id.homeSubtitle), foot=findViewById(R.id.homeFooter);
        if(title!=null) title.setTextColor(fg);
        if(sub!=null) sub.setTextColor(fg);
        if(foot!=null) foot.setTextColor(fg);
        if(seekBar!=null){
            seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }
    void showColorSettings(){
        final int original=prefs.getInt("accentColor",0xFF7C4DFF);
        final int[] colors={0xFF7C4DFF,0xFF3F51B5,0xFF2196F3,0xFF00BCD4,0xFF00BFA5,0xFF4CAF50,0xFFCDDC39,0xFFFFEB3B,0xFFFFC107,0xFFFF9800,0xFFFF5722,0xFFEF5350,0xFFE91E63,0xFF9C27B0,0xFFFFFFFF,0xFFBDBDBD,0xFF607D8B,0xFF212121};
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(18,8,18,8);
        TextView hint=new TextView(this);hint.setText("בחר צבע — התצוגה משתנה מיד");hint.setTextColor(0xFF888896);hint.setGravity(Gravity.CENTER);hint.setPadding(0,0,0,12);box.addView(hint);
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);
        for(int row=0;row<3;row++){
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);
            for(int col=0;col<6;col++){
                int c=colors[row*6+col];
                TextView dot=new TextView(this);
                android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);bg.setColor(c);bg.setStroke(2,0x66000000);
                dot.setBackground(bg);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(48,48);lp.setMargins(7,7,7,7);line.addView(dot,lp);
                final int chosen=c;dot.setOnClickListener(v->{applyAccent(chosen);if(colorDialog!=null)colorDialog.dismiss();});
            }
            grid.addView(line);
        }
        box.addView(grid);
        Button custom=new Button(this);custom.setText("＋ צבע מותאם אישית");custom.setAllCaps(false);custom.setOnClickListener(v->showCustomColorDialog());box.addView(custom);
        TextView alphaHint=new TextView(this);alphaHint.setText("בצבע מותאם אישית אפשר גם שקיפות");alphaHint.setTextColor(0xFF888896);alphaHint.setTextSize(12);alphaHint.setGravity(Gravity.CENTER);box.addView(alphaHint);
        new AlertDialog.Builder(this).setTitle("🎨 צבעי טיק דוס").setView(box)
            .setPositiveButton("שמור וסגור",null)
            .setNegativeButton("ביטול",(d,w)->applyAccent(original)).show();
    }

    void showCustomColorDialog(){
        final int original=prefs.getInt("accentColor",0xFF7C4DFF);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,8,20,8);
        TextView preview=new TextView(this);preview.setText("תצוגה");preview.setGravity(Gravity.CENTER);preview.setTextColor(Color.WHITE);
        box.addView(preview,new LinearLayout.LayoutParams(-1,70));
        SeekBar hue=new SeekBar(this);hue.setMax(360);hue.setProgress(260);box.addView(hue);
        TextView hueText=new TextView(this);hueText.setText("גוון");hueText.setGravity(Gravity.CENTER);box.addView(hueText);
        SeekBar alpha=new SeekBar(this);alpha.setMax(255);alpha.setProgress(255);box.addView(alpha);
        TextView alphaText=new TextView(this);alphaText.setText("אטימות: 100%");alphaText.setGravity(Gravity.CENTER);box.addView(alphaText);
        Runnable refresh=()->{
            float[] hsv={hue.getProgress(),0.72f,1f};int c=Color.HSVToColor(alpha.getProgress(),hsv);
            preview.setBackgroundColor(c);preview.setTextColor(contrastTextColor(c));applyAccent(c);
            alphaText.setText("אטימות: "+Math.round(alpha.getProgress()*100f/255f)+"%");
        };
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){refresh.run();if(f){float[] hsv={hue.getProgress(),0.72f,1f};applyAccent(Color.HSVToColor(alpha.getProgress(),hsv));}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){refresh.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        refresh.run();
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("צבע מותאם אישית").setView(box).setNegativeButton("ביטול",(d,w)->applyAccent(original)).setPositiveButton("סגור",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->dlg.dismiss()));
        dlg.show();
    }

    void showFirstImageSetup(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,8,20,8);
        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setImageResource(R.drawable.logo_tikdos);
        box.addView(preview,new LinearLayout.LayoutParams(-1,230));
        TextView info=new TextView(this);info.setText("בפעם הראשונה אפשר לבחור תמונה שתופיע במסך הפתיחה של טיק דוס.");info.setTextSize(16);info.setGravity(Gravity.CENTER);info.setPadding(0,12,0,8);box.addView(info);
        Button choose=new Button(this);choose.setText("בחר תמונה");choose.setAllCaps(false);box.addView(choose);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("ברוכים הבאים לטיק דוס").setView(box).setNegativeButton("דלג",null).create();
        choose.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),8));
        dlg.setOnDismissListener(d->{if(!prefs.getBoolean("imageConfigured",false)) prefs.edit().putBoolean("imageConfigured",true).apply(); if(!prefs.getBoolean("zoomConfigured",false)) handler.postDelayed(this::showFirstZoomSetup,250);});
        dlg.show();
    }

    boolean isVideo(){ return player!=null && player.isPlaying() || movie.getVisibility()==View.VISIBLE && items.size()>0 && isVideoUri(items.get(pos)); }
    boolean isVideoUri(Uri u){ String m=getContentResolver().getType(u); return m!=null && m.startsWith("video/"); }

    void togglePlayback() {
        if (player==null) return;
        if (player.isPlaying()) player.pause(); else player.start();
        if (player.isPlaying()) { hidePauseIndicator(); } else { showPauseIndicator(); }
    }

    void seekBy(int ms) {
        if (player==null) return;
        int target=Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+ms));
        player.seekTo(target);
        speed.setText("+10");
        speed.setVisibility(View.VISIBLE);
        handler.postDelayed(()->speed.setVisibility(View.GONE),500);
    }

    void setSpeed(float s) {
        if (player==null) return;
        try { player.setPlaybackParams(new PlaybackParams().setSpeed(s)); } catch(Exception ignored){}
        speed.setText(s>1 ? "כפול 2" : "");
        speed.setVisibility(s>1 ? View.VISIBLE : View.GONE);
    }

    void pick() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,7);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==7&&c==RESULT_OK&&d!=null&&d.getData()!=null){
            Uri u=d.getData();
            try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            if(!roots.contains(u)){roots.add(u);saveRoots();}
            status.setText("נבחרו "+roots.size()+" תיקיות. אפשר להוסיף עוד או להתחיל לצפות.");
        }
    }

    void share(){if(items.isEmpty())return;Uri u=items.get(pos);String mime=getContentResolver().getType(u);Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime!=null?mime:"*/*");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"שיתוף"));}

    void showPauseIndicator(){if(pauseIndicator!=null)pauseIndicator.setVisibility(View.VISIBLE);}
    void hidePauseIndicator(){if(pauseIndicator!=null)pauseIndicator.setVisibility(View.GONE);}

    void saveRoots(){StringBuilder b=new StringBuilder();for(Uri u:roots){if(b.length()>0)b.append("\n");b.append(u);}likes.edit().putString("roots",b.toString()).apply();}
    void loadRoots(){String s=likes.getString("roots","");if(!s.isEmpty())for(String x:s.split("\\n"))try{roots.add(Uri.parse(x));}catch(Exception ignored){} if(!roots.isEmpty())status.setText("נבחרו "+roots.size()+" תיקיות. אפשר להתחיל לצפות.");}

    void startScan(){
        if(scanning)return;
        if(roots.isEmpty()){toast("קודם בחר תיקייה אחת לפחות");return;}
        scanning=true; status.setText("סורק את התיקיות ומחפש תמונות וסרטונים…");
        new Thread(()->{
            final LinkedHashSet<String> found=new LinkedHashSet<>();
            for(Uri root:roots)walk(root,found);
            final ArrayList<Uri> result=new ArrayList<>(); for(String v:found)result.add(Uri.parse(v));
            Collections.sort(result,(a,b)->a.toString().compareToIgnoreCase(b.toString()));
            runOnUiThread(()->{
                items.clear();items.addAll(result);scanning=false;
                if(items.isEmpty()){status.setText("לא נמצאה מדיה בתיקיות שנבחרו");toast("לא נמצאה מדיה בתיקיות שנבחרו");return;}
                status.setText("נמצאו "+items.size()+" פריטי מדיה. אפשר להתחיל לצפות.");pos=0;openViewer();
            });
        }).start();
    }

    void walk(Uri tree,Set<String> found){try{walkDoc(tree,DocumentsContract.getTreeDocumentId(tree),new HashSet<String>(),found);}catch(Exception ignored){}}
    void walkDoc(Uri tree,String doc,Set<String> seen,Set<String> found){
        if(!seen.add(doc))return;
        try{
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,doc);
            android.database.Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null);
            if(c==null)return;
            while(c.moveToNext()){
                String id=c.getString(0),mime=c.getString(1);
                if(mime!=null&&(mime.startsWith("image/")||mime.startsWith("video/")))found.add(DocumentsContract.buildDocumentUriUsingTree(tree,id).toString());
                else if("vnd.android.document/directory".equals(mime))walkDoc(tree,id,seen,found);
            } c.close();
        }catch(Exception ignored){}
    }

    void openViewer(){home.setVisibility(View.GONE);viewer.setVisibility(View.VISIBLE);render();}
    void closeViewer(){releasePlayer();viewer.setVisibility(View.GONE);home.setVisibility(View.VISIBLE);}

    void render(){
        if(items.isEmpty())return;
        releasePlayer();
        if(prefs.getBoolean("transitionAnim",true)) viewer.setAlpha(0.92f); else viewer.setAlpha(1f);
        viewer.setTranslationY(18f);
        if(prefs.getBoolean("transitionAnim",true)) viewer.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        Uri u=items.get(pos); boolean vid=isVideoUri(u);
        movie.setVisibility(vid?View.VISIBLE:View.GONE); picture.setVisibility(vid?View.GONE:View.VISIBLE);
        if(vid){
            movie.setTag(u);
            if(movie.isAvailable()) prepareVideo(u,movie.getSurfaceTexture());
            speed.setVisibility(View.GONE);
        } else picture.setImageURI(u);
        count.setText((pos+1)+" / "+items.size());
    }

    void prepareVideo(Uri u,SurfaceTexture st){
        try{
            player=new MediaPlayer();
            player.setDataSource(this,u);
            Surface surface=new android.view.Surface(st);
            player.setSurface(surface);
            player.setLooping(false);
            player.setOnVideoSizeChangedListener((mp,w,h)->fitVideo(w,h));
            player.setOnCompletionListener(mp->{ if(!items.isEmpty()) next(); });
            player.setOnPreparedListener(mp->{fitVideo(mp.getVideoWidth(),mp.getVideoHeight());
                boolean startNow=prefs.getBoolean("autoPlay",true) && !returningFromBackground;
                if(startNow) mp.start(); else {try{mp.pause();}catch(Exception ignored){}}
                returningFromBackground=false;updateTime();startProgressUpdater();if(mp.isPlaying())scheduleControlsHide();});
            player.prepareAsync();
        }catch(Exception e){toast("הסרטון לא ניתן להפעלה");}
    }

    void fitVideo(int vw,int vh){
        if(vw<=0||vh<=0)return;
        int sw=movie.getWidth(),sh=movie.getHeight();
        if(sw<=0||sh<=0)return;
        Matrix m=new Matrix();
        float sx=(float)sw/(float)vw;
        float sy=(float)sh/(float)vh;
        // TextureView transform uses view-space scale: enlarge the texture to exactly fill the screen.
        float zoom=prefs.getInt("videoZoom",96)/100f;
        m.setScale(((float)vw/(float)sw)*zoom,((float)vh/(float)sh)*zoom,sw/2f,sh/2f);
        movie.setTransform(m);
    }

    void updateTime(){
        if(player==null){seekBar.setProgress(0);timeText.setText("00:00 / 00:00");return;}
        int d=Math.max(0,player.getDuration()), p=Math.max(0,player.getCurrentPosition());
        seekBar.setProgress(d>0?(int)((long)p*1000/d):0);
        timeText.setText(formatTime(p)+" / "+formatTime(d));
    }
    String formatTime(int ms){int s=Math.max(0,ms/1000);return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);}
    void startProgressUpdater(){
        if(progressUpdater!=null)handler.removeCallbacks(progressUpdater);
        progressUpdater=()->{updateTime();if(player!=null)handler.postDelayed(progressUpdater,250);};
        handler.post(progressUpdater);
    }

    void releasePlayer(){ speedLocked=false;setControlsVisible(true);if(progressUpdater!=null)handler.removeCallbacks(progressUpdater);progressUpdater=null;if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}if(seekBar!=null)seekBar.setProgress(0);if(timeText!=null)timeText.setText("00:00 / 00:00");}

    void next(){speedLocked=false;if(!items.isEmpty()){pos=(pos+1)%items.size();render();}}
    void prev(){speedLocked=false;if(!items.isEmpty()){pos=(pos-1+items.size())%items.size();render();}}
    void showGuide(){
        ScrollView scroll=new ScrollView(this);
        TextView guide=new TextView(this);
        guide.setTextSize(15);
        guide.setTextColor(0xFF222222);
        guide.setPadding(22,12,22,12);
        guide.setText("📖 מדריך מלא לטיק דוס\n\n"+
            "🏠 מסך הבית\n• הלוגו למעלה הוא הלוגו של האפליקציה.\n• ״הוסף תיקיות״ – בוחרים תיקייה מהמכשיר ואפשר להוסיף כמה.\n• ״פתח את הפיד״ – סורק את התיקיות ומציג תמונות וסרטונים.\n\n"+
            "🎬 צפייה בפיד\n• החלקה למעלה – פריט הבא.\n• החלקה למטה – פריט קודם.\n• לחיצה – הפעלה או עצירה.\n• דאבל־טאפ במרכז – הפעלה/עצירה; בצדדים – קפיצה של 10 שניות.\n• לחיצה ארוכה – מהירות כפול 2.\n• בסיום סרטון עוברים לפריט הבא.\n• כפתור החזרה בפינה העליונה והשיתוף בפינה שממול.\n\n"+
            "⚙️ הגדרות\n• הסתרת כפתורים – מסתירה את הפקדים אחרי 2 שניות.\n• דאבל־טאפ – מפעיל או מבטל את פעולות הדאבל־טאפ.\n• לחיצה ארוכה – מפעילה או מבטלת כפול 2.\n• נעילת כפול 2 – נועלת את המהירות בזמן לחיצה ארוכה.\n• הפעלה אוטומטית – קובעת אם סרטון חדש יתחיל מיד.\n• אנימציית מעבר – מעבר חלק בין פריטים.\n• גודל סרטון – שינוי זום עם תצוגה מקדימה חיה.\n\n"+
            "🎨 צבעי האפליקציה\n• צבעים מהעיגולים משתנים מיד.\n• אפשר צבע מותאם אישית עם גוון ואטימות.\n• בצבעים בהירים, כולל לבן, הכיתוב הופך לשחור.\n• ״שמור וסגור״ שומר; ״ביטול״ מחזיר את הצבע הקודם.\n\n"+
            "⏸️ יציאה מהאפליקציה\n• ביציאה הסרטון נעצר.\n• בחזרה לאפליקציה הוא לא ממשיך אוטומטית.\n\n"+
            "📤 שיתוף\n• כפתור השיתוף משתף את הפריט שמוצג כרגע.\n\n"+
            "ℹ️ אודות\nטיק דוס • YB Apps\n\nגרסה 01");
        scroll.addView(guide);
        new AlertDialog.Builder(this).setTitle("📖 מדריך טיק דוס • גרסה 01").setView(scroll).setPositiveButton("סגור",null).show();
    }

    void showAbout(){new AlertDialog.Builder(this).setTitle("אודות • גרסה 01").setMessage("טיק דוס\n\nYB Apps\n\nגרסה 01").setPositiveButton("סגור",null).show();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){if(movie.getTag()!=null)prepareVideo((Uri)movie.getTag(),st);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){if(player!=null)fitVideo(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
    @Override protected void onPause(){
        super.onPause();speedLocked=false;returningFromBackground=true;
        if(player!=null){try{player.pause();}catch(Exception ignored){}}
        hidePauseIndicator();
    }
    @Override protected void onResume(){
        super.onResume();speedLocked=false;
        // Always return paused. There is intentionally no auto-resume option.
        if(player!=null && player.isPlaying()){try{player.pause();}catch(Exception ignored){}}
        hidePauseIndicator();
    }
}