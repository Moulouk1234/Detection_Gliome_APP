package com.example.detection_gliome;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class Report_result_warning extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_result_warning);
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> goBack());
    }
    private void goBack() {
        finish();


    }
}