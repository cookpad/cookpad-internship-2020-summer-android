# 3. UI テスト

レシピ一覧画面でレシピがタップされた場合の画面遷移を UI テストによって確認してみましょう。

View のテストは Presenter のモックを作成し Presenter の挙動をコントロールすることでテストケースを実装することができます。
しかし Activity/Fragment のような OS 側にインスタンスを生成されるオブジェクトのパラメータを外部から適切なタイミングですり替えることは難しいです。

Android アプリケーションの開発では、この問題に対しての手段として DI (Dependency Injection)を利用する方法が一般的です。
DI コンテナを利用することにより、Presenter のような Activity/Fragment 内で利用するオブジェクトの生成方法を外部に切り離し、テスト実行時にはその生成方法をすり替えることでオブジェクトのすり替えを行います。

しかし DI の仕組みは複雑であり導入にも時間がかかってしまうので、DI の仕組みを導入せず、現状の仕組みでできる範囲でテストを実装します。

今回は最初に本物の Presenter を利用し、途中からモックにすり替えを行うという手段でテストを実装します。
具体的にはレシピ一覧の取得までは本物の Presenter (RecipeListPresenter)を利用し、レシピタップ前にモックにすり替えます。

まずは UI テストのために Fragment を起動してみましょう。

## Fragment テスト

Fragment の UI テストを実装するためには基本的に公式の [fragment-testing ライブラリ](https://developer.android.com/training/basics/fragments/testing)を利用します。
Fragment を表示するためには Activity にアタッチする必要がありますが、fragment-testing には `FragmentScenario` というテスト用の Activity に自動的にアタッチする機能があり非常に便利なライブラリとなっています。
また FragmentScenario は特定のライフサイクルタイベントまで進めることができ、イベントのタイミングをコントロールしやすくなっています。

はじめにレシピ一覧を取得して onResume まで進めるコードを書いてみましょう。

ここで注意が必要なのはレシピ一覧の取得は非同期処理なので、取得が完了するまで待つ必要があります。
これはネットワークに依存した処理となってしまうのでテストが不安定となる要因になってしまい本来行うべきではありませんが、今回は理解しやすいように sleep で非同期処理を待ちます。

UI テストは Android 実行環境で動作させる必要があり、Android 実行環境で動作させるテストは `app/src/androidTest` 以下に作成する必要があります。
パッケージはユニットテストと同様にテスト対象と同じパッケージに設置するので、以下のようなテストクラスを `app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt` に作成してください。

```kotlin
// RecipeListFragmentTest.kt
@RunWith(AndroidJUnit4::class) // UI テスト用の JUnit テストランナー
class RecipeListFragmentTest {
    @Test
    fun verifyRecipeDetailNavigation() {
        // given
        val fragmentScenario = launchFragmentInContainer(themeResId = R.style.AppTheme) { // アプリのテーマを設定
            RecipeListFragment()
        }.moveToState(Lifecycle.State.RESUMED)
        sleep(5000) // レシピ一覧の取得を待つ(本来はやるべきではない)
    }
}
```

このコードを実行してみるとエミュレータが立ち上がってレシピ一覧が一瞬表示されるようになります。
次に Presenter をモックにすり替えて見ましょう。

## Presenter のすり替え

モックの作り方はユニットテストと全く同じです。
FragmentScenario では `FragmentScenario#onFragment` メソッドを利用することで直接 Fragment にアクセスすることが出来ます。
これを利用して RecipeListFragment の Presenter をモックに切り替えます。
また合わせてレシピがタップされた際に呼び出される予定のメソッドのモックを作成します。

```diff
--- a/app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt
+++ b/app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt
@RunWith(AndroidJUnit4::class)
class RecipeListFragmentTest {
+
+    lateinit var presenter: RecipeListContract.Presenter
+
+    @Before
+    fun setup() {
+        presenter = mock()
+    }
+
    @Test
    fun verifyRecipeDetailNavigation() {
        // given
+        whenever(presenter.onRecipeDetailRequested(any(), any())).then {}
        val fragmentScenario = launchFragmentInContainer(themeResId = R.style.AppTheme) {
            RecipeListFragment()
        }.moveToState(Lifecycle.State.RESUMED)
+        fragmentScenario.onFragment { it.presenter = presenter }
}
```

## UI 操作の実行

次にレシピをタップするという UI 操作を実行してみましょう。
UI 操作は [Espresso](https://developer.android.com/training/testing/espresso) というライブラリを利用することで簡単に模倣することができます。

基本的な Espresso の UI 操作時に利用する構文は `onView(何に対して).perform(どうする(操作))` です。
Espresso ではタップやスクロール、文字の入力等人間が行う操作の殆どを模倣することができます。
操作以外にも Espresso には View のアサーションを行うことができます。実行できる内容については[公式のチートシート](https://developer.android.com/training/testing/espresso/cheat-sheet)を参照してください。

今回はレシピのタイトルに対してタップするという挙動を以下のように実装できます。
本来 RecyclerView などの動的にコンテンツが変化する View に対しては「RecyclerView のアイテムの1つ目」というように対象をとって操作を行いますが、これは少し複雑な内容となってしまいます。  
なので今回は最初から追加されているレシピのタイトルを指定して操作を行います。

```diff
--- a/app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt
+++ b/app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt
        fragmentScenario.onFragment { it.presenter = presenter }
+
+        // when
+        onView(withText("鶏肉と胡瓜のあっさり塩炒め")).perform(click()) // 「鶏肉と胡瓜のあっさり塩炒め」というテキストを持つ View に対して「クリック(タップ)」操作を行います
    }
}
```

## アサーション

最後にユニットテストと同様に意図通り Presenter のメソッドが呼び出されているか確認してみましょう。
タップされた場合には `RecipeListContract.Presenter#onRecipeDetailRequested` のメソッドが呼び出されるので、`verify` メソッドで確認してみましょう。

```diff
--- a/app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt
+++ b/app/src/androidTest/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragmentTest.kt
        onView(withText("鶏肉と胡瓜のあっさり塩炒め")).perform(click())
+
+        // then
+        verify(presenter).onRecipeDetailRequested(any(), eq("鶏肉と胡瓜のあっさり塩炒め")) // レシピID は人によって異なるので、レシピ名だけチェック
    }
}
```

これでレシピ一覧をタップした場合に Presenter のメソッドの呼び出しを確認する UI テストが実装できました。
実際にテストを実行してテストが通るか確認してください。
