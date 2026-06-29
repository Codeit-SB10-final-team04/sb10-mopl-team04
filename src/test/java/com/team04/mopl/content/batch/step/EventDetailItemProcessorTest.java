package com.team04.mopl.content.batch.step;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.SportsDbClient;

@ExtendWith(MockitoExtension.class)
class EventDetailItemProcessorTest {

    @Mock
    private SportsDbClient sportsDbClient;

    @InjectMocks
    private EventDetailItemProcessor processor;

    @Test
    @DisplayName("경기 상세 조회 성공 시 JsonNode를 반환한다")
    void process_returnsJsonNode_whenDetailFound() throws Exception {
        // given
        ObjectNode detail = JsonNodeFactory.instance.objectNode();
        detail.put("idEvent", "12345");
        when(sportsDbClient.getEventDetail("12345")).thenReturn(Optional.of(detail));

        // when
        JsonNode result = processor.process("12345");

        // then
        assertThat(result).isEqualTo(detail);
    }

    @Test
    @DisplayName("경기 상세 조회 결과 없으면 EventDetailNotFoundException을 던진다")
    void process_throwsEventDetailNotFoundException_whenDetailNotFound() {
        // given
        when(sportsDbClient.getEventDetail("99999")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> processor.process("99999"))
            .isInstanceOf(EventDetailNotFoundException.class)
            .hasMessageContaining("99999");
    }
}
