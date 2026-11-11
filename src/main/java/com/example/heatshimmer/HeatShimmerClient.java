package com.example.heatshimmer;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HeatShimmerClient implements ClientModInitializer {
	public static final String MOD_ID = "heatshimmer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("[HeatShimmer] Stage 0 (recon) build loaded.");
		try {
			Recon.run();
		} catch (Throwable t) {
			LOGGER.error("[HeatShimmer] Recon failed", t);
		}
	}
}
