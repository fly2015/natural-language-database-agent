SELECT 'CREATE DATABASE nlda_governance'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nlda_governance')\gexec

SELECT 'CREATE DATABASE nlda_retrieval'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nlda_retrieval')\gexec
