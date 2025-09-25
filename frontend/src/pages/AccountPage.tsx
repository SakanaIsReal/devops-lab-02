import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { useAuth } from '../contexts/AuthContext';
import { ArrowUpOnSquareIcon } from '@heroicons/react/24/outline';

export const AccountPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [name, setName] = useState(user?.name || '');
  const [email, setEmail] = useState(user?.email || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [avatar, setAvatar] = useState(user?.imageUrl || 'https://via.placeholder.com/150');
  const [qrCode, setQrCode] = useState<string | null>(null);

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      const file = event.target.files[0];
      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatar(reader.result as string);
      };
      reader.readAsDataURL(file);
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
    }
  };

  const handleSave = () => {
    // Here you would typically call an API to save the user's data
    console.log('Saving user data:', { name, email, phone, avatar });
    alert('Profile saved successfully!');
  };

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">
      <Navbar />
      <div className="p-4 flex-grow">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div className="flex flex-col items-center">
          <div className="w-32 h-32 rounded-full overflow-hidden bg-gray-300 mb-4">
            <img src={avatar} alt="Avatar" className="w-full h-full object-cover" />
          </div>
          <div className="flex items-center mb-6">
            <p className="text-sm text-gray-600 mr-4">Add Picture from Your gallery</p>
            <input
              type="file"
              id="avatar-upload"
              className="hidden"
              accept="image/*"
              onChange={handleFileSelect}
            />
            <label
              htmlFor="avatar-upload"
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
                  id="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
              </div>
              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700">Email</label>
                <input
                  type="email"
                  id="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
              </div>
              <div>
                <label htmlFor="phone" className="block text-sm font-medium text-gray-700">Phone</label>
                <input
                  type="tel"
                  id="phone"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
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
              onClick={handleSave}
              className="w-full mt-6 bg-[#52bf52] text-white font-semibold py-2 px-4 rounded-lg hover:bg-[#47a647] transition duration-300"
            >
              Save Changes
            </button>
          </div>
        </div>
      </div>
      <BottomNav activeTab={'home'} />
    </div>
  );
};