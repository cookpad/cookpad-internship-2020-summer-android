package com.cookpad.android.minicookpad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookpad.android.minicookpad.databinding.FragmentRecipeListBinding
import com.google.firebase.firestore.FirebaseFirestore

class RecipeListFragment : Fragment() {
    private lateinit var binding: FragmentRecipeListBinding

    private lateinit var adapter: RecipeListAdapter

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RecipeListAdapter { recipeId, recipeName ->
            findNavController()
                .navigate(RecipeListFragmentDirections.showRecipeDetail(recipeId, recipeName))
        }.also { binding.recipeList.adapter = it }
        binding.recipeList.layoutManager = LinearLayoutManager(requireContext())
        binding.createButton.setOnClickListener { findNavController().navigate(R.id.createRecipe) }

        db.collection("recipes")
            .get()
            .addOnSuccessListener { result ->
                adapter.update(result.mapNotNull { Recipe.fromDocument(it) })
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "レシピ一覧の取得に失敗しました", Toast.LENGTH_SHORT).show()
            }
    }
}