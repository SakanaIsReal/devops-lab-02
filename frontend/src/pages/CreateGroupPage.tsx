import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import CircleBackButton from '../components/CircleBackButton';
import Navbar from '../components/Navbar';
import { UserPlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import { searchUsers, createGroup , addMembers } from '../utils/api';
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

  // NEW: รูปปกกลุ่ม
  const [groupImageFile, setGroupImageFile] = useState<File | null>(null);
  const [groupImagePreview, setGroupImagePreview] = useState<string | null>(null);
  const [imageError, setImageError] = useState<string | null>(null);

  // --- ให้มีผู้ใช้ปัจจุบันในลิสต์เสมอและกันซ้ำ ---
  useEffect(() => {
    if (user) {
      setParticipants((prev) => {
        const exists = prev.some(p => String(p.id) === String(user.id));
        return exists ? prev : [user, ...prev];
      });
    }
  }, [user]);

  // --- เสิร์ชผู้ใช้ด้วย debounce + try/catch ---
  useEffect(() => {
    if (!participantName.trim()) {
      setSearchResults([]);
      return;
    }

    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        setIsSearching(true);
        const results = await searchUsers(participantName.trim());

        // normalize ให้หน้าใช้ได้เสมอ
        const normalized: User[] = results.map((u: any) => ({
          ...u,
          name: u.userName ?? u.name ?? '',
          imageUrl: u.avatarUrl ?? u.imageUrl ?? '',
        }));

        // ไม่โชว์ตัวเองในผลลัพธ์
        const filtered = normalized.filter(u => String(u.id) !== String(user?.id));
        if (!cancelled) setSearchResults(filtered);
      } catch (e) {
        if (!cancelled) setSearchResults([]);
        console.error('searchUsers error:', e);
      } finally {
        if (!cancelled) setIsSearching(false);
      }
    }, 500);

    return () => { cancelled = true; clearTimeout(t); };
  }, [participantName, user]);

  const handleAddParticipant = (u: User) => {
    if (!u) return;
    setParticipants(prev => {
      const exists = prev.some(p => String(p.id) === String(u.id));
      if (exists) return prev;
      return [...prev, u];
    });
    setParticipantName('');
    setSearchResults([]);
  };

  const handleDeleteParticipant = (id: number | string) => {
    setParticipants(prev => prev.filter(p => String(p.id) !== String(id)));
  };

  // NEW: เลือกรูป + validation เบื้องต้น
  const handleImageChange: React.ChangeEventHandler<HTMLInputElement> = (e) => {
    setImageError(null);
    const file = e.target.files?.[0];
    if (!file) {
      setGroupImageFile(null);
      if (groupImagePreview) URL.revokeObjectURL(groupImagePreview);
      setGroupImagePreview(null);
      return;
    }
    const isImage = file.type.startsWith('image/');
    const isLt5MB = file.size <= 5 * 1024 * 1024;

    if (!isImage) { setImageError('กรุณาเลือกรูปภาพเท่านั้น'); return; }
    if (!isLt5MB) { setImageError('ไฟล์ใหญ่เกินไป (เกิน 5MB)'); return; }

    setGroupImageFile(file);
    const url = URL.createObjectURL(file);
    if (groupImagePreview) URL.revokeObjectURL(groupImagePreview);
    setGroupImagePreview(url);
  };

  const clearImage = () => {
    setImageError(null);
    setGroupImageFile(null);
    if (groupImagePreview) URL.revokeObjectURL(groupImagePreview);
    setGroupImagePreview(null);
  };

  const handleCreateGroup = async () => {
    if (!groupName.trim()) { alert('กรุณากรอก Group Name'); return; }
    if (participants.length === 0) { alert('ต้องมีสมาชิกอย่างน้อย 1 คน'); return; }

    setIsCreating(true);
    try {
      // 1) สร้างกลุ่ม (multipart: group JSON + cover File) — ใช้ API ของคุณที่รองรับอยู่แล้ว
      const newGroup = await createGroup(groupName, participants, {
        ownerUserId: user?.id,
        cover: groupImageFile || undefined, // <<<<<< ส่งไฟล์ไปตาม API
      });
      if (!newGroup?.id) throw new Error('No group id returned');

      // 2) เตรียม userIds แบบ unique และ “ตัด owner ออก” (ถ้า BE auto-add owner)
      const uniqueIds = Array.from(new Set(participants.map(p => Number(p.id))));
      const ownerId = Number(user?.id);
      const memberIds = uniqueIds.filter(id => id !== ownerId);

      // 3) เพิ่มเฉพาะคนที่ยังไม่อยู่ในกลุ่ม (ฟังก์ชันจะเช็คให้ + ข้าม 409 ให้อยู่แล้ว)
      await addMembers(newGroup.id, memberIds);

      // 4) ไปหน้า group
      navigate(`/group/${newGroup.id}`, { state: { group: newGroup } });

    } catch (error: any) {
      console.error('CREATE FLOW FAILED', {
        status: error?.response?.status,
        url: error?.config?.url,
        method: error?.config?.method,
        data: error?.response?.data,
      });
      alert(`สร้างกลุ่มไม่สำเร็จ: ${error?.response?.status ?? ''}`);
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />
      <div className="p-4">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div className="flex justify-center">
          <div className="flex flex-col items-start w-full max-w-xl">
            <h1 className="text-2xl font-bold text-left my-4">Create Group</h1>

            <div className="bg-white rounded-lg shadow-md p-6 w-full">
              {/* Group name */}
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

              {/* NEW: Group image upload */}
              <div className="mb-4">
                <label className="block text-gray-700 font-semibold mb-2">Group Image (optional)</label>
                <div className="flex items-center gap-4">
                  <div>
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleImageChange}
                      className="block w-full text-sm text-gray-900
                                 file:mr-4 file:py-2 file:px-4
                                 file:rounded-lg file:border-0
                                 file:text-sm file:font-semibold
                                 file:bg-gray-900 file:text-white
                                 hover:file:bg-gray-800"
                    />
                    {imageError && <p className="text-red-600 text-sm mt-2">{imageError}</p>}
                  </div>

                  {groupImagePreview && (
                    <div className="relative">
                      <img
                        src={groupImagePreview}
                        alt="Preview"
                        className="w-20 h-20 rounded-lg object-cover border"
                      />
                      <button
                        type="button"
                        onClick={clearImage}
                        className="text-xs text-red-600 mt-1"
                      >
                        ลบรูป
                      </button>
                    </div>
                  )}
                </div>
              </div>

              {/* Add participant */}
              <div className="mb-4">
                <label htmlFor="participantName" className="block text-gray-700 font-semibold mb-2">Add Participant</label>
                <div className="relative">
                  <input
                    type="text"
                    id="participantName"
                    value={participantName}
                    onChange={(e) => setParticipantName(e.target.value)}
                    className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
                    placeholder="Enter participant's username"
                    autoComplete="off"
                  />
                  {isSearching && (
                    <div className="absolute right-3 top-3">
                      <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-gray-900"></div>
                    </div>
                  )}
                  {searchResults.length > 0 && (
                    <ul className="absolute z-10 w-full bg-white border border-gray-300 rounded-lg mt-1 max-h-64 overflow-auto">
                      {searchResults.map(u => (
                        <li
                          key={String(u.id)}
                          onClick={() => handleAddParticipant(u)}
                          className="p-2 hover:bg-gray-100 cursor-pointer"
                        >
                          {u.name}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>

              {/* Participants list */}
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
                    {participants.map((u) => (
                      <li key={String(u.id)} className="flex justify-between items-center bg-gray-100 p-2 rounded-lg">
                        <div className="flex items-center">
                          <img
                            src={u.imageUrl || 'https://placehold.co/80x80?text=User'}
                            alt={u.name}
                            className="w-10 h-10 rounded-full mr-3 object-cover"
                          />
                          <div>
                            <p className="font-semibold">{u.name}</p>
                            {u.phone && <p className="text-sm text-gray-600">{u.phone}</p>}
                          </div>
                        </div>
                        <button
                          onClick={() => handleDeleteParticipant(u.id)}
                          className="text-red-500 hover:text-red-700"
                          disabled={String(u.id) === String(user?.id)} // ไม่ให้ลบ owner ตัวเองออก
                          title={String(u.id) === String(user?.id) ? 'Owner cannot be removed' : 'Remove'}
                        >
                          <TrashIcon className="w-5 h-5" />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {/* Submit */}
              <button
                onClick={handleCreateGroup}
                className="w-full bg-gray-900 text-white font-semibold py-2 px-4 rounded-lg hover:bg-gray-800 transition duration-300 flex items-center justify-center disabled:opacity-60"
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
