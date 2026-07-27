package com.scoregrid.score;

import org.springframework.boot.SpringApplication;

public class TestScoreServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ScoreServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
