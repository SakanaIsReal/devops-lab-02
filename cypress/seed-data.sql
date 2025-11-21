-- E2E Test Seed Data
-- This data is inserted before Cypress tests run

INSERT INTO users (avatar_url, email, first_name, last_name, password_hash, phone, qr_code_url, `role`, user_name) VALUES
    ('https://via.placeholder.com/150', 'user@example.com', NULL, NULL, '$2a$12$R4F1hAJD2DH0raTAUdil3./4RM1t1Cus3R3x2WpM..G.sEmB2bDjq', '0958487321', '', 1, 'ggg'),
    (NULL, 'use@example.com', NULL, NULL, '$2a$12$BwM1aIRBlEZPmuJtHb9xIu2trxdq2kXrFYIWE4R6gbMciwDIkxGgS', '', NULL, 1, 'ni'),
    (NULL, 'u@example.com', NULL, NULL, '$2a$12$I.Ut2H8cipdQ/QI9vkBZ7OILBhw7mstUGMDy4A93a3iu0HN6mOfNS', '', NULL, 1, 'ni'),
    (NULL, 'ujdjd@example.com', NULL, NULL, '$2a$12$67qllih/05pPVPIWlIXe6eSKOjwosF3/VqeJEyuo8QPWkcwRgCIfy', '0999999999', NULL, 1, 'ni89'),
    (NULL, 'g@gmail.com', 'ni', 'ni', '$2a$12$C0c8Rpz5UtrG.4iZL90FtOcJR6/y9.LpXme9MGfeORy.TESvdYJkG', '', NULL, 1, 'nn'),
    ('http://localhost:3000/files/7', 'nice@gmail.com', 'ni', 'ni', '$2a$12$U.E1.oT1PsqIy5BQ3Uy1i.5HkNLNnNZuRNY/MYTJ7qqMmcXNtsF5i', '0958487321', '', 1, 'testest'),
    (NULL, 'tedfgh@gmail.com', 'ni', 'ni', '$2a$12$8aDtITE6QGmceMCtYqbr5uQxt/D6tktqrpsj5GXtiey56O7YeUmy2', '09999999', NULL, 1, 'gg'),
    (NULL, 'yuio@gmail.com', 'ni', 'ni', '$2a$12$WCoK6Rjpvy0EpqySPMHI4.N9K5XhWW/5R6eMY0y3G.uJc5RIcWDPG', '09999999', NULL, 1, 'mib'),
    (NULL, 'jj@gmail.com', NULL, NULL, '$2a$12$DX64dP64MCs/A5XckYTB1e4kvFHwAXAI653ISpqsNVeA1Nzc59QXK', '', NULL, 1, 'yupp'),
    ('http://localhost:3000/files/8', 'nicetest@gmail.com', 'navadol', 'somboonkul', '$2a$12$hBPbu7bBx/4TLGMGTdyGz.RKEzo6PompZNV2Ovkf5vSifUqCqSEQG', '9876543210', 'string', 1, 'Man-DevOps'),
    (NULL, 'testcy@gmail.com', 'navadol', 'somboonkul', '$2a$12$yMtjjFyHArlI0BZNS13nyuQFvfFykfdkyyUbs8l3BqaLZNMtNFOC2', '0987654321', NULL, 1, 'Testnice'),
    (NULL, 'suki@gmail.com', NULL, NULL, '$2a$12$/HrLOAeXtijR4OTWSub9HeTD1EKyiIhzDgGmwALPAyf7WzrFVrhda', '0987654321', NULL, 1, 'sukikana'),
    (NULL, 'TestCypress@example.com', NULL, NULL, '$2a$12$V1meuSFMzEd6jY98XXpMEOqndH7KMlfjbFC462/gsLGBxNOkZH.Zy', '1234567890', NULL, 1, 'MxnlodySoTest');
