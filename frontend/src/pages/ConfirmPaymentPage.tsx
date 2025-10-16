import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { BottomNav } from '../components/BottomNav';
import CircleBackButton from '../components/CircleBackButton';
import { getPayment, updatePaymentStatus, getUserInformation } from '../utils/api';
import { Payment } from '../types';

export const ConfirmPaymentPage: React.FC = () => {
    const navigate = useNavigate();
    const { expenseId, paymentId } = useParams<{ expenseId: string; paymentId: string }>();
    const [payment, setPayment] = useState<Payment | null>(null);
    const [fromUserName, setFromUserName] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        if (expenseId && paymentId) {
            getPayment(Number(expenseId), Number(paymentId))
                .then(async (data) => {
                    setPayment(data);
                    if (data.fromUserId) {
                        const userInfo = await getUserInformation(data.fromUserId);
                        setFromUserName(userInfo.userName);
                    }
                })
                .catch(() => setError('Failed to fetch payment details'))
                .finally(() => setLoading(false));
        }
    }, [expenseId, paymentId]);

    const handleAccept = async () => {
        if (expenseId && paymentId) {
            try {
                await updatePaymentStatus(Number(expenseId), Number(paymentId), 'VERIFIED');
                alert('Payment accepted');
                navigate(`/groups/${payment?.expenseId}`);
            } catch {
                alert('Failed to accept payment');
            }
        }
    };

    const handleReject = async () => {
        if (expenseId && paymentId) {
            try {
                await updatePaymentStatus(Number(expenseId), Number(paymentId), 'REJECTED');
                alert('Payment rejected');
                navigate(`/groups/${payment?.expenseId}`);
            } catch {
                alert('Failed to reject payment');
            }
        }
    };

    if (loading) {
        return <div>Loading...</div>;
    }

    if (error) {
        return <div>{error}</div>;
    }

    if (!payment) {
        return <div>Payment not found</div>;
    }

    return (
        <div className="min-h-screen h-[120vh] bg-gray-100 flex flex-col p">
            <Navbar />

            <div className='p-4 pb-1'>
                <CircleBackButton onClick={() => navigate(-1)} />
                <div className="items-center justify-between mt-4 mb-6">
                    <div className="text-center mt-6 mb-8">
                        <h1 className="text-3xl font-extrabold text-[#2c4359]">Confirm Payment</h1>
                        <p className="text-gray-500 mt-1">Please review and confirm this transaction</p>
                    </div>
                    <div className="bg-white shadow-md rounded-2xl p-6 mx-auto max-w-md">
                        <div className="mt-6 flex justify-center items-center flex-col">
                            <h2 className="text-xl font-semibold text-gray-800">
                                Transaction from: <span className="text-blue-600">karnlnwza</span>
                            </h2>
                            <h3 className="text-lg font-semibold text-gray-700">
                                Amount: <span className="text-green-600">฿200,000</span>
                            </h3>
                            <div className="w-[260px] h-[350px] rounded-xl mt-5 overflow-hidden shadow-sm border border-gray-200">
                                {payment.receiptFileUrl && <img src={payment.receiptFileUrl} className="w-full object-cover" alt="" />}
                            </div>
                        </div>
                        <div className="flex justify-center gap-4 mt-8">
                            <button
                                onClick={handleAccept}
                                className="bg-green-500 text-white font-semibold px-6 py-2.5 rounded-full hover:bg-green-600 active:scale-95 transition-all"
                            >
                                Accept
                            </button>
                            <button
                                onClick={handleReject}
                                className="bg-red-500 text-white font-semibold px-6 py-2.5 rounded-full hover:bg-red-600 active:scale-95 transition-all"
                            >
                                Reject
                            </button>
                        </div>
                    </div>

                </div>
            </div>



            <BottomNav activeTab={'groups'} />
        </div>
    );
};
