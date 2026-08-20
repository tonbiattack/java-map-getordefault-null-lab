package jp.tonbiattack.debuglab.region;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TenantRegionResolverTest {

    @Test
    void nullOverride_usesGlobalAndUpdatesTheResolvedState() {
        TenantRegionResolver resolver = new TenantRegionResolver();
        resolver.putOverride("alpha", "APAC");
        resolver.resolve("alpha");
        resolver.putOverride("beta", null);

        String resolved = resolver.resolve("beta");

        assertAll(
                () -> assertEquals("GLOBAL", resolved,
                        "nullとして設定されたテナントもGLOBALへ解決する"),
                () -> assertEquals("GLOBAL", resolver.lastResolvedRegion(),
                        "最後に解決したリージョンはGLOBALへ更新する"),
                () -> assertEquals(1, resolver.globalFallbackCount(),
                        "null設定の解決を既定リージョンの一回として数える")
        );
    }

    @Test
    void absentOverride_usesTheExistingGlobalFallback() {
        TenantRegionResolver resolver = new TenantRegionResolver();

        String resolved = resolver.resolve("gamma");

        assertAll(
                () -> assertEquals("GLOBAL", resolved),
                () -> assertEquals("GLOBAL", resolver.lastResolvedRegion()),
                () -> assertEquals(1, resolver.globalFallbackCount())
        );
    }
}
