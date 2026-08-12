package com.autojob.modules.jobnormalizer.support;

import com.autojob.modules.jobnormalizer.config.NormalizationTaxonomyProperties;
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

    private static final List<String> TAXONOMY_FILES =
            List.of(
                    "skills.yml",
                    "locations.yml",
                    "seniority.yml",
                    "job-types.yml",
                    "salary.yml",
                    "experience.yml",
                    "date.yml"
            );

    private TaxonomyTestLoader() {
    }

    public static NormalizationTaxonomyProperties load() {
        Path taxonomyDirectory =
                findTaxonomyDirectory();

        MutablePropertySources propertySources =
                new MutablePropertySources();

        YamlPropertySourceLoader loader =
                new YamlPropertySourceLoader();

        for (String fileName : TAXONOMY_FILES) {
            Path file =
                    taxonomyDirectory.resolve(
                            fileName
                    );

            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException(
                        "Missing taxonomy file: "
                                + file.toAbsolutePath()
                );
            }

            Resource resource =
                    new FileSystemResource(file);

            try {
                List<PropertySource<?>> loaded =
                        loader.load(
                                "test-taxonomy-" + fileName,
                                resource
                        );

                for (PropertySource<?> propertySource
                        : loaded) {
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

        Binder binder =
                new Binder(
                        ConfigurationPropertySources.from(
                                propertySources
                        )
                );

        return binder.bind(
                        "autojob.normalization.taxonomy",
                        NormalizationTaxonomyProperties.class
                )
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Cannot bind taxonomy configuration from: "
                                        + taxonomyDirectory.toAbsolutePath()
                        )
                );
    }

    private static Path findTaxonomyDirectory() {
        Path current =
                Path.of(
                                System.getProperty(
                                        "user.dir"
                                )
                        )
                        .toAbsolutePath()
                        .normalize();

        while (current != null) {
            Path candidate =
                    current.resolve(
                            "configs/taxonomy/job-normalizer"
                    );

            if (Files.isDirectory(candidate)) {
                return candidate;
            }

            current = current.getParent();
        }

        throw new IllegalStateException(
                "Cannot find configs/taxonomy/job-normalizer. "
                        + "Current working directory: "
                        + System.getProperty(
                        "user.dir"
                )
        );
    }
}