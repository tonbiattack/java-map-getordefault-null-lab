package jp.tonbiattack.debuglab.region;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class MapGetOrDefaultObservationTest {

    @Test
    void getOrDefaultDistinguishesAbsentKeyFromKeyMappedToNull() {
        Map<String, String> regions = new HashMap<>();
        regions.put("beta", null);

        String absentResult = regions.getOrDefault("gamma", "GLOBAL");
        String nullMappingResult = regions.getOrDefault("beta", "GLOBAL");
        String nullNormalizedResult = Objects.requireNonNullElse(regions.get("beta"), "GLOBAL");

        assertAll(
                () -> assertEquals("GLOBAL", absentResult,
                        "マッピングがないキーでは既定値が返る"),
                () -> assertTrue(regions.containsKey("beta"),
                        "betaは存在するがnullへマッピングされている"),
                () -> assertNull(nullMappingResult,
                        "nullマッピングではgetOrDefaultは既定値でなくnullを返す"),
                () -> assertEquals("GLOBAL", nullNormalizedResult,
                        "nullを既定値へ正規化するなら取得結果を別途扱う")
        );
    }
}
