package com.temaconsulting.agente_iso;

import org.springframework.boot.SpringApplication;

public class TestAgenteIsoApplication {

	public static void main(String[] args) {
		SpringApplication.from(AgenteIsoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
