-- Convert Postgres enum columns to TEXT so Hibernate's VARCHAR binds work.

-- channel.platform
ALTER TABLE channel
ALTER COLUMN platform TYPE text USING platform::text;

-- rank_snapshot.platform
ALTER TABLE rank_snapshot
ALTER COLUMN platform TYPE text USING platform::text;

-- drop the enum type if nothing else uses it
DROP TYPE IF EXISTS platform;