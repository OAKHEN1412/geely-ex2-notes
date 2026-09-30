package com.oakhen.ex2dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * หน้า "บันทึกการเดินทาง": สรุปเดือนนี้ + รายการทริป/การชาร์จ (ใหม่ → เก่า)
 * ค่าใช้จ่าย = kWh ที่ใช้ (แบตที่ลด × ความจุ) × ราคาต่อหน่วย · แตะทริป = ใส่ค่าใช้จ่ายจริงเอง
 */
public class TripActivity extends Activity {
    private static final int BG = 0xFF1B1C1E, CARD = 0xFF2A2B2E, CLAY = 0xFFD97757, CREAM = 0xFFF2EFE9, MUTED = 0xFF9AA0A6;
    private LinearLayout list;
    private TextView live, summary, settings;
    private final Handler ui = new Handler();
    private final Runnable liveTick = new Runnable() {
        public void run() { live.setText(TripLog.live().isEmpty() ? "กำลังเริ่มอ่านค่าจากรถ…" : TripLog.live()); ui.postDelayed(this, 2000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("บันทึกการเดินทาง");
        title.setTextColor(CLAY); title.setTextSize(24); title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        live = text("", 14, MUTED);
        root.addView(live, top(4));

        summary = text("", 17, CREAM);
        summary.setBackground(bg(CARD));
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(summary, top(14));

        settings = text("", 14, CLAY);
        settings.setPadding(0, dp(10), 0, dp(4));
        settings.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editSettings(); } });
        root.addView(settings, top(4));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, top(8));
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); render(); ui.post(liveTick); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(liveTick); }

    private void render() {
        final float cap = TripLog.capacity(this), price = TripLog.price(this);
        settings.setText(String.format(Locale.US, "ค่าไฟ %.2f ฿/kWh · แบต %.1f kWh  (แตะเพื่อแก้)", price, cap));
        List<Object> all = TripLog.readAll(this);
        Calendar c = Calendar.getInstance();
        int month = c.get(Calendar.MONTH), year = c.get(Calendar.YEAR);
        int n = 0; double km = 0, kwh = 0, baht = 0;
        for (Object o : all) {
            if (!(o instanceof TripState.Trip)) continue;
            TripState.Trip t = (TripState.Trip) o;
            c.setTimeInMillis(t.start);
            if (c.get(Calendar.MONTH) != month || c.get(Calendar.YEAR) != year) continue;
            n++; km += t.km; kwh += TripState.kwh(t, cap); baht += TripState.cost(t, cap, price);
        }
        summary.setText(n == 0 ? "เดือนนี้ยังไม่มีทริป — ขับรถแล้วทริปจะบันทึกเอง (จอด P 2 นาทีหรือดับรถ = จบทริป)"
                : String.format(Locale.US, "เดือนนี้  %d ทริป · %.1f km · %.1f kWh · ฿%.0f\nเฉลี่ย %.1f kWh/100 km · ฿%.2f/km",
                    n, km, kwh, baht, km > 0 ? kwh / km * 100 : 0, km > 0 ? baht / km : 0));

        list.removeAllViews();
        List<Object> rev = new ArrayList<Object>(all);
        Collections.reverse(rev);
        SimpleDateFormat day = new SimpleDateFormat("EEE d MMM HH:mm", new Locale("th", "TH"));
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        int shown = 0;
        for (Object o : rev) {
            if (++shown > 200) break;
            if (o instanceof TripState.Charge) {
                TripState.Charge ch = (TripState.Charge) o;
                TextView t = text(String.format(Locale.US, "⚡ ชาร์จ  %.0f%% → %.0f%%  (+%.1f kWh)  ก่อน %s",
                        ch.from, ch.to, (ch.to - ch.from) / 100 * cap, day.format(new Date(ch.at))), 14, MUTED);
                t.setPadding(dp(8), dp(8), dp(8), dp(4));
                list.addView(t);
                continue;
            }
            final TripState.Trip t = (TripState.Trip) o;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(bg(CARD));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            long mins = Math.max(1, (t.end - t.start) / 60000);
            card.addView(text(day.format(new Date(t.start)) + " – " + hm.format(new Date(t.end)) + "  (" + mins + " นาที)", 15, CREAM));
            double k = TripState.kwh(t, cap), cost = TripState.cost(t, cap, price);
            String soc = t.socStart >= 0 && t.socEnd >= 0 ? String.format(Locale.US, "แบต %.0f%% → %.0f%%", t.socStart, t.socEnd) : "แบต ?";
            card.addView(text(String.format(Locale.US, "%.1f km · %s · %.1f kWh · เฉลี่ย %.0f km/h · สูงสุด %.0f",
                    t.km, soc, k, t.avgKmh(), t.maxKmh), 14, MUTED), top(2));
            card.addView(text(String.format(Locale.US, "฿%.0f%s", cost, t.cost >= 0 ? "  (ใส่เอง)" : "  (คำนวณ · แตะเพื่อใส่ค่าจริง)"), 15, CLAY), top(2));
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editCost(t); } });
            list.addView(card, top(8));
        }
    }

    private void editCost(final TripState.Trip t) {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        in.setHint("บาท");
        if (t.cost >= 0) in.setText(String.valueOf(t.cost));
        new AlertDialog.Builder(this)
                .setTitle("ค่าใช้จ่ายจริงของทริปนี้")
                .setView(in)
                .setPositiveButton("บันทึก", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try { TripLog.setCost(TripActivity.this, t.start, Double.parseDouble(in.getText().toString().trim())); } catch (Throwable ignored) {}
                        render();
                    }
                })
                .setNeutralButton("ใช้ค่าคำนวณ", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { TripLog.setCost(TripActivity.this, t.start, -1); render(); }
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
    }

    private void editSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText price = new EditText(this), cap = new EditText(this);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        cap.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setText(String.valueOf(TripLog.price(this)));
        cap.setText(String.valueOf(TripLog.capacity(this)));
        TextView l1 = new TextView(this); l1.setText("ค่าไฟ บาท/kWh (บ้าน ~4–5 · ตู้ชาร์จเร็ว ~7–9)");
        TextView l2 = new TextView(this); l2.setText("ความจุแบต kWh");
        box.addView(l1); box.addView(price); box.addView(l2); box.addView(cap);
        new AlertDialog.Builder(this)
                .setTitle("ตั้งค่าการคำนวณ")
                .setView(box)
                .setPositiveButton("บันทึก", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            float p = Float.parseFloat(price.getText().toString().trim());
                            float c = Float.parseFloat(cap.getText().toString().trim());
                            if (p >= 0 && c > 5 && c < 200) Prefs.of(TripActivity.this).edit().putFloat(TripLog.K_PRICE, p).putFloat(TripLog.K_CAP, c).apply();
                        } catch (Throwable ignored) {}
                        render();
                    }
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private GradientDrawable bg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(m);
        return lp;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
