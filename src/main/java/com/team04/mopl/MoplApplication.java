package com.team04.mopl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;

@SpringBootApplication(
	exclude = {
		ElasticsearchDataAutoConfiguration.class,
		ElasticsearchClientAutoConfiguration.class,
		ReactiveElasticsearchClientAutoConfiguration.class
	}
)
public class MoplApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoplApplication.class, args);
    }

}
