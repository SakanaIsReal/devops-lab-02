
package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpenseStatus;
import com.smartsplit.smartsplitback.model.ExpenseType;
import com.smartsplit.smartsplitback.model.Group;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExpenseServiceTest {

    @Mock private ExpenseRepository repo;
    @InjectMocks private ExpenseService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== Helpers (หลีกเลี่ยงการเรียก setter ที่โมเดลอาจไม่มี) =====
    private static Group group(Long id) {
        Group g = new Group();
        // สมมติว่ามี setId(); ถ้าไม่มี ไม่จำเป็นต่อเทสต์นี้
        try { Group.class.getMethod("setId", Long.class).invoke(g, id); } catch (Exception ignored) {}
        return g;
    }

    private static User user(Long id, String email) {
        User u = new User();
        try { User.class.getMethod("setId", Long.class).invoke(u, id); } catch (Exception ignored) {}
        try { User.class.getMethod("setEmail", String.class).invoke(u, email); } catch (Exception ignored) {}
        return u;
    }

    private static Expense expense(Long id, Long groupId, Long payerId, String title, String amount) {
        Expense e = new Expense();
        e.setId(id);
        e.setGroup(group(groupId));
        e.setPayer(user(payerId, "u@example.com"));
        e.setTitle(title);
        e.setAmount(new BigDecimal(amount));
        e.setStatus(ExpenseStatus.OPEN);
        e.setType(ExpenseType.EQUAL);
        return e;
    }

    // ========================= list() =========================
    @Nested
    @DisplayName("list()")
    class ListAll {
        @Test
        @DisplayName("มีข้อมูล → คืนรายการทั้งหมดจาก repo")
        void list_hasData() {
            when(repo.findAll()).thenReturn(List.of(
                    expense(1L, 10L, 2L, "Food", "100.00"),
                    expense(2L, 10L, 3L, "Taxi", "50.00")
            ));

            List<Expense> result = service.list();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            verify(repo).findAll();
        }

        @Test
        @DisplayName("ไม่มีข้อมูล → คืนลิสต์ว่าง")
        void list_empty() {
            when(repo.findAll()).thenReturn(List.of());

            List<Expense> result = service.list();

            assertThat(result).isEmpty();
            verify(repo).findAll();
        }
    }

    // ========================= listByGroup(groupId) =========================
    @Nested
    @DisplayName("listByGroup(groupId)")
    class ListByGroup {
        @Test
        @DisplayName("มีรายการในกลุ่ม → คืนลิสต์ที่ตรง groupId")
        void byGroup_hasData() {
            Long groupId = 10L;
            when(repo.findByGroup_Id(groupId)).thenReturn(List.of(
                    expense(1L, groupId, 2L, "Food", "100.00")
            ));

            List<Expense> result = service.listByGroup(groupId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getGroup()).isNotNull();
            // ถ้า Group ไม่มี getId() ก็ไม่จำเป็นต้อง assert id ของ group
            verify(repo).findByGroup_Id(groupId);
        }

        @Test
        @DisplayName("ไม่มีรายการในกลุ่ม → คืนลิสต์ว่าง")
        void byGroup_empty() {
            Long groupId = 11L;
            when(repo.findByGroup_Id(groupId)).thenReturn(List.of());

            List<Expense> result = service.listByGroup(groupId);

            assertThat(result).isEmpty();
            verify(repo).findByGroup_Id(groupId);
        }
    }

    // ========================= listByPayer(userId) =========================
    @Nested
    @DisplayName("listByPayer(userId)")
    class ListByPayer {
        @Test
        @DisplayName("มีรายการที่ผู้ใช้เป็นผู้จ่าย → คืนลิสต์ที่ตรง userId")
        void byPayer_hasData() {
            Long userId = 3L;
            when(repo.findByPayer_Id(userId)).thenReturn(List.of(
                    expense(2L, 10L, userId, "Taxi", "50.00")
            ));

            List<Expense> result = service.listByPayer(userId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPayer()).isNotNull();
            verify(repo).findByPayer_Id(userId);
        }

        @Test
        @DisplayName("ไม่มีรายการที่ผู้ใช้เป็นผู้จ่าย → คืนลิสต์ว่าง")
        void byPayer_empty() {
            Long userId = 4L;
            when(repo.findByPayer_Id(userId)).thenReturn(List.of());

            List<Expense> result = service.listByPayer(userId);

            assertThat(result).isEmpty();
            verify(repo).findByPayer_Id(userId);
        }
    }

    // ========================= get(id) =========================
    @Nested
    @DisplayName("get(id)")
    class GetOne {
        @Test
        @DisplayName("พบ → คืน Expense")
        void get_found() {
            when(repo.findById(100L)).thenReturn(Optional.of(
                    expense(100L, 10L, 2L, "Hotel", "300.00")
            ));

            Expense e = service.get(100L);

            assertThat(e).isNotNull();
            assertThat(e.getTitle()).isEqualTo("Hotel");
            verify(repo).findById(100L);
        }

        @Test
        @DisplayName("ไม่พบ → คืน null")
        void get_notFound() {
            when(repo.findById(999L)).thenReturn(Optional.empty());

            Expense e = service.get(999L);

            assertThat(e).isNull();
            verify(repo).findById(999L);
        }
    }

    // ========================= save(expense) =========================
    @Nested
    @DisplayName("save(expense)")
    class SaveOne {
        @Test
        @DisplayName("บันทึกสำเร็จ → คืนค่า entity (ใส่ id หลัง save)")
        void save_ok() {
            Expense toSave = expense(null, 10L, 2L, "Snacks", "20.00");

            when(repo.save(any(Expense.class))).thenAnswer(inv -> {
                Expense e = inv.getArgument(0);
                e.setId(500L);
                return e;
            });

            Expense saved = service.save(toSave);

            assertThat(saved.getId()).isEqualTo(500L);
            assertThat(saved.getTitle()).isEqualTo("Snacks");
            assertThat(saved.getGroup()).isNotNull();
            assertThat(saved.getPayer()).isNotNull();
            verify(repo).save(toSave);
        }
    }

    // ========================= delete(id) =========================
    @Nested
    @DisplayName("delete(id)")
    class DeleteOne {
        @Test
        @DisplayName("เรียกลบตาม id ที่กำหนด")
        void delete_ok() {
            service.delete(777L);
            verify(repo).deleteById(777L);
        }
    }
}
