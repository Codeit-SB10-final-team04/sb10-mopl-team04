-- 계정 잠금, 권한 변경, 비밀번호 변경 시 사용자의 현재 인증 세션 삭제
-- KEYS[1]: 사용자 세션 Hash / KEYS[2]: 현재 refresh 역인덱스 또는 더미 키
-- ARGV[1]: Java가 조회한 현재 refresh token hash

-- 조회 이후 재로그인이 발생했는지 비교하기 위해 실제 hash 다시 조회
local currentHash = redis.call('HGET', KEYS[1], 'refreshTokenHash')

-- 예상 hash와 다르면 새 세션을 잘못 삭제하지 않도록 RETRY 반환
if (currentHash or '') ~= ARGV[1] then
    return -1
end

-- 완전한 세션이면 사용자 세션과 현재 refresh 역인덱스를 함께 삭제
if currentHash then
    redis.call('DEL', KEYS[1], KEYS[2])
else
    -- refresh hash가 누락된 불완전 세션도 활성 상태로 남지 않도록 세션 키 정리
    redis.call('DEL', KEYS[1])
end

-- 세션이 이미 없었던 경우를 포함해 삭제 요청을 멱등 성공으로 처리
return 1
