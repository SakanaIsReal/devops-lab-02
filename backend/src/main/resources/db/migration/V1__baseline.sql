-- V1__baseline.sql  (MySQL 8, utf8mb4_0900_ai_ci)

-- 1) ผู้ใช้
CREATE TABLE `users` (
                         `user_id` BIGINT NOT NULL AUTO_INCREMENT,
                         `avatar_url` VARCHAR(500) DEFAULT NULL,
                         `email` VARCHAR(120) NOT NULL,
                         `first_name` VARCHAR(80) DEFAULT NULL,
                         `last_name` VARCHAR(80) DEFAULT NULL,
                         `password_hash` VARCHAR(100) NOT NULL,
                         `phone` VARCHAR(30) DEFAULT NULL,
                         `qr_code_url` VARCHAR(500) DEFAULT NULL,
                         `role` INT NOT NULL,
                         `user_name` VARCHAR(80) DEFAULT NULL,
                         PRIMARY KEY (`user_id`),
                         UNIQUE KEY `idx_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) กลุ่ม
CREATE TABLE `groups_tbl` (
                              `group_id` BIGINT NOT NULL AUTO_INCREMENT,
                              `cover_image_url` VARCHAR(500) DEFAULT NULL,
                              `name` VARCHAR(120) NOT NULL,
                              `owner_user_id` BIGINT NOT NULL,
                              PRIMARY KEY (`group_id`),
                              KEY `fk_groups_owner` (`owner_user_id`),
                              CONSTRAINT `fk_groups_owner`
                                  FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`user_id`)
                                      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3) รายการค่าใช้จ่าย
CREATE TABLE `expenses` (
                            `expense_id` BIGINT NOT NULL AUTO_INCREMENT,
                            `amount` DECIMAL(18,2) NOT NULL,
                            `created_at` DATETIME(6) NOT NULL,
                            `exchange_rates_json` TEXT,
                            `status` ENUM('CANCELED','OPEN','SETTLED') NOT NULL,
                            `title` VARCHAR(200) NOT NULL,
                            `type` ENUM('CUSTOM','EQUAL','PERCENTAGE') NOT NULL,
                            `group_id` BIGINT NOT NULL,
                            `payer_user_id` BIGINT NOT NULL,
                            PRIMARY KEY (`expense_id`),
                            KEY `idx_expenses_group` (`group_id`),
                            KEY `idx_expenses_payer` (`payer_user_id`),
                            CONSTRAINT `fk_expenses_group`
                                FOREIGN KEY (`group_id`) REFERENCES `groups_tbl` (`group_id`)
                                    ON DELETE CASCADE,
                            CONSTRAINT `fk_expenses_payer`
                                FOREIGN KEY (`payer_user_id`) REFERENCES `users` (`user_id`)
                                    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4) ไอเท็มย่อยของค่าใช้จ่าย
CREATE TABLE `expense_items` (
                                 `expense_item_id` BIGINT NOT NULL AUTO_INCREMENT,
                                 `amount` DECIMAL(19,2) NOT NULL,
                                 `currency` VARCHAR(3) NOT NULL,
                                 `name` VARCHAR(200) NOT NULL,
                                 `expense_id` BIGINT NOT NULL,
                                 PRIMARY KEY (`expense_item_id`),
                                 KEY `idx_expense_items_expense` (`expense_id`),
                                 CONSTRAINT `fk_expense_items_expense`
                                     FOREIGN KEY (`expense_id`) REFERENCES `expenses` (`expense_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5) การแบ่งส่วนแชร์ของไอเท็ม
CREATE TABLE `expense_item_shares` (
                                       `share_id` BIGINT NOT NULL AUTO_INCREMENT,
                                       `share_original_value` DECIMAL(19,6) DEFAULT NULL,
                                       `share_percent` DECIMAL(5,6) DEFAULT NULL,
                                       `share_value` DECIMAL(19,6) DEFAULT NULL,
                                       `expense_item_id` BIGINT NOT NULL,
                                       `participant_user_id` BIGINT NOT NULL,
                                       PRIMARY KEY (`share_id`),
                                       KEY `idx_item_shares_item` (`expense_item_id`),
                                       KEY `idx_item_shares_participant` (`participant_user_id`),
                                       CONSTRAINT `fk_item_share_item`
                                           FOREIGN KEY (`expense_item_id`) REFERENCES `expense_items` (`expense_item_id`),
                                       CONSTRAINT `fk_item_share_participant`
                                           FOREIGN KEY (`participant_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6) สมาชิกกลุ่ม (many-to-many)
CREATE TABLE `group_members` (
                                 `group_id` BIGINT NOT NULL,
                                 `user_id` BIGINT NOT NULL,
                                 PRIMARY KEY (`group_id`,`user_id`),
                                 KEY `fk_group_members_user` (`user_id`),
                                 CONSTRAINT `fk_group_members_group`
                                     FOREIGN KEY (`group_id`) REFERENCES `groups_tbl` (`group_id`)
                                         ON DELETE CASCADE,
                                 CONSTRAINT `fk_group_members_user`
                                     FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
                                         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 7) การชำระเงินของค่าใช้จ่าย
CREATE TABLE `expense_payments` (
                                    `payment_id` BIGINT NOT NULL AUTO_INCREMENT,
                                    `amount` DECIMAL(19,2) NOT NULL,
                                    `created_at` DATETIME(6) NOT NULL,
                                    `status` ENUM('PENDING','REJECTED','VERIFIED') NOT NULL,
                                    `verified_at` DATETIME(6) DEFAULT NULL,
                                    `expense_id` BIGINT NOT NULL,
                                    `from_user_id` BIGINT NOT NULL,
                                    PRIMARY KEY (`payment_id`),
                                    KEY `idx_payments_expense` (`expense_id`),
                                    KEY `idx_payments_from_user` (`from_user_id`),
                                    KEY `idx_payments_status` (`status`),
                                    CONSTRAINT `fk_payment_expense`
                                        FOREIGN KEY (`expense_id`) REFERENCES `expenses` (`expense_id`),
                                    CONSTRAINT `fk_payment_from_user`
                                        FOREIGN KEY (`from_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 8) สลิป/ไฟล์อ้างอิงการชำระ
CREATE TABLE `payment_receipts` (
                                    `receipt_id` BIGINT NOT NULL AUTO_INCREMENT,
                                    `file_url` VARCHAR(1000) NOT NULL,
                                    `payment_id` BIGINT NOT NULL,
                                    PRIMARY KEY (`receipt_id`),
                                    UNIQUE KEY `UK50ip0w8r9wb960x3h8vjnwtdd` (`payment_id`),
                                    KEY `idx_receipts_payment` (`payment_id`),
                                    CONSTRAINT `fk_receipt_payment`
                                        FOREIGN KEY (`payment_id`) REFERENCES `expense_payments` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 9) ที่เก็บไฟล์ (ถ้าระบบใช้อัปโหลดภายใน)
CREATE TABLE `stored_files` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT,
                                `content_type` VARCHAR(100) DEFAULT NULL,
                                `created_at` DATETIME(6) NOT NULL,
                                `data_url` LONGTEXT NOT NULL,
                                `ext` VARCHAR(10) DEFAULT NULL,
                                `folder` VARCHAR(100) NOT NULL,
                                `original_name` VARCHAR(255) DEFAULT NULL,
                                `size_bytes` BIGINT DEFAULT NULL,
                                PRIMARY KEY (`id`),
                                KEY `idx_stored_files_folder` (`folder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
