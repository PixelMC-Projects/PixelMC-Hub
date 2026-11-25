import React from "react";
import { restorePreviewReply } from "./guide.js";
import { ItemText, ItemReference } from "./ItemReference.jsx";
import { Glyph } from "./Glyph.jsx";
import AnswerReveal from "./AnswerReveal.jsx";

export default function Answer({ reply: saved, appear, onGrow }) {
  const reply = restorePreviewReply(saved);
  const { recipe, items } = reply;
  return <AnswerReveal play={appear} onGrow={onGrow}>
    <p><ItemText text={reply.text} items={items} /></p>
    {recipe && <div className="wz-recipe" role="group" aria-label="合成配方">
      <span className="wz-grid" style={{ gridTemplateColumns: "repeat(" + recipe.columns + ", 1fr)" }}>
        {recipe.slots.map((slot, i) => <span key={i}>
          {slot && <ItemReference stack={items[slot]} iconOnly />}
        </span>)}
      </span>
      <Glyph name="chevron" size={15} />
      <span className="wz-recipe-output"><ItemReference stack={items[recipe.output]} count={recipe.count} /></span>
    </div>}
    {reply.note && <p className="wz-note"><ItemText text={reply.note} items={items} /></p>}
  </AnswerReveal>;
}
