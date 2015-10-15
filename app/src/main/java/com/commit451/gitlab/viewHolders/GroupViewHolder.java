package com.commit451.gitlab.viewHolders;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.commit451.gitlab.R;
import com.commit451.gitlab.model.Group;
import com.squareup.picasso.Picasso;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * View associated with a group
 * Created by John on 10/8/15.
 */
public class GroupViewHolder extends RecyclerView.ViewHolder{

    public static GroupViewHolder newInstance(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Bind(R.id.group_image) public ImageView image;
    @Bind(R.id.group_name) public TextView name;
    @Bind(R.id.group_description) public TextView description;


    public GroupViewHolder(View view) {
        super(view);
        ButterKnife.bind(this, view);
    }

    public void bind(Group group) {
        Picasso.with(itemView.getContext())
                .load(group.getAvatarUrl())
                .into(image);
        name.setText(group.getName());
        if (TextUtils.isEmpty(group.getDescription())) {
            description.setVisibility(View.VISIBLE);
            description.setText(group.getDescription());
        } else {
            description.setVisibility(View.GONE);
        }
    }
}
