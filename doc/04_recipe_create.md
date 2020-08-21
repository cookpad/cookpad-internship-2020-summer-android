# 4. 投稿画面の実装

これまでの情報をもとに VIPER + Layered アーキテクチャでレシピ投稿画面を実装してみましょう。

## 実装する機能

レシピをユーザ入力したあとに保存する際の処理を実装してください。
保存ボタンをタップした場合に期待される挙動は以下のとおりです。

- 保存ボタンをタップすると入力されたレシピデータをアップロード
- アップロードに成功した場合は「レシピを作成しました」とトースト表示して画面を終了
- アップロードに失敗した場合は「レシピの保存に失敗しました」とトースト表示

<img src="images/recipe-create.gif" width="300" />

レイアウトファイル(activity_recipe_create.xml)と RecipeCreateActivity は用意してあるのでそちらを利用してください。
レシピには画像を設定する機能がありますが、 RecipeCreateActivity にはレシピ画像の選択機能を既に実装してあります。
選択された画像の URI が ViewModel に保存されるようになっているので、送信時にはそちらを利用してください。

大まかな手順とそれぞれヒントを用意しているので、確認しながら実装を進めてください。

Firestore のレシピのデータ構造はすでに Datasource 上で定義されている通りです。
UI 上では作り方の数は固定で3つとなっています。

## 実装手順

大まかな実装手順は以下のとおりです。

手順ごとにそれぞれヒントを用意しているので、実装に詰まったときには確認してください。

- 画像アップロードを行う ImageDataSource を定義
- RecipeDataSource にレシピ作成のメソッド定義を追加
- Contract と Entity を作成
- Presenter を作成
- Interactor を作成
- View を作成
- View, Presenter, Interactor の処理を接続

### 画像アップロードの ImageDataSource を定義

画像ファイルの URI を受け取り、アップロードに成功した場合アップロードした画像のパス(Cloud Storage 上でのパス)をコールバックを通して返す `ImageDataSource` と、
その実装として Cloud Storage へ画像ファイルをアップロードする `FirebaseImageDataSource` を追加します。

具体的には以下のような実装となります。

```kotlin
// ImageDataSource.kt
interface ImageDataSource {
    fun saveImage(uri: String, onSuccess: (String) -> Unit, onFailed: (Throwable) -> Unit)
}

// FirebaseImageDataSource.kt
class FirebaseImageDataSource : ImageDataSource {
    private val reference = FirebaseStorage.getInstance().reference

    override fun saveImage(
        uri: String,
        onSuccess: (String) -> Unit,
        onFailed: (Throwable) -> Unit
    ) {
        val imageRef = "$COLLECTION_PATH/${UUID.randomUUID()}" // "images/87360ebc00235b3b9b03e1716844de57" のようなパスにアップロード
        reference.child(imageRef).putFile(Uri.parse(uri))
            .addOnSuccessListener { onSuccess.invoke(imageRef) }
            .addOnFailureListener(onFailed)
    }

    private companion object {
        const val COLLECTION_PATH = "images"
    }
}
```

Cloud Storage へのアップロード方法の詳しい内容は[ドキュメント](https://firebase.google.com/docs/storage/android/upload-files)を参照してください。

### RecipeDataSource にレシピ作成のメソッド定義を追加

レシピデータを受け取って Firestore にアップロードするメソッドを追加します。
レシピ保存後に値を受け取る必要はないので、成功時のコールバックには引数は必要ありません。

Firestore へのデータ追加する際にはいくつか方法がありますが、今回は Map 構造に変換して保存します。
Map への変換メソッドは用意してあるので、具体的には以下のようにレシピの保存を行うことができます。

```kotlin
db.collection("recipes").add(recipe.toMap())
```

Firestore へのデータ追加方法は他にもいくつか存在します。
詳しくは[ドキュメント](https://firebase.google.com/docs/firestore/manage-data/add-data)を参照してください。

### Contract と Entity を作成

空コンポーネントを定義した Contract と Entity を作成します。

Entity は入力された値を保持するようデータ構造を定義します。

**ヒント**

<details>
```kotlin
data class Recipe(
    val title: String,
    val imageUri: String,
    val steps: List<String>
)
```
</details>

### Presenter を作成

保存ボタンをタップした際に受け取るイベントを定義します。
メソッドは保存するレシピデータの Entity を引数として受け取ります。

### Interactor を作成

作成した ImageDataSource と RecipeDataSource を呼び出してデータのアップロードするメソッドを定義します。
このメソッドは Presenter から Entity を受け取り、画像のアップロードとレシピデータのアップロードを行います。

レシピの保存には画像のパスが必要になるため、最初に画像をアップロードした後にそのコールバックでアップロードした画像のパスを受け取り、パス情報を合わせてレシピデータを Firestore にアップロードします。
レシピデータにはレシピの作者名が必要となりますが、ここでは固定の名前を指定するようにしてください(発展課題にこの実装を書き直す課題があります)。

レシピデータのアップロード後、画面へ表示する内容に処理結果が必要ないため、Interactorに追加するメソッドのonSuccessコールバックで値を渡す必要はありません。

### Routing を作成

保存に成功した場合、レシピ作成画面を終了させる必要があります。
画面終了のメソッドを Contract に定義して、メソッドを実装しましょう。

画面の終了は単に `Activity#finish` を読んで画面を終了してください。
`Activity#finish` を呼び出すために Routing のコンストラクタには Activity を受け渡してください。

### View を作成

はじめに先程定義した Presenter を View のパラメータとして追加しましょう。

次に保存ボタンがタップされた際に先程実装した Presenter のメソッドを呼び出しましょう。
その際メソッドの引数に UI からテキストと ViewModel から画像の URI をまとめて Entity としてデータを構築して渡します。

最後に送信が成功した場合とエラーの場合のトーストの表示をイベントごとにメソッドを分けて Contract に定義し、実装します。

### View, Presenter, Interactor の処理を接続

最後にこれまで実装した View, Interactor, Routing の処理を接続してみましょう。

Interactor を通してレシピ保存を行う際に適切に View のメソッドをコールバックとして渡しましょう。
成功した場合は画面を終了するので View のメソッド呼び出しと合わせて Routing のメソッドを呼び出す必要があります。


ここまで実装できたらアプリを起動してレシピ保存が保存出来ているかを確認してください。
レシピが保存出来ている場合、アプリを起動し直すとレシピ一覧に保存したレシピが表示されるようになります。
