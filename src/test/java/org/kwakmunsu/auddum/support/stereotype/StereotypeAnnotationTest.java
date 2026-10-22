package org.kwakmunsu.auddum.support.stereotype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

class StereotypeAnnotationTest {

    @WebApi
    static class SampleController {

    }

    @CommandService
    static class SampleCommandService {

    }

    @QueryService
    static class SampleQueryService {

    }

    @Test
    void CommandService는_서비스이고_쓰기_트랜잭션을_쓴다() {
        var transactional = AnnotatedElementUtils.findMergedAnnotation(SampleCommandService.class, Transactional.class);

        assertThat(AnnotatedElementUtils.hasAnnotation(SampleCommandService.class, ApplicationService.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(SampleCommandService.class, Component.class)).isTrue();
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void QueryService는_서비스이고_읽기_전용_트랜잭션을_쓴다() {
        var transactional = AnnotatedElementUtils.findMergedAnnotation(SampleQueryService.class, Transactional.class);

        assertThat(AnnotatedElementUtils.hasAnnotation(SampleQueryService.class, ApplicationService.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(SampleQueryService.class, Component.class)).isTrue();
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
