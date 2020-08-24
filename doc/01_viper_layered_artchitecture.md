# 1. レシピ一覧のリファクタリング

レシピ一覧画面を VIPER + Layered アーキテクチャに沿ってリファクタリングしてみましょう。

ここでは以下の流れでリファクタリングを進めます。

- レシピ一覧の表示
  - [DataSource を作成](#datasource-を作成)
  - [Contract と Entity を作成](#contract-と-entity-を作成)
  - [Presenter を作成](#presenter-を作成)
  - [Interactor を作成](#interactor-を作成)
  - [View, Presenter, Interactor の接続](#view-presenter-interactor-の接続)
- レシピタップ時の挙動
  - [Routing を作成](#routing-を作成)
  - [Presenter にメソッドを追加](#presenter-にメソッドを追加)
  - [View の修正](#view-の修正)

## レシピ一覧の表示

レシピ一覧画面は Firestore からデータを取得し、RecyclerView に表示しています。
まずは Firestore からデータを取得する処理を DataSource として実装します。

### DataSource を作成

レシピ一覧を取得する DataSource を作成しましょう。
DataSource を定義する `datasource` パッケージを `com.cookpad.android.minicookpad` 以下に追加します。

![](https://s3.g.cookpad.com/files/upload/20200723133923_img20200723-10-nwhjiq.png)

最初に DataSource で扱うデータクラスとインタフェースを定義します。
データクラスは定義済みの `Recipe.kt` を流用するので `datasource` パッケージに移動しましょう。

移動する際に VIPER の Entity と区別をしやすくするため `RecipeEntity` にクラス名を変更してください。

次にインタフェースを定義しますが、インタフェースを定義する際に Firestore から取得した値をどう呼び出し元に受け渡すかを考える必要があります。

DataSource で実行されるリモートサーバやDBへのリクエスト処理はかなり時間がかかるので、ほとんどの場合 UI をブロックしないよう非同期処理として実行し、値をどうにかしてメインスレッドに返します。
Firestore もネットワークを通してリクエストを送信するので値の受け渡し方法を考える必要があります。
Android アプリで非同期処理と待ち合わせの実現方法は様々ありますが、今回はシンプルなコールバックを通して非同期処理の返り値の受け渡しを行います。
コールバックはデータの取得に成功した場合にレシピのリストを渡すものと、取得に失敗した場合に例外を渡すものの2つを定義します。

```kotlin
// RecipeDataSource.kt
interface RecipeDataSource {
    fun fetchAll(onSuccess: (List<RecipeEntity>) -> Unit, onFailed: (Throwable) -> Unit)
}
```

次にこのインタフェースを満たすように DataSource を実装します。
Firestore に接続する処理は `RecipeListFragment` で書かれているものを参考(コピペ)にしましょう。
実装を移す際にパース
ここでは Firestore SDK の操作を詳しく解説しませんが、詳しくは[公式ドキュメント](https://firebase.google.com/docs/firestore/query-data/get-data)を参照してください。

インタフェースを実装する具体化クラスには、実体がわかりやすいような命名にしましょう。

```kotlin
// FirebaseRecipeDataSource.kt
class FirebaseRecipeDataSource : RecipeDataSource {
    private val db = FirebaseFirestore.getInstance()

    override fun fetchAll(onSuccess: (List<RecipeEntity>) -> Unit, onFailed: (Throwable) -> Unit) {
        db.collection("recipes")
            .get()
            .addOnSuccessListener { result ->
                // アダプタに渡すかわりにコールバックに受け渡すよう変更
                onSuccess.invoke(result.mapNotNull { RecipeEntity.fromDocument(it) })
            }
            .addOnFailureListener(onFailed)
    }
}
```

これでレシピ一覧を取得する DataSource が実装できました。

次に VIPER 部分に取り掛かります。

### Contract と Entity を作成

VIPER は構成する要素が多いため、画面ごとにパッケージを分けるとわかりやすくなります。
VIPER (View, Interactor, Presenter, Entity, Routing)の1つのまとまりのことを Scene(シーン)といいますが、クックパッドアプリではこの Scene ごとにパッケージを分けています。
今回はそれに習ってレシピ一覧画面用に `recipelist` パッケージを追加します。
これから作成するファイルはすべてこのパッケージに追加していきます。

![](https://s3.g.cookpad.com/files/upload/20200723140130_img20200723-10-15yqypt.png)

はじめに VIPER の定義をまとめる Contract を定義します。
各コンポーネントの詳細は徐々に追加するので、最初は空の状態でそれぞれ定義します。

`Entity` は VIPER Scene で必要な情報を持つデータクラスです。
レシピ一覧ではレシピを表示するために必要な要素(レシピ名、画像、作り方、作者名)を定義します。

DataSource では作り方(steps)は `List<String>` となっていましたが、画面に表示する際にはまとめて表示するため1つの文字列としています。
このように VIPER Scene 側の都合でデータ構造(Entity)が決まるので、VIPER の Entity と DataSource のデータクラスは分けて定義する必要があります。

```kotlin
// RecipeListContract.kt
interface RecipeListContract {
    interface View

    interface Interactor

    interface Presenter

    interface Routing

    data class Recipe(
        val id: String,        // レシピのID
        val title: String,     // レシピのタイトル
        val imagePath: String, // Firebase Storage 上のパス: "images/hogehoge.png"
        val steps: String,     // 作り方のテキストをまとめたもの
        val authorName: String // 作者名
    )
}
```

### Presenter を作成

View からイベントを受け取り、他コンポーネントとのやり取りをまとめる Presenter を作成します。

最初にレシピ一覧を取得際に View から呼び出されるメソッドを定義します。
レシピ一覧の取得際に値は必要ないので引数はなく、View からはイベントを通知するだけなので返り値もありません。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
interface RecipeListContract {
    ...
    interface Presenter {
+        fun onRecipeListRequested()
    }
    ...
}
```

次に定義したインタフェースを満たす Presenter を実装します。
Presenter は View からイベントを受け取り、View, Interactor それぞれのメソッドを呼び出すので、それぞれのインスタンスを保持します(本来 Routing も必要ですが、後で必要になった際に追加します)。
メソッドの実装には他のコンポーネントの定義が必要となるので、最後に戻ってきて実装します。

```kotlin
// RecipeListPresenter.kt
class RecipeListPresenter(
    private val view: RecipeListContract.View,
    private val interactor: RecipeListContract.Interactor    
) : RecipeListContract.Presenter {
    override fun onRecipeListRequested() {
        // TODO: 後で実装
    }
}
```

### Interactor を作成

ビジネスロジックを担う Interactor を定義します。
Interactor では Firestore からレシピ取得し、DataSource のデータクラスから Entity に変換するロジックを実装します。

Interactor から Presenter に値を返す必要があるので、DataSource と同様にコールバックで値を返します。
この時コールバックで受け取る値の型が `List<RecipeContract.Recipe>` となっていることに注意してください。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
interface RecipeContract {
    ...
    interface Interactor {
+        fun fetchRecipeList(onSuccess: (List<Recipe>) -> Unit, onFailed: (Throwable) -> Unit)
    }
    ...
}
```

次にこのインタフェースを満たす Interactor を実装します。

Interactor は DataSource を呼び出すためコンストラクタで DataSource を受け取ります。
先程説明したように VIPER + Layered アーキテクチャのオブジェクト間はインタフェースを通してやり取りを行うため、ここでは `FirebaseRecipeDataSource` ではなく `RecipeDataSource` を受け取るようにします。
DataSource のメソッドを呼び出しコールバックで受け取ったデータは Contract 上で扱う Entity に変換して、コールバックの呼び出し時に受け渡します。

```kotlin
// RecipeInteractor.kt
class RecipeListInteractor(
    private val recipeDataSource: RecipeDataSource
) : RecipeListContract.Interactor {
    override fun fetchRecipeList(
        onSuccess: (List<RecipeListContract.Recipe>) -> Unit,
        onFailed: (Throwable) -> Unit
    ) {
        recipeDataSource.fetchAll(
            onSuccess = { list -> onSuccess.invoke(list.map { it.translate() }) },
            onFailed = onFailed
        )
    }

    // データ構造変換
    private fun RecipeEntity.translate(): RecipeListContract.Recipe =
        RecipeListContract.Recipe(
            id = this.id,
            title = this.title,
            imagePath = this.imagePath ?: "",
            steps = this.steps.joinToString("、"), // 作り方をカンマでまとめます
            authorName = this.authorName
        )
}
```

### View を作成

値を受け取って画面を更新する View を定義します。
画面を更新するメソッドは、レシピ一覧を受け取った場合とエラーを受け取った場合の2つパターンを定義します。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
interface RecipeListContract {
    interface View {
+        fun renderRecipeList(recipeList: List<RecipeListContract.Recipe>)
+        fun renderError(exception: Throwable)
    }
    ...
}
```

次に View として実装する RecipeListFragment を `recipelist` パッケージ以下に移動し、先程 Contact で定義した View を RecipeListFragment で実装します。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/RecipeListFragment.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
- class RecipeListFragment : Fragment() {
+ class RecipeListFragment : Fragment(), RecipeListContract.View {
    ...
+
+    override fun renderRecipeList(recipeList: List<RecipeListContract.Recipe>) {
+        adapter.update(recipeList)
+    }
+
+    override fun renderError(exception: Throwable) {
+        Toast.makeText(requireContext(), "Failed to fetch recipe list.", Toast.LENGTH_SHORT).show()
+    }
}
```

既存のアダプタ実装(RecipeListAdapter)のデータ型を VIPER の Entity(`RecipeListContract.Recipe`)に変更します。
その際 steps の型が変わっているので、変更を適用します。
また RecipeListFragment と同様にパッケージを `recipelist` 以下に移動します。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/RecipeListAdapter.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListAdapter.kt
class RecipeListAdapter(
    private val onRecipeClickListener: OnRecipeClickListener
) : RecyclerView.Adapter<RecipeListAdapter.RecipeViewHolder>() {

-    private var recipeList: List<RecipeEntity> = mutableListOf()
+    private var recipeList: List<RecipeListContract.Recipe> = mutableListOf()


-	fun update(recipeList: List<RecipeEntity>) {
+   fun update(recipeList: List<RecipeListContract.Recipe>) {

-        binding.steps.text = recipe.steps.joinToString("、")
+        binding.steps.text = recipe.steps

	class RecipeDiffCallback(
-        private val oldList: List<RecipeEntity>,
-        private val newList: List<RecipeEntity>
+        private val oldList: List<RecipeListContract.Recipe>,
+        private val newList: List<RecipeListContract.Recipe>
    ) : DiffUtil.Callback() {
```

最後に Presenter を利用するように既存の処理を書き換えます。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
    private lateinit var binding: FragmentRecipeListBinding
+
+    lateinit var presenter: RecipeListContract.Presenter
...
     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
+        presenter = RecipeListPresenter(
+            view = this,
+            interactor = RecipeListInteractor(FirebaseRecipeDataSource())
+        )
+        presenter.onRecipeListRequested()

-        db.collection("recipes")
-            .get()
-            .addOnSuccessListener { result ->
-                adapter.update(result.mapNotNull { Recipe.fromDocument(it) })
-            }
-            .addOnFailureListener {
-                Toast.makeText(requireContext(), "レシピ一覧の取得に失敗しました", Toast.LENGTH_SHORT).show()
-            }
```

### View, Presenter, Interactor の接続

最後に定義しておいた Presenter のメソッドを実装します。

Interactor のメソッドを呼び出し、そのコールバックとして View のメソッドを呼び出すようにすれば処理をつなげることができます。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListPresenter.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListPresenter.kt
class RecipeListPresenter {
    override fun onRecipeListRequested() {
-        // TODO: 後で実装
+        interactor.fetchRecipeList(
+            onSuccess = { view.renderRecipeList(it) },
+            onFailed = { view.renderError(it) }
+        )
    }
}
```

これで VIPER のすべての流れが実装できました。
アプリを起動してレシピ一覧が表示されることを確認してください。

## レシピタップ時の挙動

レシピ一覧のレシピをタップするとレシピ詳細画面に遷移します。
この処理にも同様にアーキテクチャを適用していきましょう。

### Routing を作成

画面遷移を司る Routing を定義します。
レシピ詳細画面では `Recipe#id` と `Recipe#name` が必要になるので、引数として値を受け取るメソッドを追加します。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
interface RecipeListContract {
    ...
    interface Routing {
+        fun navigateRecipeDetail(recipeId: String, recipeName: String)
    }
    ...
}
```

Routing には Android の画面遷移に必要な Activity/Fragment を保持します(今回は Fragment)。

MiniCookpad では画面遷移で Navigation Component を利用しています。
Navigation Component 及び SafeArgs の詳しい内容については[公式ドキュメント](https://developer.android.com/guide/navigation/navigation-getting-started)を参照してください。

```kotlin
// RecipeListRouting.kt
class RecipeListRouting(
    private val fragment: RecipeListFragment
) : RecipeListContract.Routing {
    override fun navigateRecipeDetail(recipeId: String, recipeName: String) {
        fragment.findNavController()
            .navigate(RecipeListFragmentDirections.showRecipeDetail(recipeId, recipeName))
    }
}
```

### Presenter にメソッドを追加

View から画面遷移のイベントを受け取るメソッドを Presenter に追加します。
先程定義したようにレシピ詳細への遷移には `Recipe#id` と `Reccipe#name` が必要となるため、View から値を受け取れるように Presenter のメソッドにも引数として定義します。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListContract.kt
interface RecipeListContract {
    ...
    interface Presenter {
        fun onRecipeListRequested()
+        fun onRecipeDetailRequested(recipeId: String, recipeName: String)
    }
    ...
}
```

レシピ一覧からレシピ詳細への画面遷移時に遷移以外の処理は必要ないため、ここでは単純に Routing を呼び出します。
また Presenter から Routing の呼び出しが必要になるため、コンストラクタを経由して値を保持するように書き換えます。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListPresenter.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListPresenter.kt
class RecipeListPresenter(
    private val view: RecipeListContract.View,
-    private val interactor: RecipeListContract.Interactor
+    private val interactor: RecipeListContract.Interactor,
+    private val routing: RecipeListContract.Routing
) : RecipeListContract.Presenter {    
+
+    override fun onRecipeDetailRequested(recipeId: String, recipeName: String) {
+        routing.navigateRecipeDetail(recipeId, recipeName)
+    }
}
```

さらに View から Presenter の初期化時に、追加された Routing を受け渡すように変更します。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
        presenter = RecipeListPresenter(
            view = this,
-            interactor = RecipeListInteractor(FirebaseRecipeDataSource())
+            interactor = RecipeListInteractor(FirebaseRecipeDataSource()),
+            routing = RecipeListRouting(this)
        )
```

### View の修正

RecipeListFragment で画面遷移を行っていた箇所を Presenter の呼び出しに切り替えます。

```diff
--- a/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
+++ b/app/src/main/java/com/cookpad/android/minicookpad/recipelist/RecipeListFragment.kt
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RecipeListAdapter { recipeId, recipeName ->
-            findNavController()
-                .navigate(RecipeListFragmentDirections.showRecipeDetail(recipeId, recipeName))
+        presenter.onRecipeDetailRequested(recipeId, recipeName)
```

レシピ一覧からレシピ詳細への画面遷移が実装できました。
アプリを起動して一覧から詳細画面への遷移を確かめて見ましょう。
