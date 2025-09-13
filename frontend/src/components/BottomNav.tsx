import React from "react";
import {
  HomeIcon,
  UserGroupIcon,
  CurrencyDollarIcon,
} from "@heroicons/react/24/solid";
import { NavLink, useLocation } from "react-router-dom";

export type NavId = "home" | "groups" | "split";

interface BottomNavProps {
  /** ไม่จำเป็นแล้ว แต่ถ้าอยากให้ parent รับรู้ว่าผู้ใช้กดแท็บไหน ก็ส่ง callback มาได้ */
  onTabChange?: (tab: NavId) => void;
}

const routes: Record<NavId, string> = {
  home: "/",
  groups: "/groups",
  split: "/split",
};

const labels: Record<NavId, string> = {
  home: "Home",
  groups: "Group",
  split: "Split",
};

const icons: Record<NavId, React.ReactElement> = {
  home: <HomeIcon className="w-6 h-6" />,
  groups: <UserGroupIcon className="w-6 h-6" />,
  split: <CurrencyDollarIcon className="w-6 h-6" />,
};

// กำหนด active โดยอิง path ปัจจุบัน
function isActiveByPath(pathname: string, id: NavId) {
  if (id === "home") return pathname === "/" || pathname.startsWith("/home");
  if (id === "groups") return pathname.startsWith("/groups");
  if (id === "split") return pathname.startsWith("/split");
  return false;
}

export const BottomNav: React.FC<BottomNavProps> = ({ onTabChange }) => {
  const { pathname } = useLocation();
  const items: NavId[] = ["home", "groups", "split"];

  return (
    <nav
      className="fixed bottom-0 inset-x-0 bg-[#222831] text-white z-50
                 pb-[env(safe-area-inset-bottom)] border-t border-white/10"
      role="navigation"
      aria-label="Bottom Navigation"
    >
      <div className="flex justify-around items-center">
        {items.map((id) => {
          const active = isActiveByPath(pathname, id);
          const base =
            "flex flex-col items-center py-3 px-4 transition-colors text-xs";
          const color = active ? "text-white" : "text-gray-400 hover:text-white/90";
          return (
            <NavLink
              key={id}
              to={routes[id]}
              onClick={() => onTabChange?.(id)}
              className={`${base} ${color}`}
              aria-label={labels[id]}
              aria-current={active ? "page" : undefined}
            >
              <div className={active ? "text-white" : "text-gray-400"}>
                {icons[id]}
              </div>
              <span className="mt-1">{labels[id]}</span>
            </NavLink>
          );
        })}
      </div>
    </nav>
  );
};
