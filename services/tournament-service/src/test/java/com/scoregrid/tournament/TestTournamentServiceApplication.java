package com.scoregrid.tournament;

import org.springframework.boot.SpringApplication;

public class TestTournamentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(TournamentServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
