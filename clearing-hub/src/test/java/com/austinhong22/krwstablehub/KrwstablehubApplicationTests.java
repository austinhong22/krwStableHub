package com.austinhong22.krwstablehub;

import com.austinhong22.krwstablehub.service.EpochService;
import com.austinhong22.krwstablehub.service.ObligationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class KrwstablehubApplicationTests {

	@MockitoBean
	private ObligationService obligationService;

	@MockitoBean
	private EpochService epochService;

	@Test
	void contextLoads() {
	}

}
