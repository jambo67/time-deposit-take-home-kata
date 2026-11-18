-- Optional demonstration data. Run once after the application's migrations have completed.
INSERT INTO "timeDeposits" (id, "planType", balance, days) VALUES
    (1, 'basic', 1200.00, 31),
    (2, 'student', 1200.00, 365),
    (3, 'premium', 1200.00, 46),
    (4, 'student', 1200.00, 366);

-- Withdrawals are stored history; no rule says to deduct them again during an interest update.
INSERT INTO withdrawals (id, "timeDepositId", amount, date) VALUES
    (1, 1, 50.00, '2026-01-15');
