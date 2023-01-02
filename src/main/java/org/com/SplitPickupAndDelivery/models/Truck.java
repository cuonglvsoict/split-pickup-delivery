package org.com.SplitPickupAndDelivery.models;

import java.util.ArrayList;

public class Truck {

    private String truckID;
    private String location;
    private String startWorkingTime;
    private double capacity;

    public String getStartWorkingTime() {
        return startWorkingTime;
    }

    public void setStartWorkingTime(String startWorkingTime) {
        this.startWorkingTime = startWorkingTime;
    }

    private ArrayList<String> forbiddenPoints;

    public String getTruckID() {
        return truckID;
    }

    public String getLocation() {
        return location;
    }

    public double getCapacity() {
        return capacity;
    }

    public ArrayList<String> getForbiddenPoints() {
        return forbiddenPoints;
    }

    public void setTruckID(String truckID) {
        this.truckID = truckID;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    public void setForbiddenPoints(ArrayList<String> forbiddenPoints) {
        this.forbiddenPoints = forbiddenPoints;
    }
}
