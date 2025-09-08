import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import CircleBackButton from '../components/CircleBackButton';
import Navbar from '../components/Navbar';
import { UserPlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import { mockSearchUsersApi, mockCreateGroupApi } from '../utils/mockApi';
import { User } from '../types';
import { useAuth } from '../contexts/AuthContext';

const CreateGroupPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [groupName, setGroupName] = useState('');
  const [participants, setParticipants] = useState<User[]>([]);
  const [participantName, setParticipantName] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    if (user) {
      setParticipants([user]);
    }
  }, [user]);

  useEffect(() => {
    if (participantName === '') {
      setSearchResults([]);
      return;
    }

    const search = async () => {
      setIsSearching(true);
      const results = await mockSearchUsersApi(participantName);
      setSearchResults(results.filter(u => u.id !== user?.id)); // Exclude current user from search results
      setIsSearching(false);
    };

    const debounceSearch = setTimeout(() => {
      search();
    }, 500);

    return () => clearTimeout(debounceSearch);
  }, [participantName, user]);

  const handleAddParticipant = (user: User) => {
    if (user && !participants.find(p => p.id === user.id)) {
      setParticipants([...participants, user]);
      setParticipantName('');
      setSearchResults([]);
    }
  };

  const handleDeleteParticipant = (id: string) => {
    setParticipants(participants.filter(p => p.id !== id));
  };

  const handleCreateGroup = async () => {
    setIsCreating(true);
    try {
      const newGroup = await mockCreateGroupApi(groupName, participants);
      navigate(`/group/${newGroup.id}`, { state: { group: newGroup } });
    } catch (error) {
      console.error("Error creating group:", error);
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />
      <div className="p-4">
        <CircleBackButton onClick={() => navigate(-1)} />
          <div className='flex justify-center'>
          <div className='flex flex-col items-start'>
        <h1 className="text-2xl font-bold text-left my-4">Create Group</h1>
        <div className=" bg-white rounded-lg shadow-md p-6">
          
          <div className="mb-4">
            <label htmlFor="groupName" className="block text-gray-700 font-semibold mb-2">Group Name</label>
            <input
              type="text"
              id="groupName"
              value={groupName}
              onChange={(e) => setGroupName(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
              placeholder="Enter group name"
            />
          </div>
          <div className="mb-4">
            <label htmlFor="participantName" className="block text-gray-700 font-semibold mb-2">Add Participant</label>
            <div className="relative">
              <input
                type="text"
                id="participantName"
                value={participantName}
                onChange={(e) => setParticipantName(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
                placeholder="Enter participant\'s username"
              />
              {isSearching && <div className="absolute right-3 top-3"><div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-gray-900"></div></div>}
              {searchResults.length > 0 && (
                <ul className="absolute z-10 w-full bg-white border border-gray-300 rounded-lg mt-1">
                  {searchResults.map(user => (
                    <li
                      key={user.id}
                      onClick={() => handleAddParticipant(user)}
                      className="p-2 hover:bg-gray-100 cursor-pointer"
                    >
                      {user.name}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
          <div className="mb-4">
            <h3 className="text-lg font-semibold mb-2">Participants</h3>
            {participants.length === 0 ? (
              <div className="text-center text-gray-500 bg-gray-100 p-4 rounded-lg">
                <UserPlusIcon className="w-12 h-12 mx-auto text-gray-400" />
                <p className="mt-2">No participants added yet.</p>
                <p>Add participants using the field above.</p>
              </div>
            ) : (
              <ul className="space-y-2">
                {participants.map((user, index) => (
                  <li key={index} className="flex justify-between items-center bg-gray-100 p-2 rounded-lg">
                    <div className="flex items-center">
                      <img src={user.imageUrl} alt={user.name} className="w-10 h-10 rounded-full mr-3" />
                      <div>
                        <p className="font-semibold">{user.name}</p>
                        <p className="text-sm text-gray-600">{user.phone}</p>
                      </div>
                    </div>
                    <button
                      onClick={() => handleDeleteParticipant(user.id)}
                      className="text-red-500 hover:text-red-700"
                    >
                      <TrashIcon className="w-5 h-5" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <button
            onClick={handleCreateGroup}
            className="w-full bg-gray-900 text-white font-semibold py-2 px-4 rounded-lg hover:bg-gray-800 transition duration-300 flex items-center justify-center"
            disabled={isCreating}
          >
            {isCreating && <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-white mr-3"></div>}
            {isCreating ? 'Creating...' : 'Create Group'}
          </button>
        </div>
        </div>
        </div>
      </div>
    </div>
  );
};

export default CreateGroupPage;

