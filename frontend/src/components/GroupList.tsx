import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Group } from '../types';
import { getGroups } from '../utils/api';
import FAB from './FAB';

const GroupList: React.FC = () => {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchGroups = async () => {
      try {
        const fetchedGroups = await getGroups();
        setGroups(fetchedGroups);
      } catch (error) {
        console.error("Error fetching groups:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchGroups();
  }, []);

  const handleFabClick = () => {
    navigate('/creategroup');
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-t-2 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-md mx-auto">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">All Groups</h1>
        <div className="space-y-4">
          {groups.map((group) => (
            <Link to={`/group/${group.id}`} key={group.id} className="bg-white rounded-lg shadow-sm p-4 flex items-center">
              <img src={group.imageUrl} alt={group.name} className="w-28 h-28 rounded-md object-cover" />
              <div className="ml-4 flex justify-between flex-col">
                <div>
                    <h2 className="text-lg font-bold text-gray-900">{group.name}</h2>
                    <p className="text-gray-600">{group.participantCount} participants</p>
                </div>
                <div className="mt-4 flex space-x-2">
                  <button className="px-4 py-2 bg-gray-900 text-white rounded-md text-sm font-medium">
                    View
                  </button>
                  <button className="px-4 py-2 bg-gray-200 text-gray-600 rounded-md text-sm font-medium">
                    Edit
                  </button>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
      <FAB onClick={handleFabClick} />
    </div>
  );
};

export default GroupList;
