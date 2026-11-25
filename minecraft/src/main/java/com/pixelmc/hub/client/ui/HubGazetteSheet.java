/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;

/** The stocks, rules and printed plates used by WinterGazette. */
record HubGazetteSheet(boolean dark) {
    int stock() {
        return dark ? 0xff303a35 : 0xfffaf6e9;
    }

    int index() {
        return dark ? 0xff28352f : 0xffe9e4cf;
    }

    int ink() {
        return dark ? 0xfff2e8cf : 0xff293e35;
    }

    int body() {
        return dark ? 0xffdedecb : 0xff394b41;
    }

    int muted() {
        return dark ? 0xffb9c4ae : 0xff586854;
    }

    int rule() {
        return dark ? 0xff687564 : 0xffa6ad94;
    }

    int red() {
        return dark ? 0xffe7a183 : 0xff974b36;
    }

    int active() {
        return dark ? 0xff3f4d3f : 0xfff8f1dc;
    }

    void spread(
            HubDraw d,
            float x,
            float y,
            float width,
            float height,
            float leftWidth,
            float plateHeight) {
        d.rect(x, y, width, height, stock());
        d.rect(x, y, leftWidth, height, index());
        HubWinterSheet.grain(d, x, y, width, height, 0, 1);
        String plate = "images/winter/gazette-press.png";
        var image = d.imageResource(plate);
        float scale = Math.max(leftWidth / image.getWidth(), plateHeight / image.getHeight());
        float w = image.getWidth() * scale, h = image.getHeight() * scale;
        d.save();
        d.clipRoundRect(x, y, leftWidth, plateHeight, 0);
        float plateX = x + (leftWidth - w) * .5f, plateY = y + (plateHeight - h) * .6f;
        if (dark) HubCollectionArt.filtered(d, "gazette-press", plateX, plateY, w, h, .76f, .77f);
        else d.image(plate, plateX, plateY, w, h, 1);
        d.restore();
        float bandY = y + plateHeight;
        int bandInk = dark ? 0xffe8dec3 : 0xfffcf1d5;
        d.rect(x, bandY, leftWidth, 34.25f, dark ? 0xff233e34 : 0xff3d5748);
        d.textSerif("刊期目录", x + 17, bandY + 23, 15, bandInk, 650);
        int bandRule = HubWinterSheet.alpha(bandInk, .42f);
        d.line(x + 98, bandY + 16, x + leftWidth - 17, bandY + 16, 1, bandRule);
        d.line(x + 98, bandY + 19, x + leftWidth - 17, bandY + 19, 1, bandRule);
        d.line(x + leftWidth - .5f, y, x + leftWidth - .5f, y + height, 1, rule());
    }

    void mast(HubDraw d, float x, float y, float w, String date) {
        d.textTracked("驿站公报", x, y + 42, 20, ink(), 800, 3.8f, true);
        d.textSerif(date, x + w - d.textWidthSerif(date, 11, 500), y + 41, 11, muted(), 500);
        d.rect(x, y + 55.4f, w, 1, ink());
        d.rect(x, y + 58.4f, w, 1, ink());
    }
}
