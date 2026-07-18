-- 로그아웃 요청의 sessionId가 현재 활성 세션과 같을 때만 해당 세션 삭제
-- KEYS[1]: 사용자 세션 Hash / KEYS[2]: 현재 refresh 역인덱스 또는 더미 키
-- ARGV[1]: 현재 hash / ARGV[2]: 로그아웃 access token의 sessionId

-- 조회 이후 재로그인이나 token 갱신이 발생했는지 확인
local currentHash = redis.call('HGET', KEYS[1], 'refreshTokenHash')

-- 예상 hash와 다르면 최신 상태를 다시 읽도록 RETRY 반환
if (currentHash or '') ~= ARGV[1] then
    return -1
end

-- 오래된 access token의 로그아웃 요청이면 현재 새 세션 삭제하지 않음
if redis.call('HGET', KEYS[1], 'sessionId') ~= ARGV[2] then
    return 0
end

-- 정상 세션이면 세션 Hash와 refresh 역인덱스를 함께 삭제
if currentHash then
    redis.call('DEL', KEYS[1], KEYS[2])
else
    -- refresh hash가 없는 불완전 세션은 sessionId가 맞을 때 세션 키만 정리
    redis.call('DEL', KEYS[1])
end

-- 현재 로그아웃 대상 세션 삭제 완료
return 1
