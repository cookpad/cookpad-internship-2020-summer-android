package com.cookpad.android.minicookpad

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cookpad.android.minicookpad.databinding.ListitemRecipeBinding
import com.google.firebase.storage.FirebaseStorage

typealias OnRecipeClickListener = (String, String) -> Unit

class RecipeListAdapter(
    private val onRecipeClickListener: OnRecipeClickListener
) : RecyclerView.Adapter<RecipeListAdapter.RecipeViewHolder>() {

    private var recipeList: List<Recipe> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ListitemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(
            binding
        )
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(recipeList[position], onRecipeClickListener)
    }

    override fun getItemCount(): Int = recipeList.size

    override fun getItemViewType(position: Int): Int =
        R.layout.listitem_recipe

    fun update(recipeList: List<Recipe>) {
        DiffUtil
            .calculateDiff(
                RecipeDiffCallback(
                    this.recipeList,
                    recipeList
                )
            )
            .dispatchUpdatesTo(this)
        this.recipeList = recipeList
    }

    class RecipeViewHolder(
        private val binding: ListitemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context

        fun bind(recipe: Recipe, onRecipeClickListener: OnRecipeClickListener) {
            binding.root.setOnClickListener {
                recipe.id?.let { id -> onRecipeClickListener.invoke(id, recipe.title) }
            }
            binding.title.text = recipe.title
            binding.authorName.text = "by ${recipe.authorName}"
            recipe.imagePath?.let { path ->
                Glide.with(context)
                    .load(FirebaseStorage.getInstance().reference.child(path))
                    .into(binding.image)
            }
            binding.steps.text = recipe.steps.joinToString("、")
        }
    }

    class RecipeDiffCallback(
        private val oldList: List<Recipe>,
        private val newList: List<Recipe>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].javaClass == newList[newItemPosition].javaClass

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].id == newList[newItemPosition].id
    }
}