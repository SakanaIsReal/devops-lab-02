import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Group } from '../types';
import { getGroups, getGroupMembers, deleteGroup } from '../utils/api';
import FAB from './FAB';
import { useAuth } from '../contexts/AuthContext';

const GroupList: React.FC = () => {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const  {user}  = useAuth();

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
  const [busyId, setBusyId] = useState<number | null>(null);

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
  const handleDelete = (g: Group) => async (e?: React.MouseEvent) => {
    e?.preventDefault();
    e?.stopPropagation();
    if (window.confirm('Are you sure you want to delete this group?')) {
      setBusyId(Number(g.id));
      try {
        await deleteGroup(g.id);
        alert('group deleted');
        setGroups(groups.filter(group => group.id !== g.id));
      } catch (err) {
        console.error(err);
      } finally {
        setBusyId(null);
      }
    }
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
              <img src={group.coverImageUrl} alt={group.name} className="w-28 h-28 rounded-md object-cover" />
              <div className="ml-4 flex justify-between flex-col">
                <div>
                  <h2 className="text-lg font-bold text-gray-900">{group.name}</h2>
                  <p className="text-gray-600">{group.memberCount} Participants</p>
                </div>
                <div className="mt-4 flex space-x-2">
                  <button className="px-4 py-2 bg-blue-500 text-white rounded-md text-sm font-medium">
                    View
                  </button>
                  {user && group.ownerUserId === parseInt(user.id) && (
                    <>
                      <button onClick={handleEdit(group)} disabled={busyId === Number(group.id)} className="px-4 py-2 bg-yellow-500 text-white rounded-md text-sm font-medium">
                        Edit
                      </button>
                      <button onClick={handleDelete(group)} disabled={busyId === Number(group.id)} className="px-4 py-2 bg-red-500 text-white rounded-md text-sm font-medium">
                        Delete
                      </button>
                    </>
                  )}
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
