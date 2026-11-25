// Both handbook entry points use the receiving browser's appearance settings.
export function previewAppearance(saved, winter = new URLSearchParams(location.search).get("winter")) {
  if (winter === "light" || winter === "dark") return { homeStyle: "winter", theme: winter };
  return {
    homeStyle: saved.homeStyle === "winter" ? "winter" : "classic",
    theme: saved.theme === "dark" ? "dark" : "light",
  };
}
