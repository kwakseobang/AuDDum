package org.kwakmunsu.auddum.poll.application;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.kwakmunsu.auddum.poll.application.dto.NewPoll;
import org.kwakmunsu.auddum.poll.domain.Category;

/*
 * 테스트 코드에서 반복적으로 사용되는 Poll 데이터를 모아둔 클래스
 * */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PollFixture {

    static final String 질문 = "읽씹 3시간 뒤 답장, 이거 관심 없는 걸까요?";

    static NewPoll createNewPoll() {
        return createNewPoll(질문, List.of("가", "나"), null);
    }

    static NewPoll createNewPoll(String question) {
        return createNewPoll(question, List.of("가", "나"), null);
    }

    static NewPoll createNewPoll(String question, List<String> options, LocalDateTime closesAt) {
        return NewPoll.builder()
            .question(question)
            .category(Category.SOME)
            .options(options)
            .closesAt(closesAt)
            .build();
    }

}
