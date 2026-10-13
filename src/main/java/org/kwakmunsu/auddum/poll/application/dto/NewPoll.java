package org.kwakmunsu.auddum.poll.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import org.kwakmunsu.auddum.poll.domain.Category;

@Builder
public record NewPoll(
    String question,
    Category category,
    List<String> options,
    LocalDateTime closesAt
) {

}
