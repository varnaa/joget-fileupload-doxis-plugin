package ae.gcg.plugins.doxis.fileupload.util;

import okhttp3.OkHttpClient;

public class HttpClientFactory {
    private static OkHttpClient client;

    private HttpClientFactory() {
    }

    public static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient();
        }
        return client;
    }
}

