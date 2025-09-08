import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import CircleBackButton from "../components/CircleBackButton";
import Navbar from "../components/Navbar";
import { BottomNav, NavTab } from "../components/BottomNav";
import TransactionList from "../components/TransactionList";
import FAB from "../components/FAB";
import { mockGetGroupDetailsApi } from "../utils/mockApi";
import { Group } from "../types";

export const GroupDetailPage: React.FC = () => {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [group, setGroup] = useState<Group | null>(location.state?.group || null);
  const [loading, setLoading] = useState(!location.state?.group);
  const [activeTab, setActiveTab] = useState<NavTab>("home");

  useEffect(() => {
    if (!group && groupId) {
      const fetchGroupDetails = async () => {
        try {
          const fetchedGroup = await mockGetGroupDetailsApi(groupId);
          if (fetchedGroup) {
            setGroup(fetchedGroup);
          }
        } catch (error) {
          console.error("Error fetching group details:", error);
        } finally {
          setLoading(false);
        }
      };

      fetchGroupDetails();
    }
  }, [groupId, group]);

  const handleBack = () => {
    navigate(-1);
  };

  const handleFabClick = () => {
    console.log("FAB clicked");
    // Add your create logic here
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-t-2 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />
      <div className="p-4">
        <CircleBackButton
          onClick={handleBack}
          className="border-b border-gray-200"
          iconClassName="text-blue-600"
        />

        <h1 className="text-2xl font-bold text-left my-4">Group Detail</h1>
        {group && (
          <div className="flex items-center space-x-4">
            <img src={group.imageUrl} alt={group.name} className="w-24 h-24 rounded-full flex-shrink-0" />
            <div className="flex flex-col">
              <h2 className="text-xl font-semibold">{group.name}</h2>
              <p className="text-gray-600">Participants: {group.participantCount}</p>
            </div>
          </div>
        )}
        {groupId && <TransactionList groupId={groupId} />}
      </div>
      <FAB onClick={handleFabClick} />
      <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  );
};
