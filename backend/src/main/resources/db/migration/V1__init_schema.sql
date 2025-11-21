-- ราก: ไม่มีใครอ้างถึง
CREATE TABLE `users` (
                         `user_id` bigint NOT NULL AUTO_INCREMENT,
                         `avatar_url` varchar(500) DEFAULT NULL,
                         `email` varchar(120) NOT NULL,
                         `first_name` varchar(80) DEFAULT NULL,
                         `last_name` varchar(80) DEFAULT NULL,
                         `password_hash` varchar(100) NOT NULL,
                         `phone` varchar(30) DEFAULT NULL,
                         `qr_code_url` varchar(500) DEFAULT NULL,
                         `role` int NOT NULL,
                         `user_name` varchar(80) DEFAULT NULL,
                         PRIMARY KEY (`user_id`),
                         UNIQUE KEY `idx_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ราก: owner_user_id FK → users (ต้องสร้าง users ก่อน)
CREATE TABLE `groups_tbl` (
                              `group_id` bigint NOT NULL AUTO_INCREMENT,
                              `cover_image_url` varchar(500) DEFAULT NULL,
                              `name` varchar(120) NOT NULL,
                              `owner_user_id` bigint NOT NULL,
                              PRIMARY KEY (`group_id`),
                              KEY `fk_groups_owner` (`owner_user_id`),
                              CONSTRAINT `fk_groups_owner`
                                  FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK → groups_tbl, users (ต้องตามหลังสองตารางบน)
CREATE TABLE `group_members` (
                                 `group_id` bigint NOT NULL,
                                 `user_id` bigint NOT NULL,
                                 PRIMARY KEY (`group_id`,`user_id`),
                                 KEY `fk_group_members_user` (`user_id`),
                                 CONSTRAINT `fk_group_members_group`
                                     FOREIGN KEY (`group_id`) REFERENCES `groups_tbl` (`group_id`) ON DELETE CASCADE,
                                 CONSTRAINT `fk_group_members_user`
                                     FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK → groups_tbl, users
CREATE TABLE `expenses` (
                            `expense_id` bigint NOT NULL AUTO_INCREMENT,
                            `amount` decimal(18,2) NOT NULL,
                            `created_at` datetime(6) NOT NULL,
                            `exchange_rates_json` text,
                            `status` enum('CANCELED','OPEN','SETTLED') NOT NULL,
                            `title` varchar(200) NOT NULL,
                            `type` enum('CUSTOM','EQUAL','PERCENTAGE') NOT NULL,
                            `group_id` bigint NOT NULL,
                            `payer_user_id` bigint NOT NULL,
                            PRIMARY KEY (`expense_id`),
                            KEY `idx_expenses_group` (`group_id`),
                            KEY `idx_expenses_payer` (`payer_user_id`),
                            CONSTRAINT `fk_expenses_group`
                                FOREIGN KEY (`group_id`) REFERENCES `groups_tbl` (`group_id`) ON DELETE CASCADE,
                            CONSTRAINT `fk_expenses_payer`
                                FOREIGN KEY (`payer_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK → expenses
CREATE TABLE `expense_items` (
                                 `expense_item_id` bigint NOT NULL AUTO_INCREMENT,
                                 `amount` decimal(19,2) NOT NULL,
                                 `currency` varchar(3) NOT NULL,
                                 `name` varchar(200) NOT NULL,
                                 `expense_id` bigint NOT NULL,
                                 PRIMARY KEY (`expense_item_id`),
                                 KEY `idx_expense_items_expense` (`expense_id`),
                                 CONSTRAINT `fk_expense_items_expense`
                                     FOREIGN KEY (`expense_id`) REFERENCES `expenses` (`expense_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK → expense_items, users
CREATE TABLE `expense_item_shares` (
                                       `share_id` bigint NOT NULL AUTO_INCREMENT,
                                       `share_original_value` decimal(18,6) NOT NULL,
                                       `share_percent` decimal(9,6) DEFAULT NULL,
                                       `share_value` decimal(18,6) NOT NULL,
                                       `expense_item_id` bigint NOT NULL,
                                       `participant_user_id` bigint NOT NULL,
                                       PRIMARY KEY (`share_id`),
                                       KEY `idx_item_shares_item` (`expense_item_id`),
                                       KEY `idx_item_shares_participant` (`participant_user_id`),
                                       CONSTRAINT `fk_item_share_item`
                                           FOREIGN KEY (`expense_item_id`) REFERENCES `expense_items` (`expense_item_id`),
                                       CONSTRAINT `fk_item_share_participant`
                                           FOREIGN KEY (`participant_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK → expenses, users
CREATE TABLE `expense_payments` (
                                    `payment_id` bigint NOT NULL AUTO_INCREMENT,
                                    `amount` decimal(19,2) NOT NULL,
                                    `created_at` datetime(6) NOT NULL,
                                    `status` enum('PENDING','REJECTED','VERIFIED') NOT NULL,
                                    `verified_at` datetime(6) DEFAULT NULL,
                                    `expense_id` bigint NOT NULL,
                                    `from_user_id` bigint NOT NULL,
                                    PRIMARY KEY (`payment_id`),
                                    KEY `idx_payments_expense` (`expense_id`),
                                    KEY `idx_payments_from_user` (`from_user_id`),
                                    KEY `idx_payments_status` (`status`),
                                    CONSTRAINT `fk_payment_expense`
                                        FOREIGN KEY (`expense_id`) REFERENCES `expenses` (`expense_id`),
                                    CONSTRAINT `fk_payment_from_user`
                                        FOREIGN KEY (`from_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FK → expense_payments
CREATE TABLE `payment_receipts` (
                                    `receipt_id` bigint NOT NULL AUTO_INCREMENT,
                                    `file_url` varchar(1000) NOT NULL,
                                    `payment_id` bigint NOT NULL,
                                    PRIMARY KEY (`receipt_id`),
                                    UNIQUE KEY `UK50ip0w8r9wb960x3h8vjnwtdd` (`payment_id`),
                                    KEY `idx_receipts_payment` (`payment_id`),
                                    CONSTRAINT `fk_receipt_payment`
                                        FOREIGN KEY (`payment_id`) REFERENCES `expense_payments` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE `stored_files` (
                                `id` bigint NOT NULL AUTO_INCREMENT,
                                `content_type` varchar(100) DEFAULT NULL,
                                `created_at` datetime(6) NOT NULL,
                                `data_url` longtext NOT NULL,
                                `ext` varchar(10) DEFAULT NULL,
                                `folder` varchar(100) NOT NULL,
                                `original_name` varchar(255) DEFAULT NULL,
                                `size_bytes` bigint DEFAULT NULL,
                                PRIMARY KEY (`id`),
                                KEY `idx_stored_files_folder` (`folder`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
