package com.austinhong22.krwstablehub;

import com.austinhong22.krwstablehub.service.EpochService;
import com.austinhong22.krwstablehub.service.EpochCloseService;
import com.austinhong22.krwstablehub.service.EpochQueryService;
import com.austinhong22.krwstablehub.service.NettingService;
import com.austinhong22.krwstablehub.service.ObligationService;
import com.austinhong22.krwstablehub.service.SettlementProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class KrwstablehubApplicationTests {

	@MockitoBean
	private ObligationService obligationService;

	@MockitoBean
	private EpochService epochService;

	@MockitoBean
	private NettingService nettingService;

	@MockitoBean
	private EpochCloseService epochCloseService;

	@MockitoBean
	private EpochQueryService epochQueryService;

	@MockitoBean
	private SettlementProcessor settlementProcessor;

	@Test
	void contextLoads() {
	}

}
