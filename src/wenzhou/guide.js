import { findItems, itemById } from './itemCatalog.js';
import { demoReceipt } from './usage.js';
import { effortKey, readValue } from './managerPreview.js';
import { blockKey, blockLabel, blockPreviewReceipt } from './blockSamples.js';

// Local fixtures for the handbook's web preview. Do not send questions to a service.
// A reference names a stack within this answer. Two stacks can share a registry ID
// while carrying different SNBT, rendered icons and tooltip lines.
const vanilla = (key, name) => ({
  id: "minecraft:" + key, name, icon: "/assets/wenzhou-items/" + key + ".png",
});
const item = (key, explanation) => ({ item: key, explanation });
function efficiencyBook(level) {
  const romanLevel = ["I", "II", "III", "IV", "V"][level - 1];
  return {
    ...vanilla("enchanted_book", "附魔书"),
    snbt: '{StoredEnchantments:[{id:"minecraft:efficiency",lvl:' + level + 's}]}',
    rarity: "uncommon", glint: true, tooltip: [{ text: "效率 " + romanLevel }],
  };
}

const guides = {
  barrel: {
    name: "签筒", icon: "barrel", source: "每日签笺",
    items: { barrel: {
      id: "dailyfortunes:fortune_barrel", name: "问津签筒", icon: "/assets/fortune-barrel.png",
      tooltip: [{ text: "每日一次，普通右键问签" }, { text: "蹲下右键放置；放置后右键问签" }],
    } },
    text: ["手持", item("barrel"), "，蹲下对着方块表面按右键就能摆好。松开潜行，再对着放好的签筒按右键，即可抽签。"],
    note: "每位玩家每天可以抽一次，换一个签筒也共用今天的次数。",
  },
  bench: {
    name: "工作台", icon: "bench", source: "原版合成 · 示例",
    items: { wood: vanilla("oak_planks", "橡木木板"), bench: vanilla("crafting_table", "工作台") },
    text: ["打开背包，在四个合成格里各放一块木板，例如", item("wood", "可混用不同树种的木板"), "，就能取出", item("bench"), "。"],
    recipe: { slots: ["wood", "wood", "wood", "wood"], columns: 2, output: "bench", count: 1 },
  },
  torch: {
    name: "火把", icon: "torch", source: "原版合成 · 示例",
    items: {
      coal: vanilla("coal", "煤炭"), charcoal: vanilla("charcoal", "木炭"),
      stick: vanilla("stick", "木棍"), torch: vanilla("torch", "火把"),
    },
    text: ["在同一列里，上方放", item("coal"), "或", item("charcoal"), "，下方放", item("stick"),
      "，一次能做出 4 支", item("torch"), "。在背包里就能合成。"],
    recipe: { slots: ["coal", null, "stick", null], columns: 2, output: "torch", count: 4 },
  },
  enchant: {
    name: "附魔书", icon: "book", source: "原版铁砧 · 示例",
    items: {
      anvil: vanilla("anvil", "铁砧"),
      input: efficiencyBook(3), output: efficiencyBook(4),
    },
    text: ["以合并效率 IV 为例，在", item("anvil"), "的两个输入格中各放一本", item("input", "效率 III，两本都要有这个附魔和等级"),
      "。支付界面显示的经验等级，就能取出", item("output", "效率 IV"), "。"],
  },
  inscriber: {
    name: "AE2 压印器", icon: "command", source: "AE2 15.4.10 · 查阅演示",
    items: {},
    text: "开启分面输入时，机器顶面接上槽，底面接下槽。其余四面可以向中间槽送料，也可以抽走成品。机器横放后，接面跟着机器一起转。\n关闭分面输入时，任意一面都能送入符合配方的材料，也能抽走成品。管道只能从输出槽抽取。",
  },
};

const inscriberChecks = [
  { purpose: "核对压印器各面的槽位", report: "查到槽位是按机器自身的朝向分配的。横放会改变哪面能进料，我再核对成品能从哪面抽走。" },
  { purpose: "查看哪些槽位允许管道抽取" },
];

export const examples = [
  { icon: "barrel", text: "签筒怎么放下来？" },
  { icon: "bench", text: "工作台怎么合成？" },
  { icon: "torch", text: "做 64 支火把需要多少材料？" },
  { icon: "book", text: "效率 IV 的附魔书怎么合？" },
  { icon: "command", text: "AE2 压印器横着放，管道怎么接？" },
];

const previewTitles = {
  barrel: "签筒的摆放和使用", bench: "工作台的合成方法", torch: "合成火把需要的材料",
  enchant: "效率附魔书的合成", inscriber: "AE2 压印器的管道接法",
};

export function previewSessionTitle(row) {
  const first = row.turns[0];
  return row.title || previewTitles[first?.reply?.key] || first?.question || "";
}

// Upgrade only the shipped v1 demos. Preserve other saved replies verbatim.
const oldAnswers = {
  barrel: "把签筒拿在手上，蹲下对着方块表面按右键，就能把它摆好。想抽签时，松开潜行，再对着放好的签筒按右键。",
  bench: "打开背包，在 2 × 2 合成栏里放满木板，右边就能取出工作台。不同树种的木板也可以混用。",
  torch: "在同一列里，上方放煤炭或木炭，下方放木棍，一次能做出 4 支火把。在背包里就能合成。",
};
export function restorePreviewReply(reply) {
  if (reply && Object.hasOwn(oldAnswers, reply.key) && reply.text === oldAnswers[reply.key])
    return { key: reply.key, ...guides[reply.key] };
  return reply;
}

// The offline adapter recognizes the demo topics. A model adapter emits the same
// topic.confirm event before any tools when it detects an unrelated question.
const topicTerms = {
  inscriber: /压印器|inscriber/i,
  barrel: /签筒|抽签|问签|能抽|抽几次/,
  bench: /工作台/,
  torch: /火把/,
  enchant: /附魔书|效率|铁砧/,
};

export function previewMemory(turns) {
  const topics = new Set();
  for (const turn of turns) {
    if (turn.reply?.key && Object.hasOwn(guides, turn.reply.key)) topics.add(turn.reply.key);
  }
  return { topics, previousKey: turns.at(-1)?.reply?.key ?? null, hasReply: turns.some(turn => turn.reply) };
}

function mentionsMaterial(question, key) {
  return Object.values(guides[key].items).some(stack => question.includes(stack.name))
    || (key === "bench" && question.includes("木板"));
}

export function lookupPreview(question, previousKey, recalledKey) {
  const key = Object.keys(topicTerms).find(id => topicTerms[id].test(question))
    ?? recalledKey
    ?? (/^(那|它|这个|这个东西)/.test(question) ? previousKey : null);
  const entry = guides[key];
  if (!entry) return {
    key: null, name: "手册索引", icon: "book", source: "预览手册",
    text: "这页手册还没有收录你问的内容。可以试试首页的示例，比如问 AE2 压印器怎么接管道。",
  };
  if (key === "barrel" && /几次|次数|一天|每天/.test(question)) {
    return { key, ...entry, text: entry.note, note: null };
  }
  return { key, ...entry };
}

function pause(signal, duration) {
  return new Promise((resolve, reject) => {
    signal.throwIfAborted();
    const abort = () => { clearTimeout(timer); reject(signal.reason); };
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", abort);
      resolve();
    }, duration);
    signal.addEventListener("abort", abort, { once: true });
  });
}

// Progress and tool results are independent of the per-call usage receipts.
// A live model adapter can emit zero or more notices and any sequence of approved tools.
export async function* previewEvents(question, memory, signal, topicConfirmed = false, turnId = crypto.randomUUID(), parts = [], effort = readValue(effortKey, 'low')) {
  const { previousKey, topics } = memory;
  const mentioned = Object.keys(topicTerms).filter(key => topicTerms[key].test(question));
  const recalledKey = topics.has(previousKey) && mentionsMaterial(question, previousKey)
    ? previousKey : [...topics].reverse().find(key => mentionsMaterial(question, key));
  const switchesTopic = /^(换个(?:话题|问题)|另问一件事|另外问个问题)/.test(question)
    || (topics.size > 0 && mentioned.length > 0 && !recalledKey && !mentioned.some(key => topics.has(key)));
  if (!topicConfirmed && memory.hasReply && switchesTopic) {
    const topic = previousKey && guides[previousKey]?.name;
    yield { type: "topic.confirm", text: topic ? "这个问题和前面聊的「" + topic + "」有关吗？" : "这个问题和前面的对话有关吗？" };
    return;
  }
  // The preview can resolve only its bundled catalog and demo topics.
  const term = question.trim().match(/^(?:请问)?(.+?)(?:怎么(?:获得|获取|合成|做|弄到)|如何获得|有什么用|的用途)[？?。!！]*$/)?.[1]?.trim();
  if (term && !parts.some(p => p?.item || p?.block)) {
    const matches = findItems(term);
    const exact = matches.filter(item => item.name === term || item.id === term);
    const candidates = exact.length ? exact : matches;
    if (exact.length !== 1 && (candidates.length || !mentioned.length)) {
      yield {type: "usage", receipt: demoReceipt(turnId + "/lookup", "lookup")};
      if (candidates.length >= 1 && candidates.length <= 3) {
        yield {type: "item.choice", prompt: "你说的「" + term + "」是哪件物品？", options: candidates.map(i => i.id)};
      } else {
        yield {type: "answer", reply: {text: "请用工具箱里的「内联物品」指定你问的那件，我会接着回答。"}};
      }
      return;
    }
  }
  const blockParts = Array.isArray(parts) ? parts.filter(part => part?.block) : [];
  const blocks = [...new Map(blockParts.map(part => [blockKey(part), part])).values()];
  const receipts = blocks.map(block => ({label: blockLabel(block), ...blockPreviewReceipt(block)}));
  const reply = receipts.length ? {
    key: null, name: "世界方块", icon: "block-fields", source: "世界方块 · 演示数据",
    text: "",
  } : lookupPreview(question, previousKey, recalledKey);
  const referenced = parts.find(p => p?.item);
  if (referenced && !reply.key && !blocks.length) {
    const chosen = itemById.get(referenced.item);
    if (chosen) {
      reply.name = chosen.name;
      reply.text = "已选中 " + chosen.mod + " 的「" + chosen.name + "」。预览手册还没有收录这件物品的攻略。";
    }
  }
  const target = "「" + reply.name + "」";
  const usage = stage => ({ type: 'usage', receipt: demoReceipt(turnId + '/' + stage, stage) });
  yield usage(memory.hasReply ? 'followup' : 'lookup');
  if (!blocks.length && (referenced || reply.key || term)) {
    const subject = referenced ? itemById.get(referenced.item)?.name : (term || reply.name);
    const label = "检索记忆库 · " + subject;
    yield {type:"tool.start", id:"memory-index", icon:"book", text:label};
    await pause(signal, 280);
    yield {type:"tool.finish", id:"memory-index", text:label, detail:reply.key === "torch" ? "匹配 1 条摘要" : "没有匹配的摘要"};
  }
  const thinking = effort !== 'off';
  if (thinking) yield { type: "progress", id: "thought" };
  await pause(signal, 240);
  const reuse = reply.key && (reply.key === previousKey
    ? /^(那|它|这个)/.test(question) : topics.has(reply.key));
  if (receipts.length) {
    const answers = [];
    for (const [index, row] of receipts.entries()) {
      const listId = "list-block-fields-" + index, readId = "read-block-field-" + index;
      const list = "查看方块数据 · " + row.label;
      yield {type: "tool.start", id: listId, icon: "block-fields", text: list};
      await pause(signal, 560);
      yield {type: "tool.finish", id: listId, text: list, detail: row.list};
      if (row.read) {
        const read = "读取方块字段 · " + row.label + " / " + row.path;
        yield {type: "tool.start", id: readId, icon: "block-field", text: read};
        await pause(signal, 640);
        yield {type: "tool.finish", id: readId, text: read, detail: row.read};
      }
      answers.push(row.label + "：" + row.text);
      if (row.denied) break;
    }
    reply.text = answers.join("\n");
  } else if (!reply.key) {
    yield { type: "tool.start", id: "index", icon: "book", text: "正在翻阅手册索引" };
    await pause(signal, 620);
    yield { type: "tool.finish", id: "index", text: "已翻阅手册索引", detail: "当前示例中没有匹配条目。" };
  } else if (!reuse && reply.key === "inscriber") {
    for (const [index, step] of inscriberChecks.entries()) {
      const id = "read-command-" + index;
      yield { type: "tool.start", id, icon: "command", text: "运行命令 · " + step.purpose };
      await pause(signal, 950);
      yield { type: "tool.finish", id, text: "运行命令 · " + step.purpose };
      if (step.report) {
        yield {type:"stage.report", id:id + "-report", text:step.report};
        await pause(signal, 1450);
      }
    }
    yield usage('command');
  } else if (!reuse) {
    yield { type: "tool.start", id: "item", icon: reply.icon, text: "正在查询物品" + target };
    await pause(signal, 680);
    yield { type: "tool.finish", id: "item", text: "已查到物品" + target, detail: reply.source };
    if (reply.recipe) {
      yield usage('recipe');
      yield { type: "tool.start", id: "recipe", icon: "bench", text: "正在核对" + target + "的配方" };
      await pause(signal, 780);
      const { output, count } = reply.recipe;
      yield { type: "tool.finish", id: "recipe", text: "已核对合成配方", detail: reply.items[output].name + " ×" + count };
    }
  }
  const countMatch = reply.key === "torch" && question.match(/(\d{1,6})\s*(?:支|个|根)?\s*火把/);
  if (countMatch && Number(countMatch[1]) > 0) {
    const requested = Number(countMatch[1]);
    const batches = Math.ceil(requested / reply.recipe.count);
    yield {type:"stage.report", id:"batch-size", text:"配方里一份材料能做 " + reply.recipe.count + " 支火把。我按你要的 " + requested + " 支换算材料数量。"};
    await pause(signal, 1100);
    const purpose = "制作 " + requested + " 支火把需要的合成次数";
    yield { type: "tool.start", id: "calculate", icon: "calculator", text: "正在计算 · " + purpose };
    await pause(signal, 650);
    yield { type: "tool.finish", id: "calculate", text: "已计算 · " + purpose,
      detail: "ceil(" + requested + " / " + reply.recipe.count + ") = " + batches };
    reply.note = "至少合成 " + batches + " 次，需要 " + batches + " 根木棍和 " + batches + " 个煤炭或木炭。";
  }
  if (thinking) yield {type:"progress", id:"thought", text: "思考 · " + (reply.key === "inscriber"
    ? "区分机器朝向、输入槽和成品抽取条件" : receipts.length ? "核对所选方块的可读字段" : reply.key ? "核对" + target + "的使用条件" : "查阅手册中的相关条目")};
  yield usage('answer');
  yield { type: "answer", reply };
}
