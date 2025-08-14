-- Types
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'platform') THEN
CREATE TYPE platform AS ENUM ('YOUTUBE', 'TWITCH');
END IF;
END $$;

-- Tables
CREATE TABLE IF NOT EXISTS channel (
                                       id BIGSERIAL PRIMARY KEY,
                                       platform platform NOT NULL,
                                       platform_id TEXT NOT NULL,
                                       handle TEXT NOT NULL,
                                       title TEXT NOT NULL,
                                       avatar_url TEXT,
                                       country TEXT,
                                       created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(platform, platform_id)
    );

CREATE TABLE IF NOT EXISTS daily_stat (
                                          id BIGSERIAL PRIMARY KEY,
                                          channel_id BIGINT NOT NULL REFERENCES channel(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    subscribers BIGINT,
    views BIGINT,
    videos BIGINT,
    followers BIGINT,
    live_views BIGINT,
    UNIQUE(channel_id, snapshot_date)
    );

CREATE TABLE IF NOT EXISTS rank_snapshot (
                                             id BIGSERIAL PRIMARY KEY,
                                             snapshot_date DATE NOT NULL,
                                             platform platform NOT NULL,
                                             metric TEXT NOT NULL,
                                             channel_id BIGINT NOT NULL REFERENCES channel(id) ON DELETE CASCADE,
    rank INT NOT NULL
    );

-- Latest stat view for fast leaderboards
CREATE OR REPLACE VIEW v_latest_stat AS
SELECT DISTINCT ON (ds.channel_id)
    ds.channel_id,
    ds.snapshot_date,
    ds.subscribers,
    ds.views,
    ds.videos,
    ds.followers,
    ds.live_views
FROM daily_stat ds
ORDER BY ds.channel_id, ds.snapshot_date DESC;