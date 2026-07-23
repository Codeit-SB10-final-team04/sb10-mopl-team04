import {sleep} from 'k6';

// min과 max를 모두 포함하는 범위에서 정수 선택
export const randomIntBetween = (min, max) => {
  if (!Number.isInteger(min) || !Number.isInteger(max) || min > max) {
    throw new Error('min과 max는 정수이며 min은 max 이하여야 합니다.');
  }

  return Math.floor(Math.random() * (max - min + 1)) + min;
};

// 배열에서 임의의 항목 하나를 반환하며 빈 배열은 조기 실패
export const randomItem = (items) => {
  if (!Array.isArray(items) || items.length === 0) {
    throw new Error('items는 비어 있지 않은 배열이어야 합니다.');
  }

  return items[randomIntBetween(0, items.length - 1)];
};

// users.json에서 읽은 사용자 배열을 전달해 시나리오별 사용자 선택
export const randomUser = (users) => randomItem(users);

// 실제 사용자의 요청 간 대기 시간 (기본 범위: 1~3초)
export const thinkTime = (minSeconds = 1, maxSeconds = 3) => {
  const seconds = randomIntBetween(minSeconds, maxSeconds);
  sleep(seconds);
  return seconds;
};

// 테스트 사용자 수를 초과하지 않는 부하 단계 생성
export const capStageTargets = (sourceStages, maxVus) => {
  if (!Array.isArray(sourceStages) || sourceStages.length === 0) {
    throw new Error('sourceStages는 비어 있지 않은 배열이어야 합니다.');
  }

  if (!Number.isInteger(maxVus) || maxVus < 1) {
    throw new Error('maxVus는 1 이상의 정수여야 합니다.');
  }

  return sourceStages.map((stage) => ({
    ...stage,
    target: Math.min(stage.target, maxVus),
  }));
};
