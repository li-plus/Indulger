package com.inftyloop.indulger.model.response;

import androidx.annotation.Keep;
import com.google.gson.Gson;
import com.inftyloop.indulger.model.entity.NewsData;
import com.inftyloop.indulger.model.entity.TipEntity;

import java.util.List;

@Keep
public class NewsResponse {
    public int login_status;
    public int total_number;
    public boolean has_more;
    public String post_content_hint;
    public int show_et_status;
    public int feed_flag;
    public int action_to_last_stick;
    public String message;
    public boolean has_more_to_refresh;
    public TipEntity tips;
    public List<NewsData> data;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
