import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import tokens from "./tokens.json";
import "./styles.css";
import "./glass-theme.css";
import "./detail-polish.css";
import "./community-integration.css";
import "./dark-theme.css";

for (const [key, value] of Object.entries(tokens.color))
  document.documentElement.style.setProperty(`--${key}`, value);
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
