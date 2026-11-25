import pricing from '../../minecraft/src/main/resources/assets/pixelmc_hub/wenzhou/flash-pricing.json' with { type: 'json' };

function rateAt(time) {
  const date = new Date(time + 8 * 3600_000);
  const day = date.getUTCDay();
  const hour = date.getUTCHours();
  const { offPeak, peak } = pricing.ratesNanoCnyPerToken;
  if (day === 0 || day === 6 || !pricing.peakHoursBeijing.some(([from, to]) => hour >= from && hour < to)) return offPeak;
  const holidays = pricing.holidays[date.getUTCFullYear()];
  if (!holidays) return null;
  const monthDay = date.toISOString().slice(5, 10);
  return holidays.some(([from, to]) => monthDay >= from && monthDay <= to) ? offPeak : peak;
}

const count = n => Number.isSafeInteger(n) && n >= 0;

// Snapshot a completed model call. Future price changes must not reprice saved sessions.
export function usageReceipt(id, apiUsage, time) {
  const hit = apiUsage?.prompt_cache_hit_tokens;
  const miss = apiUsage?.prompt_cache_miss_tokens;
  const output = apiUsage?.completion_tokens;
  if (![hit, miss, output].every(count)) return { id };
  const reasoning = apiUsage.completion_tokens_details?.reasoning_tokens;
  const rate = rateAt(time);
  return { id, at:time, hit, miss, output, reasoning: count(reasoning) && reasoning <= output ? reasoning : null,
    nanoYuan: rate ? hit * rate.hit + miss * rate.miss + output * rate.output : null, tariff: pricing.revision };
}

// The browser preview has no provider connection. Use explicit sample receipts only for new demo calls.
export function demoReceipt(id, stage, time = Date.now()) {
  const [hit, miss, output, reasoning] = {
    lookup: [0, 420, 36, 20], followup: [512, 96, 42, 24],
    recipe: [512, 145, 58, 38], command: [556, 210, 106, 52], answer: [640, 210, 184, 92],
  }[stage];
  return usageReceipt(id, { prompt_cache_hit_tokens: hit, prompt_cache_miss_tokens: miss,
    completion_tokens: output, completion_tokens_details: { reasoning_tokens: reasoning } }, time);
}

// Fixed USD amounts for the offline preview. This helper does not estimate provider pricing.
export function demoTerraReceipt(receipt) {
  const {id, at, hit, miss, output, reasoning} = receipt;
  return {id, at, hit, miss, output, reasoning, billing:'openai_api', nanoUsd:1_500_000};
}

export function summarizeUsage(turns, terra = false) {
  const currency = terra ? 'nanoUsd' : 'nanoYuan';
  const total = { hit: 0, miss: 0, output: 0, reasoning: 0, [currency]: 0, missing: false, known: false };
  const seen = new Set();
  for (const turn of turns) {
    if (!Array.isArray(turn.usageReceipts)) {
      if (terra === (turn.model === 'terra-api')) total.missing = true;
      continue;
    }
    for (const receipt of turn.usageReceipts) {
      if (!receipt?.id || seen.has(receipt.id) || terra !== (receipt.billing === 'openai_api')) continue;
      seen.add(receipt.id);
      if (![receipt.hit, receipt.miss, receipt.output].every(count)) { total.missing = true; continue; }
      total.known = true;
      for (const field of ['hit', 'miss', 'output']) total[field] += receipt[field];
      for (const field of ['reasoning', currency]) {
        total[field] = count(total[field]) && count(receipt[field]) ? total[field] + receipt[field] : null;
      }
    }
  }
  return total;
}

const integers = new Intl.NumberFormat('zh-CN');
const percent = new Intl.NumberFormat('zh-CN', {style:'percent', maximumFractionDigits:1});
const yuan = new Intl.NumberFormat('zh-CN', { maximumFractionDigits:3, minimumFractionDigits:3 });
const usd = new Intl.NumberFormat('zh-CN', {maximumFractionDigits:4, minimumFractionDigits:4});
export function usageDisplay(turns, terra = false) {
  const t = summarizeUsage(turns, terra);
  const unavailable = t.missing && !t.known;
  const number = n => unavailable || n === null ? '—' : integers.format(n);
  const amount = terra ? t.nanoUsd : t.nanoYuan;
  const cost = unavailable || amount === null ? '—' : terra ? '$' + usd.format(amount / 1e9) : '¥' + yuan.format(amount / 1e9);
  return {
    cost, note: !t.missing ? '' : unavailable ? '用量未记录' : '仅含已记录用量',
    metrics: [
      ['未命中输入', number(t.miss)], ['命中输入', number(t.hit)], ['输出', number(t.output)],
      ['其中思考', number(t.reasoning)], ['缓存命中率', t.hit + t.miss ? percent.format(t.hit / (t.hit + t.miss)) : '—'],
      [terra ? 'Terra 累计费用（美元）' : '累计费用', cost],
    ],
  };
}
