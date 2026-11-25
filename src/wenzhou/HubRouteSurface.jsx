import React from "react";
import { motion, useIsPresent } from "motion/react";

export default function HubRouteSurface({ side, reduceMotion, children }) {
  const present = useIsPresent();
  const offset = side === "guide" ? 22 : -22;
  const away = reduceMotion ? { opacity: 1, x: 0 } : { opacity: 0, x: offset };
  return (
    <motion.div className="hub-route-surface" data-side={side} inert={!present}
      initial={away} animate={{ opacity: 1, x: 0 }} exit={away}
      transition={{ duration: reduceMotion ? 0 : present ? 0.27 : 0.18, ease: [0.22, 0.68, 0.18, 1] }}>
      {children}
    </motion.div>
  );
}
