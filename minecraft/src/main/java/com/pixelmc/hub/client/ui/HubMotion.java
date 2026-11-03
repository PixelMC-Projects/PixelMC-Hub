/*
 * Motion formulas adapted from src/vendor/react-bits/GlassIcons.jsx and TiltedCard.jsx.
 * Copyright (c) 2026 David Haz. MIT + Commons Clause License Condition v1.0.
 * This port is part of PixelMC-Hub; it is not offered as a standalone component.
 * The complete retained license is src/vendor/react-bits/LICENSE.md and the
 * packaged licenses/React-Bits-LICENSE.md. Do not relicense this file as Apache.
 */
package com.pixelmc.hub.client.ui;

/** Direct numerical translation of the web reference's springs and CSS easing. */
final class HubMotion {
    private HubMotion() {}
    static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    static float smoother(float x){x=clamp(x,0,1);return x*x*x*(x*(x*6-15)+10);}
    static float bezier(float x,float x1,float y1,float x2,float y2) {
        if(x<=0)return 0;if(x>=1)return 1;
        float low=0,high=1,t=x;
        for(int i=0;i<18;i++){t=(low+high)*.5f;float s=1-t;float px=3*s*s*t*x1+3*s*t*t*x2+t*t*t;if(px<x)low=t;else high=t;}
        float s=1-t;return 3*s*s*t*y1+3*s*t*t*y2+t*t*t;
    }
    static float ease(float x){return bezier(x,.25f,.1f,.25f,1);}
    static float easeOut(float x){return bezier(x,0,0,.58f,1);}

    static final class Spring {
        float value,velocity;
        Spring(){}Spring(float start){value=start;}
        void drive(float target,float stiffness,float damping,float dt) {
            int steps=Math.max(1,(int)Math.ceil(dt*240));float h=dt/steps;
            for(int i=0;i<steps;i++){velocity+=(stiffness*(target-value)-damping*velocity)*h;value+=velocity*h;}
            if(Math.abs(target-value)<.0005f&&Math.abs(velocity)<.001f){value=target;velocity=0;}
        }
        void tilt(float target,float omega,float zeta,float dt) {
            drive(target,omega*omega,2*zeta*omega,dt);
            if(Math.abs(target-value)<.001f&&Math.abs(velocity)<.01f){value=target;velocity=0;}
        }
        void snap(float value){this.value=value;velocity=0;}
    }
    static final class Navigation {
        final Spring hover=new Spring(),press=new Spring(),x=new Spring(),y=new Spring(),enter=new Spring();
        void update(float dt,boolean reduced,boolean hovered,boolean pressed,float nx,float ny,boolean entered) {
            dt=Math.min(1f/30,dt);
            if(reduced){hover.snap(0);press.snap(0);x.snap(0);y.snap(0);enter.snap(1);return;}
            hover.drive(hovered?1:0,170,24,dt);press.drive(pressed?1:0,420,40,dt);
            x.drive(hovered?nx:0,90,19,dt);y.drive(hovered?ny:0,90,19,dt);enter.drive(entered?1:0,60,15.5f,dt);
        }
    }
    static final class Tilt {
        final Spring rx=new Spring(),ry=new Spring(),ty=new Spring(),lift=new Spring(),press=new Spring();
        float opacity=1;
        Tilt(boolean intro){if(intro){rx.value=16;ty.value=22;opacity=0;}}
        void update(float dt,boolean reduced,boolean hovering,boolean focused,boolean pressed,float nx,float ny,float amplitude) {
            dt=Math.min(.05f,dt);
            if(reduced){rx.snap(0);ry.snap(0);ty.snap(0);lift.snap(0);press.snap(0);opacity=1;return;}
            float omega=20*(float)Math.pow(.2,.6),zeta=1-.3f*.6f;
            rx.tilt(hovering?-ny*amplitude:0,omega,zeta,dt);ry.tilt(hovering?nx*amplitude:0,omega,zeta,dt);
            ty.tilt(0,omega*.8f,.9f,dt);lift.tilt(hovering||focused?1:0,omega,Math.max(zeta,.85f),dt);
            press.tilt(pressed?1:0,26,.8f,dt);opacity=Math.min(1,opacity+dt/.45f);
        }
        float scale(float hoverScale){return 1+(hoverScale-1)*lift.value-.02f*press.value;}
        float shown(){return opacity<1?smoother(opacity):1;}
        float glare(float amount,float amplitude){return amount*Math.max(lift.value,Math.min(1,(float)Math.hypot(rx.value,ry.value)/Math.max(1,amplitude)))*shown();}
    }
    static final class Transition {
        float from,value,target,reversingFrom,shortening=1;long started,runDuration;boolean initialized;
        float sample(float next,long now,long duration,boolean reduced) {
            if(!initialized){initialized=true;target=next;from=value=reversingFrom=0;started=now;runDuration=duration;}
            if(runDuration<=0&&duration>0)runDuration=duration;
            float current=runDuration<=0?1:clamp((float)(now-started)/runDuration,0,1),timing=ease(current);
            value=from+(target-from)*timing;
            if(next!=target){
                // CSS Transitions 1, starting/reversing transitions: returning to
                // the reversing-adjusted start shortens the new duration.
                if(next==reversingFrom){shortening=clamp(Math.abs(timing*shortening+1-shortening),0,1);reversingFrom=target;}
                else {shortening=1;reversingFrom=value;}
                from=value;target=next;started=now;runDuration=Math.round(duration*shortening);
            }
            if(reduced){from=value=target;reversingFrom=target;shortening=1;started=now;runDuration=0;}
            return value;
        }
    }
}
