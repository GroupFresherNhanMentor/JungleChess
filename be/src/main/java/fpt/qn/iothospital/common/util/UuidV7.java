package fpt.qn.iothospital.common.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public final class UuidV7 {

    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    private UuidV7() {}
}
