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
    boolean scanning = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        setContentView(R.layout.activity_main);

        home=findViewById(R.id.home); viewer=findViewById(R.id.viewer);
        picture=findViewById(R.id.picture); movie=findViewById(R.id.movie);
        like=findViewById(R.id.like); share=findViewById(R.id.share); save=findViewById(R.id.save);
        count=findViewById(R.id.count); status=findViewById(R.id.status); speed=findViewById(R.id.speed);
        seekBar=findViewById(R.id.seekBar); timeText=findViewById(R.id.timeText);
        likes=getSharedPreferences("likes",0);
        movie.setSurfaceTextureListener(this);

        loadRoots();
        findViewById(R.id.add).setOnClickListener(v->pick());
        findViewById(R.id.start).setOnClickListener(v->startScan());
        findViewById(R.id.back).setOnClickListener(v->closeViewer());
        like.setOnClickListener(v->toggleLike());
        share.setOnClickListener(v->share());
        save.setOnClickListener(v->save());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                if(fromUser && player!=null && player.getDuration()>0){
                    player.seekTo((int)((long)p*player.getDuration()/1000L));
                }
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
        if (e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=e.getX(); downY=e.getY(); downTime=System.currentTimeMillis(); moved=false; longPressing=false;
            if (isVideo()) {
                handler.postDelayed(()->{
                    if (!moved && isVideo()) { longPressing=true; setSpeed(2f); }
                }, 320);
            }
            return true;
        }
        if (e.getAction()==MotionEvent.ACTION_MOVE) {
            if (Math.abs(e.getX()-downX)>35 || Math.abs(e.getY()-downY)>35) moved=true;
            return true;
        }
        if (e.getAction()==MotionEvent.ACTION_UP) {
            handler.removeCallbacksAndMessages(null);
            if (longPressing) { setSpeed(1f); longPressing=false; return true; }
            if (moved) {
                float d=e.getY()-downY;
                if (Math.abs(d)>90) { if (d<0) next(); else prev(); }
                return true;
            }
            long duration=System.currentTimeMillis()-downTime;
            if (duration<300) {
                if (doubleTapPending) {
                    doubleTapPending=false;
                    seekBy(10_000);
                } else {
                    doubleTapPending=true;
                    handler.postDelayed(()->{
                        if (doubleTapPending) { doubleTapPending=false; togglePlayback(); }
                    }, 240);
                }
            }
            return true;
        }
        return true;
    }

    boolean isVideo(){ return player!=null && player.isPlaying() || movie.getVisibility()==View.VISIBLE && items.size()>0 && isVideoUri(items.get(pos)); }
    boolean isVideoUri(Uri u){ String m=getContentResolver().getType(u); return m!=null && m.startsWith("video/"); }

    void togglePlayback() {
        if (player==null) return;
        if (player.isPlaying()) player.pause(); else player.start();
        speed.setText(player.isPlaying() ? "" : "⏸");
        speed.setVisibility(View.VISIBLE);
        if (player.isPlaying()) handler.postDelayed(()->speed.setVisibility(View.GONE),450);
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
        Uri u=items.get(pos); boolean vid=isVideoUri(u);
        movie.setVisibility(vid?View.VISIBLE:View.GONE); picture.setVisibility(vid?View.GONE:View.VISIBLE);
        if(vid){
            movie.setTag(u);
            if(movie.isAvailable()) prepareVideo(u,movie.getSurfaceTexture());
            speed.setVisibility(View.GONE);
        } else picture.setImageURI(u);
        like.setText(""); like.setAlpha(likes.getBoolean(u.toString(),false)?1f:0.65f);
        count.setText((pos+1)+" / "+items.size());
    }

    void prepareVideo(Uri u,SurfaceTexture st){
        try{
            player=new MediaPlayer();
            player.setDataSource(this,u);
            player.setSurface(new android.view.Surface(st));
            player.setLooping(true);
            player.setOnVideoSizeChangedListener((mp,w,h)->fitVideo(w,h));
            player.setOnPreparedListener(mp->{fitVideo(mp.getVideoWidth(),mp.getVideoHeight());mp.start();updateTime();startProgressUpdater();});
            player.prepareAsync();
        }catch(Exception e){toast("הסרטון לא ניתן להפעלה");}
    }

    void fitVideo(int vw,int vh){
        if(vw<=0||vh<=0)return;
        int sw=movie.getWidth(),sh=movie.getHeight();
        if(sw<=0||sh<=0)return;

        // Fill the entire screen with the complete video frame.
        // No crop and no black bars: the source is stretched independently
        // in width and height to exactly match the TextureView.
        Matrix m=new Matrix();
        m.setScale((float)sw/(float)vw,(float)sh/(float)vh);
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

    void releasePlayer(){if(progressUpdater!=null)handler.removeCallbacks(progressUpdater);progressUpdater=null;if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}if(seekBar!=null)seekBar.setProgress(0);if(timeText!=null)timeText.setText("00:00 / 00:00");}

    void next(){if(!items.isEmpty()){pos=(pos+1)%items.size();render();}}
    void prev(){if(!items.isEmpty()){pos=(pos-1+items.size())%items.size();render();}}
    void toggleLike(){if(items.isEmpty())return;Uri u=items.get(pos);boolean n=!likes.getBoolean(u.toString(),false);likes.edit().putBoolean(u.toString(),n).apply();like.setText(""); like.setAlpha(n?1f:0.65f);}
    void share(){if(items.isEmpty())return;Uri u=items.get(pos);String mime=getContentResolver().getType(u);Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime!=null?mime:"*/*");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"שיתוף"));}
    void save(){
        if(items.isEmpty())return;Uri src=items.get(pos);String mime=getContentResolver().getType(src);if(mime==null){toast("סוג הקובץ לא זוהה");return;}
        boolean vid=mime.startsWith("video/");String extension=vid?".mp4":".jpg";String name="מבט_"+System.currentTimeMillis()+extension;
        ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,vid?Environment.DIRECTORY_MOVIES+"/מבט":Environment.DIRECTORY_PICTURES+"/מבט");
        try{Uri out=getContentResolver().insert(vid?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(out==null)throw new IOException();
            InputStream in=getContentResolver().openInputStream(src);OutputStream os=getContentResolver().openOutputStream(out);if(in==null||os==null)throw new IOException();
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)os.write(buffer,0,n);in.close();os.close();toast("נשמר בגלריה");
        }catch(Exception e){toast("שמירה נכשלה");}
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){if(movie.getTag()!=null)prepareVideo((Uri)movie.getTag(),st);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){if(player!=null)fitVideo(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
    @Override protected void onPause(){super.onPause();if(player!=null)player.pause();}
    @Override protected void onResume(){super.onResume();if(player!=null)player.start();}
}