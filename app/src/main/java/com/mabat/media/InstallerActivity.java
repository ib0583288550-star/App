package com.mabat.media;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;

public class InstallerActivity extends Activity {
    ImageView preview;
    TextView status;
    Button choose, build, install;
    Uri selected;
    File generatedApk;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
    }

    void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28,32,28,28);
        root.setBackgroundColor(Color.rgb(12,10,20));

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.logo_tikdos);
        root.addView(logo,new LinearLayout.LayoutParams(110,110));

        TextView title=new TextView(this);
        title.setText("טיק דוס • אינסטלר");
        title.setTextColor(Color.WHITE); title.setTextSize(28); title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        TextView info=new TextView(this);
        info.setText("בחר תמונה אחת. האינסטלר ייצור APK חדש של טיק דוס עם התמונה בפנים ועם התמונה כאייקון.");
        info.setTextColor(0xFFBDB8C8); info.setTextSize(16); info.setGravity(Gravity.CENTER);
        info.setPadding(8,10,8,18);
        root.addView(info,new LinearLayout.LayoutParams(-1,-2));

        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setImageResource(R.drawable.logo_tikdos);
        root.addView(preview,new LinearLayout.LayoutParams(-1,230));

        status=new TextView(this);
        status.setText("עדיין לא נבחרה תמונה");
        status.setTextColor(0xFFBDB8C8); status.setTextSize(14); status.setGravity(Gravity.CENTER);
        status.setPadding(0,12,0,8);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        choose=new Button(this);
        choose.setText("בחר תמונה");
        choose.setAllCaps(false);
        root.addView(choose,new LinearLayout.LayoutParams(-1,54));

        build=new Button(this);
        build.setText("צור APK של טיק דוס");
        build.setAllCaps(false);
        build.setEnabled(false);
        install=new Button(this);
        install.setText("התקן את טיק דוס");
        install.setAllCaps(false);
        install.setEnabled(false);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,56); ip.topMargin=10;
        root.addView(install,ip);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,56); bp.topMargin=10;
        root.addView(build,bp);

        TextView foot=new TextView(this);
        foot.setText("YB Apps");
        foot.setTextColor(0xFF777784); foot.setGravity(Gravity.CENTER); foot.setPadding(0,20,0,0);
        root.addView(foot,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
        choose.setOnClickListener(v->pickImage());
        build.setOnClickListener(v->createApk());
        install.setOnClickListener(v->{ if(generatedApk!=null && generatedApk.exists()) installApk(generatedApk); else Toast.makeText(this,"קודם צור את ה־APK",Toast.LENGTH_SHORT).show(); });
    }

    void pickImage(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,101);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==101 && c==RESULT_OK && d!=null && d.getData()!=null){
            selected=d.getData();
            try{getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            preview.setImageURI(selected);
            status.setText("התמונה מוכנה ✓");
            build.setEnabled(true);
        }
    }

    void createApk(){
        if(selected==null){Toast.makeText(this,"בחר תמונה קודם",Toast.LENGTH_SHORT).show();return;}
        status.setText("יוצר APK חדש של טיק דוס…");
        build.setEnabled(false);
        new Thread(()->{
            try{
                File out=new File(getFilesDir(),"generated/tik-dos.apk");
                File dir=out.getParentFile(); if(!dir.exists()) dir.mkdirs();
                try(InputStream in=getContentResolver().openInputStream(selected)){
                    if(in==null) throw new IOException("לא ניתן לפתוח את התמונה");
                    ApkGenerator.create(this,in,out);
                }
                runOnUiThread(()->{
                    generatedApk=out;
                    status.setText("טיק דוס החדש מוכן ✓");
                    build.setEnabled(true);
                    install.setEnabled(true);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("יצירת ה־APK נכשלה");
                    build.setEnabled(true);
                    install.setEnabled(generatedApk!=null && generatedApk.exists());
                    new AlertDialog.Builder(this).setTitle("לא הצלחתי ליצור APK").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("סגור",null).show();
                });
            }
        }).start();
    }

    void installApk(File apk){
        try{
            Uri uri=FileProvider.getUriForFile(this,"com.mabat.installer.fileprovider",apk);
            Intent i=new Intent(Intent.ACTION_INSTALL_PACKAGE);
            i.setData(uri);
            i.setType("application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("ה־APK נוצר").setMessage("הקובץ מוכן. לחץ על כפתור \\"התקן את טיק דוס\\" כדי לפתוח את ההתקנה.").setPositiveButton("סגור",null).show();
        }
    }
}