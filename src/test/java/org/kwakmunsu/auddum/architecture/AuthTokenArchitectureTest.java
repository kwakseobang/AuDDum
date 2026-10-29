package org.kwakmunsu.auddum.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

/** 아키텍처 설계서: 토큰 처리는 auth.application·domain·infrastructure, 컨트롤러·필터·핸들러는 auth.api. infrastructure는 위 계층에 의존하지 않는다. */
@AnalyzeClasses(packages = "org.kwakmunsu.auddum.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthTokenArchitectureTest {

    @ArchTest
    static void auth_infrastructure는_application과_api에_의존하지_않는다(JavaClasses classes) {
        noClasses().that().resideInAPackage("..auth.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..auth.application..", "..auth.api..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @ArchTest
    static void auth_domain은_다른_auth_계층에_의존하지_않는다(JavaClasses classes) {
        noClasses().that().resideInAPackage("..auth.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..auth.application..", "..auth.api..", "..auth.infrastructure..")
            .allowEmptyShould(true)
            .check(classes);
    }

}
