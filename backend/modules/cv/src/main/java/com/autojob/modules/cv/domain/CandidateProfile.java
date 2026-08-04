package com.autojob.modules.cv.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "candidate_profiles")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_candidate_profiles_raw_cv_id",
                def = "{'rawCvId': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_candidate_profiles_owner_created_at",
                def = "{'ownerUserId': 1, 'createdAt': -1}"
        ),
        @CompoundIndex(
                name = "idx_candidate_profiles_parser_version",
                def = "{'parserVersion': 1}"
        )
})
public class CandidateProfile {

    @Id
    private String id;

    private String rawCvId;
    private String ownerUserId;

    private String fullName;
    private String headline;
    private String professionalSummary;
    private String careerObjective;

    private ContactInformation contact;

    @Builder.Default
    private List<LinkEntry> links = List.of();

    @Builder.Default
    private List<String> targetJobTitles = List.of();

    @Builder.Default
    private List<String> targetIndustries = List.of();

    @Builder.Default
    private List<String> preferredLocations = List.of();

    @Builder.Default
    private List<WorkMode> preferredWorkModes = List.of();

    @Builder.Default
    private List<EmploymentType> preferredEmploymentTypes =
            List.of();

    private String expectedSalaryText;
    private String availabilityText;

    @Builder.Default
    private List<Skill> skills = List.of();

    @Builder.Default
    private List<WorkExperience> workExperiences = List.of();

    @Builder.Default
    private List<ProjectExperience> projects = List.of();

    @Builder.Default
    private List<Education> educations = List.of();

    @Builder.Default
    private List<Certification> certifications = List.of();

    @Builder.Default
    private List<LicenseEntry> licenses = List.of();

    @Builder.Default
    private List<LanguageSkill> languages = List.of();

    @Builder.Default
    private List<Award> awards = List.of();

    @Builder.Default
    private List<Publication> publications = List.of();

    @Builder.Default
    private List<VolunteerExperience> volunteerExperiences =
            List.of();

    @Builder.Default
    private List<ProfessionalActivity> activities = List.of();

    @Builder.Default
    private List<TrainingCourse> trainingCourses = List.of();

    @Builder.Default
    private List<String> interests = List.of();

    private Double experienceYears;
    private Seniority seniority;
    private EducationLevel highestEducationLevel;

    @Builder.Default
    private List<String> recentJobTitles = List.of();

    @Builder.Default
    private List<String> recentCompanies = List.of();

    private DetectedLanguage detectedLanguage;
    private String rawText;

    @Builder.Default
    private List<ParsedSection> sections = List.of();

    private String parserVersion;

    @Builder.Default
    private List<String> parserWarnings = List.of();

    private ParseQuality parseQuality;

    private String sourceBucket;
    private String sourceObjectKey;
    private String sourceOriginalFilename;
    private String sourceContentType;
    private long sourceSizeBytes;
    private String sourceSha256;

    private Instant createdAt;
    private Instant updatedAt;

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