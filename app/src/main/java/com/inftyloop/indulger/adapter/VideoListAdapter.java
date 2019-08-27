package com.inftyloop.indulger.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.inftyloop.indulger.R;
import com.inftyloop.indulger.listener.VideoStateListenerAdapter;
import com.inftyloop.indulger.model.entity.News;
import com.inftyloop.indulger.ui.MyJzVideoPlayer;
import com.inftyloop.indulger.util.ConfigManager;
import com.inftyloop.indulger.viewholder.BaseRecyclerViewHolder;

import java.util.List;

import cn.jzvd.JzvdStd;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;


public class VideoListAdapter extends BaseRecyclerViewAdapter<News, BaseRecyclerViewHolder> {

    private Context mContext;

    public VideoListAdapter(Context context, @NonNull List<News> data) {
        super(data);
        mContext = context;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int itemType) {
        BaseRecyclerViewHolder vh;
        switch (itemType) {
            case News.NOTIFICATION_HEADER:
                vh = new BaseRecyclerViewHolder(viewGroup, R.layout.notification_header);
                ((TextView) vh.findViewById(R.id.notification_header_text)).setText(String.format(mContext.getString(R.string.news_list_notification), ConfigManager.getInt("update_news_num", 35)));
                return vh;
            case News.LOAD_MORE_FOOTER:
                vh = new BaseRecyclerViewHolder(viewGroup, R.layout.load_more_footer);
                return vh;
            case News.VIDEO_NEWS:
                vh = new BaseRecyclerViewHolder(viewGroup, R.layout.item_video_list);
                return vh;
            default:
                vh = new BaseRecyclerViewHolder(viewGroup, R.layout.no_more_footer);
                return vh;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BaseRecyclerViewHolder vh, int position) {
        if (getItemViewType(position) != News.VIDEO_NEWS)
            return;
        News news = getData().get(position);

        vh.findViewById(R.id.ll_title).setVisibility(VISIBLE);
        vh.findViewById(R.id.ll_duration).setVisibility(VISIBLE);
        ((TextView) vh.findViewById(R.id.tv_title)).setText(news.getNewsEntry().getTitle());
        ((TextView) vh.findViewById(R.id.tv_duration)).setText("2:00");
        ((TextView) vh.findViewById(R.id.tv_author)).setText(news.getNewsEntry().getPublisherName());

        MyJzVideoPlayer videoPlayer = vh.findViewById(R.id.video_player);

        Glide.with(mContext)
                .setDefaultRequestOptions(new RequestOptions().frame(5000000).centerCrop())
                .load(news.getNewsEntry().getVideoUrl())
                .into(videoPlayer.thumbImageView); // pic
//        Glide.with(mContext).load(news.getNewsEntry().getPublisherAvatarUrl()).into((ImageView) vh.findViewById(R.id.iv_avatar));

        videoPlayer.setAllControlsVisiblity(GONE, GONE, VISIBLE, GONE, VISIBLE, GONE, GONE);
        videoPlayer.tinyBackImageView.setVisibility(GONE);
        videoPlayer.titleTextView.setText("");  // clear title
        videoPlayer.setVideoStateListener(new VideoStateListenerAdapter() {
            @Override
            public void onStartClick() {
                videoPlayer.setUp(news.getNewsEntry().getVideoUrl(), news.getNewsEntry().getTitle(), JzvdStd.SCREEN_NORMAL);
                videoPlayer.startVideo();

                videoPlayer.setAllControlsVisiblity(GONE, GONE, GONE, VISIBLE, VISIBLE, GONE, GONE);
                vh.findViewById(R.id.ll_duration).setVisibility(GONE);
                vh.findViewById(R.id.ll_title).setVisibility(GONE);
            }
        });

        // init favorite button
        ImageView buttonFavorite = vh.findViewById(R.id.iv_fav);
        buttonFavorite.setOnClickListener(new View.OnClickListener() {
            boolean isFav = false;

            @Override
            public void onClick(View view) {
                isFav = !isFav;
                int drawableResId = (isFav ? R.drawable.ic_favorite_fill_day_night : R.drawable.ic_favorite_day_night);
                buttonFavorite.setImageDrawable(mContext.getDrawable(drawableResId));
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return getData().get(position).getType();
    }
}