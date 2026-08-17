package com.kinderp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.kinderp")
class LayerDependencyTest {

    @ArchTest
    static final ArchRule repositories_must_not_depend_on_application_layers = noClasses()
            .that()
            .resideInAnyPackage("..domain..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..domain..service..", "..domain..controller..");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_repositories = noClasses()
            .that()
            .resideInAnyPackage("..domain..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..domain..repository..");
}
