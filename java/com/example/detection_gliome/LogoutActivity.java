package com.example.detection_gliome;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class LogoutActivity extends AppCompatActivity {
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logout_dialog);

        btn = findViewById(R.id.logout_button);

        // Set the button click listener to close the app
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeApp();
            }
        });
    }

    // Function to close the app
    private void closeApp() {
        try {
            User.setInstance(null);
            Intent i = new Intent(LogoutActivity.this, SplachActivity.class);

            startActivity(i);

            finishAffinity();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
