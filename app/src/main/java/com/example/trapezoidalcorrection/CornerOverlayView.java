package com.example.trapezoidalcorrection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CornerOverlayView: 既存の機能はそのままに、Magnifier を
 * imageView のビットマップ座標から正確に作るように修正。
 */
public class CornerOverlayView extends View {
    // existing drawing / corner fields (省略可)...
    private float[] corners = new float[8];
    private int activeIndex = -1;

    private ImageView imageView;
    private Bitmap sourceBitmap;

    // Magnifier
    private PopupWindow magnifierPopup;
    private MagnifierView magView;
    private final int MAG_SIZE = 340; // 表示サイズ（ピクセル）

    public CornerOverlayView(Context c) { this(c, null); }
    public CornerOverlayView(Context c, @Nullable AttributeSet a) { this(c, a, 0); }
    public CornerOverlayView(Context c, @Nullable AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        init();
    }

    private void init() {
        // 初期角は later setImageView で設定する
        for (int i = 0; i < 8; i++) corners[i] = 0f;

        magView = new MagnifierView(getContext());
        magnifierPopup = new PopupWindow(magView, MAG_SIZE, MAG_SIZE, false);
        magnifierPopup.setClippingEnabled(false);
    }

    public void setImageView(ImageView iv) {
        this.imageView = iv;
        // 初期コーナーを view に合わせて設定（必要なら）
        post(() -> {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;
            float pad = Math.min(w, h) * 0.1f;
            corners[0] = pad; corners[1] = pad;
            corners[2] = w - pad; corners[3] = pad;
            corners[4] = pad; corners[5] = h - pad;
            corners[6] = w - pad; corners[7] = h - pad;
            invalidate();
        });
    }

    public void setBitmap(Bitmap bmp) {
        this.sourceBitmap = bmp;
        if (magView != null) magView.setSourceBitmap(bmp);

        // imageView またはその matrix がまだ準備できていない場合は遅延して行う
        if (imageView == null) {
            // imageView が未設定なら、corners はそのままにしておく
            invalidate();
            return;
        }

        // update corners based on image size
        imageView.post(() -> {
            // ImageView の matrix を取得して、ビットマップ座標 (0,0)-(w,h) を view 座標にマップする
            try {
                Matrix imgMatrix = imageView.getImageMatrix();
                RectF srcRect = new RectF(0f, 0f, (float) bmp.getWidth(), (float) bmp.getHeight());
                RectF dstRect = new RectF();
                imgMatrix.mapRect(dstRect, srcRect);

                // mapRect が無効な値を返す場合は ImageView の表示領域を使う
                if (dstRect.width() <= 0 || dstRect.height() <= 0) {
                    dstRect.set(0f, 0f, imageView.getWidth(), imageView.getHeight());
                }

                // 少し内側に余白を取る (例: 5%)
                float padX = dstRect.width() * 0.05f;
                float padY = dstRect.height() * 0.05f;

                corners[0] = dstRect.left + padX;      corners[1] = dstRect.top + padY;    // 左上
                corners[2] = dstRect.right - padX;     corners[3] = dstRect.top + padY;    // 右上
                corners[4] = dstRect.left + padX;      corners[5] = dstRect.bottom - padY; // 左下
                corners[6] = dstRect.right - padX;     corners[7] = dstRect.bottom - padY; // 右下

                invalidate();
            } catch (Exception e) {
                // 万一失敗したら view 全体にフォールバック
                int w = getWidth();
                int h = getHeight();
                float pad = Math.min(w, h) * 0.1f;
                corners[0] = pad; corners[1] = pad;
                corners[2] = w - pad; corners[3] = pad;
                corners[4] = pad; corners[5] = h - pad;
                corners[6] = w - pad; corners[7] = h - pad;
                invalidate();
            }
        });
    }

    // drawing resources
    private final static android.graphics.Paint linePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final static android.graphics.Paint dotPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    // (既存の onDraw をここに入れる - 省略していない場合はそのまま残してください)
    @Override
    protected void onDraw(@NonNull android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        // （四角線と点を描く既存コードをここに入れてください）
        linePaint.setColor(0x99FFFFFF); // ARGB
        linePaint.setStrokeWidth(3f);
        // polygon lines
        canvas.drawLine(corners[0], corners[1], corners[2], corners[3], linePaint);
        canvas.drawLine(corners[2], corners[3], corners[6], corners[7], linePaint);
        canvas.drawLine(corners[6], corners[7], corners[4], corners[5], linePaint);
        canvas.drawLine(corners[4], corners[5], corners[0], corners[1], linePaint);
        // dots
        dotPaint.setColor(0xFFFF0000); // ARGB
        for (int i = 0; i < 4; i++) {
            canvas.drawCircle(corners[i*2], corners[i*2+1], 10f, dotPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float vx = event.getX();
        float vy = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeIndex = findNearbyCorner(vx, vy);
                if (activeIndex >= 0) {
                    showMagnifierAt(vx, vy);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (activeIndex >= 0) {
                    corners[activeIndex*2] = vx;
                    corners[activeIndex*2+1] = vy;
                    updateMagnifierAt(vx, vy);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                hideMagnifier();
                activeIndex = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private int findNearbyCorner(float x, float y) {
        float best = Float.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < 4; i++) {
            float dx = x - corners[i*2];
            float dy = y - corners[i*2+1];
            float d = dx*dx + dy*dy;
            if (d < best && d < 2500f) { // 半径50px
                best = d;
                idx = i;
            }
        }
        return idx;
    }

    private void showMagnifierAt(float vx, float vy) {
        if (imageView == null || sourceBitmap == null) return;

        // view coords -> bitmap coords
        float[] pts = new float[]{vx, vy};
        Matrix inv = new Matrix();
        imageView.getImageMatrix().invert(inv);
        inv.mapPoints(pts);
        float bmpX = pts[0], bmpY = pts[1];

        // update magView center and show popup near finger (上に表示)
        magView.setCenterAndScale(bmpX, bmpY, 2.5f); // 2.5x 拡大 (お好みで調整)
        magView.setSourceBitmap(sourceBitmap);

        // position popup so it doesn't cover finger: show above touch
        int px = (int) (vx - MAG_SIZE/2f);
        int py = (int) (vy - MAG_SIZE - 40); // 40px margin above
        if (!magnifierPopup.isShowing()) {
            magnifierPopup.showAsDropDown(this, px, py);
        } else {
            magnifierPopup.update(px, py, MAG_SIZE, MAG_SIZE);
        }
    }

    private void updateMagnifierAt(float vx, float vy) {
        if (!magnifierPopup.isShowing()) {
            showMagnifierAt(vx, vy);
            return;
        }
        // update center
        float[] pts = new float[]{vx, vy};
        Matrix inv = new Matrix();
        imageView.getImageMatrix().invert(inv);
        inv.mapPoints(pts);
        float bmpX = pts[0], bmpY = pts[1];
        magView.setCenterAndScale(bmpX, bmpY, magView.getScale());
        // move popup to follow finger
        int px = (int) (vx - MAG_SIZE/2f);
        int py = (int) (vy - MAG_SIZE - 40);
        magnifierPopup.update(px, py, MAG_SIZE, MAG_SIZE);
        magView.invalidate();
    }

    private void hideMagnifier() {
        if (magnifierPopup != null && magnifierPopup.isShowing()) magnifierPopup.dismiss();
    }

    // 外部に角座標を取り出す API
    public float[] getCornersViewCoords() { return corners.clone(); }
}
