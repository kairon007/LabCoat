package com.commit451.gitlab.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.commit451.gitlab.R;
import com.commit451.gitlab.model.api.Snippet;
import com.commit451.gitlab.viewHolder.LoadingFooterViewHolder;
import com.commit451.gitlab.viewHolder.SnippetViewHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class SnippetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{

    private static final int FOOTER_COUNT = 1;

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    public interface Listener {
        void onSnippetClicked(Snippet snippet);
    }
    private Listener mListener;
    private List<Snippet> mValues;
    private boolean mLoading;

    private final View.OnClickListener mOnItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = (int) v.getTag(R.id.list_position);
            mListener.onSnippetClicked(getValueAt(position));
        }
    };

    public SnippetAdapter(Listener listener) {
        mListener = listener;
        mValues = new ArrayList<>();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_ITEM:
                SnippetViewHolder holder = SnippetViewHolder.inflate(parent);
                holder.itemView.setOnClickListener(mOnItemClickListener);
                return holder;
            case TYPE_FOOTER:
                return LoadingFooterViewHolder.inflate(parent);
        }
        throw new IllegalStateException("No holder for viewType " + viewType);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SnippetViewHolder) {
            Snippet snippet = getValueAt(position);
            ((SnippetViewHolder) holder).bind(snippet);
            holder.itemView.setTag(R.id.list_position, position);
        } else if (holder instanceof LoadingFooterViewHolder) {
            ((LoadingFooterViewHolder) holder).bind(mLoading);
        }
    }

    @Override
    public int getItemCount() {
        return mValues.size() + FOOTER_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mValues.size()) {
            return TYPE_FOOTER;
        } else {
            return TYPE_ITEM;
        }
    }

    public Snippet getValueAt(int position) {
        return mValues.get(position);
    }

    public void setData(Collection<Snippet> milestones) {
        mValues.clear();
        addData(milestones);
    }

    public void addData(Collection<Snippet> milestones) {
        if (milestones != null) {
            mValues.addAll(milestones);
        }
        notifyDataSetChanged();
    }

    public void addSnippet(Snippet milestone) {
        mValues.add(0, milestone);
        notifyItemInserted(0);
    }

    public void updateIssue(Snippet snippet) {
        int indexToDelete = -1;
        for (int i=0; i<mValues.size(); i++) {
            if (mValues.get(i).getId() == snippet.getId()) {
                indexToDelete = i;
                break;
            }
        }
        if (indexToDelete != -1) {
            mValues.remove(indexToDelete);
            mValues.add(indexToDelete, snippet);
        }
        notifyItemChanged(indexToDelete);
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
        notifyItemChanged(mValues.size());
    }
}