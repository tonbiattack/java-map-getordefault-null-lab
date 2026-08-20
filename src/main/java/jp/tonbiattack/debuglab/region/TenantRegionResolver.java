package jp.tonbiattack.debuglab.region;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * テナントごとのリージョン上書きと、GLOBAL既定値を解決します。
 */
public class TenantRegionResolver {

    private static final String GLOBAL = "GLOBAL";

    private final Map<String, String> regions = new HashMap<>();
    private String lastResolvedRegion;
    private int globalFallbackCount;

    public void putOverride(String tenantId, String region) {
        regions.put(tenantId, region);
    }

    public String resolve(String tenantId) {
        String resolved = Objects.requireNonNullElse(regions.get(tenantId), GLOBAL);
        if (GLOBAL.equals(resolved)) {
            globalFallbackCount++;
        }
        if (resolved != null) {
            lastResolvedRegion = resolved;
        }
        return resolved;
    }

    public String lastResolvedRegion() {
        return lastResolvedRegion;
    }

    public int globalFallbackCount() {
        return globalFallbackCount;
    }
}
