package com.autojob.modules.cv.client.dto;

import java.util.List;

public record CvParseResponse(
        String rawCvId,
        String parserVersion,
        Integer extractedTextLength,
        DetectedLanguage detectedLanguage,
        CandidateProfilePayload profile,
        List<String> warnings
) {

    public enum DetectedLanguage {
        VI,
        EN,
        MIXED,
        UNKNOWN
    }

    public enum LinkType {
        LINKEDIN,
        GITHUB,
        PORTFOLIO,
        PERSONAL_WEBSITE,
        BEHANCE,
        DRIBBBLE,
        STACK_OVERFLOW,
        PUBLICATION,
        SOCIAL_PROFILE,
        OTHER
    }

    public enum SkillCategory {
        TECHNICAL,
        SOFTWARE,
        TOOL,
        EQUIPMENT,
        MACHINERY,
        DOMAIN_KNOWLEDGE,
        BUSINESS,
        SALES,
        MARKETING,
        FINANCE,
        ACCOUNTING,
        HEALTHCARE,
        EDUCATION,
        ENGINEERING,
        TRADE,
        MANAGEMENT,
        LEADERSHIP,
        COMMUNICATION,
        LANGUAGE,
        SAFETY,
        COMPLIANCE,
        OTHER
    }

    public enum ProficiencyLevel {
        BASIC,
        ELEMENTARY,
        INTERMEDIATE,
        UPPER_INTERMEDIATE,
        ADVANCED,
        FLUENT,
        NATIVE,
        UNKNOWN
    }

    public enum EmploymentType {
        FULL_TIME,
        PART_TIME,
        CONTRACT,
        TEMPORARY,
        INTERNSHIP,
        FREELANCE,
        SEASONAL,
        SHIFT_WORK,
        UNKNOWN
    }

    public enum WorkMode {
        ONSITE,
        REMOTE,
        HYBRID,
        UNKNOWN
    }

    public enum EducationLevel {
        SECONDARY,
        HIGH_SCHOOL,
        VOCATIONAL,
        CERTIFICATE,
        DIPLOMA,
        ASSOCIATE,
        BACHELOR,
        MASTER,
        DOCTORATE,
        PROFESSIONAL_DEGREE,
        OTHER,
        UNKNOWN
    }

    public enum Seniority {
        INTERN,
        TRAINEE,
        FRESHER,
        ENTRY_LEVEL,
        JUNIOR,
        MID,
        SENIOR,
        LEAD,
        SUPERVISOR,
        MANAGER,
        HEAD,
        DIRECTOR,
        EXECUTIVE,
        UNKNOWN
    }

    public enum SectionType {
        HEADER,
        CONTACT,
        SUMMARY,
        OBJECTIVE,
        SKILLS,
        WORK_EXPERIENCE,
        PROJECTS,
        EDUCATION,
        CERTIFICATIONS,
        LICENSES,
        TRAINING,
        LANGUAGES,
        AWARDS,
        PUBLICATIONS,
        VOLUNTEERING,
        ACTIVITIES,
        INTERESTS,
        REFERENCES,
        OTHER
    }

    public record CandidateProfilePayload(
            String fullName,
            String headline,
            String professionalSummary,
            String careerObjective,
            ContactInformation contact,
            List<LinkEntry> links,
            List<String> targetJobTitles,
            List<String> targetIndustries,
            List<String> preferredLocations,
            List<WorkMode> preferredWorkModes,
            List<EmploymentType> preferredEmploymentTypes,
            String expectedSalaryText,
            String availabilityText,
            List<Skill> skills,
            List<WorkExperience> workExperiences,
            List<ProjectExperience> projects,
            List<Education> educations,
            List<Certification> certifications,
            List<LicenseEntry> licenses,
            List<LanguageSkill> languages,
            List<Award> awards,
            List<Publication> publications,
            List<VolunteerExperience> volunteerExperiences,
            List<ProfessionalActivity> activities,
            List<TrainingCourse> trainingCourses,
            List<String> interests,
            Double experienceYears,
            Seniority seniority,
            EducationLevel highestEducationLevel,
            List<String> recentJobTitles,
            List<String> recentCompanies,
            String rawText,
            List<ParsedSection> sections,
            List<String> parserWarnings,
            ParseQuality parseQuality
    ) {
    }

    public record ContactInformation(
            String email,
            String phone,
            String addressText,
            String city,
            String provinceOrState,
            String country,
            String postalCode
    ) {
    }

    public record LinkEntry(
            LinkType type,
            String url,
            String label
    ) {
    }

    public record Skill(
            String name,
            String normalizedName,
            SkillCategory category,
            String proficiencyText,
            ProficiencyLevel normalizedProficiency,
            Double yearsOfExperience,
            String lastUsedDate,
            List<String> evidenceSources
    ) {
    }

    public record WorkExperience(
            String companyName,
            String companyIndustry,
            String jobTitle,
            String normalizedJobTitle,
            EmploymentType employmentType,
            String location,
            WorkMode workMode,
            String startDate,
            String endDate,
            Boolean current,
            Integer durationMonths,
            String description,
            List<String> responsibilities,
            List<String> achievements,
            List<String> skills,
            List<String> tools,
            List<String> equipment
    ) {
    }

    public record ProjectExperience(
            String name,
            String role,
            String domain,
            String startDate,
            String endDate,
            Boolean current,
            String description,
            List<String> responsibilities,
            List<String> achievements,
            List<String> skills,
            List<String> tools,
            List<String> equipment,
            String teamSizeText,
            String projectUrl,
            String repositoryUrl
    ) {
    }

    public record Education(
            String institutionName,
            String degree,
            EducationLevel normalizedDegreeLevel,
            String fieldOfStudy,
            String specialization,
            String startDate,
            String endDate,
            Boolean current,
            String grade,
            List<String> achievements,
            String description
    ) {
    }

    public record Certification(
            String name,
            String issuer,
            String issuedDate,
            String expirationDate,
            Boolean expired,
            String credentialId,
            String credentialUrl,
            List<String> relatedSkills
    ) {
    }

    public record LicenseEntry(
            String name,
            String issuingAuthority,
            String licenseNumber,
            String issuedDate,
            String expirationDate,
            Boolean expired,
            String jurisdiction
    ) {
    }

    public record TrainingCourse(
            String name,
            String provider,
            String completionDate,
            String durationText,
            String description,
            List<String> relatedSkills
    ) {
    }

    public record LanguageSkill(
            String language,
            String proficiencyText,
            ProficiencyLevel normalizedProficiency,
            String framework,
            String score
    ) {
    }

    public record Award(
            String name,
            String issuer,
            String awardedDate,
            String description
    ) {
    }

    public record Publication(
            String title,
            List<String> authors,
            String publisher,
            String publishedDate,
            String url,
            String description
    ) {
    }

    public record VolunteerExperience(
            String organizationName,
            String role,
            String startDate,
            String endDate,
            String description,
            List<String> responsibilities,
            List<String> skills
    ) {
    }

    public record ProfessionalActivity(
            String name,
            String organization,
            String role,
            String startDate,
            String endDate,
            String description
    ) {
    }

    public record ParsedSection(
            SectionType sectionType,
            String heading,
            Integer startOffset,
            Integer endOffset,
            String text
    ) {
    }

    public record ParseQuality(
            Double overallScore,
            Double textExtractionScore,
            Double sectionDetectionScore,
            Double workExperienceScore,
            List<String> missingImportantFields,
            List<String> ambiguousFields
    ) {
    }
}