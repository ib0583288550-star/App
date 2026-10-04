package com.mabat.media;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout home;
    FrameLayout viewer;
    ImageView picture;
    VideoView movie;
    TextView like, share, save, count, status;
    ArrayList<Uri> roots = new ArrayList<>(), items = new ArrayList<>();
    int pos = 0;
    float downY;
    SharedPreferences likes;
    boolean scanning = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        home = findViewById(R.id.home);
        viewer = findViewById(R.id.viewer);
        picture = findViewById(R.id.picture);
        movie = findViewById(R.id.movie);
        like = findViewById(R.id.like);
        share = findViewById(R.id.share);
        save = findViewById(R.id.save);
        count = findViewById(R.id.count);
        status = findViewById(R.id.status);
        likes = getSharedPreferences("likes", 0);

        loadRoots();

        findViewById(R.id.add).setOnClickListener(v -> pick());
        findViewById(R.id.start).setOnClickListener(v -> startScan());
        findViewById(R.id.back).setOnClickListener(v -> closeViewer());
        like.setOnClickListener(v -> toggleLike());
        share.setOnClickListener(v -> share());
        save.setOnClickListener(v -> save());

        viewer.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downY = e.getY();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float d = e.getY() - downY;
                if (Math.abs(d) > 90) {
                    if (d < 0) next();
                    else prev();
                }
                return true;
            }
            return true;
        });
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, 7);
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == 7 && c == RESULT_OK && d != null && d.getData() != null) {
            Uri u = d.getData();
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            if (!roots.contains(u)) {
                roots.add(u);
                saveRoots();
            }
            status.setText("נבחרו " + roots.size() + " תיקיות. אפשר להוסיף עוד או להתחיל לצפות.");
        }
    }

    void saveRoots() {
        StringBuilder b = new StringBuilder();
        for (Uri u : roots) {
            if (b.length() > 0) b.append("\n");
            b.append(u);
        }
        likes.edit().putString("roots", b.toString()).apply();
    }

    void loadRoots() {
        String s = likes.getString("roots", "");
        if (!s.isEmpty()) {
            for (String x : s.split("\\n")) {
                try { roots.add(Uri.parse(x)); } catch (Exception ignored) {}
            }
        }
        if (!roots.isEmpty()) {
            status.setText("נבחרו " + roots.size() + " תיקיות. אפשר להתחיל לצפות.");
        }
    }

    void startScan() {
        if (scanning) return;
        if (roots.isEmpty()) {
            toast("קודם בחר תיקייה אחת לפחות");
            return;
        }

        scanning = true;
        status.setText("סורק את התיקיות ומחפש תמונות וסרטונים…");

        new Thread(() -> {
            final LinkedHashSet<String> found = new LinkedHashSet<>();
            for (Uri root : roots) {
                walk(root, found);
            }

            final ArrayList<Uri> result = new ArrayList<>();
            for (String value : found) result.add(Uri.parse(value));

            Collections.sort(result, (a, b) -> a.toString().compareToIgnoreCase(b.toString()));

            runOnUiThread(() -> {
                items.clear();
                items.addAll(result);
                scanning = false;

                if (items.isEmpty()) {
                    status.setText("לא נמצאה מדיה בתיקיות שנבחרו");
                    toast("לא נמצאה מדיה בתיקיות שנבחרו");
                    return;
                }

                status.setText("נמצאו " + items.size() + " פריטי מדיה. אפשר להתחיל לצפות.");
                pos = 0;
                openViewer();
            });
        }).start();
    }

    void walk(Uri tree, Set<String> found) {
        try {
            walkDoc(tree, DocumentsContract.getTreeDocumentId(tree), new HashSet<String>(), found);
        } catch (Exception ignored) {}
    }

    void walkDoc(Uri tree, String doc, Set<String> seen, Set<String> found) {
        if (!seen.add(doc)) return;

        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc);
            android.database.Cursor c = getContentResolver().query(
                    children,
                    new String[] {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    null, null, null
            );

            if (c == null) return;

            while (c.moveToNext()) {
                String id = c.getString(0);
                String mime = c.getString(1);

                if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) {
                    found.add(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                } else if ("vnd.android.document/directory".equals(mime)) {
                    walkDoc(tree, id, seen, found);
                }
            }
            c.close();
        } catch (Exception ignored) {}
    }

    void openViewer() {
        home.setVisibility(View.GONE);
        viewer.setVisibility(View.VISIBLE);
        render();
    }

    void closeViewer() {
        try { movie.stopPlayback(); } catch (Exception ignored) {}
        viewer.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
    }

    void render() {
        if (items.isEmpty()) return;

        Uri u = items.get(pos);
        String mime = getContentResolver().getType(u);
        boolean vid = mime != null && mime.startsWith("video/");

        movie.setVisibility(vid ? View.VISIBLE : View.GONE);
        picture.setVisibility(vid ? View.GONE : View.VISIBLE);

        if (vid) {
            movie.setVideoURI(u);
            movie.setOnPreparedListener(m -> {
                m.setLooping(true);
                movie.start();
            });
        } else {
            picture.setImageURI(u);
        }

        like.setText(likes.getBoolean(u.toString(), false) ? "♥" : "♡");
        count.setText((pos + 1) + " / " + items.size());
    }

    void next() {
        if (!items.isEmpty()) {
            pos = (pos + 1) % items.size();
            render();
        }
    }

    void prev() {
        if (!items.isEmpty()) {
            pos = (pos - 1 + items.size()) % items.size();
            render();
        }
    }

    void toggleLike() {
        if (items.isEmpty()) return;
        Uri u = items.get(pos);
        boolean n = !likes.getBoolean(u.toString(), false);
        likes.edit().putBoolean(u.toString(), n).apply();
        like.setText(n ? "♥" : "♡");
    }

    void share() {
        if (items.isEmpty()) return;
        Uri u = items.get(pos);
        String mime = getContentResolver().getType(u);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime != null ? mime : "*/*");
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "שיתוף"));
    }

    void save() {
        if (items.isEmpty()) return;

        Uri src = items.get(pos);
        String mime = getContentResolver().getType(src);
        if (mime == null) {
            toast("סוג הקובץ לא זוהה");
            return;
        }

        boolean vid = mime.startsWith("video/");
        String extension = vid ? ".mp4" : ".jpg";
        String name = "מבט_" + System.currentTimeMillis() + extension;

        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                vid ? Environment.DIRECTORY_MOVIES + "/מבט"
                    : Environment.DIRECTORY_PICTURES + "/מבט");

        try {
            Uri out = getContentResolver().insert(
                    vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);

            if (out == null) throw new IOException("MediaStore insert failed");

            InputStream in = getContentResolver().openInputStream(src);
            OutputStream os = getContentResolver().openOutputStream(out);

            if (in == null || os == null) throw new IOException("Stream failed");

            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) os.write(buffer, 0, n);

            in.close();
            os.close();
            toast("נשמר בגלריה");
        } catch (Exception e) {
            toast("שמירה נכשלה");
        }
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
