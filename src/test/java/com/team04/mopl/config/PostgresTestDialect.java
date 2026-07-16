package com.team04.mopl.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

// PostgreSQL Testcontainers 환경에서 NAMED_ENUM을 VARCHAR로 처리
// Hibernate가 CREATE TYPE ... AS ENUM DDL을 생성하지 않도록 우회
public class PostgresTestDialect extends PostgreSQLDialect {
	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes(typeContributions, serviceRegistry);
		typeContributions.getTypeConfiguration()
			.getJdbcTypeRegistry()
			.addDescriptor(SqlTypes.NAMED_ENUM, VarcharJdbcType.INSTANCE);
	}
}
