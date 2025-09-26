// pages/EditGroupPage.tsx
import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import CircleBackButton from '../components/CircleBackButton';
import { UserPlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import {
  getGroupById,
  getGroupMembers,
  updateGroup,
  setGroupMembers,
  addMembers, // เผื่อใช้กรณีเฉพาะ
  searchUsers,
} from '../utils/api';
import type { User } from '../types';
import { useAuth } from '../contexts/AuthContext';

const EditGroupPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { id: idParam } = useParams<{ id: string }>();
  const groupId = useMemo(() => idParam ?? '', [idParam]);

  // รับ state.group ถ้ามี (มาจากหน้าก่อน)
  const { state } = useLocation() as { state?: { group?: any } };
  const stateGroup = state?.group;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [groupName, setGroupName] = useState('');
  const [ownerUserId, setOwnerUserId] = useState<number | undefined>(undefined);

  const [participants, setParticipants] = useState<User[]>([]);
  const [originalMemberIds, setOriginalMemberIds] = useState<number[]>([]); // ไว้ดูว่ามีคนถูกลบออกใน UI มั้ย

  const [participantName, setParticipantName] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  const [removalWarning, setRemovalWarning] = useState<string | null>(null);

  // ปรับโครง user จาก API ให้หน้าใช้เสมอ
  const normalizeUser = (u: any): User => ({
    ...u,
    id: u.id ?? u.userId ?? u.memberId,
    name: u.userName ?? u.name ?? '',
    imageUrl: u.avatarUrl ?? u.imageUrl ?? '',
    phone: u.phone ?? u.mobile ?? '',
    email: u.email ?? '',
  });

  // โหลดข้อมูลกลุ่ม + สมาชิก
  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        setLoading(true);

        // group info
        let g = stateGroup;
        if (!g) g = await getGroupById(groupId);

        if (cancelled) return;

        const nameFromApi = g?.name ?? g?.groupName ?? '';
        setGroupName(nameFromApi);
        setOwnerUserId(
          g?.ownerUserId != null ? Number(g.ownerUserId) : undefined
        );

        // members (แยก endpoint)
        const members = await getGroupMembers(groupId);
        if (cancelled) return;

        const normalized = (members ?? []).map(normalizeUser);
        setParticipants(normalized);
        setOriginalMemberIds(
          normalized
            .map((m) => Number(m.id))
            .filter((v) => !Number.isNaN(v))
        );
      } catch (e) {
        console.error('Load group failed:', e);
        alert('โหลดข้อมูลกลุ่มไม่สำเร็จ');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    if (groupId) run();
    return () => { cancelled = true; };
  }, [groupId, stateGroup]);

  // ค้นหาผู้ใช้ (debounce)
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
        const normalized: User[] = (results ?? []).map(normalizeUser);

        // ไม่แสดงคนที่อยู่แล้ว
        const currentIds = new Set(participants.map((p) => String(p.id)));
        const filtered = normalized.filter((u) => !currentIds.has(String(u.id)));

        if (!cancelled) setSearchResults(filtered);
      } catch (e) {
        console.error('searchUsers error:', e);
        if (!cancelled) setSearchResults([]);
      } finally {
        if (!cancelled) setIsSearching(false);
      }
    }, 500);

    return () => { cancelled = true; clearTimeout(t); };
  }, [participantName, participants]);

  const handleAddParticipant = (u: User) => {
    if (!u) return;
    setParticipants((prev) => {
      const exists = prev.some((p) => String(p.id) === String(u.id));
      return exists ? prev : [...prev, u];
    });
    setParticipantName('');
    setSearchResults([]);
  };

  const handleDeleteParticipant = (id: number | string) => {
    setParticipants((prev) => prev.filter((p) => String(p.id) !== String(id)));
  };

  const handleSave = async () => {
    if (!groupName.trim()) { alert('กรุณากรอกชื่อกลุ่ม'); return; }
    if (!groupId) { alert('ไม่พบรหัสกลุ่ม'); return; }

    setSaving(true);
    setRemovalWarning(null);
    try {
      // 1) อัปเดตชื่อ (และ owner ถ้าต้องการ)
      await updateGroup(groupId, {
        name: groupName,
        ownerUserId: ownerUserId,
      });

      // 2) อัปเดตสมาชิก (ด้วย setGroupMembers -> เพิ่มเฉพาะที่ยังไม่มี)
      const currentIds = Array.from(
        new Set(
          participants
            .map((p) => Number(p.id))
            .filter((v) => !Number.isNaN(v))
        )
      );

      // ตรวจว่ามีคนถูกลบออกไหม (UI จะให้ลบได้ แต่ BE ยังไม่รองรับลบจริง)
      const before = new Set(originalMemberIds);
      const after = new Set(currentIds);
      const removed = originalMemberIds.filter((id) => !after.has(id));
      if (removed.length > 0) {
        setRemovalWarning('ตอนนี้ระบบจะยังไม่ลบสมาชิกออกจากกลุ่ม (ฝั่ง API ไม่มี endpoint ลบ) — จะเพิ่มสมาชิกใหม่ให้ครบเท่านั้น');
      }

      // เติมสมาชิกที่ยังไม่มี
      await setGroupMembers(groupId, currentIds);

      // เสร็จแล้วกลับหน้า group detail
      navigate(`/group/${groupId}`);
    } catch (e: any) {
      console.error('SAVE EDIT FAILED', {
        status: e?.response?.status,
        url: e?.config?.url,
        method: e?.config?.method,
        data: e?.response?.data,
      });
      alert(`บันทึกไม่สำเร็จ: ${e?.response?.status ?? ''}`);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-100">
        <Navbar />
        <div className="p-4">
          <CircleBackButton onClick={() => navigate(-1)} />
          <div className="flex justify-center mt-10">
            <div className="flex items-center">
              <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-gray-900 mr-3"></div>
              <span className="text-gray-700">กำลังโหลดข้อมูลกลุ่ม...</span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />
      <div className="p-4">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div className="flex justify-center">
          <div className="flex flex-col items-start w-full max-w-xl">
            <h1 className="text-2xl font-bold text-left my-4">Edit Group</h1>

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

              {/* Owner (ถ้าต้องให้แก้) */}
              {/* <div className="mb-4">
                <label className="block text-gray-700 font-semibold mb-2">Owner User ID</label>
                <input
                  type="number"
                  value={ownerUserId ?? ''}
                  onChange={(e) => setOwnerUserId(e.target.value ? Number(e.target.value) : undefined)}
                  className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
                  placeholder="Owner user id (optional)"
                />
              </div> */}

              {/* Add participant (search) */}
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
                      {searchResults.map((u) => (
                        <li
                          key={String(u.id)}
                          onClick={() => handleAddParticipant(u)}
                          className="p-2 hover:bg-gray-100 cursor-pointer"
                        >
                          {u.name || u.email || `User #${u.id}`}
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
                    <p className="mt-2">ยังไม่มีสมาชิกในกลุ่ม</p>
                    <p>เพิ่มสมาชิกโดยใช้ช่องค้นหาด้านบน</p>
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
                            <p className="font-semibold">{u.name || u.email || `User #${u.id}`}</p>
                            {u.phone && <p className="text-sm text-gray-600">{u.phone}</p>}
                          </div>
                        </div>
                        <button
                          onClick={() => handleDeleteParticipant(u.id)}
                          className="text-red-500 hover:text-red-700"
                          title="Remove"
                        >
                          <TrashIcon className="w-5 h-5" />
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {removalWarning && (
                <div className="mb-4 text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded p-3">
                  {removalWarning}
                </div>
              )}

              {/* Save */}
              <button
                onClick={handleSave}
                className="w-full bg-gray-900 text-white font-semibold py-2 px-4 rounded-lg hover:bg-gray-800 transition duration-300 flex items-center justify-center disabled:opacity-60"
                disabled={saving}
              >
                {saving && <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-white mr-3"></div>}
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default EditGroupPage;
