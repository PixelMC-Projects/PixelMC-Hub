import React, { useImperativeHandle, useLayoutEffect, useRef } from "react";
import { itemById } from "./itemCatalog.js";
import { blockDetail, blockIcon, blockKey, blockLabel, blockPart, blockSamples } from "./blockSamples.js";

const limit = 1200;

export function questionText(parts, includeIds = false) {
  return parts.map(part => {
    if (typeof part === "string") return part;
    if (part.block) return includeIds ? blockDetail(part) : blockLabel(part);
    const name = itemById.get(part.item)?.name || part.item;
    return includeIds ? name + " [" + part.item + "]" : name;
  }).join("");
}

function readParts(root) {
  const parts = [];
  function append(text) {
    if (!text) return;
    if (typeof parts.at(-1) === "string") parts[parts.length - 1] += text;
    else parts.push(text);
  }
  function visit(node) {
    if (node.nodeType === Node.TEXT_NODE) append(node.data.replaceAll("\u00a0", " "));
    else if (node.dataset?.block) {
      const sample = blockSamples.find(block => blockKey(block) === node.dataset.block);
      if (sample) parts.push(blockPart(sample));
      else append(node.textContent);
    }
    else if (itemById.has(node.dataset?.item)) parts.push({item: node.dataset.item});
    else if (node.nodeName === "BR") append("\n");
    else {
      if (node.nodeName === "DIV" && node.previousSibling) append("\n");
      node.childNodes.forEach(visit);
    }
  }
  root.childNodes.forEach(visit);
  // Chromium leaves a line break to hold the caret after deleting the last character.
  if (root.childNodes.length === 1 && root.firstChild.nodeName === "BR") return [];
  return parts;
}

function makeToken(part) {
  const item = part.block ? null : itemById.get(part.item);
  const token = document.createElement("span");
  token.contentEditable = "false";
  token.className = "wz-inline-item";
  if (part.block) {
    token.dataset.block = blockKey(part);
    token.title = blockDetail(part);
  } else {
    token.dataset.item = part.item;
    token.title = item.mod + " · " + part.item;
  }
  const icon = document.createElement("img");
  icon.src = part.block ? blockIcon(part) : item.icon;
  icon.alt = "";
  icon.draggable = false;
  token.append(icon, part.block ? blockLabel(part) : item.name);
  return token;
}

function editorRange(editor) {
  const selection = window.getSelection();
  if (selection.rangeCount && editor.contains(selection.getRangeAt(0).commonAncestorContainer)) {
    return selection.getRangeAt(0).cloneRange();
  }
  const range = document.createRange();
  range.selectNodeContents(editor);
  range.collapse(false);
  return range;
}

function adjacentItem(range, backward) {
  if (!range.collapsed) return null;
  const node = range.startContainer;
  const offset = range.startOffset;
  let neighbor;
  if (node.nodeType === Node.TEXT_NODE) {
    if (offset !== (backward ? 0 : node.length)) return null;
    neighbor = backward ? node.previousSibling : node.nextSibling;
  } else {
    neighbor = node.childNodes[backward ? offset - 1 : offset];
  }
  while (neighbor?.nodeType === Node.TEXT_NODE && !neighbor.length) {
    neighbor = backward ? neighbor.previousSibling : neighbor.nextSibling;
  }
  return neighbor?.dataset?.item || neighbor?.dataset?.block ? neighbor : null;
}

// Keep the browser's editing DOM and selection during typing and IME composition.
export default function InlineQuestion({ editorRef, parts, onChange, disabled, description, placeholder, onPicker, onSend }) {
  const root = useRef(null);
  const saved = useRef(null);
  const published = useRef(null);
  useLayoutEffect(() => {
    if (parts === published.current) return;
    root.current.replaceChildren(...parts.map(part => typeof part === "string" ? document.createTextNode(part) : makeToken(part)));
    saved.current = null;
    published.current = parts;
  }, [parts]);
  function update() {
    const next = readParts(root.current);
    published.current = next;
    onChange(next);
  }
  function spaceLeft(range) {
    const removed = questionText(readParts(range.cloneContents())).length;
    return limit - questionText(readParts(root.current)).length + removed;
  }
  useImperativeHandle(editorRef, () => ({
    focus: options => root.current.focus(options),
    remember() {
      if (root.current.contains(window.getSelection()?.anchorNode) || !saved.current) saved.current = editorRange(root.current);
    },
    restore() {
      const range = saved.current;
      root.current.focus({preventScroll: true});
      if (range && root.current.contains(range.commonAncestorContainer)) {
        window.getSelection().removeAllRanges();
        window.getSelection().addRange(range);
      }
    },
    insert(reference) {
      const editor = root.current;
      const range = saved.current || editorRange(editor);
      editor.focus({preventScroll: true});
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      const part = reference.block ? blockPart(reference) : {item: reference.id};
      if (spaceLeft(range) < questionText([part]).length) return false;
      {
        const token = makeToken(part);
        token.dataset.inserting = "";
        document.execCommand("insertHTML", false, token.outerHTML);
        const inserted = editor.querySelector("[data-inserting]");
        if (inserted) {
          delete inserted.dataset.inserting;
          range.setStartAfter(inserted);
          range.collapse(true);
          selection.removeAllRanges();
          selection.addRange(range);
        }
      }
      saved.current = null;
      return true;
    },
  }));
  return <div ref={root} role="textbox" aria-label="向问舟提问" aria-multiline="true" aria-describedby={description}
    aria-disabled={disabled} contentEditable={!disabled} suppressContentEditableWarning className="wz-inline-editor"
    data-empty={!parts.length || undefined} data-placeholder={placeholder} aria-placeholder={placeholder}
    onBlur={() => { if (root.current.contains(window.getSelection()?.anchorNode)) saved.current = editorRange(root.current); }}
    onInput={update} onBeforeInput={event => {
      if (event.data && !event.nativeEvent.isComposing && spaceLeft(editorRange(root.current)) < event.data.length) event.preventDefault();
    }}
    onPaste={event => {
      event.preventDefault();
      document.execCommand("insertText", false, event.clipboardData.getData("text/plain").slice(0, Math.max(0, spaceLeft(editorRange(root.current)))));
    }}
    onDrop={event => event.preventDefault()}
    onKeyDown={event => {
      if (event.nativeEvent.isComposing || event.keyCode === 229) return;
      if (event.key === "Backspace" || event.key === "Delete") {
        const range = editorRange(root.current);
        const item = adjacentItem(range, event.key === "Backspace");
        if (item) {
          event.preventDefault();
          range.selectNode(item);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
          document.execCommand("delete");
        }
      }
      if (event.key === "@") { event.preventDefault(); onPicker(); }
      if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); onSend(); }
    }}/>
}

