import React, { useEffect, useId, useRef, useState } from "react";
import { motion } from "motion/react";
import { BoatMark } from "./Glyph.jsx";
import { agreementDocument, isAgreementLink } from './agreementDocument.js';


const readingSeconds = 30;

function useReadingTime(required) {
  const [seconds, setSeconds] = useState(required ? readingSeconds : 0);
  useEffect(() => {
    if (!required) return;
    let remaining = readingSeconds * 1000;
    let started = null;
    let timer;
    function update() {
      const now = performance.now();
      if (started !== null) remaining = Math.max(0, remaining - (now - started));
      started = document.hidden ? null : now;
      setSeconds(Math.ceil(remaining / 1000));
      clearTimeout(timer);
      if (remaining && started !== null) timer = setTimeout(update, remaining % 1000 || 1000);
    }
    update();
    document.addEventListener("visibilitychange", update);
    return () => {
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", update);
    };
  }, [required]);
  return seconds;
}

function AgreementNode({node}) {
  if (node.type === 'text') {
    let text = node.text;
    for (const mark of node.marks || []) {
      if (mark.type === 'bold') text = <strong>{text}</strong>;
      if (mark.type === 'link' && isAgreementLink(mark.attrs?.href)) {
        text = <a href={mark.attrs.href} rel="noreferrer" target="_blank">{text}</a>;
      }
    }
    return text;
  }
  if (node.type === 'hardBreak') return <br/>;
  const children = node.content?.map((child,index) => <AgreementNode key={index} node={child}/>);
  switch (node.type) {
    case 'doc': return children;
    case 'paragraph': return <p>{children}</p>;
    case 'heading': return node.attrs?.level === 3 ? <h3>{children}</h3> : <h2>{children}</h2>;
    case 'bulletList': return <ul>{children}</ul>;
    case 'listItem': return <li>{children}</li>;
    default: return null;
  }
}

function AgreementText({text}) {
  return <AgreementNode node={agreementDocument(text)}/>;
}

export default function Agreement({ policy, quotaEnabled, review, reduceMotion, onAccept, onBack, researchAllowed, onResearchChange }) {
  const heading = useRef(null);
  const headingId = useId();
  const [terms, setTerms] = useState(false);
  const [api, setApi] = useState(false);
  const [optIn, setOptIn] = useState(false);
  const seconds = useReadingTime(!review);
  const canAccept = seconds === 0 && terms && api;

  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, []);

  return <motion.section className="wz-agreement" aria-labelledby={headingId}
    initial={reduceMotion ? false : { opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>
    <div className="wz-agreement-reader wz-scroll" tabIndex={0} aria-label="协议正文" role="region">
      <article className="wz-agreement-copy">
        <header>
          <BoatMark />
          <div><h1 id={headingId} ref={heading} tabIndex={-1}>问舟内测使用协议</h1>
            <small>内测协议 · {policy.version}</small></div>
        </header>
        <p className="wz-agreement-preview">当前演示只在本浏览器保存记录。以下为正式服内测规则。</p>
        <section className="wz-agreement-abuse"><AgreementText text={policy.text.rules} /></section>
        
        <div className="wz-agreement-brief"><AgreementText text={policy.text.brief} /></div>
        
        {quotaEnabled && <section className="wz-agreement-brief"><h2>当前额度规则</h2><p>每期额度从该期首次提问的分钟起计算，七天后重置。之后再次提问时开始新的一期。额度耗尽后，已开始的回答可以继续完成，也可以手动停止。重置后可以再次提问。无限额度的玩家可正常提问。</p></section>}
        <details className="wz-agreement-details"><summary>完整说明</summary><section><AgreementText text={policy.text.details} /></section></details>
        <form className="wz-agreement-footer" onSubmit={event => {
          event.preventDefault();
          if (!review && canAccept) onAccept(optIn);
        }}>
          <div className="wz-agreement-controls">
            <div className="wz-agreement-checks">
              {!review && <><label><input type="checkbox" disabled={seconds > 0} checked={terms}
                onChange={e => setTerms(e.target.checked)} />我已阅读并同意《问舟内测使用协议》</label>
              <label><input type="checkbox" disabled={seconds > 0} checked={api}
                onChange={e => setApi(e.target.checked)} />我单独同意按上述说明向 DeepSeek 发送回答所需的信息</label></>}
              <label><input type="checkbox" disabled={seconds > 0} checked={review ? researchAllowed : optIn}
                onChange={e => review ? onResearchChange(e.target.checked) : setOptIn(e.target.checked)} />（可选）允许服主用授权期间的新问答原文改进本服问舟</label>
            </div>
            {!review && <button type="submit" disabled={!canAccept}>同意并进入预览</button>}
          </div>
          <small>{review ? "可随时取消勾选，照常使用问舟。" : <>
            {seconds > 0 && `阅读 ${seconds} 秒后可以勾选。`}勾选必选项并确认即表示同意。如不同意，可<button type="button" className="wz-decline" onClick={onBack}>返回手册</button>。
          </>}</small>
        </form>
      </article>
    </div>
  </motion.section>;
}
