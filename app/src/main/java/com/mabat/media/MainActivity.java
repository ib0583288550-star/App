    boolean handleTouch(MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=e.getX(); downY=e.getY(); downTime=System.currentTimeMillis(); moved=false; longPressing=false; longPressDown=false;
            if (isVideo() && prefs.getBoolean("longSpeed",true)) handler.postDelayed(()->{
                if (!moved && isVideo()) {
                    longPressing=true; longPressDown=true; setSpeed(2f);
                    speedLocked=false;
                }
            }, 320);
            return true;
        }
        if (e.getAction()==MotionEvent.ACTION_MOVE) {
            if (Math.abs(e.getX()-downX)>35 || Math.abs(e.getY()-downY)>35) moved=true;
            if (longPressing && prefs.getBoolean("speedLock",true) && e.getY()-downY>90) {
                speedLocked=true;
                speed.setText("כפול 2 • נעול");
                speed.setVisibility(View.VISIBLE);
            }
            return true;
        }
        if (e.getAction()==MotionEvent.ACTION_UP) {
            handler.removeCallbacksAndMessages(null);
            if (longPressing) {
                if (!speedLocked) setSpeed(1f);
                longPressing=false; longPressDown=false;
                scheduleControlsHide();
                return true;
            }
            if (moved) {
                float d=e.getY()-downY;
                if (Math.abs(d)>90) { if (d<0) next(); else prev(); }
                return true;
            }
            long duration=System.currentTimeMillis()-downTime;
            if (duration<300) {
                if (doubleTapPending) {
                    doubleTapPending=false;
                    float x=e.getX();
                    if (prefs.getBoolean("gestureDoubleTap",true)) {
                        float third=viewer.getWidth()/3f;
                        if (x<third) seekBy(-10_000);
                        else if (x>third*2f) seekBy(10_000);
                        else toggleLike();
                    } else togglePlayback();
                } else {
                    doubleTapPending=true;
                    handler.postDelayed(()->{
                        if (doubleTapPending) {
                            doubleTapPending=false;
                            togglePlayback();
                        }
                    }, 240);
                }
            }
            return true;
        }
        return true;
    }    boolean handleTouch(MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=e.getX(); downY=e.getY(); downTime=System.currentTimeMillis(); moved=false; longPressing=false;
            if (isVideo()) handler.postDelayed(()->{ if (!moved && isVideo()) { longPressing=true; setSpeed(2f); } }, 320);
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
                if (doubleTapPending) { doubleTapPending=false; seekBy(10_000); }
                else {
                    doubleTapPending=true;
                    handler.postDelayed(()->{ if (doubleTapPending) { doubleTapPending=false; togglePlayback(); } }, 240);
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
        viewer.setAlpha(0.92f);
        viewer.setTranslationY(18f);
        viewer.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new AccelerateDecelerateInterpolator()).start();
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
            Surface surface=new android.view.Surface(st);
            player.setSurface(surface);
            player.setLooping(true);
            player.setOnVideoSizeChangedListener((mp,w,h)->fitVideo(w,h));
            player.setOnPreparedListener(mp->{fitVideo(mp.getVideoWidth(),mp.getVideoHeight());mp.start();updateTime();startProgressUpdater();scheduleControlsHide();});
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
        m.setScale(sx,sy,sw/2f,sh/2f);
        movie.setTransform(m);
    }

    void scheduleControlsHide(){
        if(!prefs.getBoolean("autoFullscreen",true)) return;
        controlsHidden=false;
        setControlsVisible(true);
        handler.postDelayed(()->{
            if(player!=null && player.isPlaying()){
                controlsHidden=true;
                setControlsVisible(false);
            }
        },2000);
    }

    void setControlsVisible(boolean visible){
        int v=visible?View.VISIBLE:View.GONE;
        count.setVisibility(v);
        findViewById(R.id.back).setVisibility(v);
        findViewById(R.id.actionColumn).setVisibility(v);
        findViewById(R.id.bottomInfo).setVisibility(v);
        seekBar.setVisibility(v);
        timeText.setVisibility(v);
        speed.setVisibility(speedLocked?View.VISIBLE:(visible?speed.getVisibility():View.GONE));
    }

    void showSettings(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28,8,28,8);
        CheckBox auto=new CheckBox(this); auto.setText("הסתרת כפתורים אחרי 2 שניות"); auto.setChecked(prefs.getBoolean("autoFullscreen",true)); box.addView(auto);
        CheckBox gestures=new CheckBox(this); gestures.setText("מחוות: שמאל −10 | אמצע לייק | ימין +10"); gestures.setChecked(prefs.getBoolean("gestureDoubleTap",true)); box.addView(gestures);
        CheckBox longSpeed=new CheckBox(this); longSpeed.setText("לחיצה ארוכה = כפול 2"); longSpeed.setChecked(prefs.getBoolean("longSpeed",true)); box.addView(longSpeed);
        CheckBox lock=new CheckBox(this); lock.setText("החלקה למטה בזמן כפול 2 = נעילת מהירות"); lock.setChecked(prefs.getBoolean("speedLock",true)); box.addView(lock);
        new AlertDialog.Builder(this).setTitle("הגדרות").setView(box).setPositiveButton("שמור",(d,w)->{
            prefs.edit().putBoolean("autoFullscreen",auto.isChecked()).putBoolean("gestureDoubleTap",gestures.isChecked()).putBoolean("longSpeed",longSpeed.isChecked()).putBoolean("speedLock",lock.isChecked()).apply();
        }).setNegativeButton("ביטול",null).show();
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

    void releasePlayer(){speedLocked=false;if(progressUpdater!=null)handler.removeCallbacks(progressUpdater);progressUpdater=null;if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}if(seekBar!=null)seekBar.setProgress(0);if(timeText!=null)timeText.setText("00:00 / 00:00");}

    void next(){if(!items.isEmpty()){speedLocked=false;pos=(pos+1)%items.size();render();}}
    void prev(){if(!items.isEmpty()){speedLocked=false;pos=(pos-1+items.size())%items.size();render();}}
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
    void showAbout(){new AlertDialog.Builder(this).setTitle("אודות").setMessage("מבט\n\nYB Apps").setPositiveButton("סגור",null).show();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){if(movie.getTag()!=null)prepareVideo((Uri)movie.getTag(),st);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){if(player!=null)fitVideo(player.getVideoWidth(),player.getVideoHeight());}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){releasePlayer();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
    @Override protected void onPause(){super.onPause();if(player!=null)player.pause();}
    @Override protected void onResume(){super.onResume();if(player!=null)player.start();}
}