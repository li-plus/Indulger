package com.inftyloop.indulger.util;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.inftyloop.indulger.BuildConfig;
import com.inftyloop.indulger.MainApplication;
import com.inftyloop.indulger.api.ApiRetrofit;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@GlideModule
public class OkHTTPGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpUrlLoader.Factory factory = new OkHttpUrlLoader.Factory(OkHTTPImageClient.getInstance());
        glide.getRegistry().replace(GlideUrl.class, InputStream.class, factory);
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
