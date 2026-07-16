package com.team04.mopl.follow.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;
import com.team04.mopl.follow.listener.FollowRedisSyncProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 동기화에 최종 실패하여 DLQ(Dead Letter Queue)로 빠진 이벤트를
 * 다시 소비하여 재처리(Self-Healing)하거나 관리자에게 알림을 남기는 컨슈머입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowRedisSyncDlqConsumer {

	private static final String DLQ_TOPIC = "follow-redis-sync-dlq";

	private final ObjectMapper objectMapper;

	private final FollowRedisSyncProcessor followRedisSyncProcessor;

	@KafkaListener(topics = DLQ_TOPIC, groupId = "follow-dlq-group")
	public void consumeDlq(String kafkaEvent) {
		log.info("[REDIS_SYNC_DLQ_CONSUME_START] DLQ 메시지 수신 및 재처리 시작: event={}",
			kafkaEvent);

		try {
			// 1. 문자열 형태의 JSON 이벤트를 JsonNode 트리로 파싱하여 구조 분석
			JsonNode jsonNode = objectMapper.readTree(kafkaEvent);

			// 2. 이벤트 종류 동적 라우팅
			// FollowCreatedEvent에는 'followerName' 필드가 존재하지만, DeletedEvent에는 없음.
			// 이를 기준으로 어떤 객체로 역직렬화할지 결정합니다.
			if (jsonNode.has("followerName")) {
				// 3-A. 팔로우 생성 이벤트로 판단 -> 역직렬화 후 Processor 재호출
				FollowCreatedEvent event = objectMapper.treeToValue(jsonNode, FollowCreatedEvent.class);

				log.info("[REDIS_SYNC_DLQ_ROUTING] FollowCreatedEvent로 식별됨. 생성을 재시도합니다: followerId={}",
					event.followerId());

				followRedisSyncProcessor.syncRedisOnFollowCreated(event);

			} else {
				// 3-B. 팔로우 취소 이벤트로 판단 -> 역직렬화 후 Processor 재호출
				FollowDeletedEvent event = objectMapper.treeToValue(jsonNode, FollowDeletedEvent.class);
				log.info("[REDIS_SYNC_DLQ_ROUTING] FollowDeletedEvent로 식별됨. 삭제를 재시도합니다: followerId={}",
					event.followerId());

				followRedisSyncProcessor.syncRedisOnFollowDeleted(event);
			}

			log.info("[REDIS_SYNC_DLQ_CONSUME_SUCCESS] DLQ 메시지 재처리(복구) 완벽 성공!");

		} catch (JsonProcessingException e) {
			// 파싱 실패: 잘못된 데이터가 토픽에 들어왔을 경우
			log.error("[REDIS_SYNC_DLQ_PARSE_ERROR] DLQ 메시지 역직렬화 실패 (데이터 폐기): event={}",
				kafkaEvent, e);

		} catch (Exception e) {
			// 재처리 중 또 실패: 아직 Redis 장애가 복구되지 않은 경우
			// *주의*: 여기서 예외를 밖으로 던지면(throw e) Kafka 컨슈머가 무한 루프에 빠질 수 있으므로 로그만 남기고 일단 넘어갑니다.
			log.error("[REDIS_SYNC_DLQ_RETRY_FAILED] DLQ 메시지 재처리 최종 실패! (관리자 수동 개입 및 확인 필요): event={}", kafkaEvent, e);

			// TODO: 추후 여기에 Slack 채널 알림 연동 (예: slackAlertService.sendError(...))
		}
	}
}