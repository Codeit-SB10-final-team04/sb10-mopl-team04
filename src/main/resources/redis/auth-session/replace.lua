-- 로그인 시 기존 세션을 새 세션으로 교체
-- KEYS[1]: 사용자 세션 Hash / KEYS[2]: 기존 refresh 역인덱스 / KEYS[3]: 새 refresh 역인덱스
-- ARGV[1]: Java가 조회한 기존 hash / ARGV[2..7]: 새 세션 값과 만료 시각

-- 조회 이후 세션이 바뀌었는지 확인
local currentHash = redis.call('HGET', KEYS[1], 'refreshTokenHash')

-- 예상 hash와 실제 hash가 다르면 최신 상태를 다시 읽도록 RETRY 반환
if (currentHash or '') ~= ARGV[1] then
    return -1
end

-- 기존 세션이 있었다면 더 이상 사용하면 안 되는 refresh 역인덱스를 먼저 제거
if currentHash then
    redis.call('DEL', KEYS[2])
end

-- 새 로그인 세션의 식별자, token hash, 만료 시각을 하나의 Redis Hash로 저장
redis.call(
    'HSET', KEYS[1],
    'userId', ARGV[2],
    'sessionId', ARGV[3],
    'refreshTokenHash', ARGV[4],
    'accessExpiresAt', ARGV[5],
    'refreshExpiresAt', ARGV[6],
    'lastRefreshedAt', '',
    'updatedAt', ARGV[7]
)

-- 사용자 세션은 refresh token 만료 시각에 Redis가 자동 정리하도록 절대 TTL 설정
redis.call('PEXPIREAT', KEYS[1], ARGV[6])

-- refresh token hash로 userId를 찾을 수 있도록 역인덱스 생성
redis.call('SET', KEYS[3], ARGV[2])

-- 세션 Hash와 역인덱스가 같은 시각에 만료되도록 TTL 설정
redis.call('PEXPIREAT', KEYS[3], ARGV[6])

-- 세션 교체와 역인덱스 저장 완료
return 1
