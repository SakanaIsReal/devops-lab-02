import { UserCircleIcon } from "@heroicons/react/24/outline";
import { Menu, MenuButton, MenuItem, MenuItems, Transition } from "@headlessui/react";
import { useAuth } from "../contexts/AuthContext";
import logo from "../assets/logo-transparent-x256.png";
import { Fragment } from "react";
import { Link } from "react-router-dom";

const Navbar = () => {
  const { logout } = useAuth();

  const handleLogout = () => {
    if (window.confirm("Are you sure you want to log out?")) {
      logout();
    }
  };

  return (
    <nav className="sticky top-0 z-50 flex items-center justify-between px-4 py-3 bg-[#222831] text-white shadow-md">
      <div className="flex justify-center items-center gap-3">
        <img src={logo} alt="Company Logo" className="w-10 h-10" />
        <div className="flex items-start flex-col gap-1">
          <h1 className="text-lg font-bold tracking-widest leading-4">SMART</h1>
          <h1 className="text-lg font-bold tracking-widest leading-4">SPLIT</h1>
        </div>
      </div>
      <div className="flex items-center">
        <Menu as="div" className="relative inline-block text-left">
          <div>
            <MenuButton
              aria-label="User account"
              className="p-2 rounded-full hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-[#222831] focus:ring-white transition-colors"
            >
              <UserCircleIcon className="h-6 w-6" />
            </MenuButton>
          </div>
          <Transition
            as={Fragment}
            enter="transition ease-out duration-100"
            enterFrom="transform opacity-0 scale-95"
            enterTo="transform opacity-100 scale-100"
            leave="transition ease-in duration-75"
            leaveFrom="transform opacity-100 scale-100"
            leaveTo="transform opacity-0 scale-95"
          >
            <MenuItems className="absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none">
              <div className="py-1">
                <MenuItem>
                  {({ focus }) => (
                    <Link to="/account" className={`${
                        focus ? "bg-gray-100 text-gray-900" : "text-gray-700"
                      } block w-full px-4 py-2 text-left text-sm`}
                    >
                      Account
                    </Link>
                  )}
                </MenuItem>
                <MenuItem>
                  {({ focus }) => (
                    <button
                      onClick={handleLogout}
                      className={`${
                        focus ? "bg-gray-100 text-gray-900" : "text-gray-700"
                      } block w-full px-4 py-2 text-left text-sm`}
                    >
                      Sign out
                    </button>
                  )}
                </MenuItem>
              </div>
            </MenuItems>
          </Transition>
        </Menu>
      </div>
    </nav>
  );
};

export default Navbar;
