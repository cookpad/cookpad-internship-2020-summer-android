# 2. ユニットテスト実装

実装したレシピ一覧の VIPER コンポーネントに対してユニットテストを実装してみましょう。

ユニットテストを実装する際は、テスト対象単体(ユニット)をテストできるようにしなければなりません。
一般的な手法としては依存するオブジェクトをモックして振る舞いを制御し、テスト対象のオブジェクトに集中するという方法をとります。

VIPER はコンポーネント間はインタフェースを通して参照しているので、実装に依存することなくモックの作成ができます。
またコンストラクタを経由して依存するオブジェクトの受け渡しをするため、モックの注入も簡単に行えるようになっています。

今回はテストの実装方法がわかりやすい Interactor と Presenter のテストを実装してみましょう。

## Interactor のテスト

依存する DataSource のモックを作成して RecipeListInteractor のユニットテストを実装します。

### 依存オブジェクトのモックを作成

はじめにテストクラスを作成してみましょう。

ユニットテストは `app/src/test` 以下にテストクラスを配置する必要があり、基本的にテスト対象のオブジェクトと同じパッケージ以下に作成します。
今回はすでに用意されている `RecipeListInteractorTest.kt` を利用してください。

はじめに依存するオブジェクトのモックとテスト対象のオブジェクトを作成します。
JUnit ではテストケースの実行前に実行されるメソッドを `@Before` アノテーションを付けることで定義できます。
テストケースの実行ごとにモックとテスト対象オブジェクトを作り直したいので、`@Before` がつけられたメソッドでそれぞれイニシャライズします。

今回使用する Mockito は Kotlin 用の拡張ライブラリ(mockito-kotlin)を利用することで、以下のように簡単にモックを作成することができます。

```kotlin
// RecipeListInteractorTest.kt
class RecipeListInteractorTest {
    lateinit var recipeDataSource: RecipeDataSource

    lateinit var interactor: RecipeListInteractor

    @Before
    fun setup() {
        recipeDataSource = mock() // モック作成
        interactor = RecipeListInteractor(recipeDataSource)
    }
}
```

次に GWT に沿ってレシピ一覧を取得する際のテストケースを実装します。

### DataSource のメソッドをモック(Given)

`RecipeDataSource#fetchAll` を呼び出した際に、コールバックにレシピ一覧データを載せて呼び出すようにメソッドをモックします。
Mockito によるメソッドのモック方法は大まかに以下のような方法です。

```kotlin
// メソッドが値を返さない場合
whenever(モックしたいメソッドの呼び出し).then { 呼び出された際に実行したい処理 }
// メソッドが値を返す場合
whenever(モックしたいメソッドの呼び出し).thenReturn(返す値)
```

モックしたメソッドが呼び出される条件として引数にマッチャーを渡せますが、今回は説明を省くためどんな引数にもマッチする `any()` を指定します。

ここでは一旦単に空のリストを返すようにします。

```diff
--- a/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
+++ b/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
+    @Test
+    fun verifyFetchRecipeListSuccess() {
+        // given
+        whenever(recipeDataSource.fetchAll(any(), any())).then {
+            // 引数として受け取ったコールバックをキャストして呼び出す
+            (it.arguments[0] as  (List<Recipe>) -> Unit).invoke(listOf()) // とりあえずからのリストを返す
+        }
+    }
}
```

### DataSource が呼び出されていることを確認(When, Then)

次に実際にテストしたい Interactor のメソッドを呼び、意図通りに RecipeDataSource のメソッドが呼び出されていることを Mockito の `verify` メソッドで確認します。

```diff
--- a/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
+++ b/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
    @Test
    fun verifyFetchRecipeListSuccess() {
    ...
+        // when
+        interactor.fetchRecipeList({}, {}) // からのメソッドを渡す
+
+        // then
+        verify(recipeDataSource).fetchAll(any(), any())
    }
```

ここまで実装できたらテストを実行して見ましょう。
テストはクラス名の横のスタートボタンから実行できます。

### Interactor の値の扱いをチェックする

次にメソッドが実行される際に利用される値について確認していきます。

最初に DataSource に渡されたコールバックが実際に呼び出されているかを確認します。

```diff
--- a/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
+++ b/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
    @Test
    fun verifyFetchRecipeListSuccess() {
        // given
+        val onSuccess: (List<RecipeListContract.Recipe>) -> Unit = mock()
        whenever(recipeDataSource.fetchAll(any(), any())).then {

        // when
-        interactor.fetchRecipeList({}, {}) // からのメソッドを渡す
+        interactor.fetchRecipeList(onSuccess, {})

        // then
        verify(recipeDataSource).fetchAll(any(), any())
+        verify(onSuccess).invoke(any())
    }
```

次にレシピの変換も同様に変換ロジックが問題ないことも確認しましょう。

`argumentCaptor<T>` 関数で生成されるオブジェクト使って `verify` メソッドの引数として `capture` メソッドを渡すとメソッド呼び出し時の引数を取り出すことができます。
取り出した値からデータの変換が正しく行われているかを確認します。

値のアサーションには様々なライブラリが存在していますが、今回は Google 謹製のアサーションライブラリの Truth を使ってみましょう。
Truth による値のアサーションは `assertThat(評価する値).isEqualTo(期待する値)` という構文で実行できます。

```diff
--- a/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
+++ b/app/src/test/java/com/cookpad/android/minicookpad/recipelist/RecipeListInteractorTest.kt
	@Test
    fun verifyFetchRecipeListSuccess() {
        // given
        val onSuccess: (List<RecipeListContract.Recipe>) -> Unit = mock()
+        val recipeList = listOf(
+            Recipe(
+                id = "xxxx",
+                title = "おいしいきゅうりの塩もみ",
+                imagePath = "images/recipe.png",
+                steps = listOf("きゅうりを切る", "塩をまく", "もむ"),
+                authorName = "クックパド美"
+            )
+        )
		whenever(recipeDataSource.fetchAll(any(), any())).then {
-            (it.arguments[0] as  (List<RecipeEntity>) -> Unit).invoke(listOf())
+            (it.arguments[0] as  (List<RecipeEntity>) -> Unit).invoke(recipeList)
        }

        // then
+        val argumentCaptor = argumentCaptor<List<RecipeListContract.Recipe>>()
        verify(recipeDataSource).fetchAll(any(), any())
-        verify(onSuccess).invoke(any())
+        verify(onSuccess).invoke(argumentCaptor.capture())
+        argumentCaptor.firstValue.first().also {
+            assertThat(it.id).isEqualTo("xxxx")
+            assertThat(it.title).isEqualTo("おいしいきゅうりの塩もみ")
+            assertThat(it.imagePath).isEqualTo("images/recipe.png")
+            assertThat(it.steps).isEqualTo("きゅうりを切る、塩をまく、もむ")
+            assertThat(it.authorName).isEqualTo("クックパド美")
+        }
```

### DataSource 側からエラーが返された場合のテストケースも追加する

同様にエラーが発生した場合のテストも実装してみましょう。
以下のヒントを参考にしながら実装してください。

```kotlin
	@Test
    fun verifyFetchRecipeListError() {
        // given
        // TODO: 前のテストケースと同様に RecipeDataSource のメソッドのモックを作成 & エラー用コールバックを作成

        // when
        // TODO: Interactor のメソッドを呼び出し

        // then
        // TODO: verify メソッドによってメソッドが呼び出されていることを確認(エラーの場合は値を確認しなくてもよいです)
    }
```

## Presenter のテスト

Presenter に対しても Interactor と同様にテストを実装してみましょう。
Presenter では View, Interactor, Routing のモックを作成することで Interactor と同様にメソッドの呼び出しを確認しながらテストが実装できます。

以下の3つのテストケースを実装してみてください。

- レシピ一覧の取得に成功した場合
- レシピ一覧の取得に失敗した場合
- レシピがタップされレシピ詳細へ画面遷移する場合
