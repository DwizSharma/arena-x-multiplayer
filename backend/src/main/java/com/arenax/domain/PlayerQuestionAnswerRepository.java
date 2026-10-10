package com.arenax.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlayerQuestionAnswerRepository extends JpaRepository<PlayerQuestionAnswer, String> {
    List<PlayerQuestionAnswer> findByResult_IdOrderByQuestionIndexAsc(String resultId);
}
