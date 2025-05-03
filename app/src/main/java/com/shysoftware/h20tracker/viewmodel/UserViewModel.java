package com.shysoftware.h20tracker.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;
import com.shysoftware.h20tracker.model.Location;
import com.shysoftware.h20tracker.model.User;
import com.shysoftware.h20tracker.repository.UserRepository;
import com.shysoftware.h20tracker.views.InputDetailsActivity;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class UserViewModel extends ViewModel {

    private final UserRepository userRepository = new UserRepository();
    private final MutableLiveData<Boolean> updateStatus = new MutableLiveData<>();
    private final MutableLiveData<Boolean> profileExists = new MutableLiveData<>();
    private final MutableLiveData<List<Location>> geocodingResults = new MutableLiveData<>();
    private final MutableLiveData<List<User>> topUsers = new MutableLiveData<>();
    private final MutableLiveData<ProfileStats> profileStats = new MutableLiveData<>();

    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_USER_ID = "user_id";

    public LiveData<List<User>> getTopUsers() {
        return topUsers;
    }

    public LiveData<Boolean> getProfileExists() {
        return profileExists;
    }

    public LiveData<List<Location>> getGeocodingResults() {
        return geocodingResults;
    }

    public LiveData<Boolean> getUpdateStatus() {
        return updateStatus;
    }

    public LiveData<ProfileStats> getProfileStats() {
        return profileStats;
    }

    /**
     * Registers a user, including email and password for authentication.
     */
    public void registerUser(Context context, String email, String password, String confirmPassword) {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showToast(context, "Some fields are empty");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showToast(context, "Passwords are not equal");
            return;
        }

        userRepository.signUpUser(email, password, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast(context, "Sign-up failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                handleUserRegistrationResponse(context, response);
            }
        });
    }

    private void handleUserRegistrationResponse(Context context, Response response) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (response.isSuccessful()) {
            try {
                JSONObject root = new JSONObject(body);
                String newUserId = root.getJSONObject("user").getString("id");

                // Save userId to SharedPreferences
                saveUserId(context, newUserId);

                showToast(context, "Successful Registration!");
                context.startActivity(new Intent(context, InputDetailsActivity.class));
            } catch (JSONException e) {
                showToast(context, "Sign-up response parse error");
            }
        } else {
            handleSignUpError(response, body);
        }
    }

    private void handleSignUpError(Response response, String body) {
        String toastMsg = "Sign-up failed";
        try {
            JSONObject err = new JSONObject(body);
            if ("user_already_exists".equals(err.optString("error_code"))) {
                toastMsg = "Email already exists";
            } else {
                toastMsg = err.optString("msg", toastMsg);
            }
        } catch (JSONException ignored) {
        }
        showToast(toastMsg);
    }

    private void showToast(Context context, String message) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        );
    }

    private void showToast(String message) {
        // Assuming a global or activity context
        Toast.makeText(Application.getAppContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void saveUserId(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    /**
     * Fetch user profile stats: total water logged, longest login streak, and average daily intake
     */
    public void fetchProfileStats(String userId) {
        userRepository.fetchProfileStats(userId, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("PROFILE_STATS", "Failed to fetch profile stats: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    try {
                        // Assuming ProfileStats is a data model with appropriate fields
                        ProfileStats stats = new Gson().fromJson(body, ProfileStats.class);
                        profileStats.postValue(stats);
                    } catch (Exception e) {
                        Log.e("PROFILE_STATS", "JSON parsing error", e);
                    }
                }
            }
        });
    }
    
    /**
     * Check if the profile exists for the given userId
     */
    public void checkProfile(String userId) {
        userRepository.isProfileExist(userId, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                profileExists.postValue(false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean exists = response.isSuccessful() && new JSONArray(response.body().string()).length() > 0;
                profileExists.postValue(exists);
            }
        });
    }

    /**
     * Fetch top users list
     */
    public void topUsers() {
        userRepository.fetchTopUsers(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("TOP_USERS", "Failed to fetch: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                handleTopUsersResponse(response);
            }
        });
    }

    private void handleTopUsersResponse(Response response) throws IOException {
        if (response.isSuccessful() && response.body() != null) {
            String responseBody = response.body().string();
            try {
                Gson gson = new Gson();
                List<User> users = gson.fromJson(responseBody, List.class);
                topUsers.postValue(users);
            } catch (Exception e) {
                Log.e("TOP_USERS", "JSON parse error", e);
            }
        }
    }
}
