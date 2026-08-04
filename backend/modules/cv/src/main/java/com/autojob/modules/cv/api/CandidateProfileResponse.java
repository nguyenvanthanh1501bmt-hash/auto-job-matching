package com.autojob.modules.cv.api;

import com.autojob.modules.cv.domain.CandidateProfile;

import java.time.Instant;
import java.util.List;

public record CandidateProfileResponse(
        String rawCvId,
        String fullName,
        String headline,
        String professionalSummary,
        String careerObjective,
        CandidateProfile.ContactInformation contact,
        List<CandidateProfile.LinkEntry> links,
        List<String> targetJobTitles,
        List<String> targetIndustries,
        List<String> preferredLocations,
        List<CandidateProfile.WorkMode> preferredWorkModes,
        List<CandidateProfile.EmploymentType> preferredEmploymentTypes,
        String expectedSalaryText,
        String availabilityText,
        List<CandidateProfile.Skill> skills,
        List<CandidateProfile.WorkExperience> workExperiences,
        List<CandidateProfile.ProjectExperience> projects,
        List<CandidateProfile.Education> educations,
        List<CandidateProfile.Certification> certifications,
        List<CandidateProfile.LicenseEntry> licenses,
        List<CandidateProfile.LanguageSkill> languages,
        List<CandidateProfile.Award> awards,
        List<CandidateProfile.Publication> publications,
        List<CandidateProfile.VolunteerExperience> volunteerExperiences,
        List<CandidateProfile.ProfessionalActivity> activities,
        List<CandidateProfile.TrainingCourse> trainingCourses,
        List<String> interests,
        Double experienceYears,
        CandidateProfile.Seniority seniority,
        CandidateProfile.EducationLevel highestEducationLevel,
        List<String> recentJobTitles,
        List<String> recentCompanies,
        CandidateProfile.DetectedLanguage detectedLanguage,
        List<CandidateProfile.ParsedSection> sections,
        String parserVersion,
        List<String> parserWarnings,
        CandidateProfile.ParseQuality parseQuality,
        Instant createdAt,
        Instant updatedAt
) {

    public static CandidateProfileResponse from(
            CandidateProfile profile
    ) {
        return new CandidateProfileResponse(
                profile.getRawCvId(),
                profile.getFullName(),
                profile.getHeadline(),
                profile.getProfessionalSummary(),
                profile.getCareerObjective(),
                profile.getContact(),
                immutable(profile.getLinks()),
                immutable(profile.getTargetJobTitles()),
                immutable(profile.getTargetIndustries()),
                immutable(profile.getPreferredLocations()),
                immutable(profile.getPreferredWorkModes()),
                immutable(
                        profile.getPreferredEmploymentTypes()
                ),
                profile.getExpectedSalaryText(),
                profile.getAvailabilityText(),
                immutable(profile.getSkills()),
                immutable(profile.getWorkExperiences()),
                immutable(profile.getProjects()),
                immutable(profile.getEducations()),
                immutable(profile.getCertifications()),
                immutable(profile.getLicenses()),
                immutable(profile.getLanguages()),
                immutable(profile.getAwards()),
                immutable(profile.getPublications()),
                immutable(
                        profile.getVolunteerExperiences()
                ),
                immutable(profile.getActivities()),
                immutable(profile.getTrainingCourses()),
                immutable(profile.getInterests()),
                profile.getExperienceYears(),
                profile.getSeniority(),
                profile.getHighestEducationLevel(),
                immutable(profile.getRecentJobTitles()),
                immutable(profile.getRecentCompanies()),
                profile.getDetectedLanguage(),
                immutable(profile.getSections()),
                profile.getParserVersion(),
                immutable(profile.getParserWarnings()),
                profile.getParseQuality(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private static <T> List<T> immutable(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }
}