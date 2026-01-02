package com.immichframe.immichframe.moderntls;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.conscrypt.Conscrypt;

import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Builder of custom {@link OkHttpClient} which supports TLS v1.2 and TLS v1.3 on older platforms.
 */
public class ModernTlsOkHttpClient {

    private static final String TAG = "ModernTlsOkHttpClient";

    private static class ReusableSingleton {
        private static final OkHttpClient INSTANCE = create();
        private static final Provider CONSCRYPT = Conscrypt.newProvider();
    }

    private ModernTlsOkHttpClient() {
    }

    /**
     * Returns an app-wide reusable {@link OkHttpClient} instance.
     */
    public static OkHttpClient reuse() {
        return ReusableSingleton.INSTANCE;
    }

    /**
     * Returns an app-wide reusable {@link Provider} instance from Conscrypt.
     */
    public static Provider conscrypt() {
        return ReusableSingleton.CONSCRYPT;
    }

    /**
     * Creates a new {@link OkHttpClient} instance.
     */
    public static OkHttpClient create() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .pingInterval(60, TimeUnit.SECONDS)
                .connectTimeout(2000, TimeUnit.MILLISECONDS);
        return enableModernTls(builder).build();
    }

    @NonNull
    public static OkHttpClient.Builder enableModernTls(@NonNull OkHttpClient.Builder client) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No modifications on Android 10+
            return client;
        }
        try {
            //X509TrustManager tm = Conscrypt.getDefaultX509TrustManager();
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS", conscrypt());
            //sslContext.init(null, new TrustManager[] { tm }, null);
            sslContext.init(null, trustAllCerts, null);
            client.sslSocketFactory(new ModernTlsSocketFactory(sslContext.getSocketFactory()), (X509TrustManager)trustAllCerts[0]);
            client.hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            Log.e(TAG, "Error while setting TLS", e);
        }
        return client;
    }
}