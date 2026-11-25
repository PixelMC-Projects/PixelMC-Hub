import React, {useLayoutEffect, useRef, useState} from "react";
import {BoatMark, Glyph} from "./Glyph.jsx";
import "./firstGuide.css";

const steps = [
  {title:"把问题写在这里", body:"写下想弄清的事，按 Enter 或点击箭头发送。Shift + Enter 可以换行。", targets:[".wz-compose"]},
  {title:"这段对话花了多少？", body:"金额会随整段对话累加。把鼠标放上去，就能看到输入、输出和缓存命中的明细。", targets:[".wz-cost",".wz-usage-tip"]},
  {title:"把要问的东西带上", body:"用「内联物品」插入物品，或选「世界方块」引用眼前的方块和坐标。一次选一块，一条消息可以放好几块。", targets:[".wz-tool-trigger",".wz-reference-menu"]},
  {title:"接着上次的话聊", body:"再打开问舟，会先回到生成中的对话，其次是最近的未读对话。历史里的圆点标出还没看的回答，点开就能接着聊。", targets:['[data-guide-target="history"]']},
  {title:"换了话题，就开新对话", body:"相关的问题留在一起。问另一件事时，请先点「新对话」，避免旧上下文干扰回答、增加费用。原来的对话仍在历史里。", targets:['[data-guide-target="new"]']},
  {title:"把回答分享给大家", body:"回答完成后，点「分享」预览，再发到游戏聊天栏。分享只包含当时已完成的问答，后续追问不会加进去。", targets:["#wz-share-open-preview"]},
  {title:"找回别人分享的回答", body:"点聊天里的对话标题就能看只读快照。点开过的会保存在「收到的分享」，可以置顶或删除。没有问舟权限也能从聊天查看。", targets:["#wz-share-open-inbox"]},
  {title:"选择怎样收到提醒", body:"空白对话左下角的齿轮可以分别开关两种提醒。离开手册后，点聊天里的完成提醒就能回到原对话。", targets:[".wz-reminder-trigger",".wz-reminder-panel"]},
];
const modelLesson = {title:"选择模型与思考强度", body:"通过发送按钮旁的模型选择器，可以切换模型并调整思考强度。", targets:[".wz-model-trigger"]};

const clamp = (n,min,max) => Math.max(min,Math.min(max,n));
function locate(layer,card,selectors) {
  const page=layer.parentElement, bounds=layer.getBoundingClientRect();
  const width=layer.clientWidth, height=layer.clientHeight;
  if (!bounds.width || !bounds.height) return null;
  const rx=width/bounds.width, ry=height/bounds.height;
  const rects=selectors.map(s=>page.querySelector(s)?.getBoundingClientRect()).filter(Boolean);
  if (!rects.length) return null;
  const left=clamp((Math.min(...rects.map(r=>r.left))-bounds.left)*rx-6,2,width-2);
  const top=clamp((Math.min(...rects.map(r=>r.top))-bounds.top)*ry-6,2,height-2);
  const right=clamp((Math.max(...rects.map(r=>r.right))-bounds.left)*rx+6,left,width-2);
  const bottom=clamp((Math.max(...rects.map(r=>r.bottom))-bounds.top)*ry+6,top,height-2);
  const cw=card.offsetWidth,ch=card.offsetHeight,gap=19;
  const cx=(left+right-cw)/2,cy=(top+bottom-ch)/2;
  const candidates=[[cx,bottom+gap],[cx,top-gap-ch],[left-gap-cw,cy],[right+gap,cy]];
  const ranked=candidates.map(([x,y])=>{
    const px=clamp(x,10,width-cw-10),py=clamp(y,10,height-ch-10);
    const overlap=Math.max(0,Math.min(px+cw,right)-Math.max(px,left))*Math.max(0,Math.min(py+ch,bottom)-Math.max(py,top));
    return {x:px,y:py,score:overlap*1000+Math.abs(px-x)+Math.abs(py-y)};
  }).sort((a,b)=>a.score-b.score);
  return {left,top,width:right-left,height:bottom-top,x:ranked[0].x,y:ranked[0].y};
}

export default function FirstGuide({step,setStep,onClose,leaving,onExited,reduceMotion,chatGptSelected}) {
  const layer=useRef(null), card=useRef(null), heading=useRef(null);
  const [box,setBox]=useState(null);
  const lesson=chatGptSelected && step === 2 ? modelLesson : steps[step-1];
  useLayoutEffect(()=>{
    heading.current?.focus({preventScroll:true});
    if (!lesson || leaving) return;
    const element=layer.current;
    const measure=()=>setBox(locate(element,card.current,lesson.targets));
    measure();
    const observer=new ResizeObserver(measure);
    observer.observe(element); observer.observe(card.current);
    lesson.targets.forEach(s=>{const node=element.parentElement.querySelector(s); if(node) observer.observe(node);});
    // Layout motion on the dock can finish without resizing its contents.
    const settle=setTimeout(measure,350);
    return ()=>{observer.disconnect();clearTimeout(settle);};
  },[lesson, leaving]);
  useLayoutEffect(() => {
    if (!leaving) return;
    if (lesson) layer.current.getAnimations({subtree:true}).forEach(animation => animation.pause());
    const fade = layer.current.animate([{opacity:1}, {opacity:0}], {
      duration: reduceMotion ? 0 : 320, easing:"cubic-bezier(.25,.1,.25,1)", fill:"forwards"
    });
    fade.onfinish = onExited;
    return () => { fade.onfinish = null; fade.cancel(); };
  }, [leaving, reduceMotion]);

  function keys(event){
    if (leaving) { event.preventDefault(); event.stopPropagation(); return; }
    if(event.key==="Escape"){event.preventDefault();event.stopPropagation();onClose();return;}
    if(event.key!=="Tab") return;
    const buttons=[...layer.current.querySelectorAll("button:not(:disabled)")];
    if(event.shiftKey && [heading.current,buttons[0]].includes(document.activeElement)){
      event.preventDefault();buttons.at(-1)?.focus();
    } else if(!event.shiftKey && document.activeElement===buttons.at(-1)){
      event.preventDefault();buttons[0]?.focus();
    }
  }
  return <div ref={layer} role="dialog" aria-modal="true" aria-label={lesson?"问舟使用教程":"欢迎使用问舟"}
    onClickCapture={event => { if (leaving) event.stopPropagation(); }}
    onKeyDown={keys} className={"wz-first-guide "+(lesson?"is-tour":"is-welcome")}>
    {!lesson ? <div className="wz-guide-welcome">
      <div className="wz-guide-emblem"><BoatMark/>
        <svg viewBox="0 0 160 22" fill="none" aria-hidden="true"><path pathLength="1" d="M6 13c24-13 45 7 70 1s45-10 77-4"/></svg>
      </div>
      <h1 ref={heading} tabIndex="-1">问舟</h1>
      <p className="wz-welcome-intro">玩法有疑问？我可以帮你查模组代码和整合包改动。</p>
      <div className="wz-welcome-actions"><button onClick={onClose}>直接体验</button><button className="wz-guide-primary" onClick={()=>setStep(1)}>使用教程</button></div>
    </div> : <>
      {box && <div className="wz-guide-cutout" aria-hidden="true" style={{left:box.left,top:box.top,width:box.width,height:box.height}}/>}
      <div ref={card} className="wz-guide-card" style={{left:box?.x ?? 0,top:box?.y ?? 0,visibility:box?"visible":"hidden"}}>
        <div key={step} className="wz-lesson-copy"><div className="wz-guide-caption"><Glyph name="boat" size={18}/><span>使用教程</span><small>{step} / {steps.length}</small></div>
          <h2 ref={heading} tabIndex="-1">{lesson.title}</h2><p>{lesson.body}</p></div>
        <div className="wz-guide-controls"><button onClick={onClose}>退出教程</button><span/>
          {step>1 && <button onClick={()=>setStep(step-1)}>上一步</button>}
          <button className="wz-guide-primary" onClick={step===steps.length?onClose:()=>setStep(step+1)}>{step===steps.length?"开始使用":"下一步"}</button>
        </div>
      </div>
    </>}
  </div>;
}
