package org.robowindows.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Project-owned classic controls: drawn shapes, no proprietary bitmap assets. */
final class ClassicUi {
    static final int GRAY = 0xffc0c0c0, NAVY = 0xff000080, INK = 0xff000000;
    static final int WHITE = 0xffffffff, SHADOW = 0xff808080;
    private ClassicUi() {}

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static Drawable bevel(Context c, boolean inset) {
        return new Bevel(dp(c, 1), inset);
    }

    /** Guest video remains visible; do not fade the whole view (including text). */
    static void sessionOverlay(ViewGroup panel) {
        panel.setBackgroundColor(0x50303030);
        styleOverlayChildren(panel);
    }

    private static void styleOverlayChildren(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Button) {
                Drawable background = bevel(child.getContext(), false);
                background.setAlpha(144);
                child.setBackground(background);
            } else if (child instanceof TextView) {
                TextView label = (TextView) child;
                label.setTextColor(WHITE);
                label.setShadowLayer(dp(child.getContext(), 1), 0, 0, INK);
            } else if (child instanceof ViewGroup) {
                styleOverlayChildren((ViewGroup) child);
            }
        }
    }

    static Button button(Context c, String label, View.OnClickListener action) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        b.setTextColor(new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled}, {}}, new int[]{0xff595959, INK}));
        b.setBackground(bevel(c, false));
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        b.setOnClickListener(action);
        return b;
    }

    static LinearLayout title(Context c, String label, Runnable close) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 8), dp(c, 4), dp(c, 4), dp(c, 4));
        bar.setBackgroundColor(NAVY);
        TextView title = new TextView(c);
        title.setText(label);
        title.setTextColor(WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (close != null) {
            Button b = button(c, "×", v -> close.run());
            b.setContentDescription("Close " + label);
            bar.addView(b, new LinearLayout.LayoutParams(dp(c, 48), -2));
        }
        return bar;
    }

    /** Explicit bounds stay >=48dp even when legacy call sites request 44dp. */
    static void ensureTouchTargets(View view) {
        if (view instanceof Button) {
            ViewGroup.LayoutParams p = view.getLayoutParams();
            if (p != null && p.height > 0) {
                p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) ensureTouchTargets(group.getChildAt(i));
        }
    }

    private static final class Bevel extends Drawable {
        private final Paint paint = new Paint();
        private final int line;
        private final boolean inset;
        private int alpha = 255;
        Bevel(int line, boolean inset) { this.line = Math.max(1, line); this.inset = inset; }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        private boolean has(int value) { for (int s : getState()) if (s == value) return true; return false; }
        private void color(int color) {
            paint.setColor(color);
            paint.setAlpha(alpha);
        }
        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            boolean down = inset || has(android.R.attr.state_pressed) || has(android.R.attr.state_selected);
            color(down ? 0xffd6d6d6 : GRAY); c.drawRect(r, paint);
            for (int i = 0; i < 2; i++) {
                int d = i * line;
                color(down ? (i == 0 ? SHADOW : INK) : (i == 0 ? WHITE : 0xffdfdfdf));
                c.drawRect(r.left+d, r.top+d, r.right-d, r.top+d+line, paint);
                c.drawRect(r.left+d, r.top+d, r.left+d+line, r.bottom-d, paint);
                color(down ? WHITE : (i == 0 ? INK : SHADOW));
                c.drawRect(r.left+d, r.bottom-d-line, r.right-d, r.bottom-d, paint);
                c.drawRect(r.right-d-line, r.top+d, r.right-d, r.bottom-d, paint);
            }
            if (has(android.R.attr.state_focused)) {
                // Focus is a narrow opaque outline, not an opaque button fill.
                paint.setColor(NAVY); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(line);
                c.drawRect(r.left+4*line, r.top+4*line, r.right-4*line, r.bottom-4*line, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        @Override public void setAlpha(int alpha) { this.alpha = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
    }
}
