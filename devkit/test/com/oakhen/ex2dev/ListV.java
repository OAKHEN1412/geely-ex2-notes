package com.oakhen.ex2dev;
public class ListV {
    public static void main(String[] a) throws Exception {
        int i = 0;
        for (ApkSource.Version v : ApkSource.versions(a[0])) { System.out.println(v + " " + v.size); if (++i >= 6) break; }
    }
}
