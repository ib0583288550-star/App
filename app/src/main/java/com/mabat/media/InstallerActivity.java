package com.mabat.media;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

public class InstallerActivity extends Activity {
    ImageView preview;
    TextView status;
    Button choose, build;
    SharedPreferences prefs;
    Uri selected;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs=getSharedPreferences("settings",0);
        buildUi();
        String saved=prefs.getString("homeImageUri",null);
        if(saved!=null){try{selected=Uri.parse(saved); preview.setImageURI(selected); status.setText("התמונה מוכנה"); build.setEnabled(true);}catch(Exception ignored){}}
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
        info.setText("בחר תמונה אחת. האינסטלר ישמור אותה, יכין את טיק דוס עם התמונה, ואז יפתח את האפליקציה המקורית.");
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
        build.setText("צור ופתח את טיק דוס");
        build.setAllCaps(false);
        build.setEnabled(false);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,56); bp.topMargin=10;
        root.addView(build,bp);

        TextView foot=new TextView(this);
        foot.setText("YB Apps");
        foot.setTextColor(0xFF777784); foot.setGravity(Gravity.CENTER); foot.setPadding(0,20,0,0);
        root.addView(foot,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
        choose.setOnClickListener(v->pickImage());
        build.setOnClickListener(v->createAndOpen());
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
            prefs.edit().putString("homeImageUri",selected.toString()).putBoolean("imageConfigured",true).apply();
            preview.setImageURI(selected);
            status.setText("התמונה מוכנה ✓");
            build.setEnabled(true);
        }
    }

    void createAndOpen(){
        if(selected==null){Toast.makeText(this,"בחר תמונה קודם",Toast.LENGTH_SHORT).show();return;}
        status.setText("מכין את טיק דוס…");
        build.setEnabled(false);
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            Intent i=new Intent(this,MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        },450);
    }
}