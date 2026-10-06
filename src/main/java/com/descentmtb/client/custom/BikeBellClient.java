package com.descentmtb.client.custom;

import com.descentmtb.client.BikeClientController;
import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.custom.BikeParts;
import com.descentmtb.network.BikeBellPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** The bell key: while riding, each press asks the server to ring the bell of the bike's build. */
public final class BikeBellClient {
    /** Called every client tick. */
    public static void tick() {
        boolean riding = BikeClientController.riding() != null;
        while (ModKeyMappings.BELL.consumeClick()) {
            if (riding) {
                PacketDistributor.sendToServer(new BikeBellPayload());
                // the hand leaves the grip (and risks the ride) only if there is a bell to ring
                var bike = BikeClientController.riding();
                if (bike.sim() != null && bike.build().bell() != BikeParts.Bell.NONE) bike.sim().ringBell();
            }
        }
    }

    private BikeBellClient() {}
}
