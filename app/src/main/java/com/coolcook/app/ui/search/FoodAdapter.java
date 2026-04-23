package com.coolcook.app.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.coolcook.app.R;
import com.coolcook.app.ui.search.data.FavoriteFoodStore;
import com.coolcook.app.ui.search.model.FoodItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {

    public interface FoodClickListener {
        void onFoodClick(@NonNull FoodItem foodItem);
    }

    @NonNull
    private final List<FoodItem> foods = new ArrayList<>();
    @NonNull
    private final FavoriteFoodStore favoriteFoodStore;
    @NonNull
    private final FoodClickListener clickListener;

    public FoodAdapter(
            @NonNull FavoriteFoodStore favoriteFoodStore,
            @NonNull FoodClickListener clickListener) {
        this.favoriteFoodStore = favoriteFoodStore;
        this.clickListener = clickListener;
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
        holder.txtFavorite.setVisibility(favorite ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> clickListener.onFoodClick(food));
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
        final TextView txtFavorite;
        final TextView txtName;
        final TextView txtTag;

        FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFood = itemView.findViewById(R.id.imgFoodCard);
            txtCookTime = itemView.findViewById(R.id.txtFoodCookTime);
            txtFavorite = itemView.findViewById(R.id.txtFoodFavorite);
            txtName = itemView.findViewById(R.id.txtFoodName);
            txtTag = itemView.findViewById(R.id.txtFoodPrimaryTag);
        }
    }
}
