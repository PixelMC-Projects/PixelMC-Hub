import { Leaf, Mountain, Star, House, Moon, Wheat, Sun, Cloud, UserRound } from "lucide-react";

// Stable badge IDs shared by the admin editor and player UI.
// Real definitions use `presentation.badge`; integer `icon` remains CustomModelData.
// The explicit web demo keeps its legacy mock `icon` string until its data adapter changes.
export const titleIconPresets = [
  { id: "leaf", label: "草木", Icon: Leaf },
  { id: "mountain", label: "山野", Icon: Mountain },
  { id: "star", label: "星光", Icon: Star },
  { id: "house", label: "筑造", Icon: House },
  { id: "moon", label: "月夜", Icon: Moon },
  { id: "wheat", label: "丰收", Icon: Wheat },
  { id: "sun", label: "曙光", Icon: Sun },
  { id: "cloud", label: "云游", Icon: Cloud },
  { id: "personal", label: "个人专属", Icon: UserRound },
];

export const titleIcons = Object.fromEntries(titleIconPresets.map(({ id, Icon }) => [id, Icon]));
