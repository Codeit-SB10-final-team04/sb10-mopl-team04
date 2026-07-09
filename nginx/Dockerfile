# mopl 리버스 프록시용 nginx 이미지 (ECS nginx 서비스에서 사용)
FROM nginx:1.27-alpine

# 기본 설정 전체를 커스텀 설정으로 교체
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80 443

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:80/health || exit 1
