const NULL_CHARACTER = '\u0000';

// STOMP 명령과 헤더, 본문을 텍스트 프레임으로 직렬화
export const createStompFrame = (command, headers = {}, body = '') => {
  const headerLines = Object.entries(headers).map(
    ([name, value]) => `${name}:${value}`,
  );

  return [
    command,
    ...headerLines,
    '',
    body,
  ].join('\n') + NULL_CHARACTER;
};

// SockJS 클라이언트 메시지 형식으로 STOMP 프레임 래핑
export const wrapSockJsMessages = (...messages) => JSON.stringify(messages);

// SockJS 서버 메시지의 프레임 유형과 페이로드 분리
export const parseSockJsPacket = (payload) => {
  if (payload === 'o') {
    return {type: 'open', messages: []};
  }

  if (payload === 'h') {
    return {type: 'heartbeat', messages: []};
  }

  if (payload.startsWith('a')) {
    const messages = JSON.parse(payload.slice(1));

    if (!Array.isArray(messages)) {
      throw new Error('SockJS 메시지 배열 형식이 올바르지 않습니다.');
    }

    return {type: 'messages', messages};
  }

  if (payload.startsWith('c')) {
    return {
      type: 'close',
      messages: [],
      close: JSON.parse(payload.slice(1)),
    };
  }

  throw new Error(`지원하지 않는 SockJS 프레임입니다: ${payload}`);
};

// STOMP 텍스트 프레임의 명령, 헤더, 본문 분리
export const parseStompFrame = (rawFrame) => {
  const normalized = rawFrame.replace(/\r\n/g, '\n');
  const frameEnd = normalized.indexOf(NULL_CHARACTER);
  const frame =
    frameEnd >= 0 ? normalized.slice(0, frameEnd) : normalized;

  if (!frame.trim()) {
    return {command: 'HEARTBEAT', headers: {}, body: ''};
  }

  const headerEnd = frame.indexOf('\n\n');
  const headerBlock = headerEnd >= 0 ? frame.slice(0, headerEnd) : frame;
  const body = headerEnd >= 0 ? frame.slice(headerEnd + 2) : '';
  const [command, ...headerLines] = headerBlock.split('\n');
  const headers = {};

  headerLines.forEach((line) => {
    const separatorIndex = line.indexOf(':');

    if (separatorIndex > 0) {
      headers[line.slice(0, separatorIndex)] = line.slice(separatorIndex + 1);
    }
  });

  return {command, headers, body};
};
