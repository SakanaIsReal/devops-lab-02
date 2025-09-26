import { useNavigate } from "react-router-dom";
import {
  HomeIcon,
  UserGroupIcon,
  // CurrencyDollarIcon,
} from "@heroicons/react/24/solid";

export type NavTab = "home" | "groups" | "split" | undefined;

interface BottomNavProps {
  activeTab: NavTab;
}

export const BottomNav: React.FC<BottomNavProps> = ({ activeTab }) => {
  const navigate = useNavigate();
  const navItems: { id: NavTab; label: string; icon: React.ReactElement; path: string }[] = [
    {
      id: "home",
      label: "Home",
      icon: <HomeIcon className="w-6 h-6" />,
      path: "/home",
    },
    {
      id: "groups",
      label: "Group",
      icon: <UserGroupIcon className="w-6 h-6" />,
      path: "/groups",
    },
    // {
    //   id: "split",
    //   label: "Split",
    //   icon: <CurrencyDollarIcon className="w-6 h-6" />,
    //   path: "/split",
    // },
  ];
  return (
    <nav className="fixed bottom-0 inset-x-0 bg-[#222831] text-white z-50 pb-[env(safe-area-inset-bottom)]">
      <div className="flex justify-around items-center">
        {navItems.map((item) => {
          const isActive = activeTab === item.id;

          return (
            <button
              key={item.id}
              onClick={() => navigate(item.path)}
              className={`flex flex-col items-center py-3 px-4 transition-color ${
                isActive ? "text-white" : "text-gray-400"
              }`}
              aria-label={item.label}
              aria-current={isActive ? "page" : undefined}
              data-cy={`bottom-nav-${item.id}`}
            >
              <div className={isActive ? "text-white" : "text-gray-400"}>
                {item.icon}
              </div>
              <span className="text-xs mt-1">{item.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
