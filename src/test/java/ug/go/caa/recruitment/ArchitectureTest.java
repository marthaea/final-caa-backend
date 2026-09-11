package ug.go.caa.recruitment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "ug.go.caa.recruitment",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule shared_code_must_not_depend_on_features = noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAPackage("..feature..");

    @ArchTest
    static final ArchRule features_must_not_bypass_shared_persistence = noClasses()
            .that().resideInAPackage("..feature..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.springframework.data.jpa..");
}
