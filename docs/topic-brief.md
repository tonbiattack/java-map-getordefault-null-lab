# 題材企画: `Map.getOrDefault`がnull値を既定リージョンへ置き換えない

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | 既定値のために`Map.getOrDefault`を使い、設定Mapが明示的なnull値を持つ可能性を見落としやすい中級者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | `Map.getOrDefault`はキーに**マッピングがない**場合だけ既定値を返し、キーがnullへマッピングされている場合はnullを返す。直接の解決値、最後に解決したリージョン、既定リージョン利用件数を分けて観測し、キーの存在・null値・既定値の三つの仮説を比較できる。 |
| 実行基盤 | Maven、Java 21、JUnit Jupiter 5.11.4 |
| フレームワーク非依存性 | 原因は`java.util.Map#getOrDefault`とnullマッピングの標準ライブラリ契約である。HTTP、DI、DB、設定ファイル、外部APIには依存しない。 |

## 学習する契約

> `alpha`を`"APAC"`へ設定し、`beta`を明示的にnullへ設定した状態で`beta`のリージョンを解決する場合、`"GLOBAL"`を返し、最後に解決したリージョンを`"GLOBAL"`へ更新し、既定リージョン利用件数を一件にすべきだが、バグ状態ではnullを返し、旧リージョン`"APAC"`と利用件数0が残る。

### 対象の直接原因

`regions.getOrDefault(tenantId, "GLOBAL")`を使っている。`getOrDefault`はキーにマッピングがない場合だけ既定値を返すため、`HashMap`に`tenantId -> null`という明示的なマッピングがあるとnullを返す。

### 対象外

このラボは設定ファイルの構文、JSON nullとキー省略の変換、複数の優先度を持つ設定階層、並行更新、Map実装ごとのnull禁止、データベースのNULL、テナント認可を扱わない。null値を許す一つの`HashMap`から、既定リージョンを文字列として解決する狭い規則だけを扱う。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `TenantRegionResolver#putOverride(String, String)`、`resolve(String)`、`lastResolvedRegion()`、`globalFallbackCount()`。 |
| 入力・初期状態 | `alpha`を`"APAC"`へ設定して一度解決後、`beta`をnullへ設定して解決する。 |
| Redの観測 | `"GLOBAL"`を期待するが、バグ状態ではnullを返す。 |
| 最終観測 | `lastResolvedRegion()`が`"GLOBAL"`となり、`globalFallbackCount()`が`1`であることを別々に検証する。 |
| 決定性 | 時刻、乱数、並行実行、`sleep`、外部I/Oを使わず、固定の文字列とインメモリMapだけを使う。 |
| 固定状態の検証コマンド | `mvn --batch-mode clean test` |
| バグ状態の確認コマンド | `git checkout <bug-commit>`後に`mvn --batch-mode test -Dtest=TenantRegionResolverTest` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| A: 既定リージョン定数が誤っている | マッピングのない`gamma`を解決し、`"GLOBAL"`が返ることを確認する。 |
| B: `beta`がMapへ登録されていない | `containsKey("beta")`と`get("beta")`を直接観測する。 |
| C: `getOrDefault`がnullマッピングを既定値へ置換しない | 同じMapで`getOrDefault("beta", "GLOBAL")`と`Objects.requireNonNullElse(get("beta"), "GLOBAL")`を比較する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | null設定で既定リージョンを返せない失敗を再現する | 対象テストが`"GLOBAL"`期待・null実際のアサーション差分で失敗する。 |
| 2 | null設定を既定リージョンへ正規化する | 同じ検証が成功し、全体も成功する。 |
