-- Manual local/test fixture only. Never a migration or application startup step.
-- Edit these three values, then run the whole file with psql -v ON_ERROR_STOP=1.
-- Re-running adds the amount again. No transaction-history row is created.
DO $fund_wallet$
DECLARE
    expected_database text := 'paynest';
    normalized_email text := 'ada.payne@example.test';
    funding_amount_text text := '100.00';
    funding_amount numeric;
    target_wallet_id bigint;
BEGIN
    IF current_database() NOT IN ('paynest', 'paynest_test')
            OR current_database() IS DISTINCT FROM expected_database THEN
        RAISE EXCEPTION 'Funding refused: connect to the explicitly selected local/test database'
            USING ERRCODE = '3D000';
    END IF;
    IF normalized_email IS NULL OR normalized_email = ''
            OR normalized_email <> lower(btrim(normalized_email)) THEN
        RAISE EXCEPTION 'Funding requires the exact normalized account email' USING ERRCODE = '22023';
    END IF;
    -- Validate text before converting: NUMERIC(19,2) alone silently rounds.
    IF funding_amount_text IS NULL
            OR funding_amount_text !~ '^[0-9]+([.][0-9]{1,2})?$' THEN
        RAISE EXCEPTION 'Funding requires a positive precise USD amount with at most two fractional digits'
            USING ERRCODE = '22023';
    END IF;
    funding_amount := funding_amount_text::numeric;
    IF funding_amount <= 0 OR funding_amount > 99999999999999999.99 THEN
        RAISE EXCEPTION 'Funding amount is outside the wallet money range' USING ERRCODE = '22003';
    END IF;
    SELECT w.id INTO STRICT target_wallet_id
        FROM paynest.wallets w JOIN paynest.users u ON u.id = w.user_id
        WHERE u.email = normalized_email AND w.currency = 'USD'
        FOR UPDATE OF w;
    UPDATE paynest.wallets SET balance = balance + funding_amount
        WHERE id = target_wallet_id;
    -- The DO statement is atomic; any missing wallet, overflow, or other error
    -- rolls back the entire statement. Other wallets and history are untouched.
END;
$fund_wallet$;
