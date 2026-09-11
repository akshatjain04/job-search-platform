CREATE SCHEMA IF NOT EXISTS app;
SET search_path TO app, public;

CREATE TABLE users (id uuid PRIMARY KEY, email text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE candidate_profiles (user_id uuid PRIMARY KEY REFERENCES users(id), data jsonb NOT NULL, updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE experience_facts (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), verified boolean NOT NULL DEFAULT false,
  data jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,user_id)
);
CREATE INDEX experience_facts_owner ON experience_facts(user_id,verified);

CREATE TABLE jobs (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), title text NOT NULL, company text NOT NULL,
  location text NOT NULL, canonical_url text NOT NULL, fingerprint text NOT NULL,
  kind text NOT NULL CHECK(kind IN ('JOB_POSTING','HIRING_POST','REFERRAL_POST')), posted_at timestamptz NOT NULL,
  remote boolean NOT NULL, salary_min integer, salary_max integer, currency text, experience_years integer,
  data jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,user_id), UNIQUE(user_id,canonical_url),
  CHECK(salary_min IS NULL OR salary_min>=0), CHECK(salary_max IS NULL OR salary_max>=COALESCE(salary_min,0))
);
CREATE INDEX jobs_owner_date ON jobs(user_id,posted_at DESC,id);
CREATE INDEX jobs_fingerprint ON jobs(user_id,fingerprint,posted_at);
CREATE INDEX jobs_fts ON jobs USING gin(to_tsvector('english',title || ' ' || company || ' ' || COALESCE(data->>'description','')));
CREATE TABLE job_sources (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), job_id uuid NOT NULL, connector text NOT NULL,
  external_id text NOT NULL, url text NOT NULL, content_hash text NOT NULL, data jsonb NOT NULL,
  FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id), UNIQUE(user_id,connector,external_id), UNIQUE(id,user_id)
);
CREATE INDEX job_sources_job ON job_sources(user_id,job_id);
CREATE TABLE job_requirements (
  user_id uuid NOT NULL, job_id uuid NOT NULL, skill text NOT NULL,
  PRIMARY KEY(job_id,user_id,skill), FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id)
);
CREATE TABLE job_matches (
  user_id uuid NOT NULL, job_id uuid NOT NULL, score numeric(6,2) NOT NULL CHECK(score BETWEEN 0 AND 100),
  eligible boolean NOT NULL, data jsonb NOT NULL, calculated_at timestamptz NOT NULL,
  PRIMARY KEY(job_id,user_id), FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id)
);
CREATE INDEX job_matches_rank ON job_matches(user_id,eligible,score DESC);

CREATE TABLE resumes (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), data jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,user_id)
);
CREATE INDEX resumes_owner ON resumes(user_id,created_at DESC);
CREATE TABLE resume_versions (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), resume_id uuid NOT NULL, job_id uuid NOT NULL,
  data jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,user_id),
  FOREIGN KEY(resume_id,user_id) REFERENCES resumes(id,user_id), FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id)
);
CREATE INDEX resume_versions_job ON resume_versions(user_id,job_id,created_at DESC);
CREATE TABLE recruiters (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), data jsonb NOT NULL, UNIQUE(id,user_id)
);
CREATE TABLE contact_points (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), recruiter_id uuid NOT NULL, type text NOT NULL,
  value text NOT NULL, verification_status text NOT NULL CHECK(verification_status IN ('PUBLIC','PROVIDER_VERIFIED','USER_VERIFIED','UNVERIFIED','INFERRED')),
  data jsonb NOT NULL, UNIQUE(id,user_id), FOREIGN KEY(recruiter_id,user_id) REFERENCES recruiters(id,user_id)
);
CREATE INDEX contact_points_owner ON contact_points(user_id,recruiter_id);
CREATE TABLE job_recruiters (
  user_id uuid NOT NULL, job_id uuid NOT NULL, recruiter_id uuid NOT NULL,
  PRIMARY KEY(user_id,job_id,recruiter_id), FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id), FOREIGN KEY(recruiter_id,user_id) REFERENCES recruiters(id,user_id)
);

CREATE TABLE applications (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), job_id uuid NOT NULL,
  state text NOT NULL CHECK(state IN ('DISCOVERED','SAVED','SHORTLISTED','PREPARING','READY_TO_APPLY','APPLIED','OUTREACH_PREPARED','OUTREACH_SENT','REPLIED','INTERVIEW','OFFER','REJECTED','WITHDRAWN')),
  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, UNIQUE(id,user_id), UNIQUE(user_id,job_id),
  FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id)
);
CREATE INDEX applications_owner_state ON applications(user_id,state);
CREATE TABLE application_events (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, application_id uuid NOT NULL,
  from_state text, to_state text NOT NULL, note text NOT NULL DEFAULT '', at timestamptz NOT NULL,
  FOREIGN KEY(application_id,user_id) REFERENCES applications(id,user_id)
);
CREATE INDEX application_events_history ON application_events(user_id,application_id,at);
CREATE TABLE outreach_messages (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), job_id uuid NOT NULL,
  channel text NOT NULL CHECK(channel IN ('EMAIL','LINKEDIN','WHATSAPP')),
  state text NOT NULL CHECK(state IN ('GENERATING','DRAFT','AWAITING_APPROVAL','REJECTED','APPROVED','QUEUED','SENDING','FAILED','SENT','DELIVERY_UNKNOWN')),
  current_version_id uuid, updated_at timestamptz NOT NULL, UNIQUE(id,user_id), FOREIGN KEY(job_id,user_id) REFERENCES jobs(id,user_id)
);
CREATE INDEX outreach_messages_owner ON outreach_messages(user_id,updated_at DESC);
CREATE TABLE outreach_versions (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, message_id uuid NOT NULL, recipient_id uuid, resume_version_id uuid,
  data jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,user_id), UNIQUE(id,message_id,user_id),
  FOREIGN KEY(message_id,user_id) REFERENCES outreach_messages(id,user_id),
  FOREIGN KEY(recipient_id,user_id) REFERENCES contact_points(id,user_id), FOREIGN KEY(resume_version_id,user_id) REFERENCES resume_versions(id,user_id)
);
ALTER TABLE outreach_messages ADD CONSTRAINT current_message_version_fk FOREIGN KEY(current_version_id,id,user_id) REFERENCES outreach_versions(id,message_id,user_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE approvals (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, message_id uuid NOT NULL, email_version_id uuid NOT NULL,
  resume_version_id uuid NOT NULL, recipient_id uuid NOT NULL, fingerprint text NOT NULL,
  approved_at timestamptz NOT NULL, invalidated_at timestamptz, UNIQUE(id,user_id),
  FOREIGN KEY(email_version_id,message_id,user_id) REFERENCES outreach_versions(id,message_id,user_id),
  FOREIGN KEY(resume_version_id,user_id) REFERENCES resume_versions(id,user_id), FOREIGN KEY(recipient_id,user_id) REFERENCES contact_points(id,user_id)
);
CREATE UNIQUE INDEX approvals_current ON approvals(user_id,message_id) WHERE invalidated_at IS NULL;

CREATE TABLE connector_configs (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), connector text NOT NULL,
  board text NOT NULL, role text NOT NULL DEFAULT '', interval_minutes integer NOT NULL CHECK(interval_minutes>=15),
  enabled boolean NOT NULL, next_run_at timestamptz NOT NULL, UNIQUE(id,user_id), UNIQUE(user_id,connector,board)
);
CREATE INDEX connector_schedule ON connector_configs(next_run_at) WHERE enabled;
CREATE TABLE connector_runs (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, config_id uuid NOT NULL, status text NOT NULL,
  discovered integer NOT NULL, error text, at timestamptz NOT NULL,
  FOREIGN KEY(config_id,user_id) REFERENCES connector_configs(id,user_id)
);
CREATE TABLE audit_events (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), action text NOT NULL, resource_type text NOT NULL,
  resource_id uuid, metadata jsonb NOT NULL DEFAULT '{}', at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_owner ON audit_events(user_id,at DESC);
CREATE TABLE outbox_events (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), event_type text NOT NULL, payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
  attempt_count integer NOT NULL DEFAULT 0 CHECK(attempt_count>=0), max_attempts integer NOT NULL DEFAULT 5 CHECK(max_attempts BETWEEN 1 AND 20),
  available_at timestamptz NOT NULL, locked_at timestamptz, locked_by text, last_error text,
  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, completed_at timestamptz,
  idempotency_key text NOT NULL, UNIQUE(user_id,event_type,idempotency_key)
);
CREATE INDEX outbox_claim ON outbox_events(event_type,available_at,created_at) WHERE status IN ('PENDING','PROCESSING');
CREATE INDEX outbox_owner ON outbox_events(user_id,created_at DESC);

CREATE TABLE mailbox_connections (
  user_id uuid PRIMARY KEY REFERENCES users(id), provider text NOT NULL CHECK(provider IN ('gmail','outlook','test')),
  address text NOT NULL, encrypted_access_token text NOT NULL, encrypted_refresh_token text NOT NULL, expires_at timestamptz NOT NULL
);
CREATE TABLE auth_sessions (
  token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), csrf_token text NOT NULL,
  encrypted_access_token text NOT NULL, encrypted_refresh_token text NOT NULL, token_expires_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auth_session_expiry ON auth_sessions(expires_at);
CREATE TABLE oauth_states (
  state_hash text PRIMARY KEY, flow text NOT NULL, user_id uuid REFERENCES users(id), encrypted_verifier text NOT NULL,
  redirect_uri text NOT NULL, extra jsonb NOT NULL DEFAULT '{}', expires_at timestamptz NOT NULL
);
CREATE TABLE access_credentials (
  token_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), purpose text NOT NULL,
  expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX access_credentials_owner ON access_credentials(user_id,expires_at);
CREATE TABLE extension_codes (
  code_hash text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), challenge text NOT NULL,
  redirect_uri text NOT NULL, expires_at timestamptz NOT NULL
);
CREATE TABLE ai_cache (
  user_id uuid NOT NULL REFERENCES users(id), cache_key text NOT NULL, output jsonb NOT NULL,
  usage jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,cache_key)
);
CREATE TABLE ai_usage (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), task text NOT NULL, provider text NOT NULL,
  model text NOT NULL, input_tokens bigint NOT NULL, output_tokens bigint NOT NULL, latency_ms bigint NOT NULL,
  estimated_cost numeric(15,8), cached boolean NOT NULL, at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_usage_owner ON ai_usage(user_id,at);

-- Immutable evidence cannot be silently edited even through accidental repository updates.
CREATE FUNCTION reject_immutable_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'immutable record: create a new version instead'; END;
$$;
CREATE TRIGGER immutable_resume_versions BEFORE UPDATE OR DELETE ON resume_versions FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER immutable_outreach_versions BEFORE UPDATE OR DELETE ON outreach_versions FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER immutable_application_events BEFORE UPDATE OR DELETE ON application_events FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE TRIGGER immutable_audit_events BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION reject_immutable_mutation();
CREATE FUNCTION protect_approval_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF ROW(NEW.id,NEW.user_id,NEW.message_id,NEW.email_version_id,NEW.resume_version_id,NEW.recipient_id,NEW.fingerprint,NEW.approved_at)
     IS DISTINCT FROM ROW(OLD.id,OLD.user_id,OLD.message_id,OLD.email_version_id,OLD.resume_version_id,OLD.recipient_id,OLD.fingerprint,OLD.approved_at)
     OR (OLD.invalidated_at IS NOT NULL AND NEW.invalidated_at IS DISTINCT FROM OLD.invalidated_at)
  THEN RAISE EXCEPTION 'approval binding is immutable'; END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_approval_binding BEFORE UPDATE ON approvals FOR EACH ROW EXECUTE FUNCTION protect_approval_binding();

-- Keep this private schema out of Supabase's client-facing PostgREST exposed schemas.
REVOKE ALL ON SCHEMA app FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA app FROM PUBLIC;
