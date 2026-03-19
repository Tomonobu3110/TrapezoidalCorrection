package com.example.trapezoidalcorrection;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK_IMAGE = 1001;
    private static final int REQ_PERM = 2001;
    ImageView imageView;
    CornerOverlayView overlay;
    Bitmap sourceBitmap;
    Spinner aspectSpinner;
    Button btnPick, btnCrop;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // controls にだけ bottom padding を追加
            View controls = findViewById(R.id.controls);
            controls.setPadding(
                    controls.getPaddingLeft(),
                    controls.getPaddingTop(),
                    controls.getPaddingRight(),
                    systemBars.bottom
            );
            return insets;
        });

        imageView = findViewById(R.id.imageView);
        overlay = findViewById(R.id.overlay);
        aspectSpinner = findViewById(R.id.aspect_spinner);
        btnPick = findViewById(R.id.btn_pick);
        btnCrop = findViewById(R.id.btn_crop);
        String[] aspects = new String[]{"元の比率", "16:9", "4:3", "1:1", "3:4", "9:16"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, aspects);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        aspectSpinner.setAdapter(adapter);
        btnPick.setOnClickListener(v -> pickImage());
        btnCrop.setOnClickListener(v -> cropAndSave());
// handle SEND intent
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) loadUriBitmap(uri);
        }
// request permissions for older OS
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
            }
        }
    }
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "画像を選択"), REQ_PICK_IMAGE);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) loadUriBitmap(uri);
        }
    }

    /*
    private void loadUriBitmap(Uri uri) {
        try {
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            sourceBitmap = bmp;
            imageView.setImageBitmap(bmp);
            // reset image matrix to fit center
            fitImageToView();
            overlay.setImageView(imageView);
            overlay.setBitmap(sourceBitmap);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "画像読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
    */
    private void loadUriBitmap(Uri uri) {
        if (uri == null) return;
        try {
            // --- 1) InputStream を使ってまずは回転情報を取得（Content URI 対応）
            InputStream isForExif = getContentResolver().openInputStream(uri);
            int orientation = ExifInterface.ORIENTATION_UNDEFINED;
            if (isForExif != null) {
                ExifInterface exif = new ExifInterface(isForExif);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                isForExif.close();
            }

            // --- 2) ビットマップを適切に読み込む（メモリ対策で縮小読み込みを入れることを推奨）
            // ここではシンプルに完全読み込みします。必要なら Options.inSampleSize を設定して縮小してください。
            InputStream isForBitmap = getContentResolver().openInputStream(uri);
            Bitmap bmp = null;
            if (isForBitmap != null) {
                // 大きな画像で OOM が起きる場合は BitmapFactory.Options を使って縮小読み込みしてください
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bmp = BitmapFactory.decodeStream(isForBitmap, null, opts);
                isForBitmap.close();
            }

            if (bmp == null) {
                Toast.makeText(this, "画像読み込みに失敗しました", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- 3) EXIF 回転情報に従って回転・反転を適用
            Bitmap fixed = rotateBitmapIfRequired(bmp, orientation);

            // 保存して使う bitmap を置き換え（既存の変数 sourceBitmap に格納）
            sourceBitmap = fixed;
            imageView.setImageBitmap(fixed);

            // reset image matrix to fit center (既存の処理)
            fitImageToView();

            // overlay にビットマップをセット（拡大鏡や四隅初期化用）
            overlay.setImageView(imageView);
            overlay.setBitmap(sourceBitmap);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "画像読み込みに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap rotateBitmapIfRequired(Bitmap img, int exifOrientation) {
        int rotationDegrees = 0;
        boolean flipHorizontal = false;

        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotationDegrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotationDegrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotationDegrees = 270;
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                flipHorizontal = true;
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE: // flip + rotate 270
                flipHorizontal = true;
                rotationDegrees = 270;
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                flipHorizontal = true;
                rotationDegrees = 180;
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE: // flip + rotate 90
                flipHorizontal = true;
                rotationDegrees = 90;
                break;
            default:
                // ORIENTATION_NORMAL or undefined -> no-op
                break;
        }

        if (rotationDegrees == 0 && !flipHorizontal) {
            return img; // そのままで OK
        }

        return rotateBitmap(img, rotationDegrees, flipHorizontal);
    }

    private Bitmap rotateBitmap(Bitmap source, float degrees, boolean flipHorizontal) {
        Matrix matrix = new Matrix();
        if (degrees != 0f) matrix.postRotate(degrees);
        if (flipHorizontal) matrix.postScale(-1f, 1f);

        try {
            Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            // 元の bitmap と同じインスタンスを上書きしないよう注意（必要なら source.recycle() を検討）
            return rotated;
        } catch (OutOfMemoryError oom) {
            oom.printStackTrace();
            // OOM の場合は元画像をそのまま返す（最悪だがクラッシュ回避）
            return source;
        }
    }

    private void fitImageToView() {
        if (sourceBitmap == null) return;
        // center-crop fit within ImageView bounds while keeping aspect
        int vw = imageView.getWidth();
        int vh = imageView.getHeight();
        if (vw == 0 || vh == 0) {
            // view not laid out yet. post to run later
            imageView.post(this::fitImageToView);
            return;
        }
        float bw = sourceBitmap.getWidth();
        float bh = sourceBitmap.getHeight();
        float scale = Math.min((float) vw / bw, (float) vh / bh);
        float dx = (vw - bw * scale) * 0.5f;
        float dy = (vh - bh * scale) * 0.5f;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate(dx, dy);
        imageView.setImageMatrix(m);
    }
    private void cropAndSave() {
        if (sourceBitmap == null) {
            Toast.makeText(this, "画像を選んでください", Toast.LENGTH_SHORT).show();
            return;
        }
        // get four corners in view coords from overlay, convert to bitmap coords
        float[] src = overlay.getCornersViewCoords(); // [x0,y0,x1,y1,...]
        if (src == null) return;
// convert view coords to bitmap coords
        Matrix imageMatrix = imageView.getImageMatrix();
        Matrix inv = new Matrix();
        imageMatrix.invert(inv);
        float[] bmpPts = src.clone();
        inv.mapPoints(bmpPts);
// destination size based on chosen aspect
        int dstW = 1000; // base width
        int dstH = 1400; // base height
        String sel = (String) aspectSpinner.getSelectedItem();
        if (sel.equals("元の比率")) {
            // compute bounding box of src in bitmap coords
            RectF bb = new RectF(bmpPts[0], bmpPts[1], bmpPts[0], bmpPts[1]);
            for (int i = 1; i < 4; i++) {
                float x = bmpPts[i*2];
                float y = bmpPts[i*2+1];
                if (x < bb.left) bb.left = x;
                if (x > bb.right) bb.right = x;
                if (y < bb.top) bb.top = y;
                if (y > bb.bottom) bb.bottom = y;
            }
            float bboxW = bb.width();
            float bboxH = bb.height();
            if (bboxW <= 0 || bboxH <= 0) { Toast.makeText(this, "無効な領域です", Toast.LENGTH_SHORT).show(); return; }

            if (sourceBitmap == null) {
                // 念のため：sourceBitmap が無ければ元比率が取れない
                Toast.makeText(this, "画像が読み込まれていません", Toast.LENGTH_SHORT).show();
                return;
            }

            // 元画像のアスペクト比を使う（ユーザーの要求）
            float srcAspect = (float) sourceBitmap.getWidth() / (float) sourceBitmap.getHeight();

            // bbox に収まるように、元比率を満たす出力幅/高さを決める
            // まず bbox の幅を基準に高さを計算し、もし bbox の高さに収まらなければ bbox の高さを基準に幅を計算する
            float targetW = bboxW;
            float targetH = targetW / srcAspect;
            if (targetH > bboxH) {
                targetH = bboxH;
                targetW = targetH * srcAspect;
            }

            // 安全のため最小 1px、かつ実機メモリ対策で最大辺を制限する
            int maxSide = 2000; // 必要に応じて調整
            // スケールして maxSide に収める
            if (Math.max(targetW, targetH) > maxSide) {
                float scale = maxSide / Math.max(targetW, targetH);
                targetW *= scale;
                targetH *= scale;
            }

            dstW = Math.max(1, Math.round(targetW));
            dstH = Math.max(1, Math.round(targetH));
        } else {
            switch (sel) {
                case "16:9": dstW = 1600; dstH = 900; break;
                case "4:3": dstW = 1200; dstH = 900; break;
                case "1:1": dstW = 1000; dstH = 1000; break;
                case "3:4": dstW = 900; dstH = 1200; break;
                case "9:16": dstW = 900; dstH = 1600; break;
            }
        }
// prepare source points (bitmap coords) -> destination rect
        float[] srcPts = new float[8];
        for (int i = 0; i < 8; i++) srcPts[i] = bmpPts[i];
        float[] dstPts = new float[]{0f,0f, (float)dstW,0f, 0f,(float)dstH, (float)dstW,(float)dstH};
        // Note: setPolyToPoly expects points in order: 0:(x0,y0),1:(x1,y1),... mapping source->dst
        Matrix m = new Matrix();
        boolean ok = m.setPolyToPoly(srcPts, 0, dstPts, 0, 4);
        if (!ok) { Toast.makeText(this, "変換に失敗しました", Toast.LENGTH_SHORT).show(); return; }
        Bitmap outBmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(outBmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(sourceBitmap, m, p);
// save outBmp
        saveBitmapToPictures(outBmp);
    }
    private void saveBitmapToPictures(Bitmap bmp) {
        String filename = "scanned_" + System.currentTimeMillis() + ".jpg";
        OutputStream out = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TrapezoidalCorrection");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("MediaStore insert failed");
                out = getContentResolver().openOutputStream(uri);
            } else {
                Uri images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                Uri uri = getContentResolver().insert(images, values);
                out = getContentResolver().openOutputStream(uri);
            }
            if (out != null) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.close();
                Toast.makeText(this, "保存しました: Pictures/TrapezoidalCorrection/" + filename, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
    // handle permission result (for older devices)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            // nothing special to do; user may deny
        }
    }
}
