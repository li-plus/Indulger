package com.inftyloop.indulger.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.inftyloop.indulger.R;
import butterknife.BindView;
import com.inftyloop.indulger.listener.ScrollingGestureDetector;
import com.inftyloop.indulger.model.entity.NewsDetail;
import com.inftyloop.indulger.util.*;

public class NewsDetailHeaderView extends FrameLayout {
    private static final String NICK = "Indulger";  // used to bind javascript
    @BindView(R.id.tvTitle)
    TextView mTitle;
    @BindView(R.id.ll_info)
    LinearLayout mllInfo;
    @BindView(R.id.iv_avatar)
    ImageView mAvatar;
    @BindView(R.id.tv_author)
    TextView mAuthor;
    @BindView(R.id.tv_time)
    TextView mTime;
    @BindView(R.id.wv_content)
    NestedWebView mContent;
    @BindView(R.id.btn_fav)
    Button mFavBtn;

    private boolean mHasFollowed;
    private Context mContext;
    private LoadWebListener mWebListener;
    private ScrollingGestureDetector mGestureDectector;

    public NewsDetailHeaderView(Context ctx) {
        this(ctx, null);
    }

    public NewsDetailHeaderView(Context ctx, AttributeSet attrs) {
        this(ctx, attrs, 0);
    }

    public NewsDetailHeaderView(Context ctx, AttributeSet attrs, int defStyleAttr) {
        super(ctx, attrs, defStyleAttr);
        mContext = ctx;
        initView();
    }

    @SuppressWarnings("deprecation")
    private void initView() {
        inflate(getContext(), R.layout.header_news_detail, this);
        ButterKnife.bind(this, this);
        /*mGestureDectector = new ScrollingGestureDetector(new ScrollingGestureDetector.GestureListenerCallback() {
            @Override
            public void onShow() {
                mTitle.setVisibility(VISIBLE);
                mllInfo.setVisibility(VISIBLE);
            }

            @Override
            public void onHide() {
                mTitle.setVisibility(GONE);
                mllInfo.setVisibility(GONE);
            }
        });
        mContent.setGestureDetector(new GestureDetector(mGestureDectector));*/
        mHasFollowed = false;
    }

    private void addJs(WebView wv) {
        wv.loadUrl("javascript:(function pic(){ var imgList=\"\"; var imgs = document.getElementsByTagName(\"img\");" +
                "for(var i = 0; i < imgs.length; ++i) { var img = imgs[i]; imgList = imgList + img.src + \";\";" +
                "img.onclick=function(){ Indulger.openImg(this.src);} }" +
                " Indulger.getImgArray(imgList);})()");
    }

    public void setNewsDetail(NewsDetail detail, LoadWebListener listener) {
        mWebListener = listener;
        mTitle.setText(detail.title);
        if(detail.publisher == null)
            mllInfo.setVisibility(GONE);
        else {
            if(!TextUtils.isEmpty(detail.publisher.avatarUrl))
                GlideUtils.loadRound(mContext, detail.publisher.avatarUrl, mAvatar, R.mipmap.ic_circle_default);
            mAuthor.setText(detail.publisher.displayName);
            mTime.setText(DateUtils.getShortTime(mContext, detail.publishTime * 1000L));
        }
        if(TextUtils.isEmpty(detail.content))
            mContent.setVisibility(GONE);
        mContent.getSettings().setJavaScriptEnabled(true);
        mContent.addJavascriptInterface(new ShowPicJSBridge(mContext), NICK);
        // change background according to theme
        String bg_color = DisplayHelper.getColorStringFromAttr(mContext, R.attr.app_background_color);
        String text_color = DisplayHelper.getColorStringFromAttr(mContext, R.attr.foreground_text_color);

        String htmlPart1 = "<!DOCTYPE HTML html>\n" +
                "<head><meta charset=\"utf-8\"/>\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, minimum-scale=1.0, user-scalable=no\"/>\n" +
                "</head>\n" +
                "<body>\n" +
                "<style> \n" +
                "img{width:100%!important;height:auto!important}\n" +
                "body{ background-color: " + bg_color + ";\n" + "color: " + text_color +  ";\n}" +
                " </style>";
        String htmlPart2 = "</body></html>";

        String html = htmlPart1 + detail.content + htmlPart2;

        mContent.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        mContent.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                addJs(view);
                if (mWebListener != null){
                    mWebListener.onLoaded();
                }
            }
        });
    }

    @OnClick(R.id.btn_fav)
    public void onFollowBtnClick(View view) {
        mFavBtn.setText(getContext().getString(mHasFollowed ? R.string.follow : R.string.followed));
        mHasFollowed = !mHasFollowed;
        // TODO
    }

    public interface LoadWebListener {
        void onLoaded();
    }
}