package com.example.detection_gliome;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Landing_Activity extends AppCompatActivity {
    private ImageView settingButton, addpatient;
    private TextView txt;
    private RecyclerView recyclerView;
    private PatientAdapter adapter;
    private List<Patient> patientList;
    private List<Patient> originalPatientList; // Store original list for filtering
    private User user;
    private Button startHealthCheckButton;
    private int userId;
    private final Handler handler = new Handler();
    private final int REFRESH_INTERVAL = 2000;
    private EditText searchInput;
    private boolean isSearching = false; // Flag to track search state
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_landing_page);
        user = User.getInstance();
        txt = findViewById(R.id.username);

        user.refreshUserData();
        System.out.println("username" + user.getFullName());
        txt.setText(user.getFullName());
        userId = user.getId();

        // Initialize search input
        searchInput = findViewById(R.id.searchInput);
        setupSearchFunctionality();

        ImageView imgUser = findViewById(R.id.imguser);
        String baseUrl = "http://10.0.2.2:90/backend_php/User/";
        String imagePath = user.getProfileImage();
        String fullImageUrl = baseUrl + imagePath;
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            Glide.with(this)
                    .load(fullImageUrl)
                    .placeholder(R.drawable.iconuser)
                    .transform(new CircleCrop())
                    .error(R.drawable.iconuser)
                    .into(imgUser);
        } else {
            imgUser.setImageResource(R.drawable.iconuser);
        }

        settingButton = findViewById(R.id.settingbutton);
        addpatient = findViewById(R.id.addpatient);
        settingButton.setOnClickListener(v -> goprofile());
        addpatient.setOnClickListener(v -> addPatient());

        recyclerView = findViewById(R.id.recyclerViewPatients);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        patientList = new ArrayList<>();
        originalPatientList = new ArrayList<>(); // Initialize original list
        adapter = new PatientAdapter(this, patientList);
        recyclerView.setAdapter(adapter);

        loadPatients();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSearching) {
                    refreshPatientsData();
                }
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        };

        startAutoRefresh();    }

    private void setupSearchFunctionality() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSearching = s.length() > 0; // Set searching flag based on whether there's text
                filterPatients(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                isSearching = hasFocus; // Set searching flag based on focus
            }
        });
    }
    private void filterPatients(String searchText) {
        List<Patient> filteredList = new ArrayList<>();
        if (searchText.isEmpty()) {
            filteredList.addAll(originalPatientList);
            isSearching = false; // No longer searching when text is empty
        } else {
            isSearching = true; // Actively searching
            for (Patient patient : originalPatientList) {
                if (patient.getFullName().toLowerCase().contains(searchText.toLowerCase())) {
                    filteredList.add(patient);
                }
            }
        }
        patientList.clear();
        patientList.addAll(filteredList);
        adapter.notifyDataSetChanged();
    }
    private void startAutoRefresh() {
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    public void goprofile() {
        Intent i = new Intent(Landing_Activity.this, ProfileActivity.class);
        startActivity(i);
    }

    public void addPatient() {
        Intent i = new Intent(Landing_Activity.this, Add_Patient.class);
        startActivity(i);
    }

    private void loadPatients() {
        patientList.clear();
        originalPatientList.clear();
        fetchPatientData();
    }

    private void refreshPatientsData() {
        patientList.clear();
        originalPatientList.clear();
        fetchPatientData();
    }

    private void fetchPatientData() {
        String url = "http://10.0.2.2:90/backend_php/Patient/get_patients.php";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            if (jsonResponse.getBoolean("success")) {
                                JSONArray patientsArray = jsonResponse.getJSONArray("patients");
                                for (int i = 0; i < patientsArray.length(); i++) {
                                    JSONObject patientObj = patientsArray.getJSONObject(i);

                                    String fullName = patientObj.getString("full_name");
                                    String gender = patientObj.getString("gender");
                                    int age = patientObj.getInt("age");
                                    String governorate = patientObj.getString("governorate");
                                    int id = patientObj.getInt("id");
                                    String status = patientObj.getString("etat");

                                    Patient patient = new Patient(id, fullName, gender, age, governorate, status);
                                    patientList.add(patient);
                                    originalPatientList.add(patient); // Add to original list
                                }
                                adapter.notifyDataSetChanged();
                            } else {
                                Log.e("API_ERROR", "No patients found or an error occurred.");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.e("API_ERROR", "JSON parsing error: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("API_ERROR", "Error: " + error.getMessage());
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("user_id", String.valueOf(userId));
                return params;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(stringRequest);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null); // Stop auto-refresh when activity is destroyed
    }
}