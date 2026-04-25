package com.coolcook.app.feature.search.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.feature.search.data.FavoriteFoodStore;
import com.coolcook.app.feature.search.model.FoodItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {

    public interface FoodClickListener {
        void onFoodClick(@NonNull FoodItem foodItem);
    }

    public interface FoodFavoriteListener {
        void onFoodFavoriteClick(@NonNull FoodItem foodItem);
    }

    @NonNull
    private final List<FoodItem> foods = new ArrayList<>();
    @NonNull
    private final FavoriteFoodStore favoriteFoodStore;
    @NonNull
    private final FoodClickListener clickListener;
    @NonNull
    private final FoodFavoriteListener favoriteListener;

    public FoodAdapter(
            @NonNull FavoriteFoodStore favoriteFoodStore,
            @NonNull FoodClickListener clickListener,
            @NonNull FoodFavoriteListener favoriteListener) {
        this.favoriteFoodStore = favoriteFoodStore;
        this.clickListener = clickListener;
        this.favoriteListener = favoriteListener;
    }

    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food_card, parent, false);
        return new FoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
        FoodItem food = foods.get(position);
        holder.imgFood.setImageResource(food.resolveImageResId(holder.itemView.getContext()));
        holder.txtName.setText(food.getName());
        holder.txtTag.setText(food.getPrimarySuitableTag());
        holder.txtCookTime.setText(String.format(Locale.US, "%d MIN", food.getCookTimeMinutes()));

        boolean favorite = favoriteFoodStore.isFavorite(food.getId());
        holder.imgFavorite.setImageResource(favorite ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
        holder.imgFavorite.setColorFilter(holder.itemView.getContext().getColor(
                favorite ? R.color.error : R.color.on_surface_variant));
        holder.imgFavorite.setContentDescription(favorite ? "Bỏ yêu thích" : "Yêu thích");

        holder.itemView.setOnClickListener(v -> clickListener.onFoodClick(food));
        holder.imgFavorite.setOnClickListener(v -> favoriteListener.onFoodFavoriteClick(food));
    }

    @Override
    public int getItemCount() {
        return foods.size();
    }

    public void submitFoods(@NonNull List<FoodItem> nextFoods) {
        foods.clear();
        foods.addAll(nextFoods);
        notifyDataSetChanged();
    }

    static class FoodViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgFood;
        final TextView txtCookTime;
        final ImageView imgFavorite;
        final TextView txtName;
        final TextView txtTag;

        FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFood = itemView.findViewById(R.id.imgFoodCard);
            txtCookTime = itemView.findViewById(R.id.txtFoodCookTime);
            imgFavorite = itemView.findViewById(R.id.txtFoodFavorite);
            txtName = itemView.findViewById(R.id.txtFoodName);
            txtTag = itemView.findViewById(R.id.txtFoodPrimaryTag);
        }
    }
}
