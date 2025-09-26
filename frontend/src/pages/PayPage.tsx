import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import CircleBackButton from '../components/CircleBackButton';
import Navbar from '../components/Navbar';
import { getPaymentDetails, submitPayment, hasPendingPayment, getExpensePayments } from '../utils/api';
import { PaymentDetails } from '../types';
import { BottomNav, NavTab } from '../components/BottomNav';
import { useAuth } from '../contexts/AuthContext';

const PayPage: React.FC = () => {
  const { expenseId, userId } = useParams<{ expenseId: string; userId: string }>();
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const [paymentDetails, setPaymentDetails] = useState<PaymentDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [amountPaid, setAmountPaid] = useState('');
  const [paymentSlip, setPaymentSlip] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [hasPending, setHasPending] = useState(false);
  const [pendingPaymentInfo, setPendingPaymentInfo] = useState<{amount: number, createdAt: string} | null>(null);

  useEffect(() => {
    if (expenseId && userId && currentUser) {
      const checkPendingPayment = async () => {
        try {
          // Check if user already has a pending payment for this expense
          const hasPending = await hasPendingPayment(Number(expenseId), Number(userId));
          setHasPending(hasPending);
          
          if (hasPending) {
            // Get pending payment details to show to user
            const payments = await getExpensePayments(Number(expenseId));
            const userPendingPayment = payments.find(payment => 
              payment.fromUserId === Number(userId) && payment.status === "PENDING"
            );
            
            if (userPendingPayment) {
              setPendingPaymentInfo({
                amount: userPendingPayment.amount,
                createdAt: new Date(userPendingPayment.createdAt).toLocaleString()
              });
            }
          } else {
            // Only load payment details if no pending payment exists
            const details = await getPaymentDetails(expenseId, userId);
            setPaymentDetails(details);
            setAmountPaid(details.amountToPay.toString());
          }
        } catch (error) {
          console.error("Error checking pending payment:", error);
          setError("Failed to load payment information");
        } finally {
          setLoading(false);
        }
      };

      checkPendingPayment();
    } else {
      setError("Missing expense ID, user ID, or user not logged in");
      setLoading(false);
    }
  }, [expenseId, userId, currentUser]);

  const handlePay = async () => {
    if (!expenseId || !userId || !amountPaid) {
      setError("Please fill in all required fields");
      return;
    }

    const paidAmount = parseFloat(amountPaid);
    if (isNaN(paidAmount) || paidAmount <= 0) {
      setError("Please enter a valid amount");
      return;
    }

    if (paidAmount > (paymentDetails?.amountToPay || 0)) {
      setError("Payment amount cannot exceed the owed amount");
      return;
    }

    if (!paymentSlip) {
      setError("Please upload a payment receipt");
      return;
    }

    setSubmitting(true);
    setError("");

    try {
      await submitPayment(
        Number(expenseId),
        Number(userId),
        paidAmount,
        paymentSlip
      );

      alert("Payment submitted successfully!");
      navigate("/home");
    } catch (error: any) {
      console.error("Error submitting payment:", error);
      setError(error.response?.data?.message || "Failed to submit payment");
    } finally {
      setSubmitting(false);
    }
  };

  const handleAmountChange = (value: string) => {
    setAmountPaid(value);
    if (error) setError("");
  };

  const handleFileChange = (file: File | null) => {
    setPaymentSlip(file);
    if (error && file) setError("");
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-t-2 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  // Show pending payment message if user already has a pending payment
  if (hasPending) {
    return (
      <div className="min-h-screen bg-gray-100">
        <Navbar />
        <div className="p-4">
          <CircleBackButton onClick={() => navigate(-1)} />
          <div className="max-w-md mx-auto bg-white rounded-lg shadow-md p-6 text-center">
            <div className="mb-6">
              <div className="text-yellow-600 text-6xl mb-4">⏳</div>
              <h2 className="text-2xl font-bold text-yellow-700 mb-2">Pending Payment Exists</h2>
              <p className="text-gray-600 mb-4">
                You already have a pending payment for this expense. Please wait for the current payment to be verified before submitting a new one.
              </p>
              
              {pendingPaymentInfo && (
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-4">
                  <h3 className="font-semibold text-yellow-800 mb-2">Pending Payment Details:</h3>
                  <p className="text-yellow-700">Amount: ${pendingPaymentInfo.amount.toFixed(2)}</p>
                  <p className="text-yellow-700">Submitted: {pendingPaymentInfo.createdAt}</p>
                  <p className="text-yellow-700 text-sm mt-2">Status: Waiting for verification</p>
                </div>
              )}
              
              <div className="flex space-x-4 mt-6">
                <button
                  onClick={() => navigate("/home")}
                  className="flex-1 bg-gray-900 text-white py-2 px-4 rounded-lg font-semibold"
                >
                  Go to Dashboard
                </button>
                <button
                  onClick={() => navigate(-1)}
                  className="flex-1 bg-gray-300 text-gray-700 py-2 px-4 rounded-lg font-semibold"
                >
                  Go Back
                </button>
              </div>
            </div>
          </div>
        </div>
        <BottomNav activeTab={undefined} />
      </div>
    );
  }

  if (error && !paymentDetails) {
    return (
      <div className="min-h-screen bg-gray-100">
        <Navbar />
        <div className="p-4">
          <CircleBackButton onClick={() => navigate(-1)} />
          <div className="text-center mt-8 text-red-600">{error}</div>
        </div>
      </div>
    );
  }

  if (!paymentDetails) {
    return (
      <div className="min-h-screen bg-gray-100">
        <Navbar />
        <div className="p-4">
          <CircleBackButton onClick={() => navigate(-1)} />
          <div className="text-center mt-8">Payment details not found.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <Navbar />
      <div className="p-4">
        <CircleBackButton onClick={() => navigate(-1)} />
        <div>
          <h1 className="text-2xl font-bold text-left" style={{color: '#2c4359'}}>Pay your debt</h1>
          <h2 className="text-lg font-semibold text-left" style={{color: '#2c4359'}}>QR CODE</h2>
        </div>
        
        <div className="max-w-md mx-auto bg-white rounded-lg shadow-md p-6 text-center">
          {/* Payment Summary */}
          <div className="mb-6 p-4 bg-gray-50 rounded-lg">
            <p className="text-lg font-semibold mb-2">Payer: {paymentDetails.payerName}</p>
            <p className="text-sm text-gray-600">Owed Amount: ${paymentDetails.owedAmount?.toFixed(2)}</p>
            <p className="text-sm text-gray-600">Already Paid: ${paymentDetails.paidAmount?.toFixed(2)}</p>
            <p className="text-xl font-bold mt-2" style={{color: '#0d78f2'}}>
              Remaining: ${paymentDetails.amountToPay.toFixed(2)}
            </p>
            <p className={`text-sm font-semibold mt-1 ${paymentDetails.settled ? 'text-green-600' : 'text-yellow-600'}`}>
              Status: {paymentDetails.settled ? 'Settled' : 'Pending'}
            </p>
          </div>

          {/* QR Code */}
          {paymentDetails.qrCodeUrl && (
            <img src={paymentDetails.qrCodeUrl} alt="QR Code" className="mx-auto w-48 h-48 mb-4 border rounded-lg" />
          )}
          
          {/* Amount Input */}
          <div className="mb-4">
            <label htmlFor="amountPaid" className="block text-gray-700 font-semibold mb-2 text-left">
              Amount You Pay *
            </label>
            <input
              type="number"
              id="amountPaid"
              value={amountPaid}
              onChange={(e) => handleAmountChange(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
              placeholder="Enter amount you are paying"
              min="0"
              max={paymentDetails.amountToPay}
              step="0.01"
              required
            />
            <p className="text-xs text-gray-500 text-left mt-1">
              Maximum: ${paymentDetails.amountToPay.toFixed(2)}
            </p>
          </div>

          {/* Payment Slip Upload */}
          <div className="mb-4">
            <label htmlFor="paymentSlip" className="block text-gray-700 font-semibold mb-2 text-left">
              Upload Payment Receipt *
            </label>
            <input
              type="file"
              id="paymentSlip"
              onChange={(e) => handleFileChange(e.target.files ? e.target.files[0] : null)}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900"
              accept="image/*,.pdf"
              required
            />
            <p className="text-xs text-gray-500 text-left mt-1">
              Required: Upload receipt proof (JPG, PNG, PDF)
            </p>
            {paymentSlip && (
              <p className="text-xs text-green-600 text-left mt-1">
                ✓ File selected: {paymentSlip.name}
              </p>
            )}
          </div>

          {/* Error Message */}
          {error && (
            <div className="mb-4 p-2 bg-red-100 text-red-700 rounded text-sm">
              {error}
            </div>
          )}

          {/* Pay Button */}
          <button
            onClick={handlePay}
            disabled={submitting || !amountPaid || !paymentSlip}
            className={`w-full text-white font-semibold py-2 px-4 rounded-lg transition duration-300 ${
              submitting || !amountPaid || !paymentSlip
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700'
            }`}
            style={{
              backgroundColor: submitting || !amountPaid || !paymentSlip ? '#9ca3af' : '#0d78f2'
            }}
          >
            {submitting ? 'Processing...' : 'Pay'}
          </button>

          {/* Current User Info */}
          {currentUser && (
            <p className="text-xs text-gray-500 mt-4">
              Paying as: {currentUser.name} ({currentUser.email})
            </p>
          )}
        </div>
      </div>
      <BottomNav activeTab={undefined} />
    </div>
  );
};

export default PayPage;