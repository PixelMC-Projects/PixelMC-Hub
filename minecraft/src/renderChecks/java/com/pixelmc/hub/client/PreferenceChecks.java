/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PreferenceChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path path=Files.createTempDirectory("hub-preferences-check-").resolve("preferences.json");
        String a="a".repeat(64),b="b".repeat(64),demo="d".repeat(64);
        Files.writeString(path,"{\"welcomeMode\":\"once\",\"reducedMotion\":true,\"seenScopes\":[\""+demo+"\"]}");
        HubPreferences prefs=new HubPreferences(path);prefs.select(a);check(prefs.welcomeMode().equals("login"),"New account must default to once per login");
        prefs.setWelcomeMode("login");prefs.setReducedMotion(false);prefs.setCompact(true);prefs.markSeen(a);prefs.save();
        prefs.select(b);check(prefs.welcomeMode().equals("login")&&!prefs.compact(),"Preferences crossed player/archive scope");
        prefs.copyScope(a,b);prefs.select(b);check(prefs.welcomeMode().equals("login")&&prefs.compact(),"Provisional canonical migration lost preferences");
        check(prefs.shouldPlay(b,false)&&!prefs.shouldPlay(b,true),"Login welcome lifecycle incorrect");
        prefs.setWelcomeMode("once");check(!prefs.shouldPlay(b,false),"Seen flag lost on mode change");
        prefs.setReducedMotion(true);check(!prefs.shouldPlay(b,false),"Reduced motion did not override welcome");
        prefs.fallbackScope(a);prefs.save();prefs.select(a);check(prefs.welcomeMode().equals("once")&&prefs.reducedMotion(),"Canonical welcome preference lost before next login metadata");
        prefs.save();String otherArchive="c".repeat(64);prefs.copyScope(a,otherArchive);prefs.select(otherArchive);check(prefs.welcomeMode().equals("login")&&!prefs.reducedMotion(),"Connection hint seeded another canonical archive");
        prefs.setWelcomeMode("always");prefs.save();prefs=new HubPreferences(path);prefs.select(otherArchive);check(prefs.welcomeMode().equals("always")&&prefs.shouldPlay(otherArchive,true),"Explicit always preference was overwritten by the new default");
        prefs.save();prefs=new HubPreferences(path);prefs.migrateLegacyDemo(demo);prefs.select(demo);
        check(prefs.welcomeMode().equals("once")&&prefs.reducedMotion()&&!prefs.shouldPlay(demo,false),"Legacy demo migration lost preferences/history");
        System.out.println("PASS: UUID/archive preference isolation, provisional migration, legacy explicit demo, once/login/always and reduced motion semantics");
    }
}
