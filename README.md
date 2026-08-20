# `Map.getOrDefault`がnull値を既定リージョンへ置き換えない

Java標準ライブラリの`Map.getOrDefault`を題材に、**明示的にnullへ設定されたテナントリージョンが既定値にならない**問題を、失敗するテスト、原因の直接観測、最小修正、回帰テストの順に追うデバッグ教材です。既定ブランチの`main`は成功状態に保ち、意図的に失敗する状態はGit履歴に独立して残します。

## この題材で守る契約

> `alpha`を`APAC`へ設定して一度解決後、`beta`をnullへ設定して解決した場合、`GLOBAL`を返し、最後に解決したリージョンを`GLOBAL`へ更新し、既定リージョン利用件数を`1`にする。

| 段階 | 実施内容 | 確認すること |
| --- | --- | --- |
| 再現 | `beta -> null`のマッピングを作り、`beta`を解決する | 解決値はnull、最後のリージョンは旧値`APAC`、既定利用件数は0のままとなる |
| 観測 | キーなしと`key -> null`を同じMapで比較する | 前者の`getOrDefault`は`GLOBAL`、後者はnullを返す |
| 修正 | `Objects.requireNonNullElse(map.get(key), "GLOBAL")`を使う | null値を既定リージョンとして正規化できる |
| 回帰防止 | 同じ解決テストを再実行する | 解決値、最後のリージョン、既定利用件数がすべて更新される |

## 必要な環境

| 項目 | バージョン |
| --- | --- |
| JDK | 21 |
| Maven | 3.8以上 |
| テストランナー | JUnit Jupiter 5.11.4 |
| アプリケーションフレームワーク | 不使用 |

## 最短の開始手順

```bash
mvn --batch-mode clean test
```

検証済みの`main`では、3テストがすべて成功します。

## バグを再現する

```bash
git checkout 5553cf3
mvn --batch-mode test -Dtest=TenantRegionResolverTest
# expected: <GLOBAL> but was: <null>
# expected: <GLOBAL> but was: <APAC>
# expected: <1> but was: <0>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

バグコミットでは設定やMap自体の登録ではなく、nullを既定リージョンとして扱う契約だけが失敗します。完全な出力は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。

## 原因の要点

`Map.getOrDefault(key, defaultValue)`は、キーに**マッピングがない**場合にだけ既定値を返します。[1] Mapがnull値を許し、`beta -> null`を明示的に持つ場合、`getOrDefault("beta", "GLOBAL")`の結果はnullです。

`Map#get`はキーがない場合にもnullを返すため、`get`だけで不在とnullマッピングを区別することはできません。必要なら`containsKey`で区別します。[1] 一方、このラボの契約は「不在・nullのいずれもGLOBALへ正規化する」ことです。そのため`Objects.requireNonNullElse`で取得値をnullから既定値へ変換します。

## プロジェクト構成

```text
.
├── docs/
│   ├── debugging-record.md      # 観測・仮説・原因・修正・回帰保証
│   ├── novelty-report.md        # 既存Java記事との四軸比較
│   └── topic-brief.md           # 実装前に固定した契約と再現境界
├── evidence/
│   ├── 01-bug-service-test-output.txt
│   ├── 02-map-observation-output.txt
│   └── 03-fixed-full-test-output.txt
├── src/main/java/.../region/
│   └── TenantRegionResolver.java
└── src/test/java/.../region/
    ├── MapGetOrDefaultObservationTest.java
    └── TenantRegionResolverTest.java
```

詳細な調査手順は[デバッグ記録](docs/debugging-record.md)、既存コンテンツとの差分は[題材重複調査レポート](docs/novelty-report.md)を参照してください。

## スコープ

この教材はnull値を許す一つの`HashMap`と、文字列の既定リージョンを対象にします。JSONのnullとキー省略、設定ファイルの読み込み、複数優先度の設定階層、Map実装ごとのnull禁止、並行更新、データベースのNULLは対象外です。

「nullは既定値として扱う」という契約が不要で、nullが明示的な無効化・削除・未設定を意味する場合には、この修正を適用してはいけません。nullと不在を区別する必要がある場合は、`containsKey`を使って意図をコード上で明示してください。[1]

## References

[1] [Oracle: `Map` — `get`, `containsKey`, and `getOrDefault`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html)
