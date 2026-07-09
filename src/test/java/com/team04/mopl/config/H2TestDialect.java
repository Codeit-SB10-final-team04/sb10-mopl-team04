package com.team04.mopl.config;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

public class H2TestDialect extends H2Dialect {
	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes(typeContributions, serviceRegistry);

		System.out.println("======================================================");
		System.out.println("🚀🚀🚀 H2TestDialect 커스텀 방언이 성공적으로 로드되었습니다! 🚀🚀🚀");
		System.out.println("======================================================");
		
		typeContributions.getTypeConfiguration()
			.getJdbcTypeRegistry()
			.addDescriptor(SqlTypes.NAMED_ENUM, VarcharJdbcType.INSTANCE);
	}
}