SELECT 'CREATE DATABASE company_app_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'company_app_test')\gexec
