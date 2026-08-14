package com.autojob.modules.jobnormalizer.support;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.config.SharedLocationTaxonomyProperties;
import com.autojob.modules.jobnormalizer.config.SharedSeniorityTaxonomyProperties;
import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TaxonomyTestLoader {

    /**
     * Job-only taxonomy.
     *
     * Skills, locations and seniority are shared.
     */
    private static final List<String> TAXONOMY_FILES =
            List.of(
                    "job-types.yml",
                    "salary.yml",
                    "experience.yml",
                    "date.yml"
            );

    private TaxonomyTestLoader() {
    }

    public static NormalizationTaxonomyProperties load() {
        Path taxonomyDirectory =
                findRepoRoot()
                        .resolve(
                                "configs/taxonomy/job-normalizer"
                        );

        MutablePropertySources propertySources =
                new MutablePropertySources();

        for (String fileName : TAXONOMY_FILES) {
            Path file =
                    taxonomyDirectory.resolve(
                            fileName
                    );

            addYamlFile(
                    propertySources,
                    file,
                    "test-taxonomy-" + fileName
            );
        }

        return bind(
                propertySources,
                "autojob.normalization.taxonomy",
                NormalizationTaxonomyProperties.class,
                taxonomyDirectory
        );
    }

    public static SharedSkillTaxonomyProperties loadSharedSkills() {
        return loadShared(
                "skills.yml",
                "autojob.taxonomy.shared.skills",
                SharedSkillTaxonomyProperties.class,
                "test-shared-skills"
        );
    }

    public static SharedLocationTaxonomyProperties loadSharedLocations() {
        return loadShared(
                "locations.yml",
                "autojob.taxonomy.shared.locations",
                SharedLocationTaxonomyProperties.class,
                "test-shared-locations"
        );
    }

    public static SharedSeniorityTaxonomyProperties loadSharedSeniority() {
        return loadShared(
                "seniority.yml",
                "autojob.taxonomy.shared.seniority",
                SharedSeniorityTaxonomyProperties.class,
                "test-shared-seniority"
        );
    }

    private static <T> T loadShared(
            String fileName,
            String prefix,
            Class<T> type,
            String propertySourceName
    ) {
        Path file =
                findRepoRoot()
                        .resolve(
                                "configs/taxonomy/shared"
                        )
                        .resolve(
                                fileName
                        );

        MutablePropertySources propertySources =
                new MutablePropertySources();

        addYamlFile(
                propertySources,
                file,
                propertySourceName
        );

        return bind(
                propertySources,
                prefix,
                type,
                file
        );
    }

    private static void addYamlFile(
            MutablePropertySources propertySources,
            Path file,
            String propertySourceName
    ) {
        if (!Files.isRegularFile(
                file
        )) {
            throw new IllegalStateException(
                    "Missing taxonomy file: "
                            + file.toAbsolutePath()
            );
        }

        Resource resource =
                new FileSystemResource(
                        file
                );

        YamlPropertySourceLoader loader =
                new YamlPropertySourceLoader();

        try {
            List<PropertySource<?>> loaded =
                    loader.load(
                            propertySourceName,
                            resource
                    );

            for (
                    PropertySource<?> propertySource
                    : loaded
            ) {
                propertySources.addLast(
                        propertySource
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot load taxonomy file: "
                            + file.toAbsolutePath(),
                    exception
            );
        }
    }

    private static <T> T bind(
            MutablePropertySources propertySources,
            String prefix,
            Class<T> type,
            Path source
    ) {
        Binder binder =
                new Binder(
                        ConfigurationPropertySources.from(
                                propertySources
                        )
                );

        return binder.bind(
                        prefix,
                        type
                )
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Cannot bind taxonomy configuration from: "
                                                + source.toAbsolutePath()
                                )
                );
    }

    private static Path findRepoRoot() {
        Path current =
                Path.of(
                                System.getProperty(
                                        "user.dir"
                                )
                        )
                        .toAbsolutePath()
                        .normalize();

        while (current != null) {
            Path configsDirectory =
                    current.resolve(
                            "configs/taxonomy"
                    );

            if (
                    Files.isDirectory(
                            configsDirectory
                    )
            ) {
                return current;
            }

            current =
                    current.getParent();
        }

        throw new IllegalStateException(
                "Cannot find configs/taxonomy. "
                        + "Current working directory: "
                        + System.getProperty(
                        "user.dir"
                )
        );
    }
}