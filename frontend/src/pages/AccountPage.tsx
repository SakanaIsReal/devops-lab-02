import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { useAuth } from '../contexts/AuthContext';
import { ArrowUpOnSquareIcon } from '@heroicons/react/24/outline';
import { editUserInformationAcc, getUserInformation } from "../utils/api";
import { User } from '../types';

export const AccountPage: React.FC = () => {

  const navigate = useNavigate();
  const { user, isLoading, updateUser } = useAuth();
  const [formUserUpdate, setformUserUpdate] = useState({
    userName: "",
    email: "",
    phone: "",
    avatar: "",
    qr: ""
  });
  const [avatar, setAvatar] = useState(user?.imageUrl || 'https://via.placeholder.com/150');
  const [qrCode, setQrCode] = useState(user?.qrCodeUrl || null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      setformUserUpdate({
        userName: user.name ?? "",
        email: user.email ?? "",
        phone: user.phone ?? "",
        avatar: user.imageUrl || 'https://via.placeholder.com/150',
        qr: user.qrCodeUrl || ''});
    }
  }, [user]);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setformUserUpdate((prevState) => ({
      ...prevState,
      [name]: value,
    }));
    if (error) setError(null);
  };

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      const file = event.target.files[0];
      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatar(reader.result as string);
      };
      reader.readAsDataURL(file);
      setformUserUpdate((prev: any) => ({
        ...prev,
        avatar: file
      }));
    }
  };

  const handleQrCodeSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      const file = event.target.files[0];
      const reader = new FileReader();
      reader.onloadend = () => {
        setQrCode(reader.result as string);
      };
      reader.readAsDataURL(file);
      setformUserUpdate((prev: any) => ({
        ...prev,
        qr: file
      }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!user?.id) {
      console.error("User not found");
      return;
    }
    try {
      await editUserInformationAcc(user?.id, formUserUpdate);
      const res = await getUserInformation(user.id);
      const userInfo = Array.isArray(res) ? res[0] : res;
      const updatedUser: User = {
        id: userInfo.id.toString(),
        email: userInfo.email,
        name: userInfo.userName,
        phone: userInfo.phone,
        imageUrl: userInfo.avatarUrl,
        qrCodeUrl: userInfo.qrCodeUrl,
      };
      updateUser(updatedUser);
      setTimeout(() => {
        alert("Edit Information Successfully")
        navigate("/dashboard", { replace: true });
      }, 50);
    }
    catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "An unknown error occurred during sign up."
      );
    }
  };



  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <Navbar />
                <CircleBackButton onClick={() => {
            navigate("/home", { replace: true });
          }} /> 
      <form onSubmit={handleSubmit} autoComplete="off" className="space-y-4">
        <div className="p-4 flex-grow">
          <div className="flex flex-col items-center">
            <div className="w-32 h-32 rounded-full overflow-hidden bg-gray-300 mb-4">
              <img src={avatar} className="w-full h-full object-cover" />
            </div>
            <div className="flex items-center mb-6">
              <p className="text-sm text-gray-600 mr-4">Add Picture from Your gallery</p>
              <input
                type="file"
                id="avatar-upload"
                name="avatar-upload"
                className="hidden"
                accept="image/*"
                onChange={handleFileSelect}
              />
              <label
                htmlFor="avatar-upload"
                data-cy="upload-avatar-button"
                className="cursor-pointer bg-gray-200 hover:bg-gray-300 text-gray-800 font-semibold py-2 px-4 rounded-lg text-sm"
              >
                Open Gallery
              </label>
            </div>
            <div className="bg-white rounded-lg shadow-md p-6 w-full max-w-md">
              <h1 className="text-2xl font-bold text-center mb-6">Edit Account</h1>
              <div className="space-y-4">
                <div>
                  <label htmlFor="name" className="block text-sm font-medium text-gray-700">Username</label>
                  <input
                    type="text"
                    id="userName"
                    name="userName"
                    data-cy="username-input"
                    value={formUserUpdate.userName}
                    onChange={handleInputChange}
                    className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                  />
                </div>
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-gray-700">Email</label>
                  <input
                    type="email"
                    id="email"
                    name="email"
                    data-cy="email-input"
                    value={formUserUpdate.email}
                    onChange={handleInputChange}
                    className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                  />
                </div>
                <div>
                  <label htmlFor="phone" className="block text-sm font-medium text-gray-700">Phone</label>
                  <input
                    type="tel"
                    id="phone"
                    name="phone"
                    data-cy="phone-input"
                    value={formUserUpdate.phone}
                    onChange={handleInputChange}
                    className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                  />
                </div>
              </div>
              <div className="mt-6">
                <div className="flex justify-between items-center">
                  <p className="text-sm font-medium text-gray-700">Upload your QR code</p>
                  <input
                    type="file"
                    id="qr-code-upload"
                    name="qr-code-upload"
                    data-cy="upload-qr-btn"
                    className="hidden"
                    accept="image/*"
                    onChange={handleQrCodeSelect}
                  />
                  <label htmlFor="qr-code-upload" className="cursor-pointer">
                    <ArrowUpOnSquareIcon className="w-6 h-6 text-gray-600 hover:text-gray-800" />
                  </label>
                </div>
                {qrCode && (
                  <div className="mt-4">
                    <img src={qrCode} alt="QR Code" className="w-32 h-32 mx-auto" />
                  </div>
                )}
              </div>
              <button
                type="submit"
                data-cy="account-save-button"
                className="w-full mt-6 bg-[#52bf52] text-white font-semibold py-2 px-4 rounded-lg hover:bg-[#47a647] transition duration-300"
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      </form>
      <BottomNav activeTab={'home'} />
    </div>
  );
};