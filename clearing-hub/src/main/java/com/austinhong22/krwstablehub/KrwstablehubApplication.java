package com.austinhong22.krwstablehub;

import com.example.clearinghub.config.ClearingProperties;
import com.example.clearinghub.infra.ledger.LedgerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ClearingProperties.class, LedgerProperties.class})
public class KrwstablehubApplication {

	public static void main(String[] args) {
		SpringApplication.run(KrwstablehubApplication.class, args);
	}

}
