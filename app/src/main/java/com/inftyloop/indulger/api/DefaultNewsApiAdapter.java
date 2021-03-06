package com.inftyloop.indulger.api;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import com.google.gson.*;
import com.inftyloop.indulger.BuildConfig;
import com.inftyloop.indulger.MainApplication;
import com.inftyloop.indulger.R;
import com.inftyloop.indulger.listener.OnNewsListRefreshListener;
import com.inftyloop.indulger.model.entity.*;
import com.inftyloop.indulger.model.response.NewsResponse;
import com.inftyloop.indulger.util.DateUtils;
import com.inftyloop.indulger.util.NetworkUtils;

import com.inftyloop.indulger.util.Utils;
import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.http.GET;
import retrofit2.http.Query;
import rx.Observable;
import rx.Subscriber;

interface DefaultNewsApiService {
    String BASE_URL = "https://api2.newsminer.net/";
    String GET_NEWS_INFO = "svc/news/queryNewsList";

    @GET(GET_NEWS_INFO)
    Observable<JsonObject> getNewsInfo(@Query("size") int size, @Query("page") int page, @Query("startDate") String startDate,
                                       @Query("endDate") String endDate, @Query("words") String words,
                                       @Query("categories") String categories);
}

public class DefaultNewsApiAdapter extends BaseNewsApiAdapter {
    public final static String TAG = DefaultNewsApiAdapter.class.getSimpleName();

    private OnNewsListRefreshListener mRefreshListener;

    private DefaultNewsApiService mApiService;
    private ToutiaoApiService mToutiaoApiService;
    private final int NUM_ELEM_PER_PAGE = 15;
    private static final Map<String, String> CHANNEL_NAME_MAPPER = Collections.unmodifiableMap(new HashMap<String, String>() {{
        put("news_society", "社会");
        put("news_entertainment", "娱乐");
        put("news_tech", "科技");
        put("news_auto", "汽车");
        put("news_sports", "体育");
        put("news_finance", "财经");
        put("news_health", "健康");
        put("news_military", "军事");
        put("news_education", "教育");
        put("news_culture", "文化");
    }});

    // indicates start and end time of channels in memory
    private static Map<String, Long> channelStartTime = new HashMap<>();
    private static Map<String, Long> channelEndTime = new HashMap<>();

    public DefaultNewsApiAdapter(@NonNull OnNewsListRefreshListener refreshListener) {
        if (BuildConfig.DEBUG) {
            mApiService = ApiRetrofit.buildOrGet("THUDefault", DefaultNewsApiService.BASE_URL, DefaultNewsApiService.class, ApiRetrofit.LOG_INTERCEPTOR, ApiRetrofit.COMMON_HEADER_INTERCEPTOR);
        } else {
            mApiService = ApiRetrofit.buildOrGet("THUDefault", DefaultNewsApiService.BASE_URL, DefaultNewsApiService.class, ApiRetrofit.COMMON_HEADER_INTERCEPTOR);
        }
        mToutiaoApiService = ApiRetrofit.buildOrGet("toutiao_video", Definition.TOUTIAO_BASE_SERVER_URL, ToutiaoApiService.class, ApiRetrofit.TOUTIAO_HEADER_INTERCEPTOR);
        mRefreshListener = refreshListener;
    }

    private NewsLoadRecord getMostRecentNewsLoadRecordOlderThan(String channel, long tsMillis) {
        List<NewsLoadRecord> res = LitePal.where("channelCode = ? AND startTime <= ?", channel, Long.valueOf(tsMillis).toString()).order("startTime desc").find(NewsLoadRecord.class);
        if (res.size() > 0)
            return res.get(0);
        else
            return null;
    }

    private Pair<List<NewsEntry>, Pair<Long, Long>> jsonToNewsEntry(JsonObject obj, String channel, boolean save) {
        Pattern pat = Pattern.compile("\\[(.*?)\\]");
        Pattern patCr = Pattern.compile("(\\n)");
        JsonArray data = obj.getAsJsonArray("data");
        long newest_time = 0;
        long oldest_time = Long.MAX_VALUE;
        List<NewsEntry> news_entries = new ArrayList<>();
        for (JsonElement d : data) {
            JsonObject dd = d.getAsJsonObject();
            long pubTime = DateUtils.getTimeStamp(dd.get("publishTime").getAsString(), "yyyy-MM-dd HH:mm:ss");
            if (pubTime > 0) {
                if (pubTime > newest_time)
                    newest_time = pubTime;
                if (pubTime < oldest_time)
                    oldest_time = pubTime;
                NewsEntry entry = new NewsEntry();
                entry.setPublishTime(pubTime);
                entry.setTitle(dd.get("title").getAsString());
                entry.setVideoUrl(dd.get("video").getAsString());

                String imgStr = dd.get("image").getAsString();
                if (!TextUtils.isEmpty(imgStr)) {
                    Matcher matcher = pat.matcher(imgStr);
                    if (matcher.find()) {
                        String arr = matcher.group(1);
                        if (!arr.isEmpty()) {
                            String[] urls = arr.split("\\s*,\\s*");
                            for (String url : urls) {
                                if (!url.trim().isEmpty())
                                    entry.getImageUrls().add(url);
                            }
                        }
                    }
                }

                String content = dd.get("content").getAsString();
                StringBuffer contentBuf = new StringBuffer();
                if (0 < entry.getImageUrls().size()) {
                    contentBuf.append("<img src=\"" + entry.getImageUrls().get(0) + "\" " +
                            "inline=\"0\" alt=\"placeholder\" onerror=\"javascript:this.style.display='none';\">");
                }
                contentBuf.append("<p>");
                Matcher contentMatcher = patCr.matcher(content);
                while (contentMatcher.find()) {
                    contentMatcher.appendReplacement(contentBuf, "</p><p>");
                }
                contentMatcher.appendTail(contentBuf);
                contentBuf.append("</p>");
                entry.setContent(contentBuf.toString());

                entry.setUrl(dd.get("url").getAsString());
                entry.setUuid(channel + "_" + dd.get("newsID").getAsString());
                entry.setCategory(channel);
                entry.setPublisherName(dd.get("publisher").getAsString());

                JsonArray keywords = dd.getAsJsonArray("keywords");
                int curr = 0;
                for (JsonElement w : keywords) {
                    if (curr >= 5) break;
                    JsonObject ww = w.getAsJsonObject();
                    entry.getKeywords().add(ww.get("word").getAsString());
                    ++curr;
                }
                if (save)
                    entry.save();
                news_entries.add(entry);
            }
        }
        Pair<Long, Long> time_pair = new Pair<>(newest_time, oldest_time);
        return new Pair<>(news_entries, time_pair);
    }

    private Pair<List<News>, Pair<Long, Long>> sortThruNewsEntries(List<NewsEntry> entries) {
        long start_time = 0, end_time = Long.MAX_VALUE;
        List<News> newsList = new ArrayList<>();
        for (NewsEntry newsEntry : entries) {
            newsList.add(new News(newsEntry));
            if (newsEntry.getPublishTime() > start_time)
                start_time = newsEntry.getPublishTime();
            if (newsEntry.getPublishTime() < end_time)
                end_time = newsEntry.getPublishTime();
        }
        return new Pair<>(newsList, new Pair<>(start_time, end_time));
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void obtainNewsList(String channel, String keyword, boolean isLoadingMore) {
        boolean isRecommend = channel.equals(MainApplication.getContext().getString(R.string.channel_code_recommend));
        boolean isInitialLoading = !channelStartTime.containsKey(channel) || !channelEndTime.containsKey(channel);
        if (isRecommend && (!isLoadingMore || isInitialLoading)) {
            // recommend list refresh, if there is new data, reset current records
            // only hooks iff. there is network
            if(NetworkUtils.isNetworkAvailable(MainApplication.getContext())) {
                Date curr = new Date();
                addSubscription(mApiService.getNewsInfo(NUM_ELEM_PER_PAGE, 1, "",
                        DateUtils.formatDateTime(curr, "yyyy-MM-dd HH:mm:ss"), keyword, CHANNEL_NAME_MAPPER.get(channel)),
                        new Subscriber<JsonObject>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, e.getMessage());
                                Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                            }

                            @Override
                            public void onNext(JsonObject jsonObject) {
                                int total = jsonObject.get("total").getAsInt();
                                if (total > 0) {
                                    Pair<List<NewsEntry>, Pair<Long, Long>> res = jsonToNewsEntry(jsonObject, channel, true);
                                    List<NewsEntry> entries = res.first;
                                    if(entries.size() == 0) {
                                        Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                                        return;
                                    }
                                    LitePal.deleteAll(NewsLoadRecord.class, "channelCode = ?", channel);
                                    LitePal.deleteAll(NewsEntry.class, "category = ?", channel);
                                    NewsLoadRecord record = new NewsLoadRecord();
                                    record.setChannelCode(channel);
                                    record.setStartTime(res.second.first);
                                    record.setEndTime(res.second.second);
                                    record.save();
                                    Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(entries);
                                    channelStartTime.put(channel, list.second.first);
                                    channelEndTime.put(channel, list.second.second);
                                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                                }
                            }
                        });
            } else {
                if(isInitialLoading) {
                    // no network, try to read from db
                    long curr = new Date().getTime();
                    NewsLoadRecord record = getMostRecentNewsLoadRecordOlderThan(channel, curr);
                    if(record == null) {
                        Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                    } else {
                        List<NewsEntry> res = LitePal.where("publishTime <= ? AND category = ? ", Long.valueOf(record.getStartTime()).toString(), channel).order("publishTime desc").limit(15).find(NewsEntry.class);
                        Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(res);
                        if(list.first.size() > 0) {
                            channelStartTime.put(channel, list.second.first);
                            channelEndTime.put(channel, list.second.second);
                        }
                        Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                    }
                } else {
                    // do not refresh
                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                }
            }
            return;
        }
        if (isInitialLoading || isLoadingMore) {
            // first start, get some new data
            long curr = isInitialLoading ? new Date().getTime() : channelEndTime.get(channel) - 1000;
            Date currDate = new Date(curr);
            NewsLoadRecord record = getMostRecentNewsLoadRecordOlderThan(channel, curr);
            if (!NetworkUtils.isNetworkAvailable(MainApplication.getContext())) {
                if (record == null) {
                    // no data and no network, nothing can be loaded
                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                } else {
                    // fetch newest data from db, then update the status of adapter
                    List<NewsEntry> res = LitePal.where("publishTime <= ? AND category = ? ", Long.valueOf(record.getStartTime()).toString(), channel).order("publishTime desc").limit(15).find(NewsEntry.class);
                    Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(res);
                    if (isInitialLoading)
                        channelStartTime.put(channel, list.second.first);
                    channelEndTime.put(channel, list.second.second);
                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                }
            } else {
                // has network, start to load some new data
                // avoid loading duplicate values by controlling time segments
                addSubscription(mApiService.getNewsInfo(NUM_ELEM_PER_PAGE, 1,
                        record == null ? "" : DateUtils.formatDateTime(new Date(record.getStartTime() + 1000), "yyyy-MM-dd HH:mm:ss"),
                        DateUtils.formatDateTime(currDate, "yyyy-MM-dd HH:mm:ss"), keyword, CHANNEL_NAME_MAPPER.get(channel)),
                        new Subscriber<JsonObject>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, e.getMessage());
                                Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                            }

                            @Override
                            public void onNext(JsonObject jsonObject) {
                                int total = jsonObject.get("total").getAsInt();
                                if (total > 0) {
                                    Pair<List<NewsEntry>, Pair<Long, Long>> res = jsonToNewsEntry(jsonObject, channel, true);
                                    List<NewsEntry> entries = res.first;
                                    if (record != null && total < 15) {
                                        entries.addAll(LitePal.where("publishTime <= ? AND category = ? ", Long.valueOf(record.getStartTime()).toString(), channel).order("publishTime desc").limit(15 - total).find(NewsEntry.class));
                                        // merge with existing record
                                        record.setStartTime(res.second.first);
                                        record.save();
                                    } else {
                                        // create a new segment
                                        NewsLoadRecord record = new NewsLoadRecord();
                                        record.setChannelCode(channel);
                                        record.setStartTime(res.second.first);
                                        record.setEndTime(res.second.second);
                                        record.save();
                                    }
                                    Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(entries);
                                    if (isInitialLoading)
                                        channelStartTime.put(channel, list.second.first);
                                    channelEndTime.put(channel, list.second.second);
                                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                                } else if (record == null) {
                                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                                } else {
                                    List<NewsEntry> entries = LitePal.where("publishTime <= ? AND category = ? ", Long.valueOf(record.getStartTime()).toString(), channel).order("publishTime desc").limit(15).find(NewsEntry.class);
                                    Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(entries);
                                    if (isInitialLoading)
                                        channelStartTime.put(channel, list.second.first);
                                    channelEndTime.put(channel, list.second.second);
                                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                                }
                            }
                        });
            }
        } else {
            // there is some data in the controller and the user want to refresh
            if (!NetworkUtils.isNetworkAvailable(MainApplication.getContext())) {
                Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>())); // do not refresh if no network
            } else {
                Date currDate = new Date();
                long curr = currDate.getTime();
                NewsLoadRecord record = getMostRecentNewsLoadRecordOlderThan(channel, curr);
                addSubscription(mApiService.getNewsInfo(NUM_ELEM_PER_PAGE, 1,
                        DateUtils.formatDateTime(new Date(channelStartTime.get(channel) + 1000), "yyyy-MM-dd HH:mm:ss"),
                        DateUtils.formatDateTime(currDate, "yyyy-MM-dd HH:mm:ss"), keyword, CHANNEL_NAME_MAPPER.get(channel)),
                        new Subscriber<JsonObject>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, e.getMessage());
                                Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                            }

                            @Override
                            public void onNext(JsonObject jsonObject) {
                                int total = jsonObject.get("total").getAsInt();
                                Pair<List<NewsEntry>, Pair<Long, Long>> res = jsonToNewsEntry(jsonObject, channel, true);
                                List<NewsEntry> entries = res.first;
                                if (total == 0) {
                                    // do not update status if no new data could be loaded
                                    Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                                    return;
                                }
                                if (total < 15) {
                                    record.setStartTime(res.second.first);
                                    record.save();
                                } else {
                                    // create a new segment
                                    NewsLoadRecord record = new NewsLoadRecord();
                                    record.setChannelCode(channel);
                                    record.setStartTime(res.second.first);
                                    record.setEndTime(res.second.second);
                                    record.save();
                                }
                                Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(entries);
                                channelStartTime.put(channel, list.second.first);
                                if (total >= 15)
                                    channelEndTime.put(channel, list.second.second);
                                Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                            }
                        });
            }
        }
    }

    public void obtainSearchResult(String keyword, boolean isLoadingMore) {
        // if loading more is true(same keyword), fetch more news with the same keyword.
        // if loading more is false(keyword changed), reset end time to current time.
        String channel = "search";
        if (!NetworkUtils.isNetworkAvailable(MainApplication.getContext())) {
            Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
            return;
        }
        long endTime = isLoadingMore ? channelEndTime.get(channel) - 1000 : new Date().getTime();
        Date endDate = new Date(endTime);
        String endDateTime = DateUtils.formatDateTime(endDate, "yyyy-MM-dd HH:mm:ss");

        addSubscription(mApiService.getNewsInfo(NUM_ELEM_PER_PAGE, 1, "", endDateTime, keyword, ""),
                new Subscriber<JsonObject>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, e.getMessage());
                        Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                    }

                    @Override
                    public void onNext(JsonObject jsonObject) {
                        int total = jsonObject.get("total").getAsInt();
                        if (total > 0) {
                            Pair<List<NewsEntry>, Pair<Long, Long>> res = jsonToNewsEntry(jsonObject, channel, false);
                            List<NewsEntry> entries = res.first;
                            Pair<List<News>, Pair<Long, Long>> list = sortThruNewsEntries(entries);
                            channelEndTime.put(channel, list.second.second);
                            Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(list.first));
                        } else {
                            Utils.postTaskSafely(() -> mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                        }
                    }
                });
    }

    public void obtainFavoriteList(boolean isLoadingMore) {
        String channel = "favorite";
        Long endTime = (isLoadingMore ? channelEndTime.get(channel) : new Date().getTime());
        if (endTime == null)
            endTime = new Date().getTime();

        List<NewsFavEntry> res = LitePal.where("markFavoriteTime < ?", endTime.toString()).order("markFavoriteTime desc").limit(15).find(NewsFavEntry.class);
        if (res.isEmpty()) {
            mRefreshListener.onNewsListRefresh(new ArrayList<>());
            return;
        }
        channelEndTime.put(channel, res.get(res.size() - 1).getPublishTime());
        List<News> news = new ArrayList<>();
        for (NewsFavEntry newsEntry : res) {
            news.add(new News(newsEntry));
        }
        mRefreshListener.onNewsListRefresh(news);
    }

    public void obtainToutiaoVideoList(boolean isLoadingMore) {
        Long last = channelStartTime.get("toutiao_video");
        if(last == null)
            last = System.currentTimeMillis() / 1000;
        addSubscription(mToutiaoApiService.getNewsList("video", 0, last),
                new Subscriber<NewsResponse>() {
                    @Override
                    public void onCompleted() {}

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        Utils.postTaskSafely(()->mRefreshListener.onNewsListRefresh(new ArrayList<>()));
                    }

                    @Override
                    public void onNext(NewsResponse newsResponse) {
                        Long last = System.currentTimeMillis() / 1000;
                        channelStartTime.put("toutiao_video", last);
                        List<NewsData> data = newsResponse.data;
                        List<News> newsList = new ArrayList<>();
                        Gson gson = new Gson();
                        if(data != null) {
                            Log.e(TAG, "Toutiao loaded " + data.size());
                            for(NewsData item : data) {
                                try {
                                    JsonObject obj = gson.fromJson(item.content, JsonObject.class);
                                    NewsEntry entry = new NewsEntry();
                                    entry.setCategory("toutiao_video");
                                    entry.setContent("");
                                    JsonObject publisher = obj.getAsJsonObject("media_info");
                                    entry.setPublisherName(publisher.get("name").getAsString());
                                    entry.setPublisherAvatarUrl(publisher.get("avatar_url").getAsString());
                                    entry.setUrl(obj.get("article_url").getAsString());
                                    entry.setTitle(obj.get("title").getAsString());
                                    entry.setUuid(obj.get("rid").getAsString());
                                    entry.setPublishTime(obj.get("publish_time").getAsLong() * 1000);
                                    entry.setVideoUrl(obj.get("url").getAsString());
                                    News news = new News(entry);
                                    news.setVideoDuration(obj.get("video_duration").getAsInt());
                                    news.setVideoThumbUrl(obj.getAsJsonObject("video_detail_info").getAsJsonObject("detail_video_large_image").get("url").getAsString());
                                    newsList.add(news);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        Utils.postTaskSafely(()->mRefreshListener.onNewsListRefresh(newsList));
                    }
                });
    }
}
