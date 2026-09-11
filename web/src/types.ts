export type Id = string;
export interface Session {
  id: Id;
  email: string;
  csrf: string;
  method: string;
}
export interface Identity {
  name: string;
  email: string;
  phone: string;
  location: string;
  links: string[];
}
export interface Preferences {
  roles: string[];
  excludedRoles: string[];
  companies: string[];
  excludedCompanies: string[];
  locations: string[];
  remoteOnly: boolean;
  minimumSalary: number | null;
  currency: string;
  workAuthorization: string;
  noticePeriod: string;
  tone: string;
  resumeTemplate: string;
  outreachTemplates: Record<string, string>;
  alertThreshold: number;
}
export interface Profile {
  userId: Id;
  identity: Identity;
  workHistory: {
    company: string;
    role: string;
    from: string;
    to: string | null;
    description: string;
  }[];
  projects: { name: string; description: string; url: string; skills: string[] }[];
  education: { institution: string; qualification: string; dates: string }[];
  skills: string[];
  certifications: string[];
  achievements: string[];
  preferences: Preferences;
}
export interface Fact {
  id: Id;
  userId: Id;
  company: string;
  context: string;
  statement: string;
  skills: string[];
  metrics: Record<string, string>;
  from: string | null;
  to: string | null;
  provenance: string;
  verified: boolean;
}
export interface Job {
  id: Id;
  userId: Id;
  title: string;
  company: string;
  location: string;
  description: string;
  kind: 'JOB_POSTING' | 'HIRING_POST' | 'REFERRAL_POST';
  remote: boolean;
  employmentType: string;
  salaryMin: number | null;
  salaryMax: number | null;
  currency: string;
  experienceYears: number | null;
  skills: string[];
  postedAt: string;
  canonicalUrl: string;
}
export interface Match {
  score: number;
  eligible: boolean;
  matchedSkills: string[];
  missingSkills: string[];
  relevantExperience: string[];
  dimensions: Record<string, number>;
  explanation: string[];
}
export interface Source {
  id: Id;
  connector: string;
  url: string;
  contentHash: string;
  kind: string;
  observedAt: string;
}
export interface Contact {
  id: Id;
  name: string;
  value: string;
  type: 'EMAIL' | 'LINKEDIN' | 'PHONE';
  sourceUrl: string;
  confidence: number;
  verificationStatus: string;
  verificationMethod: string;
}
export interface BaseResume {
  id: Id;
  filename: string;
  extractedText: string;
  createdAt: string;
  parsed?: {
    status: string;
    publishedEmails: string[];
    listedSkills: string[];
    sections: {
      kind: string;
      sourceHeading: string;
      lines: { lineNumber: number; text: string }[];
    }[];
  };
}
export interface Scores {
  compatibility: number;
  parsing: number;
  recommended: boolean;
  dimensions: Record<string, number>;
  explanations: string[];
}
export interface ResumeVersion {
  id: Id;
  resumeId: Id;
  jobId: Id;
  createdAt: string;
  pdfHash: string;
  scoringHistory: Scores[];
  content: {
    identity: Identity;
    skills: string[];
    education?: { institution: string; qualification: string; dates: string }[];
    sections: { heading: string; bullets: { factId: Id; text: string }[] }[];
  };
}
export interface Application {
  id: Id;
  jobId: Id;
  state: string;
  updatedAt: string;
}
export interface ApplicationEvent {
  id: Id;
  from: string;
  to: string;
  note: string;
  at: string;
}
export interface Message {
  id: Id;
  jobId: Id;
  channel: string;
  state: string;
  currentVersionId: Id;
  updatedAt: string;
}
export interface MessageVersion {
  id: Id;
  recipientId: Id;
  recipientValue: string;
  subject: string;
  body: string;
  resumeVersionId: Id;
  attachmentHash: string;
}
export interface Preview {
  message: Message;
  version: MessageVersion;
  recipient: Contact | null;
  resume: ResumeVersion | null;
  fingerprint: string;
  approvalId: string | null;
}
export interface Task {
  id: Id;
  eventType: string;
  status: string;
  attemptCount: number;
  maxAttempts: number;
  lastError: string | null;
  payload: Record<string, string>;
}
export interface Connector {
  id: Id;
  connector: string;
  board: string;
  role: string;
  intervalMinutes: number;
  enabled: boolean;
  nextRunAt: string;
}
