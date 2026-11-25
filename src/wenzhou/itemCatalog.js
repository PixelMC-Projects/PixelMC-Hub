const sources = { minecraft: "Minecraft", farm_and_charm: "Farm & Charm", neapolitan: "Neapolitan", crockpot: "Crock Pot" };
const samples = [
  ["farm_and_charm:strawberry", "草莓", "cao mei"],
  ["neapolitan:strawberries", "草莓", "cao mei"],
  ["minecraft:enchanted_golden_apple", "附魔金苹果", "fu mo jin ping guo", "minecraft-golden_apple"],
  ["minecraft:golden_apple", "金苹果", "jin ping guo", "minecraft-golden_apple"],
  ["crockpot:milk_bottle", "牛奶瓶", "niu nai ping"],
  ["neapolitan:milk_bottle", "牛奶瓶", "niu nai ping"],
  ["minecraft:enchanted_book", "附魔书", "fu mo shu"],
  ["minecraft:torch", "火把", "huo ba"],
  ["minecraft:coal", "煤炭", "mei tan"],
  ["minecraft:charcoal", "木炭", "mu tan"],
  ["minecraft:crafting_table", "工作台", "gong zuo tai"],
  ["minecraft:stick", "木棍", "mu gun"],
  ["minecraft:oak_planks", "橡木木板", "xiang mu mu ban"],
  ["minecraft:anvil", "铁砧", "tie zhen"],
];
const normalize = text => text.toLowerCase().replace(/[\s'’]+/g, "");
const items = samples.map(([id, name, syllables, texture]) => {
  const [namespace, path] = id.split(":");
  const mod = sources[namespace];
  const icon = texture || (namespace === "minecraft" ? path : namespace + "-" + path);
  return {
    id, name, mod, icon: "/assets/wenzhou-items/" + icon + ".png", glint: id === "minecraft:enchanted_golden_apple",
    tooltip: [{ text: mod }, { text: id }],
    keywords: [name, syllables, syllables.split(" ").map(word => word[0]).join(""), id, path, mod].map(normalize),
  };
});
export const itemById = new Map(items.map(item => [item.id, item]));
export function findItems(query) {
  const key = normalize(query);
  if (!key) return items;
  return items.map(item => ({
    item, rank: Math.min(...item.keywords.map(word => word === key ? 0 : word.startsWith(key) ? 1 : word.includes(key) ? 2 : Infinity)),
  })).filter(hit => Number.isFinite(hit.rank)).sort((a, b) => a.rank - b.rank).map(hit => hit.item);
}
