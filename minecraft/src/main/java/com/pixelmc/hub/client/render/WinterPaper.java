package com.pixelmc.hub.client.render;

/** Printed inks and stocks shared by the winter station and its extensions. */
public record WinterPaper(boolean dark) {
    public int ink() {
        return dark ? 0xfff0e6cc : 0xff283f36;
    }

    public int muted() {
        return dark ? 0xffb2b7a5 : 0xff606e62;
    }

    public int pine() {
        return dark ? 0xffa5c7aa : 0xff345e51;
    }

    public int cover() {
        return dark ? 0xff1c3739 : 0xffe1ebe4;
    }

    public int coverBase() {
        return dark ? 0xff142b30 : 0xffd5e3d9;
    }

    public int paper() {
        return dark ? 0xff242f33 : 0xfff5f5ed;
    }

    public int sheet() {
        return dark ? 0xff303b3b : 0xfffffcf5;
    }

    public int ruling() {
        return dark ? 0xff58615a : 0xffd5dbcf;
    }

    public int mail() {
        return dark ? 0xffdb9a78 : 0xff996147;
    }

    public int mailInk() {
        return dark ? 0xffe6b196 : 0xff783c2b;
    }

    public int wool() {
        return dark ? 0xff9ab6b9 : 0xff627f78;
    }

    public int soft() {
        return dark ? 0xff293638 : 0xfff5f6ef;
    }

    public int stitch() {
        return dark ? 0xff55685e : 0xffbec9c1;
    }
}
