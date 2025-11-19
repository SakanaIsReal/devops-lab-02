-- V2__increase_image_url_size.sql
-- Increase avatar_url and qr_code_url to support larger base64 data
-- Max 5 MB image = ~6.7 MB base64 + data URL prefix = ~7 MB
-- MEDIUMTEXT supports up to 16 MB (safe for 5 MB images)

ALTER TABLE `users`
  MODIFY COLUMN `avatar_url` MEDIUMTEXT DEFAULT NULL,
  MODIFY COLUMN `qr_code_url` MEDIUMTEXT DEFAULT NULL;
