import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Group } from '../types';
import { getGroups, getGroupMembers } from '../utils/api';
import FAB from './FAB';

const GroupList: React.FC = () => {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    const fetchGroups = async () => {
      try {
        const fetchedGroups = await getGroups();
        setGroups(fetchedGroups);
      } catch (error) {
        console.error('Error fetching groups:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchGroups();
  }, []);

  const handleFabClick = () => {
    navigate('/creategroup');
  };

  const handleEdit = (g: Group) => async (e?: React.MouseEvent) => {
    e?.preventDefault();
    e?.stopPropagation();
    setBusyId(Number(g.id));
    try {
      const participants = await getGroupMembers(g.id);
      navigate(`/group/${g.id}/edit`, { state: { group: g, participants } });
    } catch (err) {
      console.error(err);
      navigate(`/group/${g.id}/edit`, { state: { group: g } });
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen" data-cy="loading">
        <div className="animate-spin rounded-full h-32 w-32 border-t-2 border-b-2 border-gray-900" />
      </div>
    );
  }

  if (groups.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 p-4">
        <div className="max-w-md mx-auto">
          <h1 className="text-2xl font-bold text-gray-900 mb-6">All Groups</h1>
          <p className="text-gray-500" data-cy="empty-groups">No groups yet</p>
        </div>
        <FAB onClick={handleFabClick} />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-md mx-auto">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">All Groups</h1>

        <div className="space-y-4">
          {groups.map((group) => (
            <Link
              to={`/group/${group.id}`}
              key={group.id}
              className="bg-white rounded-lg shadow-sm p-4 flex items-center"
              data-cy={`group-item-${group.id}`}
            >
              <img
                src={group.imageUrl}
                alt={group.name}
                className="w-28 h-28 rounded-md object-cover"
                data-cy="group-image"
              />

              <div className="ml-4 flex justify-between flex-col w-full">
                <div>
                  <h2 className="text-lg font-bold text-gray-900" data-cy="group-name">
                    {group.name}
                  </h2>
                  <p className="text-gray-600" data-cy="group-participants">
                    {group.participantCount} participants
                  </p>
                </div>

                <div className="mt-4 flex space-x-2">
                  {/* VIEW (Link wrapper navigates) */}
                  <button
                    className="px-4 py-2 bg-gray-900 text-white rounded-md text-sm font-medium"
                    data-cy="btn-view-group"
                    aria-label={`View ${group.name}`}
                  >
                    View
                  </button>

                  {/* EDIT (prevent Link navigation in handler) */}
                  <button
                    onClick={handleEdit(group)}
                    disabled={busyId === Number(group.id)}
                    data-cy="btn-edit-group"
                    aria-label={`Edit ${group.name}`}
                    className="px-4 py-2 bg-gray-200 text-gray-800 rounded-md text-sm font-medium disabled:opacity-60"
                  >
                    {busyId === Number(group.id) ? 'Editing…' : 'Edit'}
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
