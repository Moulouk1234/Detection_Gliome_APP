package com.example.detection_gliome;

public class Patient {
    private int id;
    private String fullName, gender, governorate,status;

    private int age;

    public Patient(int id,String fullName, String gender, int age, String governorate,String status) {
        this.id=id;
        this.fullName = fullName;
        this.gender = gender;
        this.age = age;
        this.governorate = governorate;
        this.status=status;
    }

    public String getFullName() { return fullName; }
    public String getGender() { return gender; }
    public int getAge() { return age; }
    public String getGovernorate() { return governorate; }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

