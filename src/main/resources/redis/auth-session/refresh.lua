-- 현재 refresh token을 한 번만 사용할 수 있도록 새 token으로 원자 회전
-- KEYS[1]: 사용자 세션 Hash / KEYS[2]: 현재 refresh 역인덱스 / KEYS[3]: 새 refresh 역인덱스
-- ARGV[1..3]: userId, sessionId, 현재 hash / ARGV[4..7]: 새 hash와 만료 시각

-- 현재 refresh 역인덱스가 요청 userId를 가리키는지 확인
if redis.call('GET', KEYS[2]) ~= ARGV[1] then
    return 0
end

-- 세션의 사용자, sessionId, 현재 hash가 모두 일치해야 로그인 세션 정상 갱신
if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1]
    or redis.call('HGET', KEYS[1], 'sessionId') ~= ARGV[2]
    or redis.call('HGET', KEYS[1], 'refreshTokenHash') ~= ARGV[3] then
    return 0
end

-- 기존 sessionId는 유지하고 token hash, 만료 시각, 마지막 갱신 시각만 새 값으로 변경
redis.call(
    'HSET', KEYS[1],
    'refreshTokenHash', ARGV[4],
    'accessExpiresAt', ARGV[5],
    'refreshExpiresAt', ARGV[6],
    'lastRefreshedAt', ARGV[7],
    'updatedAt', ARGV[7]
)

-- 새 refresh token의 만료 시각을 기준으로 사용자 세션 TTL을 연장
redis.call('PEXPIREAT', KEYS[1], ARGV[6])

-- 사용이 끝난 현재 refresh 역인덱스를 삭제해 같은 token의 재사용 차단
redis.call('DEL', KEYS[2])

-- 새 refresh token hash가 같은 사용자 세션을 가리키도록 역인덱스 생성
redis.call('SET', KEYS[3], ARGV[1])

-- 새 역인덱스도 사용자 세션과 같은 시각에 만료되도록 TTL 세팅
redis.call('PEXPIREAT', KEYS[3], ARGV[6])

-- 현재 token 검증과 새 token 저장 완료
return 1
