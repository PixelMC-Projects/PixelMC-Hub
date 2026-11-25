// Positions in the Web selection scene. The native picker will supply the hit block and dimension.
const chest = {block: "minecraft:chest", name: "箱子", icon: "/assets/collection-chest.png"};
const table = {block: "minecraft:crafting_table", name: "工作台", icon: "/assets/wenzhou-items/crafting_table.png"};
export const blockSamples = [
  {...chest, pos: [128, 64, -32]},
  {...table, pos: [129, 64, -32]},
  {...chest, pos: [130, 64, -32]},
].map(sample => ({...sample, dimension: "minecraft:overworld"}));

export function blockKey(ref) {
  return [ref.dimension, ref.block, ...ref.pos].join("|");
}

export function blockPart({block, dimension, pos}) {
  return {block, dimension, pos};
}

export function blockLabel(ref) {
  const name = blockSamples.find(sample => sample.block === ref.block)?.name ?? ref.block;
  return name + " (" + ref.pos.join(", ") + ")";
}

export function blockIcon(ref) {
  return blockSamples.find(sample => sample.block === ref.block)?.icon;
}

export function blockDetail(ref) {
  return [blockLabel(ref), ref.dimension, ref.block].join(" · ");
}

// The preview deliberately shows only fixture receipts. Its selector does not inspect a running world.
export function blockPreviewReceipt(ref) {
  const sample = blockSamples.find(row => blockKey(row) === blockKey(ref));
  if (!sample) return { list: "所选方块或字段暂不可用", text: "此位置没有方块预览数据。" };
  if (sample.block === "minecraft:crafting_table") {
    return { list: "共 2 项字段", path: "方块状态", read: "已读取 1 项字段",
      text: "示例中的工作台只有方块状态，没有可读的物品栏。" };
  }
  if (sample.pos[0] === 130) {
    return { list: "共 2 项字段", path: "物品栏", read: "查阅已停止", denied: true,
      text: "这处箱子的物品栏我没读到，里面有什么我暂时没法确认。" };
  }
  return { list: "共 4 项字段", path: "物品栏", read: "共 2 个条目",
    text: "示例中的箱子物品栏内有 16 块橡木木板和 2 个铁锭。" };
}
