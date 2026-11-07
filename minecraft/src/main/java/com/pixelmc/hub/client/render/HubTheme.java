/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import java.util.HashMap;
import java.util.Map;

/** Exact aliases for the approved CSS tokens. Unknown artwork and rich glyph colors stay literal. */
public final class HubTheme {
    private HubTheme() {}
    private static final Map<Integer,Integer> FOREGROUND=new HashMap<>(),SURFACE=new HashMap<>(),BORDER=new HashMap<>();
    private static void aliases(Map<Integer,Integer> map,int target,int... sources){for(int source:sources)map.put(source&0xffffff,target);}
    static {
        aliases(FOREGROUND,0xffe4e9f5,0x26344f,0x34415f,0x354565,0x33415c,0x37415b,0x35405d,0x35415e);
        aliases(FOREGROUND,0xffaeb9d0,0x73809a,0x77819a,0x65738c,0x687491,0x677591,0x69748b,0x64718c,0x6b7690,0x6a7691,0x6d7892,0x758098);
        aliases(FOREGROUND,0xff8895b1,0x8c8fa5,0x8a93a8,0xa7adbf,0xa4aabe,0x8791a6,0xa0a5ba,0x939cb1,0x9d90b4,0xa498ba,0x8c8495);
        aliases(FOREGROUND,0xffb4a0f4,0x765bd4,0x7861b2,0x694acb,0x7c719f,0x7558d1);
        aliases(FOREGROUND,0xff8bceb4,0x25816a,0x29836a,0x318e73);
        aliases(FOREGROUND,0xffdfbd7d,0xae8132,0xbc9345,0xb29047);
        // Home identity, statistics, today's slip and recent collections.
        aliases(FOREGROUND,0xffb2c2dd,0x586987);aliases(FOREGROUND,0xffeef2ff,0x253657);
        aliases(FOREGROUND,0xffe0e7f8,0x344566);aliases(FOREGROUND,0xffa9b7d2,0x72809c,0x71809b,0x78869d);
        aliases(FOREGROUND,0xff9eb8ed,0x6b7dd6);aliases(FOREGROUND,0xffd1a9e8,0xaa75c6);
        aliases(FOREGROUND,0xffeadfc6,0x4e4f42);aliases(FOREGROUND,0xffc5baa3,0x828069);
        aliases(FOREGROUND,0xffbeb4a5,0x928675);aliases(FOREGROUND,0xffa1b9ad,0x7f8e81);
        aliases(FOREGROUND,0xffd3b46e,0xd5ae61);aliases(FOREGROUND,0xffcbd5e9,0x4c5570);
        aliases(FOREGROUND,0xff8895b1,0x8490a5,0x8993a9);aliases(FOREGROUND,0xffc6b7e8,0x5c5790);
        aliases(FOREGROUND,0xff98a7c3,0x80899f);aliases(FOREGROUND,0xff8797b7,0xa5a9c0);
        // Title catalogue, preview and controls. Provider richGlyph bypasses these aliases.
        aliases(FOREGROUND,0xff9baac4,0x7c859a,0x7b859c,0x8d97ad);
        aliases(FOREGROUND,0xffc2cce1,0x4f5a73);aliases(FOREGROUND,0xff9fd9bc,0x4c8573,0x448772);
        aliases(FOREGROUND,0xffc3addf,0x85709d);aliases(FOREGROUND,0xffc3d0e7,0x62718f);
        aliases(FOREGROUND,0xff9eacc9,0x7d87a1);aliases(FOREGROUND,0xffb0bdd2,0x748098);
        aliases(FOREGROUND,0xffbac7dc,0x6d7890);aliases(FOREGROUND,0xff94a2bd,0x8990a5);
        aliases(FOREGROUND,0xffb5c0d6,0x65718b);aliases(FOREGROUND,0xff8f9fbb,0x8a94aa);
        aliases(FOREGROUND,0xffa5afc4,0x78859b);aliases(FOREGROUND,0xffa0c9dd,0x507b8c);
        aliases(FOREGROUND,0xffdbaf8c,0xa56d40);aliases(FOREGROUND,0xffbba5ed,0x8962c8);
        aliases(FOREGROUND,0xffdfc66f,0xb58b38);aliases(FOREGROUND,0xffe5b08a,0xbb7950);
        aliases(FOREGROUND,0xffa7bce9,0x668bc0);
        aliases(FOREGROUND,0xffaeb9d0,0x78839b);aliases(FOREGROUND,0xffd5c4fc,0x6650a7);
        aliases(FOREGROUND,0xff8d9ab6,0x949caf,0x929bb1);aliases(FOREGROUND,0xffcbbbeb,0x64539e);
        aliases(FOREGROUND,0xffa79cbb,0x9a90b3);aliases(FOREGROUND,0xffb0bdd6,0x6f7a92);
        aliases(FOREGROUND,0xffdeceff,0x5844b4);aliases(FOREGROUND,0xffc0acdf,0x7a6a99);
        // Calendar and low-luminance warm paper.
        aliases(FOREGROUND,0xffc6aceb,0x67549b);aliases(FOREGROUND,0xffc8d1e5,0x505b76);
        aliases(FOREGROUND,0xff98a6bf,0x8a8fa5);aliases(FOREGROUND,0xffa3aec6,0x8c92a8);
        aliases(FOREGROUND,0xffc3a6c6,0xae92af);aliases(FOREGROUND,0xffc8d4e9,0x65728b);
        aliases(FOREGROUND,0xff6c7a97,0xacb2c2);aliases(FOREGROUND,0xffd0b6ee,0x8f70ad);
        aliases(FOREGROUND,0xffa3afc5,0x808aa0);aliases(FOREGROUND,0xffaab1c7,0x85879a);
        aliases(FOREGROUND,0xffbec5d9,0xa39bab);aliases(FOREGROUND,0xffe6d7b7,0x615b4f);
        aliases(FOREGROUND,0xffc8bca1,0x817967);aliases(FOREGROUND,0xffbda675,0xae9565);
        aliases(FOREGROUND,0xffa1b9ae,0x7f8f87);aliases(FOREGROUND,0xffd1c7e8,0x625c80);
        aliases(FOREGROUND,0xffa59dbb,0x9290aa,0x948ba6);aliases(FOREGROUND,0xffc5d0e6,0x55617b);
        aliases(FOREGROUND,0xff9eabc6,0x8d96ac);aliases(FOREGROUND,0xffb8a7d1,0x9989b2);
        // Updates article/list, feedback and reply text.
        aliases(FOREGROUND,0xffbbc8e1,0x697695);aliases(FOREGROUND,0xff9eacc7,0x8b95aa);
        aliases(FOREGROUND,0xffccb4f2,0x8064b6);aliases(FOREGROUND,0xffb1b1d0,0x8985a7);
        aliases(FOREGROUND,0xff9daac5,0x929db4,0x8791a6);aliases(FOREGROUND,0xffd1dbef,0x4b5874);
        aliases(FOREGROUND,0xffa3afc7,0x97a0b4);aliases(FOREGROUND,0xffd1bdf7,0x7258b4);
        aliases(FOREGROUND,0xffb4bfd6,0x73819a);aliases(FOREGROUND,0xffd7e0f3,0x384466);
        aliases(FOREGROUND,0xffb7c4db,0x66748e);aliases(FOREGROUND,0xffd2def3,0x435675);
        aliases(FOREGROUND,0xffbfcae0,0x5b6881);aliases(FOREGROUND,0xff9da9c2,0xa0a7b8,0x9c9bb2);
        aliases(FOREGROUND,0xffc2aedb,0x9b8caf);aliases(FOREGROUND,0xffd6b6ee,0x9c76c3);
        aliases(FOREGROUND,0xffbecbe1,0x66728d);aliases(FOREGROUND,0xffe4e9f5,0x56627d,0x596c8e);
        aliases(FOREGROUND,0xff8d9ab6,0xa0a9bc,0x99a3b9);aliases(FOREGROUND,0xff8f9cb7,0xacb3c3);
        aliases(FOREGROUND,0xffc4d0e6,0x67708a);aliases(FOREGROUND,0xff9aa7c1,0xa7adc0,0xb1b4c4,0xacb1c0,0xa6aec0,0x979cb0);
        aliases(FOREGROUND,0xffb9c5dc,0x77809a);aliases(FOREGROUND,0xffc8b4df,0x9282b0);
        aliases(FOREGROUND,0xffa49bb7,0xaca9bd);aliases(FOREGROUND,0xffbcb4d0,0x7a7490);
        aliases(FOREGROUND,0xffe0a4b5,0xa57683,0xa06c80);aliases(FOREGROUND,0xffe0a1b3,0xb86f81);
        aliases(FOREGROUND,0xffb5bfd5,0x8890a4);aliases(FOREGROUND,0xffa3b6df,0x8190b5);
        aliases(FOREGROUND,0xff9bc9b8,0x7ca394);aliases(FOREGROUND,0xffc4acd9,0xa28bb8);
        // Rank identities and row text retain independent gold/lavender/teal accents.
        aliases(FOREGROUND,0xffe1bd87,0xa77a49);aliases(FOREGROUND,0xffd5c4fc,0x7757ab);
        aliases(FOREGROUND,0xff9ed2d7,0x578c97);aliases(FOREGROUND,0xffaeb9d0,0x7a839d);
        aliases(FOREGROUND,0xff9caac4,0x8690a6,0xa2aabc);aliases(FOREGROUND,0xffcba7c1,0xa68a9d);
        aliases(FOREGROUND,0xffe1bf7f,0xad8745);aliases(FOREGROUND,0xff91ccbb,0x639b90);
        aliases(FOREGROUND,0xffb4bbe9,0x7d83b0);aliases(FOREGROUND,0xffd2ddf0,0x63708a);
        aliases(FOREGROUND,0xffb4a5cc,0xa397b3,0xa095b6);aliases(FOREGROUND,0xffc9d5e9,0x758099);
        aliases(FOREGROUND,0xffccb6ed,0x8670b6);aliases(FOREGROUND,0xffc4d1e9,0x75829b,0x5d637f);
        aliases(FOREGROUND,0xffaebdd6,0x9ca4b6);aliases(FOREGROUND,0xffaaa0bd,0xb1a8bd,0x9d94b4);
        aliases(FOREGROUND,0xff98d7b8,0x548d77);aliases(FOREGROUND,0xffe4a5b4,0xb87680);
        aliases(FOREGROUND,0xff9faec8,0x8e94a7);aliases(FOREGROUND,0xffcbb7ed,0x8a7aa7,0x8a72b3,0x7f719b,0x8a76ac);
        aliases(FOREGROUND,0xffae9fc2,0xa69bb7,0xa49aaf);aliases(FOREGROUND,0xffccb4ee,0x9b88b9);
        aliases(FOREGROUND,0xffc2aedf,0xaaa0bd);aliases(FOREGROUND,0xffccd4e8,0x7b809c);
        aliases(FOREGROUND,0xffa7b1ca,0xa0a4b6);
        aliases(FOREGROUND,0xffa0c9dd,0x537786);aliases(FOREGROUND,0xffdbaf8c,0xa36942);
        aliases(FOREGROUND,0xffbba5ed,0x7e5eb6);aliases(FOREGROUND,0xffdfc66f,0xa68322);
        aliases(FOREGROUND,0xffe5b08a,0xc17c43);aliases(FOREGROUND,0xffa7bce9,0x647dba);
        aliases(FOREGROUND,0xffaeb9d0,0x6c7891,0x63718c,0x64748b);aliases(FOREGROUND,0xff8895b1,0x7c72a6,0x708098);
        aliases(FOREGROUND,0xffe1d9ff,0x3a3590);aliases(FOREGROUND,0xffbac5dd,0x53627e,0x52627b);
        // Explicit fill aliases. No blanket luminance conversion: glints/art remain intact.
        aliases(SURFACE,0xff30384f,0xffffff,0xfdfefc,0xfcfcff,0xfbfcff);
        aliases(SURFACE,0xff252d44,0xf4f6ff,0xf5f6ff,0xf7f8fc,0xf4f3fa);
        aliases(SURFACE,0xff373343,0xfaf6ed,0xf9f5e9,0xfff7e8,0xfcfbf8);
        aliases(SURFACE,0xff343d56,0xe7e6f1,0xdce1ee,0xdce0f0,0xe6e8f1);
        aliases(SURFACE,0xff4b4467,0xf0eff8,0xf0edf8);aliases(SURFACE,0xff515e79,0xd5daea);
        aliases(SURFACE,0xff9b80d8,0x8e76d9);aliases(SURFACE,0xff202b40,0xf4f4fa);
        aliases(SURFACE,0xff20283d,0xf5f5fa);aliases(SURFACE,0xff37445e,0xe9edf6);
        aliases(SURFACE,0xff504839,0xf6e8c8);aliases(SURFACE,0xff304d4c,0xd9f0e7);aliases(SURFACE,0xff41476a,0xe4e9fb);
        aliases(SURFACE,0xffbb9859,0xead093);aliases(SURFACE,0xff6aaa98,0xb4dbcf);aliases(SURFACE,0xff9a9cd8,0xc5cdf4);
        aliases(SURFACE,0x1775c19c,0xedf7f2);aliases(SURFACE,0x15d88ea3,0xfcf1f3);
        aliases(SURFACE,0x1ab9a0e3,0xf6f5fb);aliases(SURFACE,0x1aca7695,0xfcf3f5);
        aliases(SURFACE,0x29aa8fdf,0xeee7fb);aliases(SURFACE,0x227098d5,0xe8eeff);
        aliases(SURFACE,0x22bb8ed6,0xf1e9ff);aliases(SURFACE,0x1fcdb779,0xf8f0df);
        aliases(SURFACE,0x1c75ba98,0xeff9f4);aliases(SURFACE,0x1aa182d3,0xf6f1ff);
        aliases(SURFACE,0xff35405a,0xf8f7ff);aliases(SURFACE,0xff18243c,0xf7f7fc);
        aliases(SURFACE,0xff0f1b33,0xa3b4d5);aliases(SURFACE,0xffadbed5,0xf6f8ff);
        aliases(SURFACE,0x1c79bea4,0xeffff2);aliases(SURFACE,0x24bd9c57,0xf8edda);
        aliases(BORDER,0x2bd0dfff,0xffffff);aliases(BORDER,0x29a5b7e0,0xe7e6f1,0xe4e5f1,0xd9dcec,0xe6e7f1,0xe7e5f2);
        aliases(BORDER,0x2badbfde,0xe1d9d0);aliases(BORDER,0xffc2a6e6,0x8b6bb9);
        aliases(BORDER,0x3dc7ad71,0xe9dcbf);aliases(BORDER,0x1fdcc08c,0xcdb57e);
        aliases(BORDER,0x29d3bd8a,0xc9b78d);aliases(BORDER,0x2b97d7b8,0xd2e8dc);
        aliases(BORDER,0x2be0a0b4,0xefd8df);aliases(BORDER,0x1cb3c0dd,0xecedf5,0xeceef7,0xe7eaf3);
        aliases(BORDER,0x1abfcbef,0xe8ebf5);aliases(BORDER,0x29bcc6e0,0xcfd5e6);
        aliases(BORDER,0x2bccbbeb,0xe2d7f3,0xe8e3f5);aliases(BORDER,0x30d3b7ea,0xd9cfe8);
        aliases(BORDER,0x1abdc9e8,0xedeef6);aliases(BORDER,0x30adc1e5,0xe1e5f1);
        aliases(BORDER,0x4ab89ae4,0xd9d1ec);aliases(BORDER,0x23b8c5e2,0xdbd7e7);
    }
    private static int mapped(Map<Integer,Integer> map,int color){Integer result=map.get(color&0xffffff);return result==null?color:alpha(color,result);}
    private static int alpha(int original,int mapped){return (((original>>>24)*(mapped>>>24)+127)/255<<24)|(mapped&0xffffff);}
    public static int foreground(int color){return mapped(FOREGROUND,color);}
    public static int surface(int color){return color==0x9effffff?0x5217263c:mapped(SURFACE,color);}
    public static int border(int color){return BORDER.containsKey(color&0xffffff)?mapped(BORDER,color):foreground(color);}
    public static int line(int color){return border(color);}
    public static int mix(int first,int second,float amount){float a=Math.max(0,Math.min(1,amount));int result=0;for(int shift=0;shift<=24;shift+=8)result|=Math.round(((first>>>shift)&255)*(1-a)+((second>>>shift)&255)*a)<<shift;return result;}
    public static int[] gradient(int[] colors){
        int[] result=colors.clone();
        if(colors.length==3&&(colors[0]&0xffffff)==0xd9f0ff&&(colors[1]&0xffffff)==0xe4e8ff)return new int[]{0xff2c4860,0xff353e62,0xff4a3968};
        if(colors.length==2){
            int first=colors[0]&0xffffff,last=colors[1]&0xffffff;
            if(first==0xfffefa&&last==0xfff9ef)return new int[]{0xff343541,0xff39353e};
            if(first==0xfff9ec&&last==0xfffef9)return new int[]{0xff3e3932,0xff34343b};
            if(first==0xfffdf9&&last==0xffffff)return new int[]{0xe8373541,0xd92d3044};
            if(first==0xe6eaff&&last==0xf6edff)return new int[]{0x9e35415c,0x9c443955};
            if(first==0xfcfdff&&last==0xf5f3ff)return new int[]{0xf52b364d,0xf5322e46};
            if(last==0xe8e3ff)return new int[]{mix(colors[0],0xff263349,.77f),0x99423a5c};
            if(last==0xffffff&&(colors[1]>>>24)==0xd9&&first!=0xffffff)return new int[]{mix(colors[0],0xff273148,.77f),0xe8293247};
            if(first==0xffffff&&last==0xffffff){
                int a=colors[0]>>>24,b=colors[1]>>>24;
                if(a==0xec&&b==0xab)return new int[]{0xd9303a50,0xc7283148};
                if(a==0xf0&&b==0x8f)return new int[]{0xd939445d,0xbf303a52};
                if(a==0xd6&&b==0xa6)return new int[]{0xbf344059,0xab2b344c};
                if(a==0xbd&&b==0x63)return new int[]{0xc235415a,0x942d354b};
                if(a==0xb8&&b==0x57)return new int[]{0x23a7b9e5,0x10bbb0eb};
                if(a==0xa6&&b==0x47)return new int[]{0x29d3defc,0x0ec8d2f2};
                if(a<0xc0)return new int[]{alpha(colors[0],0x60b0bce4),alpha(colors[1],0x60b0bce4)};
            }
            if(first==0xbcbbff)return new int[]{0x70665597,0x00665597};
            if(first==0xffe9bf)return new int[]{0x2197764c,0x0097764c};
            if(first==0x8b6cf1&&last==0x6d53d6)return new int[]{0xff8c72ce,0xff7054b8};
            if(first==0x987af5&&last==0x694acb)return new int[]{0xff9c81de,0xff7c60c5};
            if(first==0xbe9455&&last==0xa57e42)return new int[]{0xffa9854f,0xff876b41};
            if(first==0x168878&&last==0x69a648)return new int[]{0xff7acdbb,0xffb7cf83};
        }
        if(colors.length==3&&(colors[0]&0xffffff)==0xa97929)return new int[]{0xffdfbf7c,0xffdfb18c,0xffc9abe9};
        for(int i=0;i<result.length;i++)result[i]=surface(result[i]);return result;
    }
    /** The legacy calendar passes lighten(grade,.43); recover the authoritative grade hue. */
    public static int[] calendarGradient(int lightenedGrade){
        int grade=0xff000000;for(int shift=0;shift<=16;shift+=8){int channel=Math.round((((lightenedGrade>>>shift)&255)-255*.43f)/.57f);grade|=Math.max(0,Math.min(255,channel))<<shift;}
        return new int[]{0xff354058,0xff303d51,mix(grade,0xff30394a,.74f),mix(grade,0xff30394a,.44f)};
    }
}
