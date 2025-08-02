package com.example.detection_gliome;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Add_Patient extends AppCompatActivity {
    EditText edtFullName, edtAge;
    RadioGroup genderGroup;
    Spinner governorateSpinner;
    Button btnAddPatient;
    User user = User.getInstance();
    int userId = user.getId();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_patient);

        // Initialize UI components
        edtFullName = findViewById(R.id.edtFullName);
        edtAge = findViewById(R.id.edtAge);
        genderGroup = findViewById(R.id.genderGroup);
        governorateSpinner = findViewById(R.id.gouvernoratSpinner);
        btnAddPatient = findViewById(R.id.addPatientbtn);

        // Populate governorate spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.tunisian_governorates, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        governorateSpinner.setAdapter(adapter);

        // Back button listener
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // Add patient button listener
        btnAddPatient.setOnClickListener(v -> addPatient());
    }

    private void addPatient() {
        final String fullName = edtFullName.getText().toString().trim();
        final int selectedGenderId = genderGroup.getCheckedRadioButtonId();
        final String gender = selectedGenderId == R.id.radioMale ? "Male" : "Female";
        final String age = edtAge.getText().toString().trim();
        final String governorate = governorateSpinner.getSelectedItem().toString();

        // Validate input fields
        if (fullName.isEmpty() || age.isEmpty() || selectedGenderId == -1) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "http://10.0.2.2:90/backend_php/Patient/add_patient.php";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        String message = jsonObject.getString("message");
                        Toast.makeText(Add_Patient.this, message, Toast.LENGTH_LONG).show();

                        // If patient is added successfully, get the patient ID and go to upload report
                        if (jsonObject.getBoolean("success")) {
                            JSONObject patientObject = jsonObject.getJSONObject("patient");
                            int patientId = patientObject.getInt("id");
                            goLoadRapport(patientId);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(Add_Patient.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> Toast.makeText(Add_Patient.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show()) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("user_id", String.valueOf(userId));
                params.put("full_name", fullName);
                params.put("gender", gender);
                params.put("age", age);
                params.put("governorate", governorate);
                return params;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(stringRequest);
    }

    // Pass patient ID to Upload_report activity
    private void goLoadRapport(int patientId) {
        Intent intent = new Intent(Add_Patient.this, Upload_report.class);
        intent.putExtra("PATIENT_ID", String.valueOf(patientId));
        startActivity(intent);
    }
}
