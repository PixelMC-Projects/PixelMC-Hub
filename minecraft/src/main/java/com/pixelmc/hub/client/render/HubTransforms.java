/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.Matrix33;

/** CSS transform list: translate(tx,ty) perspective(d) rotateX(rx) rotateY(ry) rotateZ(rz) scale(s). */
final class HubTransforms {
    private HubTransforms() {}
    static Matrix33 perspective(float cx, float cy, float distance, float rx, float ry, float rz,
                                float scale, float tx, float ty) {
        double ax = Math.toRadians(rx), ay = Math.toRadians(ry), az = Math.toRadians(rz);
        double sx = Math.sin(ax), cxr = Math.cos(ax), sy = Math.sin(ay), cyr = Math.cos(ay);
        double sz = Math.sin(az), cz = Math.cos(az);
        // Restrict the CSS 4x4 product to z=0. Retain homogeneous w for the true perspective divide.
        double a = scale * cyr * cz, b = -scale * cyr * sz;
        double c = scale * (cxr * sz + sx * sy * cz), d = scale * (cxr * cz - sx * sy * sz);
        double zx = scale * (sx * sz - cxr * sy * cz), zy = scale * (sx * cz + cxr * sy * sz);
        double p = distance > 0 ? -zx / distance : 0, q = distance > 0 ? -zy / distance : 0;
        double ox = cx + tx, oy = cy + ty;
        return new Matrix33((float)(a + ox * p), (float)(b + ox * q), (float)(ox - (a + ox * p) * cx - (b + ox * q) * cy),
            (float)(c + oy * p), (float)(d + oy * q), (float)(oy - (c + oy * p) * cx - (d + oy * q) * cy),
            (float)p, (float)q, (float)(1 - p * cx - q * cy));
    }
    static float[] inverse(Matrix33 matrix, float x, float y) {
        float[] m = matrix.getMat();
        double a=m[0], b=m[1], c=m[2], d=m[3], e=m[4], f=m[5], g=m[6], h=m[7], i=m[8];
        double aa=e*i-f*h, bb=c*h-b*i, cc=b*f-c*e;
        double dd=f*g-d*i, ee=a*i-c*g, ff=c*d-a*f;
        double gg=d*h-e*g, hh=b*g-a*h, ii=a*e-b*d;
        double w=gg*x+hh*y+ii;
        if (Math.abs(a*aa+b*dd+c*gg)<1e-12 || Math.abs(w)<1e-9) return new float[]{Float.NaN, Float.NaN};
        return new float[]{(float)((aa*x+bb*y+cc)/w), (float)((dd*x+ee*y+ff)/w)};
    }
}
