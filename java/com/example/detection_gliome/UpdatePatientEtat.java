package com.example.detection_gliome;

import android.os.AsyncTask;
import android.util.Log;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdatePatientEtat extends AsyncTask<String, Void, String> {
    private static final String TAG = "UpdatePatientEtat";

    @Override
    protected String doInBackground(String... params) {
        String id_patient = params[0];
        String etat = params[1];
        String url = "http://10.0.2.2:90/backend_php/model/save_prediction.php?id_patient=" + id_patient + "&etat=" + etat;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException e) {
            Log.e(TAG, "Erreur lors de la requête HTTP", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            Log.d(TAG, "Réponse du serveur: " + result);
        } else {
            Log.e(TAG, "Erreur: aucune réponse du serveur");
        }
    }
}
