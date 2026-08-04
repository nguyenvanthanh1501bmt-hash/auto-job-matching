package com.autojob.modules.cv.service;

import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.domain.RawCv;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
public class CandidateProfileMapper {

    public CandidateProfile toDocument(
            RawCv rawCv,
            CvParseResponse response,
            CandidateProfile existing,
            Instant now
    ) {
        Objects.requireNonNull(rawCv, "rawCv");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(now, "now");

        CvParseResponse.CandidateProfilePayload payload =
                Objects.requireNonNull(
                        response.profile(),
                        "response.profile"
                );

        Instant createdAt = existing != null
                && existing.getCreatedAt() != null
                ? existing.getCreatedAt()
                : now;

        return CandidateProfile.builder()
                .id(
                        existing == null
                                ? null
                                : existing.getId()
                )
                .rawCvId(rawCv.getId())
                .ownerUserId(rawCv.getOwnerUserId())
                .fullName(payload.fullName())
                .headline(payload.headline())
                .professionalSummary(
                        payload.professionalSummary()
                )
                .careerObjective(payload.careerObjective())
                .contact(mapContact(payload.contact()))
                .links(
                        mapList(
                                payload.links(),
                                this::mapLink
                        )
                )
                .targetJobTitles(
                        copyStrings(payload.targetJobTitles())
                )
                .targetIndustries(
                        copyStrings(payload.targetIndustries())
                )
                .preferredLocations(
                        copyStrings(payload.preferredLocations())
                )
                .preferredWorkModes(
                        mapList(
                                payload.preferredWorkModes(),
                                this::mapWorkMode
                        )
                )
                .preferredEmploymentTypes(
                        mapList(
                                payload.preferredEmploymentTypes(),
                                this::mapEmploymentType
                        )
                )
                .expectedSalaryText(
                        payload.expectedSalaryText()
                )
                .availabilityText(
                        payload.availabilityText()
                )
                .skills(
                        mapList(
                                payload.skills(),
                                this::mapSkill
                        )
                )
                .workExperiences(
                        mapList(
                                payload.workExperiences(),
                                this::mapWorkExperience
                        )
                )
                .projects(
                        mapList(
                                payload.projects(),
                                this::mapProject
                        )
                )
                .educations(
                        mapList(
                                payload.educations(),
                                this::mapEducation
                        )
                )
                .certifications(
                        mapList(
                                payload.certifications(),
                                this::mapCertification
                        )
                )
                .licenses(
                        mapList(
                                payload.licenses(),
                                this::mapLicense
                        )
                )
                .languages(
                        mapList(
                                payload.languages(),
                                this::mapLanguage
                        )
                )
                .awards(
                        mapList(
                                payload.awards(),
                                this::mapAward
                        )
                )
                .publications(
                        mapList(
                                payload.publications(),
                                this::mapPublication
                        )
                )
                .volunteerExperiences(
                        mapList(
                                payload.volunteerExperiences(),
                                this::mapVolunteerExperience
                        )
                )
                .activities(
                        mapList(
                                payload.activities(),
                                this::mapProfessionalActivity
                        )
                )
                .trainingCourses(
                        mapList(
                                payload.trainingCourses(),
                                this::mapTrainingCourse
                        )
                )
                .interests(
                        copyStrings(payload.interests())
                )
                .experienceYears(payload.experienceYears())
                .seniority(
                        mapSeniority(payload.seniority())
                )
                .highestEducationLevel(
                        mapEducationLevel(
                                payload.highestEducationLevel()
                        )
                )
                .recentJobTitles(
                        copyStrings(payload.recentJobTitles())
                )
                .recentCompanies(
                        copyStrings(payload.recentCompanies())
                )
                .detectedLanguage(
                        mapDetectedLanguage(
                                response.detectedLanguage()
                        )
                )
                .rawText(payload.rawText())
                .sections(
                        mapList(
                                payload.sections(),
                                this::mapParsedSection
                        )
                )
                .parserVersion(response.parserVersion())
                .parserWarnings(
                        mergeWarnings(
                                response.warnings(),
                                payload.parserWarnings()
                        )
                )
                .parseQuality(
                        mapParseQuality(
                                payload.parseQuality()
                        )
                )
                .sourceBucket(rawCv.getBucket())
                .sourceObjectKey(rawCv.getObjectKey())
                .sourceOriginalFilename(
                        rawCv.getOriginalFilename()
                )
                .sourceContentType(
                        rawCv.getContentType()
                )
                .sourceSizeBytes(rawCv.getSizeBytes())
                .sourceSha256(rawCv.getSha256())
                .createdAt(createdAt)
                .updatedAt(now)
                .build();
    }

    private CandidateProfile.ContactInformation mapContact(
            CvParseResponse.ContactInformation value
    ) {
        Objects.requireNonNull(
                value,
                "profile.contact"
        );

        return new CandidateProfile.ContactInformation(
                value.email(),
                value.phone(),
                value.addressText(),
                value.city(),
                value.provinceOrState(),
                value.country(),
                value.postalCode()
        );
    }

    private CandidateProfile.LinkEntry mapLink(
            CvParseResponse.LinkEntry value
    ) {
        return new CandidateProfile.LinkEntry(
                mapLinkType(value.type()),
                value.url(),
                value.label()
        );
    }

    private CandidateProfile.Skill mapSkill(
            CvParseResponse.Skill value
    ) {
        return new CandidateProfile.Skill(
                value.name(),
                value.normalizedName(),
                mapSkillCategory(value.category()),
                value.proficiencyText(),
                mapProficiencyLevel(
                        value.normalizedProficiency()
                ),
                value.yearsOfExperience(),
                value.lastUsedDate(),
                copyStrings(value.evidenceSources())
        );
    }

    private CandidateProfile.WorkExperience
    mapWorkExperience(
            CvParseResponse.WorkExperience value
    ) {
        return new CandidateProfile.WorkExperience(
                value.companyName(),
                value.companyIndustry(),
                value.jobTitle(),
                value.normalizedJobTitle(),
                mapEmploymentType(
                        value.employmentType()
                ),
                value.location(),
                mapWorkMode(value.workMode()),
                value.startDate(),
                value.endDate(),
                value.current(),
                value.durationMonths(),
                value.description(),
                copyStrings(value.responsibilities()),
                copyStrings(value.achievements()),
                copyStrings(value.skills()),
                copyStrings(value.tools()),
                copyStrings(value.equipment())
        );
    }

    private CandidateProfile.ProjectExperience mapProject(
            CvParseResponse.ProjectExperience value
    ) {
        return new CandidateProfile.ProjectExperience(
                value.name(),
                value.role(),
                value.domain(),
                value.startDate(),
                value.endDate(),
                value.current(),
                value.description(),
                copyStrings(value.responsibilities()),
                copyStrings(value.achievements()),
                copyStrings(value.skills()),
                copyStrings(value.tools()),
                copyStrings(value.equipment()),
                value.teamSizeText(),
                value.projectUrl(),
                value.repositoryUrl()
        );
    }

    private CandidateProfile.Education mapEducation(
            CvParseResponse.Education value
    ) {
        return new CandidateProfile.Education(
                value.institutionName(),
                value.degree(),
                mapEducationLevel(
                        value.normalizedDegreeLevel()
                ),
                value.fieldOfStudy(),
                value.specialization(),
                value.startDate(),
                value.endDate(),
                value.current(),
                value.grade(),
                copyStrings(value.achievements()),
                value.description()
        );
    }

    private CandidateProfile.Certification
    mapCertification(
            CvParseResponse.Certification value
    ) {
        return new CandidateProfile.Certification(
                value.name(),
                value.issuer(),
                value.issuedDate(),
                value.expirationDate(),
                value.expired(),
                value.credentialId(),
                value.credentialUrl(),
                copyStrings(value.relatedSkills())
        );
    }

    private CandidateProfile.LicenseEntry mapLicense(
            CvParseResponse.LicenseEntry value
    ) {
        return new CandidateProfile.LicenseEntry(
                value.name(),
                value.issuingAuthority(),
                value.licenseNumber(),
                value.issuedDate(),
                value.expirationDate(),
                value.expired(),
                value.jurisdiction()
        );
    }

    private CandidateProfile.LanguageSkill mapLanguage(
            CvParseResponse.LanguageSkill value
    ) {
        return new CandidateProfile.LanguageSkill(
                value.language(),
                value.proficiencyText(),
                mapProficiencyLevel(
                        value.normalizedProficiency()
                ),
                value.framework(),
                value.score()
        );
    }

    private CandidateProfile.Award mapAward(
            CvParseResponse.Award value
    ) {
        return new CandidateProfile.Award(
                value.name(),
                value.issuer(),
                value.awardedDate(),
                value.description()
        );
    }

    private CandidateProfile.Publication mapPublication(
            CvParseResponse.Publication value
    ) {
        return new CandidateProfile.Publication(
                value.title(),
                copyStrings(value.authors()),
                value.publisher(),
                value.publishedDate(),
                value.url(),
                value.description()
        );
    }

    private CandidateProfile.VolunteerExperience
    mapVolunteerExperience(
            CvParseResponse.VolunteerExperience value
    ) {
        return new CandidateProfile.VolunteerExperience(
                value.organizationName(),
                value.role(),
                value.startDate(),
                value.endDate(),
                value.description(),
                copyStrings(value.responsibilities()),
                copyStrings(value.skills())
        );
    }

    private CandidateProfile.ProfessionalActivity
    mapProfessionalActivity(
            CvParseResponse.ProfessionalActivity value
    ) {
        return new CandidateProfile.ProfessionalActivity(
                value.name(),
                value.organization(),
                value.role(),
                value.startDate(),
                value.endDate(),
                value.description()
        );
    }

    private CandidateProfile.TrainingCourse
    mapTrainingCourse(
            CvParseResponse.TrainingCourse value
    ) {
        return new CandidateProfile.TrainingCourse(
                value.name(),
                value.provider(),
                value.completionDate(),
                value.durationText(),
                value.description(),
                copyStrings(value.relatedSkills())
        );
    }

    private CandidateProfile.ParsedSection
    mapParsedSection(
            CvParseResponse.ParsedSection value
    ) {
        return new CandidateProfile.ParsedSection(
                mapSectionType(
                        value.sectionType()
                ),
                value.heading(),
                value.startOffset(),
                value.endOffset(),
                value.text()
        );
    }

    private CandidateProfile.ParseQuality
    mapParseQuality(
            CvParseResponse.ParseQuality value
    ) {
        Objects.requireNonNull(
                value,
                "profile.parseQuality"
        );

        return new CandidateProfile.ParseQuality(
                value.overallScore(),
                value.textExtractionScore(),
                value.sectionDetectionScore(),
                value.workExperienceScore(),
                copyStrings(
                        value.missingImportantFields()
                ),
                copyStrings(value.ambiguousFields())
        );
    }

    private CandidateProfile.DetectedLanguage
    mapDetectedLanguage(
            CvParseResponse.DetectedLanguage value
    ) {
        return value == null
                ? null
                : CandidateProfile.DetectedLanguage
                .valueOf(value.name());
    }

    private CandidateProfile.LinkType mapLinkType(
            CvParseResponse.LinkType value
    ) {
        return value == null
                ? null
                : CandidateProfile.LinkType
                .valueOf(value.name());
    }

    private CandidateProfile.SkillCategory
    mapSkillCategory(
            CvParseResponse.SkillCategory value
    ) {
        return value == null
                ? null
                : CandidateProfile.SkillCategory
                .valueOf(value.name());
    }

    private CandidateProfile.ProficiencyLevel
    mapProficiencyLevel(
            CvParseResponse.ProficiencyLevel value
    ) {
        return value == null
                ? null
                : CandidateProfile.ProficiencyLevel
                .valueOf(value.name());
    }

    private CandidateProfile.EmploymentType
    mapEmploymentType(
            CvParseResponse.EmploymentType value
    ) {
        return value == null
                ? null
                : CandidateProfile.EmploymentType
                .valueOf(value.name());
    }

    private CandidateProfile.WorkMode mapWorkMode(
            CvParseResponse.WorkMode value
    ) {
        return value == null
                ? null
                : CandidateProfile.WorkMode
                .valueOf(value.name());
    }

    private CandidateProfile.EducationLevel
    mapEducationLevel(
            CvParseResponse.EducationLevel value
    ) {
        return value == null
                ? null
                : CandidateProfile.EducationLevel
                .valueOf(value.name());
    }

    private CandidateProfile.Seniority mapSeniority(
            CvParseResponse.Seniority value
    ) {
        return value == null
                ? null
                : CandidateProfile.Seniority
                .valueOf(value.name());
    }

    private CandidateProfile.SectionType mapSectionType(
            CvParseResponse.SectionType value
    ) {
        return value == null
                ? null
                : CandidateProfile.SectionType
                .valueOf(value.name());
    }

    private List<String> mergeWarnings(
            List<String> responseWarnings,
            List<String> profileWarnings
    ) {
        LinkedHashSet<String> warnings =
                new LinkedHashSet<>();

        warnings.addAll(
                Objects.requireNonNull(
                        responseWarnings,
                        "response.warnings"
                )
        );
        warnings.addAll(
                Objects.requireNonNull(
                        profileWarnings,
                        "profile.parserWarnings"
                )
        );

        return List.copyOf(warnings);
    }

    private List<String> copyStrings(
            List<String> values
    ) {
        return List.copyOf(
                Objects.requireNonNull(
                        values,
                        "values"
                )
        );
    }

    private <S, T> List<T> mapList(
            List<S> values,
            Function<S, T> mapper
    ) {
        return Objects.requireNonNull(
                        values,
                        "values"
                )
                .stream()
                .map(Objects::requireNonNull)
                .map(mapper)
                .toList();
    }
}