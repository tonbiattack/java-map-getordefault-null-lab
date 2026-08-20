# E007: `Map.getOrDefault`がnullマッピングを既定リージョンへ置き換えない

## 目的

テナントごとのリージョン上書きを保持するMapで、明示的なnullは既定リージョン`GLOBAL`として扱う契約です。`alpha`を`APAC`へ設定して一度解決後、`beta`をnullへ設定して解決する場合、`GLOBAL`を返し、最後のリージョンを`GLOBAL`へ更新し、既定利用件数を`1`にする必要があります。

## 実行環境と再現境界

このラボはJava 21、Maven、JUnit Jupiter 5.11.4だけを使います。フレームワーク、設定ファイル、HTTP、ファイル、データベース、外部I/Oは使いません。公開境界は`TenantRegionResolver#putOverride(String, String)`と`resolve(String)`であり、直接の解決値に加えて、`lastResolvedRegion()`と`globalFallbackCount()`の最終状態を別々に読みます。

テストは、最初に`alpha -> APAC`を成功させたあと、`beta -> null`を解決します。このため、二回目の解決がnullを返すとき、単に戻り値だけでなく、最後に解決した値と既定利用件数が更新されないことを確認できます。入力とMap状態は固定であり、時刻、乱数、並行実行に依存しません。

## 最初に観測した事実

バグ状態はコミット[`5553cf3`](../commit/5553cf3)です。次のコマンドで、意図したアサーション差分を確認しました。

```bash
git checkout 5553cf3
mvn --batch-mode test -Dtest=TenantRegionResolverTest
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 直接の解決値 | `GLOBAL` | `null` | `TenantRegionResolverTest` |
| 最後に解決したリージョン | `GLOBAL` | `APAC` | `TenantRegionResolver#lastResolvedRegion()` |
| 既定リージョン利用件数 | `1` | `0` | `TenantRegionResolver#globalFallbackCount()` |
| キーなしの`getOrDefault` | `GLOBAL` | `GLOBAL` | `MapGetOrDefaultObservationTest` |
| nullマッピングの`getOrDefault` | `GLOBAL`を期待する契約 | `null` | `MapGetOrDefaultObservationTest` |
| `requireNonNullElse`による正規化 | `GLOBAL` | `GLOBAL` | `MapGetOrDefaultObservationTest` |

```text
nullとして設定されたテナントもGLOBALへ解決する
==> expected: <GLOBAL> but was: <null>

最後に解決したリージョンはGLOBALへ更新する
==> expected: <GLOBAL> but was: <APAC>

null設定の解決を既定リージョンの一回として数える
==> expected: <1> but was: <0>
```

完全な失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。直接の解決値だけでなく、最後のリージョンと既定利用件数を最終状態として分けて確認したため、表示だけの問題ではなく、nullマッピングが既定値として扱われず後続状態も更新されていないことを確定できます。

## 競合仮説と検証

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| 既定リージョン定数が誤っている | マッピングのない`gamma`を解決して結果を確認する | `GLOBAL`が返るため棄却。 |
| `beta`がMapへ登録されていない | 観測テストで`containsKey("beta")`を確認する | trueであり、nullへの明示的マッピングが存在するため棄却。 |
| `getOrDefault`がnullマッピングを既定値へ置換しない | 同じMapでキーなしと`beta -> null`の`getOrDefault`結果を比較する | キーなしは`GLOBAL`、nullマッピングはnull。採用。 |

## 確定した原因

バグ状態の解決処理は次のとおりでした。

```java
String resolved = regions.getOrDefault(tenantId, GLOBAL);
```

`Map.getOrDefault`は、キーにマッピングがないときにだけ`defaultValue`を返します。[1] Mapがnull値を許しており、`beta`がnullへマッピングされている場合は、キーは存在するため既定値が使われずnullを返します。

`Map#get`がnullを返すケースには「キーなし」と「キーがnullへマッピング済み」の二つがあります。二つを区別するためには`containsKey`を使えます。[1] しかし本ラボの契約は両方を既定リージョンへ正規化することなので、区別して異なる結果を返すのではなく、取得値のnullを既定値へ変換します。

## 最小修正

修正コミットは[`ebc2c97`](../commit/ebc2c97)です。取得値を`Objects.requireNonNullElse`で正規化しました。

```java
String resolved = Objects.requireNonNullElse(regions.get(tenantId), GLOBAL);
```

この式は、`regions.get(tenantId)`がnullなら`GLOBAL`を、それ以外ならマッピング値を返します。nullへの明示的マッピングとキー不在のどちらも、公開契約どおりGLOBALとなります。

`putOverride`の時点でnullを削除する、`getOrDefault`後に状態を個別に補正する、テスト期待値をnullへ下げる修正は採用していません。今回の公開契約は解決結果を既定値へ正規化することであり、解決式だけを最小変更するのが適切です。

## 回帰保証

### 再発防止テスト

最初に失敗した`nullOverride_usesGlobalAndUpdatesTheResolvedState`はそのまま残しています。このテストは、直接の解決値、最後に解決したリージョン、既定利用件数を別々に検証します。

| テスト | 回帰として守る契約 |
| --- | --- |
| `nullOverride_usesGlobalAndUpdatesTheResolvedState` | null設定をGLOBALとして解決し、最後のリージョン・既定利用件数を更新する。 |
| `absentOverride_usesTheExistingGlobalFallback` | キーが不在の場合の既存GLOBALフォールバックを保つ。 |
| `getOrDefaultDistinguishesAbsentKeyFromKeyMappedToNull` | キー不在とnullマッピングの`getOrDefault`結果が異なることを直接示し、正規化結果を確認する。 |

修正後の`mvn --batch-mode clean test`では、3テストがすべて成功しました。完全な出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。

## 再現手順

```bash
git checkout 5553cf3
mvn --batch-mode test -Dtest=TenantRegionResolverTest
# expected: <GLOBAL> but was: <null>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

## スコープと注意点

この修正は、nullとキー不在を同じ既定値として扱う契約にのみ有効です。nullが明示的な無効化、削除、継承停止を意味する場合は、`Objects.requireNonNullElse`で正規化してはいけません。その場合は`containsKey`を使い、キー不在とnullマッピングを別の分岐として設計してください。[1]

また、Map実装によってはnullキー・null値を許しません。本ラボはnull値を許す`HashMap`に限定しています。`ConcurrentHashMap`などへそのまま移植する際は、Map実装のnull契約も確認してください。

## References

[1] [Oracle: `Map` — `get`, `containsKey`, and `getOrDefault`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html)
