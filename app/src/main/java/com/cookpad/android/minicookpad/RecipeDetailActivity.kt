package com.cookpad.android.minicookpad

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.navArgs
import com.bumptech.glide.Glide
import com.cookpad.android.minicookpad.databinding.ActivityRecipeDetailBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class RecipeDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecipeDetailBinding

    private val args: RecipeDetailActivityArgs by navArgs()

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = args.recipeName
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }

        db.collection("recipes")
            .document(args.recipeId)
            .get()
            .addOnSuccessListener { document ->
                Recipe.fromDocument(document)?.let { recipe ->
                    supportActionBar?.title = recipe.title
                    recipe.imagePath?.let {
                        Glide.with(this)
                            .load(FirebaseStorage.getInstance().reference.child(it))
                            .into(binding.image)
                    }
                    (1..3).forEach { i ->
                        val stepId = resources.getIdentifier("step$i", "id", packageName)
                        findViewById<TextView>(stepId).text = recipe.steps.getOrNull(i - 1)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "レシピの取得に失敗しました", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
