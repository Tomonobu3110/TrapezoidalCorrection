package com.example.trapezoidalcorrection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class MagnifierView extends View {
    private Bitmap srcBitmap;    // 元ビットマップ（フル）
    private float centerX = 0f;  // ビットマップ座標
    private float centerY = 0f;  // ビットマップ座標
    private float scale = 2.5f;  // 拡大率
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path clipPath = new Path();

    public MagnifierView(Context c) {
        super(c);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);
        borderPaint.setColor(0xFFFFFFFF);
        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(3f);
        crossPaint.setColor(0x99FFFFFF); // 半透明白
    }

    public void setSourceBitmap(Bitmap bm) {
        this.srcBitmap = bm;
    }

    public void setCenterAndScale(float bmpX, float bmpY, float s) {
        this.centerX = bmpX;
        this.centerY = bmpY;
        this.scale = s;
    }

    public float getScale() { return this.scale; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // circle radius (マージン含む)
        float radius = Math.min(w, h) * 0.48f;
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        // クリップを丸にする
        clipPath.reset();
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        if (srcBitmap != null) {
            // 1) ビットマップサイズを取得
            final int bmpW = srcBitmap.getWidth();
            final int bmpH = srcBitmap.getHeight();

            // 2) 拡大鏡表示に対応するソース領域サイズ（ビットマップ座標系）
            float srcWidth = w / scale;
            float srcHeight = h / scale;

            // 3) srcWidth / srcHeight がビットマップより大きければ制限する
            if (srcWidth > bmpW) srcWidth = bmpW;
            if (srcHeight > bmpH) srcHeight = bmpH;

            // 4) centerX/centerY をビットマップ内にクランプ（重要）
            float cX = centerX;
            float cY = centerY;
            if (Float.isNaN(cX) || Float.isInfinite(cX)) cX = bmpW * 0.5f;
            if (Float.isNaN(cY) || Float.isInfinite(cY)) cY = bmpH * 0.5f;
            cX = Math.max(0f, Math.min(cX, bmpW));
            cY = Math.max(0f, Math.min(cY, bmpH));

            // 5) left/top を計算し、ビットマップ範囲内に収める
            float left = cX - srcWidth / 2f;
            float top  = cY - srcHeight / 2f;

            // clamp left/top
            float maxLeft = Math.max(0f, bmpW - srcWidth);
            float maxTop  = Math.max(0f, bmpH - srcHeight);
            if (left < 0f) left = 0f;
            if (top < 0f) top = 0f;
            if (left > maxLeft) left = maxLeft;
            if (top > maxTop) top = maxTop;

            // 最終 srcRect（必ず bitmap 内に収まる）
            RectF srcRect = new RectF(left, top, left + srcWidth, top + srcHeight);

            // 6) srcRect が有効かチェック（幅・高さが正）
            if (srcRect.width() <= 0f || srcRect.height() <= 0f) {
                // 描画不可なら背景だけ描いて抜ける
                canvas.drawColor(0xFF333333);
                canvas.restore();
                // draw border & cross outsideで行う（下のコードが続く）
            } else {
                // 7) srcRect -> dst(0,0,w,h) にマップして描画
                RectF dst = new RectF(0f, 0f, (float) w, (float) h);
                Matrix m = new Matrix();
                m.setRectToRect(srcRect, dst, Matrix.ScaleToFit.FILL);
                canvas.drawBitmap(srcBitmap, m, paint);
            }
            /*
            // 切り出すビットマップ領域（ビットマップ座標系）
            // 拡大後にフィットする領域サイズを計算：表示幅 / scale
            float srcWidth = w / scale;
            float srcHeight = h / scale;
            float left = centerX - srcWidth / 2f;
            float top = centerY - srcHeight / 2f;

            // ビットマップ範囲に収める
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (left + srcWidth > srcBitmap.getWidth()) left = srcBitmap.getWidth() - srcWidth;
            if (top + srcHeight > srcBitmap.getHeight()) top = srcBitmap.getHeight() - srcHeight;
            // 切り出して拡大描画する方法（Matrix を使って拡大して描画）
            RectF dst = new RectF(0, 0, w, h);
            Matrix m = new Matrix();
            // src rect -> dst rect
            RectF srcRect = new RectF(left, top, left + srcWidth, top + srcHeight);
            m.setRectToRect(srcRect, dst, Matrix.ScaleToFit.FILL);
            canvas.drawBitmap(srcBitmap, m, paint);
             */
        } else {
            // ソース無ければグレー
            canvas.drawColor(0xFF333333);
        }

        canvas.restore();

        // 円の外枠
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // 中心の薄い十字（長さは半径の 0.25）
        float crossLen = radius * 0.25f;
        canvas.drawLine(cx - crossLen, cy, cx + crossLen, cy, crossPaint);
        canvas.drawLine(cx, cy - crossLen, cx, cy + crossLen, crossPaint);

        // もしくは点にしたい場合は：
        // Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        // dot.setColor(0xCCFFFFFF);
        // canvas.drawCircle(cx, cy, 3f, dot);
    }
}
