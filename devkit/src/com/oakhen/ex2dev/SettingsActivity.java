package com.oakhen.ex2dev;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * ศูนย์ควบคุม EX2 BY OAKHEN — hub เดียวรวม 2 ส่วน:
 *   A) ฟีเจอร์ EX2 (เพิ่มโดยเรา)  — สวิตช์เปิด/ปิดต่อฟีเจอร์ (กล่องลอย/เริ่มอัตโนมัติ/ปุ่มปิดจอ/ปุ่มหรี่จอ) + กระจกไว
 *   B) ฟีเจอร์รถ (แอพเดิม Geely)  — การ์ดลัดเปิดหน้าฟีเจอร์เดิม (เปิด MainActivity + best-effort deep-link route)
 * UI เดิม (Compose) ไม่ถูกแตะ — ยังทำงานครบ; hub นี้เป็นประตูเดียวที่รวมทั้งของเราและของเดิม
 */
public class SettingsActivity extends Activity {

    // สีแบรนด์
    private static final int BG    = 0xFF1B1C1E;
    private static final int CARD  = 0xFF2A2B2E;
    private static final int CLAY  = 0xFFD97757;
    private static final int CREAM = 0xFFF2EFE9;
    private static final int MUTED = 0xFF9AA0A6;

    private static final String CAR_MAIN = "com.geely.ex2.tools.MainActivity";

    private SharedPreferences sp;
    private final ArrayList<Switch> children = new ArrayList<Switch>(); // สวิตช์ลูก (ปิดตอน master ปิด)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final Context app = getApplicationContext();
        sp = Prefs.of(this);
        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(24), pad, dp(28));
        scroll.addView(root);

        // ---------- header ----------
        TextView title = new TextView(this);
        title.setText("EX2 BY OAKHEN");
        title.setTextColor(CLAY);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("ศูนย์ควบคุม");
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        root.addView(sub, marginTop(dp(2)));

        // ========== แบ่งจอ (รวมไว้ในแอพนี้ — ไม่มีไอคอนแยกแล้ว) ==========
        sectionLabel(root, "แบ่งจอ");
        splitCard(root);

        // อัปเดตแอป (OAKHEN + แอปที่ลงไว้)
        LinearLayout upd = new LinearLayout(this);
        upd.setOrientation(LinearLayout.VERTICAL);
        upd.setBackground(cardBg());
        int up = dp(14); upd.setPadding(up, up, up, up);
        upd.setClickable(true);
        TextView ut = new TextView(this);
        ut.setText("⬆ อัปเดตแอป  ›");
        ut.setTextColor(CREAM); ut.setTextSize(17);
        upd.addView(ut);
        TextView ud = new TextView(this);
        ud.setText("EX2 BY OAKHEN build " + AppUpdater.ownBuild(this) + " · YouTube, YT Music, Maps, SmartTube, Disney+ … อัปเดตจากจอรถได้เลย ไม่ต้องต่อคอม");
        ud.setTextColor(MUTED); ud.setTextSize(13);
        upd.addView(ud, marginTop(dp(2)));
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(SettingsActivity.this, UpdateActivity.class)); } catch (Throwable e) { toast("เปิดหน้าอัปเดตไม่ได้"); }
            }
        });
        root.addView(upd, marginTop(dp(12)));

        // บันทึกการเดินทาง + ค่าใช้จ่าย
        LinearLayout trip = new LinearLayout(this);
        trip.setOrientation(LinearLayout.VERTICAL);
        trip.setBackground(cardBg());
        int tp = dp(14); trip.setPadding(tp, tp, tp, tp);
        trip.setClickable(true);
        TextView tt = new TextView(this);
        tt.setText("📒 บันทึกการเดินทาง & ค่าใช้จ่าย  ›");
        tt.setTextColor(CREAM); tt.setTextSize(17);
        trip.addView(tt);
        TextView td = new TextView(this);
        td.setText("บันทึกทุกทริปเอง: ระยะทาง, แบตที่ใช้, kWh, ค่าไฟต่อทริป, การชาร์จ · สรุปรายเดือน");
        td.setTextColor(MUTED); td.setTextSize(13);
        trip.addView(td, marginTop(dp(2)));
        trip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(SettingsActivity.this, TripActivity.class)); } catch (Throwable e) { toast("เปิดหน้าบันทึกไม่ได้"); }
            }
        });
        root.addView(trip, marginTop(dp(12)));

        // ========== A) ฟีเจอร์ EX2 (ของเรา) ==========
        sectionLabel(root, "ฟีเจอร์ EX2 · เพิ่มโดยเรา");

        final Switch master = row(root, "กล่องลอย (Floating Box)",
                "กล่อง ☰ ลอยบนจอ · แตะเพื่อกางเมนูปิด/หรี่จอ",
                Prefs.KEY_FLOATING, true, false);
        master.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                sp.edit().putBoolean(Prefs.KEY_FLOATING, on).apply();
                if (on) FloatingBox.show(app); else FloatingBox.hide(app);
                setChildrenEnabled(on);
            }
        });

        Switch autostart = child(root, "เริ่มอัตโนมัติหลังบูต",
                "โผล่กล่องเองเมื่อสตาร์ทรถ/รีบูตจอ", Prefs.KEY_AUTOSTART, true);
        autostart.setOnCheckedChangeListener(saver(Prefs.KEY_AUTOSTART));

        Switch btnOff = child(root, "ปุ่ม 🌚 ปิดจอ",
                "ในเมนูกล่อง — ดับ backlight ทั้งจอ แตะปลุก", Prefs.KEY_BTN_OFF, true);
        btnOff.setOnCheckedChangeListener(saver(Prefs.KEY_BTN_OFF));

        Switch btnDim = child(root, "ปุ่ม 🌙 หรี่จอ",
                "ในเมนูกล่อง — หรี่จอ (สำรอง) แตะปลุก", Prefs.KEY_BTN_DIM, true);
        btnDim.setOnCheckedChangeListener(saver(Prefs.KEY_BTN_DIM));

        // ไม่ใช่ลูกของกล่องลอย (row child=false) — ปิดกล่องลอยแล้ว Wi-Fi ยังเปิดเสมอได้
        Switch wifi = row(root, "Wi-Fi เปิดเสมอ",
                "เปิด Wi-Fi เองเมื่อสตาร์ทรถ + หาเครือข่ายที่เคยต่ออัตโนมัติ · ปิดสวิตช์นี้ถ้าจะปิด Wi-Fi เอง",
                Prefs.KEY_WIFI_ON, true, false);
        wifi.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Prefs.of(SettingsActivity.this).edit().putBoolean(Prefs.KEY_WIFI_ON, on).apply();
                if (on) WifiKeeper.ensureOn();
            }
        });

        Switch timeTh = row(root, "เวลาไทยอัตโนมัติ",
                "ตั้งโซนเวลา Asia/Bangkok + เทียบเวลากับ time.google.com ตอนสตาร์ทและทุก 6 ชม. · " + TimeKeeper.last(),
                Prefs.KEY_TIME_TH, true, false);
        timeTh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean(Prefs.KEY_TIME_TH, on).apply();
                if (on) Vhal.thread().post(new Runnable() { public void run() { TimeKeeper.syncNow(app); } });
            }
        });

        Switch compass = row(root, "เข็มทิศ Google Maps ใช้ทิศจาก GPS",
                "จอนี้ไม่มีเข็มทิศที่ปรับเทียบ ลูกศรใน Maps เลยหมุนไปมา · เปิด = ไม่ส่งเซ็นเซอร์ให้ Play Services (ต้นทางทิศของ Maps) ลูกศรหันตามทิศวิ่งแบบ Waze",
                Prefs.KEY_COMPASS_OFF, true, false);
        compass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean(Prefs.KEY_COMPASS_OFF, on).apply();
                SensorGuard.apply(app);
                toast(on ? "Maps ใช้ทิศจาก GPS แล้ว" : "คืนเซ็นเซอร์ให้ Play Services แล้ว");
            }
        });

        swcCard(root);

        infoRow(root, "กระจกค่อยๆ ลงตอนเปิดประตู",
                "ลดทีละสเต็ปจนถึง 20% · ปิดประตูกลางทาง = หยุดแล้วปิดกระจก · ทำงานอัตโนมัติ (ฝังในแอป)");

        // ========== B) ฟีเจอร์รถ (แอพเดิม Geely) ==========
        sectionLabel(root, "ฟีเจอร์รถ · แอพเดิม (Geely)");
        TextView hint = new TextView(this);
        hint.setText("แตะเพื่อเปิดหน้าเดิมของแอพ Geely");
        hint.setTextColor(MUTED); hint.setTextSize(13);
        root.addView(hint, marginTop(dp(2)));

        // การ์ด 2 คอลัมน์ (emoji + label -> เปิด MainActivity + route)
        String[][] feats = {
            {"🏠", "ภาพรวม",            "home"},
            {"🪟", "ปิดกระจกอัตโนมัติ",   "auto_window"},
            {"🔊", "เสียง AVAS",         "avas"},
            {"💡", "ไฟ Ambient",        "ambient_light"},
            {"🔋", "แบตเตอรี่",          "battery"},
            {"🚗", "การขับขี่",          "driving"},
            {"❄️", "ปรับอากาศ",         "hvac"},
            {"⚙️", "ตั้งค่าเดิม",         "settings"},
        };
        LinearLayout rowLay = null;
        for (int i = 0; i < feats.length; i++) {
            if (i % 2 == 0) {
                rowLay = new LinearLayout(this);
                rowLay.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(rowLay, marginTop(dp(12)));
            }
            rowLay.addView(carCard(feats[i][0], feats[i][1], feats[i][2]),
                    cellParams(i % 2 == 0));
        }

        // ปุ่มเปิดแอพเดิมเต็ม (เผื่อ route ไม่ตรง)
        LinearLayout openAll = new LinearLayout(this);
        openAll.setBackground(cardBg());
        openAll.setGravity(Gravity.CENTER);
        int op = dp(16); openAll.setPadding(op, op, op, op);
        openAll.setClickable(true);
        TextView oa = new TextView(this);
        oa.setText("↗  เปิดแอพเดิมเต็ม (ทุกฟีเจอร์รถ)");
        oa.setTextColor(CLAY); oa.setTextSize(16);
        openAll.addView(oa);
        openAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCar(null); }
        });
        root.addView(openAll, marginTop(dp(12)));

        setChildrenEnabled(master.isChecked());
        setContentView(scroll);
    }

    /** เปิด MainActivity ของแอพเดิม + ยิง route extra แบบ best-effort หลาย key (ไม่ตรงก็เปิดหน้าหลัก) */
    private void openCar(String route) {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), CAR_MAIN);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (route != null) {
                for (String k : new String[]{"route", "screen", "destination",
                        "start_destination", "nav_route", "tab"}) {
                    i.putExtra(k, route);
                }
            }
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "เปิดแอพเดิมไม่ได้", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- ปุ่มพวงมาลัย: ดักรหัส + ตั้งการกระทำ ----------
    private LinearLayout swcList;
    private TextView swcStatus;
    private final android.os.Handler ui = new android.os.Handler();
    private final Runnable swcTick = new Runnable() {
        public void run() { renderSwc(); ui.postDelayed(this, 700); }
    };
    private String swcSig = "";

    private void swcCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14);
        card.setPadding(p, p, p, p);
        root.addView(card, marginTop(dp(12)));
        TextView t = new TextView(this);
        t.setText("ปุ่มพวงมาลัย");
        t.setTextColor(CREAM); t.setTextSize(17);
        card.addView(t);
        TextView d = new TextView(this);
        d.setText("ตั้งได้ทั้งกดสั้นและกดค้าง (≥ 0.7 วิ) · ปุ่มพวกนี้รถใช้อยู่แล้ว (เพลง/เสียง/สั่งเสียง) ผูกกับ 'กดค้าง' จะชนการทำงานเดิมน้อยกว่า · ค่าเริ่ม = ไม่ทำอะไร");
        d.setTextColor(MUTED); d.setTextSize(13);
        card.addView(d, marginTop(dp(2)));
        swcStatus = new TextView(this);
        swcStatus.setTextColor(MUTED); swcStatus.setTextSize(13);
        card.addView(swcStatus, marginTop(dp(8)));
        swcList = new LinearLayout(this);
        swcList.setOrientation(LinearLayout.VERTICAL);
        card.addView(swcList, marginTop(dp(4)));
    }

    private void renderSwc() {
        if (swcList == null) return;
        java.util.List<SwcWatch.Seen> seen = SwcWatch.seen();
        swcStatus.setText("สถานะ: " + SwcWatch.status() + " · กดปุ่มบนพวงมาลัย ชื่อปุ่มจะขึ้นสีส้ม");
        java.util.List<Integer> props = new java.util.ArrayList<Integer>();
        for (int k : SwcWatch.KNOWN) props.add(k);
        for (SwcWatch.Seen s : seen) if (!props.contains(s.prop)) props.add(s.prop);
        StringBuilder sig = new StringBuilder();
        for (int p : props) {
            SwcWatch.Seen s = find(seen, p);
            sig.append(p).append(':').append(SwcWatch.action(this, p, false)).append(SwcWatch.action(this, p, true))
               .append(s == null ? "" : (s.count + "" + s.lastLong + (System.currentTimeMillis() - s.at < 1500)));
        }
        if (sig.toString().equals(swcSig)) return;
        swcSig = sig.toString();
        swcList.removeAllViews();
        for (final int p : props) {
            SwcWatch.Seen s = find(seen, p);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView info = new TextView(this);
            boolean fresh = s != null && System.currentTimeMillis() - s.at < 1500;
            info.setText(SwcWatch.name(p) + (fresh ? (s.lastLong ? "  ● กดค้าง" : "  ● กด") : ""));
            info.setTextColor(fresh ? CLAY : CREAM); info.setTextSize(15);
            row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(swcBtn(p, false), new LinearLayout.LayoutParams(dp(210), dp(44)));
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(dp(210), dp(44));
            lp2.leftMargin = dp(8);
            row.addView(swcBtn(p, true), lp2);
            swcList.addView(row, marginTop(dp(6)));
        }
    }

    private SwcWatch.Seen find(java.util.List<SwcWatch.Seen> seen, int p) {
        for (SwcWatch.Seen s : seen) if (s.prop == p) return s;
        return null;
    }

    private TextView swcBtn(final int prop, final boolean longPress) {
        int idx = java.util.Arrays.asList(SwcWatch.ACTIONS).indexOf(SwcWatch.action(this, prop, longPress));
        if (idx < 0) idx = 0;
        final int cur = idx;
        TextView btn = new TextView(this);
        btn.setText((longPress ? "ค้าง: " : "สั้น: ") + SwcWatch.ACTION_LABELS[cur] + " ▸");
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(cur == 0 ? CREAM : BG);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cur == 0 ? 0xFF3A3B3F : CLAY);
        g.setCornerRadius(dp(10));
        btn.setBackground(g);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SwcWatch.setAction(SettingsActivity.this, prop, longPress, SwcWatch.ACTIONS[(cur + 1) % SwcWatch.ACTIONS.length]);
                swcSig = "";
                renderSwc();
            }
        });
        return btn;
    }

    @Override protected void onResume() { super.onResume(); ui.post(swcTick); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(swcTick); }

    /** การ์ดแบ่งจอ: เลือกแอพ (หน้า SplitActivity) · ชุดล่าสุด · คืนเต็มจอ */
    private void splitCard(LinearLayout root) {
        final Context app = getApplicationContext();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14);
        card.setPadding(p, p, p, p);
        root.addView(card, marginTop(dp(12)));

        TextView t = new TextView(this);
        t.setText("▦  แบ่งจอ 2–3 แอพ");   // 🪟 (Unicode 13) เป็นกล่องบน Android 9
        t.setTextColor(CREAM); t.setTextSize(17);
        card.addView(t);
        TextView d = new TextView(this);
        boolean ready = SplitScreen.freeformReady(this);
        d.setText(ready ? "เปิดหลายแอพพร้อมกัน · รองรับ CarPlay (คงสัดส่วนภาพ + ช่องล่างเลือกแอพได้)"
                : "ยังไม่ได้เปิดโหมดแบ่งจอ — แตะ \"เลือกแอพ\" เพื่อตั้งค่า (รีบูตจอ 1 ครั้ง)");
        d.setTextColor(MUTED); d.setTextSize(13);
        card.addView(d, marginTop(dp(2)));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(btns, marginTop(dp(12)));
        splitBtn(btns, "เลือกแอพ", true, new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(SettingsActivity.this, SplitActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable e) { toast("เปิดหน้าแบ่งจอไม่ได้"); }
            }
        });
        final String last = sp.getString(SplitScreen.K_LAST, "");
        if (ready && last.length() > 0) {
            splitBtn(btns, "▶ ชุดล่าสุด", false, new View.OnClickListener() {
                public void onClick(View v) {   // อ่านใหม่ตอนกด (หน้านี้อาจเปิดค้างขณะเปลี่ยนชุด)
                    java.util.List<android.content.ComponentName> set = SplitScreen.parse(sp.getString(SplitScreen.K_LAST, ""));
                    if (set.isEmpty()) { toast("ไม่มีชุดล่าสุด"); return; }
                    SplitScreen.launchSet(app, set);
                }
            });
        }
        if (ready) {
            splitBtn(btns, "⤢ คืนเต็มจอ", false, new View.OnClickListener() {
                public void onClick(View v) {
                    SplitScreen.worker().post(new Runnable() { public void run() { SplitScreen.exitAll(app); } });
                    toast("คืนเต็มจอแล้ว");
                }
            });
        }
    }

    private void splitBtn(LinearLayout parent, String text, boolean primary, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(primary ? BG : CREAM);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? CLAY : 0xFF3A3B3F);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setClickable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        lp.rightMargin = dp(10);
        parent.addView(b, lp);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- builders ----------

    private void sectionLabel(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase());
        t.setTextColor(CLAY);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = marginTop(dp(26));
        parent.addView(t, lp);
    }

    /** การ์ดฟีเจอร์รถ (แตะเปิด MainActivity) */
    private View carCard(String emoji, String label, final String route) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14); card.setPadding(p, p, p, p);
        card.setClickable(true);

        TextView ic = new TextView(this);
        ic.setText(emoji); ic.setTextSize(20);
        card.addView(ic);

        TextView t = new TextView(this);
        t.setText("  " + label); t.setTextColor(CREAM); t.setTextSize(15);
        card.addView(t);

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCar(route); }
        });
        return card;
    }

    private LinearLayout.LayoutParams cellParams(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!first) lp.leftMargin = dp(12);
        return lp;
    }

    /** แถวสวิตช์ในการ์ด; child=true จะถูกปิดใช้งานเมื่อ master ปิด */
    private Switch row(LinearLayout parent, String title, String desc, String key, boolean def, boolean child) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14);
        card.setPadding(p, p, p, p);
        parent.addView(card, marginTop(dp(12)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(texts, tlp);

        TextView t = new TextView(this);
        t.setText(title); t.setTextColor(CREAM); t.setTextSize(17);
        texts.addView(t);

        TextView d = new TextView(this);
        d.setText(desc); d.setTextColor(MUTED); d.setTextSize(13);
        texts.addView(d, marginTop(dp(2)));

        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        tintSwitch(sw);
        card.addView(sw);

        if (child) children.add(sw);
        return sw;
    }

    private Switch child(LinearLayout parent, String title, String desc, String key, boolean def) {
        return row(parent, title, desc, key, def, true);
    }

    private void infoRow(LinearLayout parent, String title, String desc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBg());
        int p = dp(14);
        card.setPadding(p, p, p, p);
        parent.addView(card, marginTop(dp(12)));

        TextView t = new TextView(this);
        t.setText("● " + title); t.setTextColor(CLAY); t.setTextSize(16);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc); d.setTextColor(MUTED); d.setTextSize(13);
        card.addView(d, marginTop(dp(2)));
    }

    private CompoundButton.OnCheckedChangeListener saver(final String key) {
        return new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                sp.edit().putBoolean(key, on).apply();
            }
        };
    }

    private void setChildrenEnabled(boolean on) {
        for (Switch s : children) {
            s.setEnabled(on);
            View card = (View) s.getParent();
            if (card != null) card.setAlpha(on ? 1f : 0.4f);
        }
    }

    // ---------- style helpers ----------
    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        return g;
    }
    private void tintSwitch(Switch sw) {
        int[][] states = { { android.R.attr.state_checked }, {} };
        int[] thumb = { CLAY, 0xFFBDC1C6 };
        int[] track = { 0x80D97757, 0x55FFFFFF };
        sw.setThumbTintList(new ColorStateList(states, thumb));
        sw.setTrackTintList(new ColorStateList(states, track));
    }
    private LinearLayout.LayoutParams marginTop(int m) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = m;
        return lp;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
