package org.com.SplitPickupAndDelivery.models;

import java.util.ArrayList;

public class Route {

    public String truckID;

    public ArrayList<Hub> path;
    public ArrayList<ArrayList<Request>> pickup;   // pickup[i] is the list of requests that are picked up at the ith hub of the route
    public ArrayList<ArrayList<Request>> drop;     // drop[i] is the list of requests that are delivered up at the ith hub of the route

}
