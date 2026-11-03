import fortuneBook from "./fortunes.json";

export const TODAY = new Date(2026, 10, 3);
export const todayKey = "2026-11-03";
export const player = {
  name: "zhan_cpu",
  uuid: "7f1a0000-0000-4000-8000-000000000001",
  joined: "2026/10/17",
  lastLogin: "2026/11/02 20:26:14",
  hours: 128,
  minutes: 42,
  logins: 36,
};
export const titles = [
  {
    id: "dew",
    name: "朝露行者",
    style: "dew",
    icon: "leaf",
    category: "旅途纪念",
    rarity: "珍藏",
    owned: true,
    date: "2026/11/02",
    description: "晨露未晞，旅途初明。送给认真走过每一段旅程的你。",
    condition: "在香草纪元累计游玩 100 小时。",
  },
  {
    id: "mountain",
    name: "山野来客",
    style: "mountain",
    icon: "mountain",
    category: "初来乍到",
    rarity: "普通",
    owned: true,
    date: "2026/10/17",
    description: "山川不问来处，风与草木皆是新朋友。",
    condition: "第一次踏入香草纪元。",
  },
  {
    id: "star",
    name: "拾星之人",
    style: "star",
    icon: "star",
    category: "活动纪念",
    rarity: "稀有",
    owned: true,
    date: "2026/10/30",
    description: "把偶然遇见的微光，收成属于自己的一片星河。",
    condition: "参与「星夜漫游」服务器活动。",
  },
  {
    id: "wood",
    name: "林间筑梦",
    style: "wood",
    icon: "house",
    category: "旅途纪念",
    rarity: "稀有",
    owned: true,
    date: "2026/10/26",
    description: "一木一石，筑起日常；一窗一灯，等候归人。",
    condition: "参与服务器公共建筑共建。",
  },
  {
    id: "moon",
    name: "月下听风",
    style: "moon",
    icon: "moon",
    category: "活动纪念",
    rarity: "珍藏",
    owned: true,
    date: "2026/10/24",
    description: "留一盏灯，听一夜风，也把此刻留在旅途中。",
    condition: "参与「秋夜相聚」服务器活动。",
  },
  {
    id: "harvest",
    name: "丰收时节",
    style: "harvest",
    icon: "wheat",
    category: "旅途纪念",
    rarity: "普通",
    owned: true,
    date: "2026/10/21",
    description: "播种的日子已有回响，满怀收获，赴下一程。",
    condition: "完成第一季社区农场收获。",
  },
  {
    id: "dawn",
    name: "曙光同行",
    style: "dawn",
    icon: "sun",
    category: "旅途纪念",
    rarity: "珍藏",
    owned: false,
    description: "长路漫漫，总有人与你一同迎接曙光。",
    condition: "在香草纪元累计游玩 300 小时。",
  },
  {
    id: "cloud",
    name: "云外归客",
    style: "cloud",
    icon: "cloud",
    category: "活动纪念",
    rarity: "稀有",
    owned: false,
    description: "行过远山与流云，仍记得回家的路。",
    condition: "参与后续开放的探索活动。",
  },
];
export const fortuneById = (id) =>
  fortuneBook.fortunes.find((f) => f.id === id) || fortuneBook.fortunes[0];
export const dateKey = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
export const archive = Object.fromEntries(
  [
    ["2026-10-17", 10],
    ["2026-10-18", 22],
    ["2026-10-19", 8],
    ["2026-10-20", 32],
    ["2026-10-21", 4],
    ["2026-10-22", 18],
    ["2026-10-24", 26],
    ["2026-10-25", 2],
    ["2026-10-26", 12],
    ["2026-10-27", 34],
    ["2026-10-28", 6],
    ["2026-10-29", 15],
    ["2026-10-30", 3],
    ["2026-10-31", 24],
    ["2026-11-01", 4],
    ["2026-11-02", 17],
    ["2026-11-03", 1],
  ].map(([date, id]) => [
    date,
    { ...fortuneById(id), date, time: "20:26", status: "archived" },
  ]),
);
export const gradeColors = {
  上上签: "#b48a41",
  上吉签: "#578c68",
  中平签: "#6a90a8",
  小凶签: "#9188a1",
  下下签: "#a27b7e",
};
export function gradeColor(grade) {
  return gradeColors[grade] || "#7f9390";
}
export const stateLabels = {
  undrawn: "尚未抽签",
  unread: "等待解签",
  read: "已经解签",
  archived: "已收进藏签匣",
};
