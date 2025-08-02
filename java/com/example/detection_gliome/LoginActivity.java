package com.example.detection_gliome;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

public class LoginActivity extends AppCompatActivity {

    EditText edtEmail, edtPassword;
    TextView forgottxt,txtsignup;
    private static final int RC_SIGN_IN = 100;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        forgottxt=findViewById(R.id.forgottxt);
        txtsignup=findViewById(R.id.txtsignup);
        forgottxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SetPasswordActivity.class);
                startActivity(intent);
            }
        });
        txtsignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
                startActivity(intent);
            }
        });
        // Initialize Firebase and Google Sign-In
        mAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.cllient_id)) // Use your web client ID
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        ImageView googleSignInBtn = findViewById(R.id.btnGoogleSignIn);
        googleSignInBtn.setOnClickListener(v -> signInWithGoogle());
    }
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Toast.makeText(this, "TRY GOOGLE", Toast.LENGTH_SHORT).show();

            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        Toast.makeText(this, "ENTER FIRABASE", Toast.LENGTH_SHORT).show();

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            String email = firebaseUser.getEmail();
                            Toast.makeText(this, "Google"+email, Toast.LENGTH_SHORT).show();

                            new FetchUserByEmailTask().execute(email);
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Firebase Authentication Failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private class FetchUserByEmailTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String email = params[0];
            String result = "";

            try {
System.out.println("one");
                String urlString = "http://10.0.2.2:90/backend_php/User/getDataviaEmail.php?email=" + email;
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                InputStreamReader in = new InputStreamReader(connection.getInputStream());
                int inputStreamData = in.read();
                while (inputStreamData != -1) {
                    char current = (char) inputStreamData;
                    result += current;
                    inputStreamData = in.read();
                }

                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            try {
                JSONObject response = new JSONObject(result);
                String status = response.getString("status");
                System.out.println("two");

                if (status.equals("success")) {
                    JSONObject userJson = response.getJSONObject("data");
                    System.out.println("three"+userJson);

                    String fullName = userJson.getString("full_name");
                    String email = userJson.getString("email");
                    String password = ""; // Leave empty for Google
                    String mobile = userJson.getString("mobile");
                    String specialty = userJson.getString("specialty");
                    String profileDescription = userJson.getString("profile_description");
                    String startYear = userJson.getString("start_year");
                    String workplace = userJson.getString("workplace");
                    String profileImage = userJson.getString("profile_image");

                    User user = new User(
                            Integer.parseInt(userJson.getString("id")),
                            fullName, email, password, mobile,
                            specialty, profileDescription, startYear,
                            workplace, profileImage
                    );
                    User.setInstance(user);

                    Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, Landing_Activity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, response.getString("message"), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }


    public void goprofile(View view) {
        String email = edtEmail.getText().toString();
        String password = edtPassword.getText().toString();

        if (!email.isEmpty() && !password.isEmpty()) {
            new LoginTask().execute(email, password);
        } else {
            Toast.makeText(LoginActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
        }
    }

    private class LoginTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String email = params[0];
            String password = params[1];
            String result = "";

            try {
                URL url = new URL("http://10.0.2.2:90/backend_php/User/login.php");

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

                // Send login data as POST
                String postData = "email=" + email + "&password=" + password;
                OutputStream os = connection.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                // Get the response from the server
                InputStreamReader in = new InputStreamReader(connection.getInputStream());
                int inputStreamData = in.read();
                while (inputStreamData != -1) {
                    char current = (char) inputStreamData;
                    result += current;
                    inputStreamData = in.read();
                }

                in.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            try {
                // Parse the result from the PHP script (JSON response)
                JSONObject response = new JSONObject(result);
                String status = response.getString("status");

                if (status.equals("success")) {
                    JSONObject userJson = response.getJSONObject("user");
                    String fullName = userJson.getString("full_name");
                    String email = userJson.getString("email");
                    String password = userJson.getString("password");
                    String mobile = userJson.getString("mobile");
                    String specialty = userJson.getString("specialty");
                    String profileDescription = userJson.getString("profile_description");
                    String startYear = userJson.getString("start_year");
                    String workplace = userJson.getString("workplace");
                    String profileImage = userJson.getString("profile_image");

                    User user = new User(Integer.parseInt(userJson.getString("id")),fullName, email, password, mobile, specialty, profileDescription,
                            startYear, workplace, profileImage);
                    User.setInstance(user);
                    Intent i = new Intent(LoginActivity.this, Landing_Activity.class);

                    startActivity(i);
                    finish();
                } else {
                    // Show an error message
                    Toast.makeText(LoginActivity.this, response.getString("message"), Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
