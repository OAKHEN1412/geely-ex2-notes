package com.oakhen.ex2dev;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * ตัวการ์ตูนของผู้ช่วยสั่งเสียง (วาดเองด้วย Canvas — หุ่นยนต์ก้อนกลมสีส้มดินเผา หน้าจอดำ ตากลม มีเสาอากาศ + หูฟัง)
 * 4 สถานะ: ฟัง (เด้งเบา ๆ + วงคลื่นขยาย + ปากกลมตามระดับเสียง) · คิด (ลูกตากลอก + เสาอากาศกะพริบ) ·
 *          พูด (ปากขยับ) · เสร็จ (ตายิ้ม ^ ^) · ผิดพลาด (ตา × ปากเบะ)
 * วนวาดต่อเนื่องเฉพาะตอนติดอยู่บนจอ (postInvalidateOnAnimation) · ทุกเมธอดเรียกบน main thread
 */
final class VoiceMascot extends View {
    static final int LISTENING = 0, THINKING = 1, SPEAKING = 2, DONE = 3, ERROR = 4;

    private static final int CLAY = 0xFFD97757, CLAY_DK = 0xFFB85F42, DARK = 0xFF1B1C1E, CREAM = 0xFFF2EFE9;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private int state = LISTENING;
    private long stateAt = SystemClock.uptimeMillis();
    private float level;   // ระดับเสียงที่ได้ยิน 0..1 (ปรับให้นิ่งแล้ว)

    VoiceMascot(Context c) { super(c); }

    void setState(int s) {
        if (s == state) return;
        state = s;
        stateAt = SystemClock.uptimeMillis();
    }

    /** rmsdB จาก RecognitionListener (ประมาณ -2..10) */
    void setLevel(float rmsDb) {
        float v = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
        level = level * 0.6f + v * 0.4f;
    }

    @Override protected void onMeasure(int w, int h) {
        int s = (int) (88 * getResources().getDisplayMetrics().density + 0.5f);
        setMeasuredDimension(s, s);
    }

    @Override protected void onDraw(Canvas cv) {
        float s = getWidth();
        float t = (SystemClock.uptimeMillis() - stateAt) / 1000f;
        float clock = SystemClock.uptimeMillis() / 1000f;
        float cx = s / 2f, cy = s * 0.54f;

        // วงคลื่นตอนฟัง
        if (state == LISTENING) {
            for (int k = 0; k < 2; k++) {
                float ph = (clock * 0.9f + k * 0.5f) % 1f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.018f);
                p.setColor(CLAY);
                p.setAlpha((int) ((1f - ph) * 130));
                cv.drawCircle(cx, cy, s * (0.31f + ph * (0.13f + 0.04f * level)), p);
            }
        }

        // เด้ง/หายใจ ตามสถานะ
        float sc = 1f, dy = 0f;
        if (state == LISTENING) sc = 1f + 0.03f * (float) Math.sin(clock * 6f) + 0.04f * level;
        else if (state == SPEAKING) dy = -s * 0.02f * Math.abs((float) Math.sin(clock * 9f));
        else if (state == DONE && t < 0.5f) dy = -s * 0.08f * (float) Math.sin(t / 0.5f * Math.PI);   // ดีดดีใจตอนเสร็จ
        else if (state == ERROR && t < 0.4f) cx += s * 0.02f * (float) Math.sin(t * 60f);             // สั่นหัว
        cv.save();
        cv.translate(0, dy);
        cv.scale(sc, sc, cx, cy + s * 0.2f);

        // เงาใต้ตัว
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        r.set(cx - s * 0.24f, cy + s * 0.28f, cx + s * 0.24f, cy + s * 0.33f);
        cv.drawOval(r, p);

        // เสาอากาศ
        boolean blink = state == THINKING && ((int) (clock * 3f) % 2 == 0);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.025f);
        p.setColor(CLAY_DK);
        cv.drawLine(cx, cy - s * 0.27f, cx, cy - s * 0.37f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(state == ERROR ? 0xFFE5534B : (state == LISTENING || blink) ? 0xFFFFE08A : CREAM);
        cv.drawCircle(cx, cy - s * 0.40f, s * 0.045f, p);

        // หูฟัง
        p.setColor(CLAY_DK);
        cv.drawCircle(cx - s * 0.33f, cy, s * 0.065f, p);
        cv.drawCircle(cx + s * 0.33f, cy, s * 0.065f, p);

        // ตัว
        p.setColor(CLAY);
        r.set(cx - s * 0.31f, cy - s * 0.28f, cx + s * 0.31f, cy + s * 0.30f);
        cv.drawOval(r, p);
        p.setColor(0x26FFFFFF);   // เงาสะท้อนบนซ้าย
        r.set(cx - s * 0.24f, cy - s * 0.24f, cx - s * 0.02f, cy - s * 0.12f);
        cv.drawOval(r, p);

        // แผ่นหน้า
        p.setColor(DARK);
        r.set(cx - s * 0.23f, cy - s * 0.17f, cx + s * 0.23f, cy + s * 0.17f);
        cv.drawRoundRect(r, s * 0.12f, s * 0.12f, p);

        drawEyes(cv, cx, cy - s * 0.05f, s, t, clock);
        drawMouth(cv, cx, cy + s * 0.085f, s, clock);
        cv.restore();

        postInvalidateOnAnimation();
    }

    private void drawEyes(Canvas cv, float cx, float ey, float s, float t, float clock) {
        float dx = s * 0.105f, rx = s * 0.052f, ry = s * 0.06f;
        p.setStrokeWidth(s * 0.028f);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (state == DONE) {   // ตายิ้ม ^ ^
            p.setStyle(Paint.Style.STROKE);
            p.setColor(CREAM);
            for (int k = -1; k <= 1; k += 2) {
                r.set(cx + k * dx - rx, ey - ry * 0.6f, cx + k * dx + rx, ey + ry * 1.2f);
                cv.drawArc(r, 200, 140, false, p);
            }
            return;
        }
        if (state == ERROR) {   // ตา ×
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFFFF9C8F);
            for (int k = -1; k <= 1; k += 2) {
                float ex = cx + k * dx, a = s * 0.04f;
                cv.drawLine(ex - a, ey - a, ex + a, ey + a, p);
                cv.drawLine(ex - a, ey + a, ex + a, ey - a, p);
            }
            return;
        }
        float open = 1f;   // กะพริบทุก ~3 วิ นาน 0.12 วิ
        float bt = clock % 3.2f;
        if (bt < 0.12f) open = Math.abs(bt - 0.06f) / 0.06f * 0.9f + 0.1f;
        p.setStyle(Paint.Style.FILL);
        for (int k = -1; k <= 1; k += 2) {
            float ex = cx + k * dx;
            p.setColor(CREAM);
            r.set(ex - rx, ey - ry * open, ex + rx, ey + ry * open);
            cv.drawOval(r, p);
            if (open < 0.5f) continue;
            float px = 0, py = 0;   // ลูกตา: คิด = กลอกเป็นวง, อื่น ๆ มองตรง (ฟัง = ขยับตามเสียงนิดหน่อย)
            if (state == THINKING) { px = (float) Math.cos(clock * 4f) * rx * 0.45f; py = (float) Math.sin(clock * 4f) * ry * 0.45f; }
            p.setColor(DARK);
            cv.drawCircle(ex + px, ey + py, rx * (state == LISTENING ? 0.62f + 0.12f * level : 0.55f), p);
        }
    }

    private void drawMouth(Canvas cv, float cx, float my, float s, float clock) {
        p.setColor(CREAM);
        if (state == LISTENING) {   // ปากกลม ขยายตามเสียง
            p.setStyle(Paint.Style.FILL);
            float rr = s * (0.02f + 0.03f * level);
            r.set(cx - rr, my - rr * 0.8f, cx + rr, my + rr * 0.8f);
            cv.drawOval(r, p);
        } else if (state == SPEAKING) {   // ปากขยับ
            p.setStyle(Paint.Style.FILL);
            float h = s * (0.012f + 0.035f * Math.abs((float) Math.sin(clock * 14f)));
            r.set(cx - s * 0.04f, my - h, cx + s * 0.04f, my + h);
            cv.drawOval(r, p);
        } else if (state == THINKING) {   // เม้มปากเอียง ๆ
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.02f);
            float w = (float) Math.sin(clock * 3f) * s * 0.01f;
            cv.drawLine(cx - s * 0.04f, my + w, cx + s * 0.04f, my - w, p);
        } else {   // เสร็จ = ยิ้ม · ผิดพลาด = เบะ
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.022f);
            boolean ok = state == DONE;
            r.set(cx - s * 0.055f, my - s * (ok ? 0.04f : 0.0f), cx + s * 0.055f, my + s * (ok ? 0.04f : 0.08f));
            cv.drawArc(r, ok ? 20 : 200, ok ? 140 : 140, false, p);
        }
    }

    /** กรอบพูด (ลูกโป่ง) สีครีม มีหางชี้ไปทางตัวการ์ตูนทางขวา — ใช้เป็นพื้นของข้อความ */
    static final class BubbleBg extends Drawable {
        private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path tail = new Path();
        private final float radius, tailW;

        BubbleBg(float density) {
            radius = 16 * density; tailW = 10 * density;
            bp.setColor(0xF5F2EFE9);
        }

        /** เว้นขอบขวาให้หาง */
        float tailWidth() { return tailW; }

        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            rect.set(0, 0, w - tailW + 1, h);
            c.drawRoundRect(rect, radius, radius, bp);
            tail.reset();
            tail.moveTo(w - tailW, h * 0.5f - tailW * 0.8f);
            tail.lineTo(w, h * 0.5f);
            tail.lineTo(w - tailW, h * 0.5f + tailW * 0.8f);
            tail.close();
            c.drawPath(tail, bp);
        }
        @Override public void setAlpha(int a) { bp.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { bp.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
