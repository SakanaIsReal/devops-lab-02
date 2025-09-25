import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import CircleBackButton from '../components/CircleBackButton';
import Navbar from '../components/Navbar';
import { getPaymentDetails } from '../utils/api';
import { PaymentDetails } from '../types';
import { BottomNav, NavTab } from '../components/BottomNav';

const PayPage: React.FC = () => {
  const { transactionId } = useParams<{ transactionId: string }>();
  const navigate = useNavigate();
  const [paymentDetails, setPaymentDetails] = useState<PaymentDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [amountPaid, setAmountPaid] = useState('');
  const [paymentSlip, setPaymentSlip] = useState<File | null>(null);

  useEffect(() => {
    if (transactionId) {
      const fetchPaymentDetails = async () => {
        try {
          const details = await getPaymentDetails(transactionId);
          setPaymentDetails(details);
        } catch (error) {
          console.error("Error fetching payment details:", error);
        } finally {
          setLoading(false);
        }
      };
      fetchPaymentDetails();
    }
  }, [transactionId]);

  const handlePay = () => {
    console.log('Paying:', amountPaid, 'with slip:', paymentSlip);
    // Logic to handle payment and slip upload
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-t-2 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  if (!paymentDetails) {
    return <div className="text-center mt-8">Payment details not found.</div>;
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />
      <div className="p-4">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div>
          <h1 className="text-2xl font-bold text-left" style={{color: '#2c4359'}}>Pay your dept</h1>
          <h2 className="text-lg font-semibold text-left" style={{color: '#2c4359'}}>QR CODE</h2>
        </div>
        <div className="max-w-md mx-auto bg-white rounded-lg shadow-md p-6 text-center">
          <p className="text-lg font-semibold mb-2">Payer: {paymentDetails.payerName}</p>
          <img src={paymentDetails.qrCodeUrl} alt="QR Code" className="mx-auto w-48 h-48 mb-4" />
          <p className="text-xl font-bold mb-4">Amount to Pay: ${paymentDetails.amountToPay.toFixed(2)}</p>
          
          <div className="mb-4">
            <label htmlFor="amountPaid" className="block text-gray-700 font-semibold mb-2">Amount You Pay</label>
            <input
              type="number"
              id="amountPaid"
              value={amountPaid}
              onChange={(e) => setAmountPaid(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
              placeholder="Enter amount you are paying"
            />
          </div>

          <div className="mb-4">
            <label htmlFor="paymentSlip" className="block text-gray-700 font-semibold mb-2">Upload Payment Slip</label>
            <input
              type="file"
              id="paymentSlip"
              onChange={(e) => setPaymentSlip(e.target.files ? e.target.files[0] : null)}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
            />
          </div>

          <button
            onClick={handlePay}
            className="w-full text-white font-semibold py-2 px-4 rounded-lg transition duration-300"
            style={{backgroundColor: '#0d78f2'}}
          >
            Pay
          </button>
        </div>
      </div>
      <BottomNav activeTab={undefined} onTabChange={(tab: NavTab) => {
        if (tab === 'home' || tab === 'groups' || tab === 'split') {
          navigate('/dashboard');
        }
      }} />
    </div>
  );
};

export default PayPage;
