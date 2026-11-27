const sources = {
  minecraft: "Minecraft",
  ultramarine: "Ultramarine",
  farm_and_charm: "Farm & Charm",
  neapolitan: "Neapolitan",
  crockpot: "Crock Pot",
};
const samples = [
  ["ultramarine:chisel_table", "雕刻桌", "diao ke zhuo", "ultramarine-chisel_table.svg", false],
  ["farm_and_charm:strawberry", "草莓", "cao mei", null, false],
  ["neapolitan:strawberries", "草莓", "cao mei", null, false],
  ["minecraft:enchanted_golden_apple", "附魔金苹果", "fu mo jin ping guo", "minecraft-golden_apple", false],
  ["minecraft:golden_apple", "金苹果", "jin ping guo", "minecraft-golden_apple", true],
  ["crockpot:milk_bottle", "牛奶瓶", "niu nai ping", null, true],
  ["neapolitan:milk_bottle", "牛奶瓶", "niu nai ping", null, true],
  ["minecraft:enchanted_book", "附魔书", "fu mo shu", null, false],
  ["minecraft:torch", "火把", "huo ba", null, true],
  ["minecraft:coal", "煤炭", "mei tan", null, false],
  ["minecraft:charcoal", "木炭", "mu tan", null, true],
  ["minecraft:crafting_table", "工作台", "gong zuo tai", null, true],
  ["minecraft:stick", "木棍", "mu gun", null, true],
  ["minecraft:oak_planks", "橡木木板", "xiang mu mu ban", null, true],
  ["minecraft:anvil", "铁砧", "tie zhen", null, true],
];
const normalize = text => text.toLowerCase().replace(/[\s'’]+/g, "");
export const catalogItems = samples.map(([id, name, syllables, texture, hasJeiRecipe = true]) => {
  const [namespace, path] = id.split(":");
  const mod = sources[namespace];
  const rawIcon = texture || (namespace === "minecraft" ? path : namespace + "-" + path);
  const iconFile = /\.(png|svg|webp)$/i.test(rawIcon) ? rawIcon : rawIcon + ".png";
  return {
    id, name, mod, hasJeiRecipe,
    icon: "/assets/wenzhou-items/" + iconFile,
    glint: id === "minecraft:enchanted_golden_apple",
    tooltip: [{ text: mod }, { text: id }],
    keywords: [name, syllables, syllables.split(" ").map(word => word[0]).join(""), id, path, mod].map(normalize),
  };
});
const items = catalogItems;
export const itemById = new Map(items.map(item => [item.id, item]));
export function findItems(query) {
  const key = normalize(query);
  if (!key) return items;
  return items.map(item => ({
    item, rank: Math.min(...item.keywords.map(word => word === key ? 0 : word.startsWith(key) ? 1 : word.includes(key) ? 2 : Infinity)),
  })).filter(hit => Number.isFinite(hit.rank)).sort((a, b) => a.rank - b.rank).map(hit => hit.item);
}
