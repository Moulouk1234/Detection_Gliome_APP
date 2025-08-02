package com.example.detection_gliome;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import android.os.AsyncTask;
import android.util.Log;

public class User {
    private int id;
    private String fullName;
    private String email;
    private String password;
    private String mobile;
    private String specialty;
    private String profileDescription;
    private String startYear;
    private String workplace;
    private String profileImage;

    private static User instance;  // Singleton instance

    // Constructor with parameters
    User(int id, String fullName, String email, String password, String mobile, String specialty,
         String profileDescription, String startYear, String workplace, String profileImage) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.mobile = mobile;
        this.specialty = specialty;
        this.profileDescription = profileDescription;
        this.startYear = startYear;
        this.workplace = workplace;
        this.profileImage = profileImage;
    }

    // Constructor without ID
    User(String fullName, String email, String password, String mobile, String specialty,
         String profileDescription, String startYear, String workplace, String profileImage) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.mobile = mobile;
        this.specialty = specialty;
        this.profileDescription = profileDescription;
        this.startYear = startYear;
        this.workplace = workplace;
        this.profileImage = profileImage;
    }

    // Constructor without password and ID
    User(String fullName, String email, String mobile, String specialty, String profileDescription,
         String startYear, String workplace, String profileImage) {
        this.fullName = fullName;
        this.email = email;
        this.mobile = mobile;
        this.specialty = specialty;
        this.profileDescription = profileDescription;
        this.startYear = startYear;
        this.workplace = workplace;
        this.profileImage = profileImage;
    }

    // Public method to get the instance of User
    public static User getInstance() {
        if (instance == null) {
            instance = new User("", "", "", "", "", "", "", "", ""); // Default values or from storage
        }
        return instance;
    }

    // Public method to set the user instance
    public static void setInstance(User user) {
        instance = user;
    }

    // Method to populate User object from a JSON response
    public static void populateFromJSON(JSONObject json) {
        try {
            instance.setFullName(json.getString("full_name"));
            instance.setEmail(json.getString("email"));
            instance.setMobile(json.getString("mobile"));
            instance.setSpecialty(json.getString("specialty"));
            instance.setProfileDescription(json.getString("profile_description"));
            instance.setStartYear(json.getString("start_year"));
            instance.setWorkplace(json.getString("workplace"));
            instance.setProfileImage(json.getString("profile_image"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to refresh the user data
    public void refreshUserData() {
        System.out.println("usernmamma"+this.getFullName());
        new GetUserDataTask().execute(this.getId());

    }

    // AsyncTask to fetch user data
    private static class GetUserDataTask extends AsyncTask<Integer, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(Integer... params) {
            int userId = params[0];
            String urlString = "http://10.0.2.2:90/backend_php/User/getDataUser.php?id=" + userId;
            StringBuilder response = new StringBuilder();
            HttpURLConnection connection = null;
            BufferedReader in = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);

                in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                // Parse the response as a JSON object
                return new JSONObject(response.toString());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        @Override
        protected void onPostExecute(JSONObject result) {
            if (result != null) {
                try {
                    if ("success".equals(result.getString("status"))) {
                        JSONObject userData = result.getJSONObject("data");
                        populateFromJSON(userData);  // Update the singleton instance with new data
                    } else {
                        Log.e("User", "Error: " + result.getString("message"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // Getter and Setter methods
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getProfileDescription() {
        return profileDescription;
    }

    public void setProfileDescription(String profileDescription) {
        this.profileDescription = profileDescription;
    }

    public String getStartYear() {
        return startYear;
    }

    public void setStartYear(String startYear) {
        this.startYear = startYear;
    }

    public String getWorkplace() {
        return workplace;
    }

    public void setWorkplace(String workplace) {
        this.workplace = workplace;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }
    public static void clear() {
        instance = new User("", "", "", "", "", "", "", "", "");
    }

}
