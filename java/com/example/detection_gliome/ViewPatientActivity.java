package com.example.detection_gliome;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewPatientActivity extends AppCompatActivity {
    private EditText fullName, age;
    private RadioGroup genderGroup;
    private Spinner governorateSpinner, statusSpinner;
    private Button btnUpdate, btnDelete,btnmodifyreport;
    private int userId;

    private String[] governorates = {"Tunis", "Ariana", "Ben Arous", "Manouba", "Nabeul", "Bizerte", "Beja", "Jendouba"};
    private String[] statusOptions = {"No Tumor", "Pituitary Tumor", "Meningioma Tumor", "Glioma Tumor"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_patient);

        // Initialize UI components
        fullName = findViewById(R.id.edtFullName);
        age = findViewById(R.id.edtAge);
        genderGroup = findViewById(R.id.genderGroup);
        governorateSpinner = findViewById(R.id.gouvernoratSpinner);
        statusSpinner = findViewById(R.id.statusSpinner);
        btnUpdate = findViewById(R.id.updatePatientBtn);
        btnDelete = findViewById(R.id.deletePatientBtn);
        btnmodifyreport=findViewById(R.id.modifyReportBtn);

        // Populate Spinners
        populateSpinner(governorateSpinner, Arrays.asList(governorates));
        populateSpinner(statusSpinner, Arrays.asList(statusOptions));

        // Retrieve patient data from Intent
        userId = getIntent().getIntExtra("user_id", 0);
        String name = getIntent().getStringExtra("full_name");
        String gender = getIntent().getStringExtra("gender");
        int patientAge = getIntent().getIntExtra("age", 0);
        String governorate = getIntent().getStringExtra("governorate");
        String status = getIntent().getStringExtra("etat");

        // Set initial values
        fullName.setText(name);
        age.setText(String.valueOf(patientAge));
        setGenderSelection(gender);
        setSpinnerSelection(governorateSpinner, governorate);
        setSpinnerSelection(statusSpinner, status);

        // Handle button click
        btnUpdate.setOnClickListener(v -> updatePatientInfo());
        btnDelete.setOnClickListener(v -> deletePatient());
        btnmodifyreport.setOnClickListener(v->openModifyReportActivity());
    }
    private void openModifyReportActivity() {
        Intent i = new Intent(ViewPatientActivity.this, ModifyReportActivity.class);
        i.putExtra("PATIENT_ID", String.valueOf(userId));
        startActivity(i);
         }

    private void populateSpinner(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setGenderSelection(String gender) {
        if (gender != null) {
            if (gender.equalsIgnoreCase("Male")) {
                ((RadioButton) findViewById(R.id.radioMale)).setChecked(true);
            } else if (gender.equalsIgnoreCase("Female")) {
                ((RadioButton) findViewById(R.id.radioFemale)).setChecked(true);
            }
        }
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        if (value != null) {
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
            int position = adapter.getPosition(value);
            if (position >= 0) {
                spinner.setSelection(position);
            }
        }
    }

    private void updatePatientInfo() {

        String updatedName = fullName.getText().toString().trim();
        String updatedAge = age.getText().toString().trim();
        int selectedGenderId = genderGroup.getCheckedRadioButtonId();
        String updatedGender = (selectedGenderId == R.id.radioMale) ? "Male" : "Female";
        String updatedGovernorate = governorateSpinner.getSelectedItem().toString();
        String updatedStatus = statusSpinner.getSelectedItem().toString();

        String url = "http://10.0.2.2:90/backend_php/Patient/update_patient.php";
        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.d("Update Response", "Full Response: " + response);
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        boolean success = jsonResponse.getBoolean("success");
                        String message = jsonResponse.getString("message");

                        // Show Toast on main thread using runOnUiThread
                        runOnUiThread(() -> Toast.makeText(ViewPatientActivity.this, message, Toast.LENGTH_SHORT).show());

                        if (success) {
                            finish(); // Close activity if update is successful
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e("JSON Parsing Error", "Error parsing response: " + response);
                        runOnUiThread(() -> Toast.makeText(ViewPatientActivity.this, "Response error!", Toast.LENGTH_SHORT).show());
                    }
                },
                error -> {
                    Log.e("Update Error", "Volley Error: " + error.toString());
                    if (error.networkResponse != null) {
                        Log.e("Update Error", "Status Code: " + error.networkResponse.statusCode);
                        Log.e("Update Error", "Response Data: " + new String(error.networkResponse.data));
                    }
                    // Show Toast on main thread using runOnUiThread
                    runOnUiThread(() -> Toast.makeText(ViewPatientActivity.this, "Update failed!", Toast.LENGTH_SHORT).show());
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("user_id", String.valueOf(userId));

                // Do not show Toast here, since it's a background thread

                params.put("full_name", updatedName);
                params.put("gender", updatedGender);
                params.put("age", updatedAge);
                params.put("etat", updatedStatus);
                params.put("governorate", updatedGovernorate);

                Log.d("POST Params", params.toString());
                return params;
            }
        };

        queue.add(postRequest);
    }
    private void deletePatient() {
        String url = "http://10.0.2.2:90/backend_php/Patient/delete_patient.php";
        RequestQueue queue = Volley.newRequestQueue(this);

        StringRequest deleteRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.d("DeletePatient", "Server Response: " + response);
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        boolean success = jsonResponse.optBoolean("success", false);
                        String message = jsonResponse.optString("message", "Unknown error occurred");

                            Toast.makeText(ViewPatientActivity.this, message, Toast.LENGTH_SHORT).show();
                            if (success) {
                                finish(); // Close activity only if deletion succeeded
                            }
                    } catch (JSONException e) {
                        e.printStackTrace();
                       Toast.makeText(ViewPatientActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e("Delete Error", "Volley Error: " + error.toString());
                  Toast.makeText(ViewPatientActivity.this, "Delete failed!", Toast.LENGTH_SHORT).show();
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("user_id", String.valueOf(userId)); // Ensure `userId` is correctly initialized
                return params;
            }
        };

        // Prevent caching and retry failed requests
        deleteRequest.setRetryPolicy(new DefaultRetryPolicy(
                5000, // 5 seconds timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(deleteRequest);
    }

}
