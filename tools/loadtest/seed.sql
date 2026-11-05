-- Grows a freshly migrated database (Flyway + DevMain fixtures) to a given scale, keeping every foreign key real.
--   psql -v scale=10 -f seed.sql
-- Scale 1 is a small practice's first month: 3 branches, 30 days, 20 sessions a day at each.
-- Growth goes into both width and history: branches = 3*sqrt(scale), days = 30*sqrt(scale), clients = 600*scale,
-- so scale 1000 is ~95 branches over ~2.6 years (~1.8M sessions). The DevMain fixture branch is always one of
-- them, so its logins (owner, staff) see a full history and an open Today.
\set ON_ERROR_STOP on
SELECT setseed(0.42);
SET synchronous_commit = off;

CREATE TEMP TABLE p AS
SELECT :scale::int AS scale,
       greatest(1, round(3 * sqrt(:scale)))::int AS branches,
       greatest(1, round(30 * sqrt(:scale)))::int AS days,
       (600 * :scale)::int AS clients,
       20 AS sessions_per_day,
       6 AS sales_per_day,
       3 AS expenses_per_day,
       4 AS staff_per_branch,
       ((now() AT TIME ZONE 'Asia/Manila') - interval '4 hours')::date AS today;

-- Branches: the fixture branch plus new ones.
INSERT INTO branch (id, branch_type, name, hidden)
SELECT gen_random_uuid(), 'CLINIC', 'Load Branch ' || g, false
FROM generate_series(2, (SELECT branches FROM p)) g;

CREATE TEMP TABLE b AS SELECT id, row_number() OVER (ORDER BY name) AS n FROM branch WHERE NOT hidden;

-- Staff: practitioners at each branch. The fixture branch keeps its DevMain members too.
CREATE TEMP TABLE staff AS
SELECT gen_random_uuid() AS id, b.id AS branch_id, s AS slot, 'lt' || b.n || '_' || s AS username
FROM b, generate_series(1, (SELECT staff_per_branch FROM p)) s;
CREATE UNIQUE INDEX ON staff (branch_id, slot);

INSERT INTO app_user (id, username, password_hash, status, email, display_name)
SELECT s.id, s.username, (SELECT password_hash FROM app_user WHERE username = 'staff'), 'ACTIVE',
       s.username || '@loadtest.invalid', 'Staff ' || s.username
FROM staff s;
INSERT INTO user_role (user_id, role_id)
SELECT s.id, (SELECT id FROM role WHERE name = CASE WHEN s.slot = 1 THEN 'COORDINATOR' ELSE 'PRACTITIONER' END) FROM staff s;
INSERT INTO user_branch_assignment (id, user_id, branch_id, slot, assigned_by)
SELECT gen_random_uuid(), s.id, s.branch_id, s.slot + 10, (SELECT id FROM app_user WHERE username = 'owner') FROM staff s;

-- Days: history is PAST, today is OPEN (left for the app to make if the fixture branch already has it).
INSERT INTO branch_day (id, branch_id, date, status)
SELECT gen_random_uuid(), b.id, p.today - d, CASE WHEN d = 0 THEN 'OPEN' ELSE 'PAST' END::day_status
FROM b, p, generate_series(0, p.days - 1) d
ON CONFLICT (branch_id, date) DO NOTHING;

CREATE TEMP TABLE bd AS
SELECT d.id, d.branch_id, d.date, (SELECT today FROM p) - d.date AS age FROM branch_day d;
CREATE INDEX ON bd (branch_id);

INSERT INTO attendance (id, branch_day_id, user_id, marked_by, clock_in, clock_out)
SELECT gen_random_uuid(), bd.id, s.id, s.id,
       (bd.date + time '09:00') AT TIME ZONE 'Asia/Manila',
       CASE WHEN bd.age = 0 THEN NULL ELSE (bd.date + time '18:00') AT TIME ZONE 'Asia/Manila' END
FROM bd JOIN staff s ON s.branch_id = bd.branch_id;

-- Clients: real-looking names, so name search sees the spread of matches it would in a real practice.
CREATE TEMP TABLE names AS SELECT
  ARRAY['Maria','Jose','Juan','Ana','Rosa','Pedro','Carmen','Antonio','Elena','Ramon','Luz','Manuel','Teresita','Roberto','Josefina',
        'Ricardo','Corazon','Eduardo','Remedios','Fernando','Lourdes','Rodrigo','Gloria','Danilo','Erlinda','Rolando','Perla','Ernesto',
        'Mylene','Arnel','Rowena','Jun','Marites','Joel','Cherry','Mark','Kristine','John','Angelica','Paolo','Jasmine','Carlo','Liza',
        'Jerome','Divina','Noel','Imelda','Romeo','Nenita','Alfredo','Leticia','Bayani','Ligaya','Dante','Marilou','Efren','Aurora','Rey','Nora','Virgilio'] f,
  ARRAY['Santos','Reyes','Cruz','Bautista','Ocampo','Garcia','Mendoza','Torres','Tomas','Andrade','Castillo','Flores','Villanueva','Ramos',
        'Castro','Rivera','Aquino','Navarro','Salazar','Mercado','Dela Cruz','Del Rosario','Gonzales','Lopez','Perez','Fernandez','Domingo',
        'Gutierrez','Pascual','Soriano','Aguilar','Manalo','Dizon','Valdez','Marquez','Rosales','Santiago','Lim','Tan','Sy','Co','Ong',
        'Villareal','Panganiban','Macaraeg','Dimaculangan','Magbanua','Lacson','Pangilinan','Sison','Medina','Corpuz','Robles','Salvador',
        'Natividad','Esguerra','Galang','Ilagan','Javier','Lacsamana','Mallari','Nepomuceno','Quiambao','Sarmiento','Tolentino','Umali',
        'Vergara','Yap','Zamora','Abad','Alcantara','Bernardo','Cabrera','Dumalagan','Espiritu','Francisco','Gatchalian','Hernandez',
        'Isidro','Jimenez','Katigbak','Laurel','Montemayor','Nolasco','Ordonez','Padilla','Quizon','Roxas','Sumulong','Tuason','Ugalde',
        'Velasco','Wenceslao','Yulo','Zaldivar','Agoncillo','Buenaventura','Concepcion','Dalisay','Evangelista'] l;
INSERT INTO client (id, first_name, last_name, phone_number, address, gender, age, earlier_visits)
SELECT gen_random_uuid(), (SELECT f[1 + (g * 7) % array_length(f, 1)] FROM names), (SELECT l[1 + (g * 13 + g / 97) % array_length(l, 1)] FROM names),
       '09' || lpad(((g * 7919) % 1000000000)::text, 9, '0'),
       'Street ' || (g % 500), CASE WHEN g % 2 = 0 THEN 'F' ELSE 'M' END, 18 + g % 70, 0
FROM generate_series(1, (SELECT clients FROM p)) g;
CREATE TEMP TABLE c AS SELECT id, row_number() OVER () AS n FROM client;
CREATE UNIQUE INDEX ON c (n);

-- Sessions: completed in history, a mix of states today. Client picked so (client, day) pairs stay unique.
CREATE TEMP TABLE s AS
SELECT gen_random_uuid() AS id, bd.id AS branch_day_id, bd.branch_id, bd.date, bd.age, k,
       (SELECT id FROM c WHERE c.n = 1 + ((hashtext(bd.id::text) & 2147483647)::bigint + k * 7919) % (SELECT clients FROM p)) AS client_id
FROM bd, generate_series(1, (SELECT sessions_per_day FROM p)) k;
CREATE UNIQUE INDEX ON s (branch_day_id, k);
ANALYZE s;

INSERT INTO session (id, client_id, branch_day_id, requested_practitioner_id, session_type, is_walk_in, session_status,
                     base_price, final_price, booked_at, created_at, is_voided, created_by)
SELECT s.id, s.client_id, s.branch_day_id,
       (SELECT st.id FROM staff st WHERE st.branch_id = s.branch_id AND st.slot = 1 + s.k % 4),
       CASE WHEN s.k % 5 = 0 THEN 'SECOND_SESSION' ELSE 'REGULAR' END::session_type, s.k % 3 = 0,
       CASE WHEN s.age > 0 OR s.k <= 12 THEN 'COMPLETED' ELSE 'PENDING' END::session_status,
       500, 500, NULL, (s.date + time '09:00' + s.k * interval '20 minutes') AT TIME ZONE 'Asia/Manila',
       s.k = 17 AND s.age > 0,
       (SELECT st.id FROM staff st WHERE st.branch_id = s.branch_id AND st.slot = 1)
FROM s;

INSERT INTO session_practitioner (id, session_id, practitioner_id, slot_at_time)
SELECT gen_random_uuid(), s.id, st.id, st.slot
FROM s JOIN staff st ON st.branch_id = s.branch_id AND st.slot = 1 + s.k % 4;

INSERT INTO session_void (id, session_id, voided_at, voided_by, void_reason)
SELECT gen_random_uuid(), s.id, (s.date + time '17:00') AT TIME ZONE 'Asia/Manila',
       (SELECT st.id FROM staff st WHERE st.branch_id = s.branch_id AND st.slot = 1), 'Load test void'
FROM s WHERE s.k = 17 AND s.age > 0;

-- Products and stock.
INSERT INTO product_category (id, name) VALUES (gen_random_uuid(), 'Load Category') ON CONFLICT DO NOTHING;
INSERT INTO product (id, name, product_category_id, is_active, unit_price, commission_amount)
SELECT gen_random_uuid(), 'Load Product ' || g, (SELECT id FROM product_category WHERE name = 'Load Category'), true, 250, 25
FROM generate_series(1, 20) g;
CREATE TEMP TABLE pr AS SELECT id, row_number() OVER (ORDER BY name) AS n FROM product WHERE name LIKE 'Load Product %';
INSERT INTO branch_inventory (id, branch_id, product_id, current_stock, unit_price, commission_amount)
SELECT gen_random_uuid(), b.id, pr.id, 1000, 250, 25 FROM b, pr ON CONFLICT DO NOTHING;

CREATE TEMP TABLE ps AS
SELECT gen_random_uuid() AS id, bd.id AS branch_day_id, bd.branch_id, bd.date, bd.age, k,
       (SELECT id FROM pr WHERE pr.n = 1 + (k * 7 + bd.age) % 20) AS product_id
FROM bd, generate_series(1, (SELECT sales_per_day FROM p)) k;

INSERT INTO product_sale (id, branch_day_id, session_id, client_id, is_walk_in, product_id, product_name, handled_by,
                          quantity, unit_price_at_time, total_amount_at_time, commission_amount_at_time, sold_at)
SELECT ps.id, ps.branch_day_id,
       CASE WHEN ps.k % 2 = 0 THEN (SELECT s.id FROM s WHERE s.branch_day_id = ps.branch_day_id AND s.k = ps.k) END,
       NULL, ps.k % 2 = 1, ps.product_id, 'Load Product', (SELECT st.id FROM staff st WHERE st.branch_id = ps.branch_id AND st.slot = 2),
       1, 250, 250, 25, (ps.date + time '12:00') AT TIME ZONE 'Asia/Manila'
FROM ps;

INSERT INTO inventory_movement (id, product_id, product_sale_id, branch_id, branch_day_id, reason, quantity_change, moved_by, moved_at)
SELECT gen_random_uuid(), ps.product_id, ps.id, ps.branch_id, ps.branch_day_id, 'SALE', -1,
       (SELECT st.id FROM staff st WHERE st.branch_id = ps.branch_id AND st.slot = 2), (ps.date + time '12:00') AT TIME ZONE 'Asia/Manila'
FROM ps;

-- Money out: expenses and pay.
INSERT INTO expense (id, branch_day_id, amount, category, notes, created_by, created_at)
SELECT gen_random_uuid(), bd.id, 100 + k * 10, (enum_range(NULL::expense_category))[1 + k % 9], 'Load expense',
       (SELECT st.id FROM staff st WHERE st.branch_id = bd.branch_id AND st.slot = 1), (bd.date + time '15:00') AT TIME ZONE 'Asia/Manila'
FROM bd, generate_series(1, (SELECT expenses_per_day FROM p)) k;

INSERT INTO compensation (id, work_branch_day_id, paying_branch_day_id, user_id, amount, assigned_by)
SELECT gen_random_uuid(), bd.id, bd.id, st.id, 600, (SELECT st2.id FROM staff st2 WHERE st2.branch_id = bd.branch_id AND st2.slot = 1)
FROM bd JOIN staff st ON st.branch_id = bd.branch_id WHERE bd.age > 0;

-- Remittances: weekly, one for sessions and one for products, for history older than a week.
CREATE TEMP TABLE r AS
SELECT gen_random_uuid() AS id, b.id AS branch_id, t::remittance_type AS type, (SELECT today FROM p) - (w + 1) * 7 AS start_date
FROM b, generate_series(1, greatest(0, ((SELECT days FROM p) - 8) / 7)) w, unnest(ARRAY['SESSION', 'PRODUCT']) t;

INSERT INTO remittance (id, type, status, branch_id, method, submitted_date, submitted_at, date_range_start, date_range_end, submitted_by)
SELECT r.id, r.type, 'SUBMITTED', r.branch_id, 'BANK_TRANSFER', r.start_date + 7,
       (r.start_date + 7 + time '10:00') AT TIME ZONE 'Asia/Manila', r.start_date, r.start_date + 6,
       (SELECT st.id FROM staff st WHERE st.branch_id = r.branch_id AND st.slot = 1)
FROM r;

INSERT INTO remittance_day_breakdown (id, remittance_id, branch_day_id)
SELECT gen_random_uuid(), r.id, bd.id FROM r JOIN bd ON bd.branch_id = r.branch_id AND bd.date BETWEEN r.start_date AND r.start_date + 6;

INSERT INTO remittance_line (id, remittance_id, type, session_id, created_by, amount)
SELECT gen_random_uuid(), r.id, 'SESSION', s.id, (SELECT st.id FROM staff st WHERE st.branch_id = r.branch_id AND st.slot = 1), 500
FROM r JOIN s ON s.branch_id = r.branch_id AND s.date BETWEEN r.start_date AND r.start_date + 6
WHERE r.type = 'SESSION' AND NOT (s.k = 17);
INSERT INTO remittance_line (id, remittance_id, type, product_sale_id, created_by, amount)
SELECT gen_random_uuid(), r.id, 'PRODUCT_SALE', ps.id, (SELECT st.id FROM staff st WHERE st.branch_id = r.branch_id AND st.slot = 1), 250
FROM r JOIN ps ON ps.branch_id = r.branch_id AND ps.date BETWEEN r.start_date AND r.start_date + 6
WHERE r.type = 'PRODUCT';

UPDATE branch_day d SET status = 'REMITTED' FROM remittance_day_breakdown x WHERE x.branch_day_id = d.id;

-- Audit trail: one INSERT per session, sale and expense, plus an UPDATE for completed sessions.
INSERT INTO audit_log (id, table_name, record_id, action, changed_by, changed_at, new_value, branch_id)
SELECT gen_random_uuid(), 'session', s.id, 'INSERT', (SELECT st.id FROM staff st WHERE st.branch_id = s.branch_id AND st.slot = 1),
       (s.date + time '09:00' + s.k * interval '20 minutes') AT TIME ZONE 'Asia/Manila',
       jsonb_build_object('sessionId', s.id, 'finalPrice', 500, 'clientId', s.client_id), s.branch_id
FROM s;
INSERT INTO audit_log (id, table_name, record_id, action, changed_by, changed_at, old_value, new_value, branch_id)
SELECT gen_random_uuid(), 'session', s.id, 'UPDATE', (SELECT st.id FROM staff st WHERE st.branch_id = s.branch_id AND st.slot = 1 + s.k % 4),
       (s.date + time '10:00' + s.k * interval '20 minutes') AT TIME ZONE 'Asia/Manila',
       jsonb_build_object('sessionId', s.id, 'status', 'PENDING'), jsonb_build_object('sessionId', s.id, 'status', 'COMPLETED'), s.branch_id
FROM s WHERE s.age > 0;
INSERT INTO audit_log (id, table_name, record_id, action, changed_by, changed_at, new_value, branch_id)
SELECT gen_random_uuid(), 'product_sale', ps.id, 'INSERT', (SELECT st.id FROM staff st WHERE st.branch_id = ps.branch_id AND st.slot = 2),
       (ps.date + time '12:00') AT TIME ZONE 'Asia/Manila', jsonb_build_object('productSaleId', ps.id), ps.branch_id
FROM ps;

-- Notifications: one per session to its practitioner.
INSERT INTO notification (id, session_id, user_id, branch_id, is_read, created_at, message, event_type, source_id, target_date, dedup_key)
SELECT gen_random_uuid(), s.id, st.id, s.branch_id, s.age > 0, (s.date + time '09:00') AT TIME ZONE 'Asia/Manila',
       'New session', 'SESSION_ASSIGNED', s.id, s.date, 'lt:' || s.id
FROM s JOIN staff st ON st.branch_id = s.branch_id AND st.slot = 1 + s.k % 4;

-- Bookings: two a day, some in the future.
INSERT INTO visit_booking (id, branch_id, client_id, visit_date, status, created_by)
SELECT gen_random_uuid(), s.branch_id, s.client_id, s.date + 7, CASE WHEN s.age > 7 THEN 'CAME' ELSE 'BOOKED' END,
       (SELECT st.id FROM staff st WHERE st.branch_id = s.branch_id AND st.slot = 1)
FROM s WHERE s.k <= 2;

-- Telemetry and traces: the phone's events and the server's spans, 40 of each per branch per day.
INSERT INTO telemetry_event (session_id, seq, user_id, at, kind, data, received_at)
SELECT gen_random_uuid(), 0, st.id, t, 'nav', '{"route":"#/today"}', t
FROM bd JOIN staff st ON st.branch_id = bd.branch_id AND st.slot = 2,
     LATERAL (SELECT (bd.date + time '09:00') AT TIME ZONE 'Asia/Manila' + g * interval '13 minutes' AS t FROM generate_series(1, 40) g) x;
INSERT INTO trace_span (trace_id, span_id, parent_id, name, start_at, duration_us, error, attrs, received_at)
SELECT md5(bd.id::text || g), left(md5(g || bd.id::text), 16), NULL, 'GET /api/start',
       (bd.date + time '09:00') AT TIME ZONE 'Asia/Manila' + g * interval '13 minutes', 20000, false, '{}',
       (bd.date + time '09:00') AT TIME ZONE 'Asia/Manila' + g * interval '13 minutes'
FROM bd, generate_series(1, 40) g;

ANALYZE;
SELECT 'scale ' || scale || ': ' || branches || ' branches, ' || days || ' days' FROM p;
SELECT relname, n_live_tup FROM pg_stat_user_tables WHERE n_live_tup > 100 ORDER BY n_live_tup DESC;
