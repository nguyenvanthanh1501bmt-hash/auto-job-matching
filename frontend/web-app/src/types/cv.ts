export const CV_MAX_FILE_SIZE_BYTES =
  10 * 1024 * 1024;

export const CV_ALLOWED_EXTENSIONS = [
  "pdf",
  "doc",
  "docx"
] as const;

export const CV_ACCEPT =
  ".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document";

export type CvAllowedExtension =
  (typeof CV_ALLOWED_EXTENSIONS)[number];

export type CvProcessingStatus =
  | "UPLOADED"
  | "PARSING"
  | "PARSED"
  | "FAILED";

export type DetectedLanguage =
  | "VI"
  | "EN"
  | "MIXED"
  | "UNKNOWN";

export type LinkType =
  | "LINKEDIN"
  | "GITHUB"
  | "PORTFOLIO"
  | "PERSONAL_WEBSITE"
  | "BEHANCE"
  | "DRIBBBLE"
  | "STACK_OVERFLOW"
  | "PUBLICATION"
  | "SOCIAL_PROFILE"
  | "OTHER";

export type SkillCategory =
  | "TECHNICAL"
  | "SOFTWARE"
  | "TOOL"
  | "EQUIPMENT"
  | "MACHINERY"
  | "DOMAIN_KNOWLEDGE"
  | "BUSINESS"
  | "SALES"
  | "MARKETING"
  | "FINANCE"
  | "ACCOUNTING"
  | "HEALTHCARE"
  | "EDUCATION"
  | "ENGINEERING"
  | "TRADE"
  | "MANAGEMENT"
  | "LEADERSHIP"
  | "COMMUNICATION"
  | "LANGUAGE"
  | "SAFETY"
  | "COMPLIANCE"
  | "OTHER";

export type ProficiencyLevel =
  | "BASIC"
  | "ELEMENTARY"
  | "INTERMEDIATE"
  | "UPPER_INTERMEDIATE"
  | "ADVANCED"
  | "FLUENT"
  | "NATIVE"
  | "UNKNOWN";

export type EmploymentType =
  | "FULL_TIME"
  | "PART_TIME"
  | "CONTRACT"
  | "TEMPORARY"
  | "INTERNSHIP"
  | "FREELANCE"
  | "SEASONAL"
  | "SHIFT_WORK"
  | "UNKNOWN";

export type WorkMode =
  | "ONSITE"
  | "REMOTE"
  | "HYBRID"
  | "UNKNOWN";

export type EducationLevel =
  | "SECONDARY"
  | "HIGH_SCHOOL"
  | "VOCATIONAL"
  | "CERTIFICATE"
  | "DIPLOMA"
  | "ASSOCIATE"
  | "BACHELOR"
  | "MASTER"
  | "DOCTORATE"
  | "PROFESSIONAL_DEGREE"
  | "OTHER"
  | "UNKNOWN";

export type Seniority =
  | "INTERN"
  | "TRAINEE"
  | "FRESHER"
  | "ENTRY_LEVEL"
  | "JUNIOR"
  | "MID"
  | "SENIOR"
  | "LEAD"
  | "SUPERVISOR"
  | "MANAGER"
  | "HEAD"
  | "DIRECTOR"
  | "EXECUTIVE"
  | "UNKNOWN";

export type SectionType =
  | "HEADER"
  | "CONTACT"
  | "SUMMARY"
  | "OBJECTIVE"
  | "SKILLS"
  | "WORK_EXPERIENCE"
  | "PROJECTS"
  | "EDUCATION"
  | "CERTIFICATIONS"
  | "LICENSES"
  | "TRAINING"
  | "LANGUAGES"
  | "AWARDS"
  | "PUBLICATIONS"
  | "VOLUNTEERING"
  | "ACTIVITIES"
  | "INTERESTS"
  | "REFERENCES"
  | "OTHER";

export type RawCvResponse = {
  id: string;
  ownerUserId: string;
  originalFilename: string;
  extension: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  status: CvProcessingStatus;
  uploadedAt: string;
};

export type ContactInformation = {
  email: string | null;
  phone: string | null;
  addressText: string | null;
  city: string | null;
  provinceOrState: string | null;
  country: string | null;
  postalCode: string | null;
};

export type LinkEntry = {
  type: LinkType | null;
  url: string | null;
  label: string | null;
};

export type Skill = {
  name: string | null;
  normalizedName: string | null;
  category: SkillCategory | null;
  proficiencyText: string | null;
  normalizedProficiency:
    | ProficiencyLevel
    | null;
  yearsOfExperience: number | null;
  lastUsedDate: string | null;
  evidenceSources: string[];
};

export type WorkExperience = {
  companyName: string | null;
  companyIndustry: string | null;
  jobTitle: string | null;
  normalizedJobTitle: string | null;
  employmentType: EmploymentType | null;
  location: string | null;
  workMode: WorkMode | null;
  startDate: string | null;
  endDate: string | null;
  current: boolean | null;
  durationMonths: number | null;
  description: string | null;
  responsibilities: string[];
  achievements: string[];
  skills: string[];
  tools: string[];
  equipment: string[];
};

export type ProjectExperience = {
  name: string | null;
  role: string | null;
  domain: string | null;
  startDate: string | null;
  endDate: string | null;
  current: boolean | null;
  description: string | null;
  responsibilities: string[];
  achievements: string[];
  skills: string[];
  tools: string[];
  equipment: string[];
  teamSizeText: string | null;
  projectUrl: string | null;
  repositoryUrl: string | null;
};

export type Education = {
  institutionName: string | null;
  degree: string | null;
  normalizedDegreeLevel:
    | EducationLevel
    | null;
  fieldOfStudy: string | null;
  specialization: string | null;
  startDate: string | null;
  endDate: string | null;
  current: boolean | null;
  grade: string | null;
  achievements: string[];
  description: string | null;
};

export type Certification = {
  name: string | null;
  issuer: string | null;
  issuedDate: string | null;
  expirationDate: string | null;
  expired: boolean | null;
  credentialId: string | null;
  credentialUrl: string | null;
  relatedSkills: string[];
};

export type LicenseEntry = {
  name: string | null;
  issuingAuthority: string | null;
  licenseNumber: string | null;
  issuedDate: string | null;
  expirationDate: string | null;
  expired: boolean | null;
  jurisdiction: string | null;
};

export type TrainingCourse = {
  name: string | null;
  provider: string | null;
  completionDate: string | null;
  durationText: string | null;
  description: string | null;
  relatedSkills: string[];
};

export type LanguageSkill = {
  language: string | null;
  proficiencyText: string | null;
  normalizedProficiency:
    | ProficiencyLevel
    | null;
  framework: string | null;
  score: string | null;
};

export type Award = {
  name: string | null;
  issuer: string | null;
  awardedDate: string | null;
  description: string | null;
};

export type Publication = {
  title: string | null;
  authors: string[];
  publisher: string | null;
  publishedDate: string | null;
  url: string | null;
  description: string | null;
};

export type VolunteerExperience = {
  organizationName: string | null;
  role: string | null;
  startDate: string | null;
  endDate: string | null;
  description: string | null;
  responsibilities: string[];
  skills: string[];
};

export type ProfessionalActivity = {
  name: string | null;
  organization: string | null;
  role: string | null;
  startDate: string | null;
  endDate: string | null;
  description: string | null;
};

export type ParsedSection = {
  sectionType: SectionType | null;
  heading: string | null;
  startOffset: number | null;
  endOffset: number | null;
  text: string | null;
};

export type ParseQuality = {
  overallScore: number | null;
  textExtractionScore: number | null;
  sectionDetectionScore: number | null;
  workExperienceScore: number | null;
  missingImportantFields: string[];
  ambiguousFields: string[];
};

export type CandidateProfileResponse = {
  // Matching dùng candidateProfileId; rawCvId chỉ định CV nguồn của profile.
  candidateProfileId: string;
  rawCvId: string;

  fullName: string | null;
  headline: string | null;
  professionalSummary: string | null;
  careerObjective: string | null;

  contact: ContactInformation | null;

  links: LinkEntry[];

  targetJobTitles: string[];
  targetIndustries: string[];

  preferredLocations: string[];
  preferredWorkModes: WorkMode[];

  preferredEmploymentTypes:
    EmploymentType[];

  expectedSalaryText: string | null;
  availabilityText: string | null;

  skills: Skill[];

  workExperiences: WorkExperience[];

  projects: ProjectExperience[];

  educations: Education[];

  certifications: Certification[];

  licenses: LicenseEntry[];

  languages: LanguageSkill[];

  awards: Award[];

  publications: Publication[];

  volunteerExperiences:
    VolunteerExperience[];

  activities: ProfessionalActivity[];

  trainingCourses: TrainingCourse[];

  interests: string[];

  experienceYears: number | null;

  seniority: Seniority | null;

  highestEducationLevel:
    | EducationLevel
    | null;

  recentJobTitles: string[];

  recentCompanies: string[];

  detectedLanguage:
    | DetectedLanguage
    | null;

  sections: ParsedSection[];

  parserVersion: string | null;

  parserWarnings: string[];

  parseQuality: ParseQuality | null;

  createdAt: string;

  updatedAt: string;
};