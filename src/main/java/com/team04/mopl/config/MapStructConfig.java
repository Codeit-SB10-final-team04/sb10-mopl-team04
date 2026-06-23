package com.team04.mopl.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/*
    MapStruct 전역 설정
    -----------------
    모든 Mapper 인터페이스가 공통으로 상속받아 사용할 기본 매핑 정책을 정의
 */
@MapperConfig(
	componentModel = MappingConstants.ComponentModel.SPRING,
	// 매핑되지 않은 대상 필드가 있어도 경고 없이 넘어가도록 설정
	unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface MapStructConfig {
}