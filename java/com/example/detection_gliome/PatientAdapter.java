package com.example.detection_gliome;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {
    private List<Patient> patientList;
    private Context context;

    public PatientAdapter(Context context, List<Patient> patientList) {
        this.context = context;
        this.patientList = patientList;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        Patient patient = patientList.get(position);
        holder.fullName.setText(patient.getFullName());
        holder.gender.setText(patient.getGender());
        holder.governorate.setText(patient.getGovernorate());
        holder.age.setText("Age: " + patient.getAge());
        holder.status.setText(patient.getStatus());

        // Set click listener to open PatientViewActivity
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ViewPatientActivity.class);
            intent.putExtra("user_id",patient.getId());
            intent.putExtra("full_name", patient.getFullName());
            intent.putExtra("gender", patient.getGender());
            intent.putExtra("age", patient.getAge());
            intent.putExtra("governorate", patient.getGovernorate());
            intent.putExtra("etat", patient.getStatus());

            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return patientList.size();
    }

    public static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView fullName, gender, age, status, governorate;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            fullName = itemView.findViewById(R.id.fullName);
            gender = itemView.findViewById(R.id.gender);
            age = itemView.findViewById(R.id.age);
            status = itemView.findViewById(R.id.status);
            governorate = itemView.findViewById(R.id.governorate);
        }
    }
}
