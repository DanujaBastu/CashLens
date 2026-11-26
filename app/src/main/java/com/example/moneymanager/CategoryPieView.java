package com.example.moneymanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CategoryPieView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float[] sweeps = new float[0];
    private int[] colors = new int[0];

    public CategoryPieView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setSlices(float[] sweeps, int[] colors) {
        this.sweeps = sweeps;
        this.colors = colors;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        float start = -90f;
        for (int i = 0; i < sweeps.length; i++) {
            paint.setColor(colors[i]);
            canvas.drawArc(bounds, start, sweeps[i], true, paint);
            start += sweeps[i];
        }
    }
}
