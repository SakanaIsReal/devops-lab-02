// src/main/java/com/smartsplit/smartsplitback/repository/BalanceQueryRepository.java
package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BalanceQueryRepository extends JpaRepository<Expense, Long> {

    @Query(value = """

        SELECT 
            'YOU_OWE'                                  AS direction,
            e.group_id                                 AS groupId,
            g.name                                     AS groupName,
            e.expense_id                               AS expenseId,
            e.title                                    AS expenseTitle,
            e.payer_user_id                            AS counterpartyUserId,
            up.user_name                               AS counterpartyUserName,
            up.avatar_url                              AS counterpartyAvatarUrl,
            ROUND( SUM(COALESCE(s.share_value, (s.share_percent/100.0)*i.amount))
                   - COALESCE(pay.paid, 0), 2)         AS remaining
        FROM `expenses` e
        JOIN `groups_tbl` g            ON g.group_id = e.group_id
        JOIN `expense_items` i         ON i.expense_id = e.expense_id
        JOIN `expense_item_shares` s   ON s.expense_item_id = i.expense_item_id
                                       AND s.participant_user_id = :userId

        LEFT JOIN (
           SELECT expense_id, from_user_id,
                  SUM(CASE WHEN status = 'VERIFIED' THEN amount ELSE 0 END) AS paid
           FROM `expense_payments`
           GROUP BY expense_id, from_user_id
        ) pay ON pay.expense_id = e.expense_id AND pay.from_user_id = :userId
        JOIN `users` up ON up.user_id = e.payer_user_id
        WHERE e.payer_user_id <> :userId
        GROUP BY e.group_id, g.name, e.expense_id, e.title,
                 e.payer_user_id, up.user_name, up.avatar_url, pay.paid
        HAVING (SUM(COALESCE(s.share_value, (s.share_percent/100.0)*i.amount))
                - COALESCE(pay.paid,0)) > 0.00

        UNION ALL

        /* ===== OWES YOU (คนอื่นเป็นลูกหนี้คุณ) =====
           ผู้ใช้ (:userId) เป็น payer ของ expense นั้น
           ไม่ต้องอยู่ใน group_members เช่นกัน
        */
        SELECT 
            'OWES_YOU'                                 AS direction,
            e.group_id                                 AS groupId,
            g.name                                     AS groupName,
            e.expense_id                               AS expenseId,
            e.title                                    AS expenseTitle,
            s.participant_user_id                      AS counterpartyUserId,
            um.user_name                               AS counterpartyUserName,
            um.avatar_url                              AS counterpartyAvatarUrl,
            ROUND( SUM(COALESCE(s.share_value, (s.share_percent/100.0)*i.amount))
                   - COALESCE(pay.paid, 0), 2)         AS remaining
        FROM `expenses` e
        JOIN `groups_tbl` g            ON g.group_id = e.group_id
        JOIN `expense_items` i         ON i.expense_id = e.expense_id
        JOIN `expense_item_shares` s   ON s.expense_item_id = i.expense_item_id
                                       AND s.participant_user_id <> :userId
        LEFT JOIN (
           SELECT expense_id, from_user_id,
                  SUM(CASE WHEN status = 'VERIFIED' THEN amount ELSE 0 END) AS paid
           FROM `expense_payments`
           GROUP BY expense_id, from_user_id
        ) pay ON pay.expense_id = e.expense_id AND pay.from_user_id = s.participant_user_id
        JOIN `users` um ON um.user_id = s.participant_user_id
        WHERE e.payer_user_id = :userId
        GROUP BY e.group_id, g.name, e.expense_id, e.title,
                 s.participant_user_id, um.user_name, um.avatar_url, pay.paid
        HAVING (SUM(COALESCE(s.share_value, (s.share_percent/100.0)*i.amount))
                - COALESCE(pay.paid,0)) > 0.00
        """, nativeQuery = true)
    List<BalanceRowProjection> findBalancesForUser(@Param("userId") Long userId);
}
