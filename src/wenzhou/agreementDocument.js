import { documentText } from '../changelog/richDocument.js';

export function isAgreementLink(href) {
  if (typeof href !== 'string' || !/^https:\/\//i.test(href)) return false;
  try { return Boolean(new URL(href).hostname); } catch { return false; }
}

function legacyInline(line) {
  return line.split(/(\*\*[^*]+\*\*|\[[^\]]+\]\(https:\/\/[^)\s]+\))/g).filter(Boolean).map(part => {
    const link = /^\[([^\]]+)\]\((https:\/\/[^)\s]+)\)$/.exec(part);
    if (link && isAgreementLink(link[2])) {
      return {type:'text', text:link[1], marks:[{type:'link', attrs:{href:link[2]}}]};
    }
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return {type:'text', text:part.slice(2,-2), marks:[{type:'bold'}]};
    }
    return {type:'text', text:part};
  });
}

export function agreementDocument(value) {
  if (value?.type === 'doc' && Array.isArray(value.content)) return value;
  const content = [];
  for (const raw of (typeof value === 'string' ? value : '').split('\n')) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith('- ')) {
      let list = content.at(-1);
      if (list?.type !== 'bulletList') {
        list = {type:'bulletList', content:[]};
        content.push(list);
      }
      list.content.push({type:'listItem', content:[{type:'paragraph', content:legacyInline(line.slice(2))}]});
    } else if (line.startsWith('# ') || line.startsWith('## ')) {
      const level = line.startsWith('## ') ? 3 : 2;
      content.push({type:'heading', attrs:{level}, content:legacyInline(line.slice(level))});
    } else {
      content.push({type:'paragraph', content:legacyInline(line)});
    }
  }
  return {type:'doc', content:content.length ? content : [{type:'paragraph'}]};
}

export function validAgreementField(value) {
  if (typeof value === 'string') return Boolean(value.trim()) && value.length <= 20000;
  if (value?.type !== 'doc' || !Array.isArray(value.content)) return false;
  const text = documentText(value);
  return Boolean(text.trim()) && text.length <= 20000;
}
