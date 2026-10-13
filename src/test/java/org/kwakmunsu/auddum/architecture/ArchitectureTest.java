package org.kwakmunsu.auddum.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 아키텍처 설계서 §2의 패키지 규칙을 검증한다.
 * 도메인 패키지가 아직 비어 있어도 통과해야 하므로 allowEmptyShould를 켠다.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "org.kwakmunsu.auddum";

    private static final List<String> DOMAINS = List.of("member", "auth", "poll", "vote", "report", "admin");

    /** 의존 다이어그램(아키텍처 §2)에서 각 도메인이 쓸 수 있는 다른 도메인. support는 모두가 쓸 수 있다. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "member", Set.of(),
            "poll", Set.of(),
            "auth", Set.of("member"),
            "vote", Set.of("poll"),
            "report", Set.of("poll"),
            "admin", Set.of("report", "poll", "member")
    );

    @ArchTest
    static void 패키지_사이에_순환_의존이_없다(JavaClasses classes) {
        slices().matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(classes);
    }

    @ArchTest
    static void 도메인은_다이어그램에_없는_다른_도메인에_의존하지_않는다(JavaClasses classes) {
        for (String domain : DOMAINS) {
            String[] forbidden = DOMAINS.stream()
                    .filter(other -> !other.equals(domain) && !ALLOWED.get(domain).contains(other))
                    .map(other -> ROOT + "." + other + "..")
                    .toArray(String[]::new);

            noClasses().that().resideInAPackage(ROOT + "." + domain + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                    .because(domain + "은(는) 아키텍처 §2 다이어그램에 없는 도메인에 의존할 수 없다")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @ArchTest
    static void support는_도메인에_의존하지_않는다(JavaClasses classes) {
        String[] domains = DOMAINS.stream().map(domain -> ROOT + "." + domain + "..").toArray(String[]::new);

        noClasses().that().resideInAPackage(ROOT + ".support..")
                .should().dependOnClassesThat().resideInAnyPackage(domains)
                .because("support에는 도메인이 아닌 기술 기반만 둔다")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** admin도 예외가 아니다. 다른 도메인은 서비스의 공개 진입점으로만 부른다. */
    @ArchTest
    static void 다른_도메인의_repository를_직접_쓰지_않는다(JavaClasses classes) {
        for (String domain : DOMAINS) {
            noClasses().that().resideOutsideOfPackage(ROOT + "." + domain + "..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + "." + domain + ".repository..")
                    .because(domain + "의 repository는 " + domain + " 안에서만 쓴다")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @ArchTest
    static void common_패키지를_만들지_않는다(JavaClasses classes) {
        noClasses().should().resideInAPackage("..common..")
                .because("여러 도메인이 쓰는 규칙은 해당 도메인에 둔다")
                .allowEmptyShould(true)
                .check(classes);
    }
}
