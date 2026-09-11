CREATE TRIGGER immutable_experience_facts BEFORE UPDATE OR DELETE ON app.experience_facts
FOR EACH ROW EXECUTE FUNCTION app.reject_immutable_mutation();
