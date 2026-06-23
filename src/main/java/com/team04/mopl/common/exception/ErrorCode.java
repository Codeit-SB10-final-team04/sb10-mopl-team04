package com.team04.mopl.common.exception;

import org.springframework.http.HttpStatus;

// 각 도메인별로 해당 클래스를 구현하여 활용
public interface ErrorCode {

  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
