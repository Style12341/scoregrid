package com.scoregrid.prediction;

import org.springframework.boot.SpringApplication;

public class TestPredictionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(PredictionServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
