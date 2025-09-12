package com.smartsplit.smartsplitback.model;

public enum PaymentStatus {
    PENDING,     // รอการตรวจสอบ/ยืนยัน
    VERIFIED,    // ยืนยันแล้ว
    REJECTED     // ปฏิเสธ/ไม่ผ่าน
}
