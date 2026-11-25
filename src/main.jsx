import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import { ExternalShare } from "./wenzhou/Shares.jsx";
import tokens from "./tokens.json";
import "./styles.css";
import "./glass-theme.css";
import "./detail-polish.css";
import "./community-integration.css";
import "./dark-theme.css";
import "./wenzhou/wenzhou.css";
import "./winter-home.css";
import "./winter-pages.css";
import "./winter-details.css";

for (const [key, value] of Object.entries(tokens.color))
  document.documentElement.style.setProperty(`--${key}`, value);
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    {new URLSearchParams(location.search).has("snapshot") ? <ExternalShare/> : <App />}
  </React.StrictMode>,
);
