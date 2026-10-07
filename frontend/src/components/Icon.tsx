import type { CSSProperties } from "react";

export type IconName =
  | "users"
  | "organization"
  | "shield"
  | "menu"
  | "code"
  | "search"
  | "arrow"
  | "check"
  | "alert"
  | "lock"
  | "panel"
  | "close";

const paths: Record<IconName, string[]> = {
  users: [
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
    "M16 3a4 4 0 0 1 0 8",
    "M22 21v-2a4 4 0 0 0-3-3.87",
    "M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  ],
  organization: [
    "M9 3h6v6H9z",
    "M3 15h6v6H3z",
    "M15 15h6v6h-6z",
    "M12 9v3M6 15v-3h12v3",
  ],
  shield: ["M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11", "m9 12 2 2 4-4"],
  menu: ["M4 5h16M4 12h16M4 19h16"],
  code: ["m8 8-4 4 4 4", "m16 8 4 4-4 4", "m14 4-4 16"],
  search: ["M19 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0", "m16 16 5 5"],
  arrow: ["M5 12h14", "m13 6 6 6-6 6"],
  check: ["M22 11a10 10 0 1 1-6-9", "m9 11 3 3L22 4"],
  alert: ["M12 3 2 21h20L12 3", "M12 9v4M12 17h.01"],
  lock: ["M5 11h14v10H5z", "M8 11V7a4 4 0 0 1 8 0v4"],
  panel: ["M3 3h18v18H3z", "M9 3v18"],
  close: ["m6 6 12 12M6 18 18 6"],
};

export function Icon({
  name,
  style,
}: {
  name: IconName;
  style?: CSSProperties;
}) {
  return (
    <svg
      className="icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      style={style}
    >
      {paths[name].map((path, index) => (
        <path key={index} d={path} />
      ))}
    </svg>
  );
}
