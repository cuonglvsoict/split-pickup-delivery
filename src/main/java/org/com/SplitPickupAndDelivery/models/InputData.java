package org.com.SplitPickupAndDelivery.models;

import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;

public class InputData {

    public ArrayList<Hub> hubs;
    public HashMap<Pair<String, String>, Long> travel_time;
    public ArrayList<Truck> trucks;
    public ArrayList<Request> requests;

}
