// pages/EditGroupPage.tsx
import React, { useEffect, useMemo,useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import CircleBackButton from '../components/CircleBackButton';
import { addMembers, removeMembers,getGroupById,fetchUserProfiles, updateGroup, setGroupMembers, getGroupMembers, searchUsers } from '../utils/api';
import type { User } from '../types';
import { useAuth } from '../contexts/AuthContext';
import { BottomNav } from '../components/BottomNav';
type UIUser = { id: string; name: string; email?: string; phone?: string; imageUrl?: string | null };

const EditGroupPage: React.FC = () => {
  const navigate = useNavigate();
  const { id: idParam } = useParams<{ id: string }>();
  const groupId = useMemo(() => idParam ?? '', [idParam]);
const { user } = useAuth();
  const { state } = useLocation() as { state?: { group?: any } };
  const stateGroup = state?.group; // อาจเป็น undefined ได้เสมอ
const [ownerUserId, setOwnerUserId] = useState<number | string | undefined>(undefined);

  const [loading, setLoading] = useState(true);
  const [groupName, setGroupName] = useState('');
  const [groupImageUrl, setGroupImageUrl] = useState<string | null>(null);
  const [participants, setParticipants] = useState<UIUser[]>([]);
    const [originalMemberIds, setOriginalMemberIds] = useState<number[]>([]); 
  const [saving, setSaving] = useState(false);  
  const [participantName, setParticipantName] = useState('');
  const [searchResults, setSearchResults] = useState<UIUser[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      const previewUrl = URL.createObjectURL(file);
      setGroupImageUrl(previewUrl);
      console.log('Selected file:', file);
    }
  };

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  // ---- helpers ----
  const mapMembers = (arr: any[]): UIUser[] =>
    (arr ?? []).map((m: any) => ({
      id: String(m.id),
      name: m.userName ?? m.name ?? m.email ?? 'Unknown',
      email: m.email,
      phone: m.phone,
      imageUrl: m.avatarUrl ?? m.imageUrl ?? null,
    }));
const didLoadRef = useRef(false);

  // 1) Preload จาก location.state ให้ขึ้นเร็ว
  useEffect(() => {
  if (didLoadRef.current) return;      // กัน StrictMode เรียกซ้ำ
  didLoadRef.current = true;

  let cancelled = false;
  (async () => {
    try {
      setLoading(true);

      // โหลดข้อมูลกลุ่ม (ใช้ stateGroup ถ้ามี แต่ไม่ต้องใส่ใน deps)
      const g = stateGroup ?? await getGroupById(groupId);
      if (cancelled) return;
      setGroupName(g?.name ?? g?.groupName ?? '');
      setGroupImageUrl(g?.coverImageUrl ?? g?.imageUrl ?? null);

      // โหลดสมาชิก
      let members = await getGroupMembers(groupId);
      if (cancelled) return;

      // 🔸ถ้ามีโค้ด hydrate โปรไฟล์ (เติมชื่อจาก API ผู้ใช้) ให้วางตรงนี้
      // members = await hydrateMembersIfNeeded(members);

      setParticipants(members);
      setOriginalMemberIds(
        members.map(m => Number(m.id)).filter(Number.isFinite)
      );
    } finally {
      if (!cancelled) setLoading(false);
    }
  })();

  return () => { cancelled = true; };
}, [groupId]); 

  // 2) ถ้าไม่มี members ใน state → fetch รายละเอียดจริงด้วย id
  useEffect(() => {
  let cancelled = false;
  const run = async () => {
    try {
      setLoading(true);

      // ------ โหลดข้อมูลกลุ่ม ------
      const g = stateGroup ?? await getGroupById(groupId);
      if (cancelled) return;

      setGroupName(g?.name ?? g?.groupName ?? '');
      setGroupImageUrl(g?.coverImageUrl ?? g?.imageUrl ?? null);

      // ------ โหลดสมาชิกจาก API ------
      let members = await getGroupMembers(groupId);
      if (cancelled) return;

      // ------ เติมโปรไฟล์ (hydrate) ถ้าไม่มีชื่อ ------
      const needIds = members
        .filter(m => !(m.name && `${m.name}`.trim()))
        .map(m => Number(m.id))
        .filter(n => Number.isFinite(n));

      if (needIds.length) {
        try {
          const profMap = await fetchUserProfiles(needIds);
          if (cancelled) return;

          members = members.map(m => {
            const id = Number(m.id);
            const prof = profMap.get(id);
            if (!prof) return {
              ...m,
              name: m.name || (m.email?.split('@')[0] ?? '') || `User #${id}`,
            };
            return {
              ...m,
              name: prof.name || m.name || (m.email?.split('@')[0] ?? '') || `User #${id}`,
              email: prof.email || m.email || '',
              phone: prof.phone || m.phone || '',
              imageUrl: prof.imageUrl || m.imageUrl || '',
            };
          });
        } catch (e) {
          console.warn('hydrate profiles failed', e);
          members = members.map(m => ({
            ...m,
            name: m.name || (m.email?.split('@')[0] ?? '') || `User #${m.id}`,
          }));
        }
      }

      // ------ เซ็ตลง state ------
      setParticipants(members);
      setOriginalMemberIds(
        members.map(m => Number(m.id)).filter(n => Number.isFinite(n))
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




  // 3) ค้นหาผู้ใช้ (เดบาวซ์ + กันซ้ำ)
  useEffect(() => {
    const q = participantName.trim();
    if (!q) { setSearchResults([]); return; }

    let alive = true;
    setIsSearching(true);
    const t = setTimeout(async () => {
      try {
        const results = await searchUsers(q);
        const normalized: UIUser[] = results.map((u: any) => ({
          id: String(u.id),
          name: u.userName ?? u.name ?? u.email ?? 'Unknown',
          email: u.email,
          phone: u.phone,
          imageUrl: u.coverImageUrl ?? u.imageUrl ?? null,
        }))
        .filter(u => !participants.some(p => p.id === u.id));
        if (alive) setSearchResults(normalized);
      } finally {
        if (alive) setIsSearching(false);
      }
    }, 400);

    return () => { alive = false; clearTimeout(t); };
  }, [participantName, participants]);

  const handleAddParticipant = (u: UIUser) => {
    if (!participants.some(p => p.id === u.id)) {
      setParticipants(prev => [...prev, u]);
      setParticipantName('');
      setSearchResults([]);
    }
  };

  const handleDeleteParticipant = (id: number | string) =>
  setParticipants(prev => prev.filter(p => String(p.id) !== String(id)));


const handleSave = async () => {
  if (!groupName.trim()) { alert('กรุณากรอกชื่อกลุ่ม'); return; }
  if (!groupId) { alert('ไม่พบรหัสกลุ่ม'); return; }

  setSaving(true);
  try {
    // 1) Update group details (name and coverFile)
    await updateGroup(groupId, { name: groupName, coverFile: selectedFile });

    // 2) Calculate member diffs and update
    const currentIds = Array.from(new Set(
      participants.map(p => Number(p.id)).filter(n => Number.isFinite(n))
    ));
    const before = new Set(originalMemberIds);
    const after  = new Set(currentIds);

    const toAdd    = currentIds.filter(id => !before.has(id));
    const toRemove = originalMemberIds.filter(id => !after.has(id));

    console.log('[SAVE] toAdd=', toAdd, 'toRemove=', toRemove);

    if (toAdd.length)    await addMembers(groupId, toAdd);
    if (toRemove.length) await removeMembers(groupId, toRemove);

    // Update baseline to prevent re-sync
    setOriginalMemberIds(currentIds);

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


  // ---------- Guards ----------
  if (loading) {
    return <div className="min-h-screen bg-gray-50 p-6 text-center text-gray-600">Loading group…</div>;
  }
  if (errorText) {
    return <div className="min-h-screen bg-gray-50 p-6 text-center text-red-600">{errorText}</div>;
  }

  // ---------- UI (โครงเหมือน Create) ----------
  return (
  <div className="min-h-screen h-[150vh] bg-gray-100">
    <Navbar />

    {/* คอนเทนเนอร์หลักแบบชิดขอบบน */}
    <div className="relative">

      {/* ปุ่ม Back ยึดซ้ายบน */}
      <div className="absolute left-4 top-4 z-10">
        <CircleBackButton onClick={() => navigate(-1)} />
      </div>

      {/* เนื้อหาหลัก ตรงกลางหน้า เว้นพื้นที่ให้ปุ่ม Back ด้วย pt-16 */}
      <div className="mx-auto max-w-xl px-4 pt-16">
        <h1 className="text-2xl font-bold text-left my-4">Edit Group</h1>

        <div className="bg-white rounded-lg shadow-md p-6 w-full">
          {/* ------- Group Profile Image ------- */}
          <div className="flex flex-col items-center mb-4">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              className="hidden"
              accept="image/*"
            />
            <img
              src={groupImageUrl || 'https://placehold.co/128x128?text=Group'}
              alt="Group"
              className="w-32 h-32 rounded-full object-cover"
            />
            <button
              onClick={handleUploadClick}
              style={{ backgroundColor: '#122442' }}
              className="mt-4 px-4 py-2 text-white font-semibold rounded-md"
            >
              Upload or update group profile
            </button>
          </div>

          {/* ------- ฟอร์มเดิมของคุณ วางไว้ตรงนี้ ------- */}

          {/* Group name */}
          <div className="mb-4">
            <label htmlFor="groupName" className="block text-gray-700 font-semibold mb-2">
              Group Name
            </label>
            <input
              type="text"
              id="groupName"
              value={groupName}
              onChange={(e) => setGroupName(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
              placeholder="Enter group name"
            />
          </div>

          {/* Add participant (search) */}
          <div className="mb-4">
            <label htmlFor="participantName" className="block text-gray-700 font-semibold mb-2">
              Add Participant
            </label>
            <div className="relative">
              <input
                type="text"
                id="participantName"
                value={participantName}
                onChange={(e) => setParticipantName(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
                placeholder="Search username"
                autoComplete="off"
              />
              {isSearching && (
                <div className="absolute right-3 top-3">
                  <div className="animate-spin rounded-full h-5 w-5 border-t-2 border-b-2 border-gray-900" />
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
                <p className="mt-2">No participants.</p>
              </div>
            ) : (
              <ul className="space-y-2">
                {participants.map((u) => {
                  const displayName =
                    (u.name && u.name.trim()) ||
                    (u as any).username ||
                    (u as any).userName ||
                    (u.email ? u.email.split('@')[0] : '') ||
                    `User #${u.id}`;
                  const isOwner = String(u.id) === String(ownerUserId ?? user?.id);

                  return (
                    <li
                      key={String(u.id)}
                      className="flex items-center justify-between bg-gray-100 p-2 rounded-lg"
                    >
                      <div className="flex items-center min-w-0">
                        <img
                          src={u.imageUrl || 'https://placehold.co/80x80?text=User'}
                          alt={displayName}
                          className="w-10 h-10 rounded-full mr-3 object-cover flex-shrink-0"
                        />
                        <div className="min-w-0">
                          <p className="font-semibold truncate">{displayName}</p>
                          {u.phone ? (
                            <p className="text-sm text-gray-600 truncate">{u.phone}</p>
                          ) : u.email ? (
                            <p className="text-sm text-gray-600 truncate">{u.email}</p>
                          ) : null}
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => handleDeleteParticipant(u.id)}
                        className="text-red-500 hover:text-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        title={isOwner ? 'Owner cannot be removed' : 'Remove'}
                        disabled={isOwner}
                      >
                        {isOwner ? 'Owner' : 'Remove'}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {/* Save */}
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="w-full bg-gray-900 text-white font-semibold py-2 px-4 rounded-lg hover:bg-gray-800 transition duration-300 flex items-center justify-center disabled:opacity-60"
          >
            {saving ? 'Saving…' : 'Save Changes'}
          </button>

          {/* ------- จบฟอร์มเดิมของคุณ ------- */}
        </div>
      </div>
    </div>
          <BottomNav activeTab={'groups'}/>
    
  </div>
);
};

// ไอคอน (ย้ายจากหน้า Create มาใช้ซ้ำ)
const UserPlusIcon = (props: React.SVGProps<SVGSVGElement>) => (
  <svg {...props} viewBox="0 0 24 24" fill="none" stroke="currentColor">
    <path d="M15 12h6m-3-3v6M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="8.5" cy="7" r="3" />
  </svg>
);

export default EditGroupPage;
