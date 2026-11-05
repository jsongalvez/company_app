-- Gives the seeded clients of an existing load-test database the names seed.sql now makes (Client<n>/Load<n> before).
\set ON_ERROR_STOP on
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
WITH c AS (SELECT id, row_number() OVER (ORDER BY first_name, id) AS g FROM client WHERE first_name LIKE 'Client%')
UPDATE client SET first_name = (SELECT f[1 + (c.g * 7) % array_length(f, 1)] FROM names),
                  last_name = (SELECT l[1 + (c.g * 13 + c.g / 97) % array_length(l, 1)] FROM names),
                  phone_number = '09' || lpad(((c.g * 7919) % 1000000000)::text, 9, '0')
FROM c WHERE client.id = c.id;
VACUUM ANALYZE client;
