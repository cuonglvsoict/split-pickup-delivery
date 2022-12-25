package org.com.SplitPickupAndDelivery.models;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Request {
    private String requestID;

    private String pickupPoint;
    private String deliveryPoint;

    private String pickupDateTime;
    private String deliveryDateTime;

    private double demand;

    public void setRequestID(String requestID) {
        this.requestID = requestID;
    }

    public void setPickupPoint(String pickupPoint) {
        this.pickupPoint = pickupPoint;
    }

    public void setDeliveryPoint(String deliveryPoint) {
        this.deliveryPoint = deliveryPoint;
    }

    public void setPickupDateTime(String pickupDateTime) {
        this.pickupDateTime = pickupDateTime;
    }

    public void setDeliveryDateTime(String deliveryDateTime) {
        this.deliveryDateTime = deliveryDateTime;
    }

    public void setDemand(double demand) {
        this.demand = demand;
    }

    public String getRequestID() {
        return requestID;
    }

    public String getPickupPoint() {
        return pickupPoint;
    }

    public String getDeliveryPoint() {
        return deliveryPoint;
    }

    public String getPickupDateTime() {
        return pickupDateTime;
    }

    public String getDeliveryDateTime() {
        return deliveryDateTime;
    }

    public double getDemand() {
        return demand;
    }

    public Request clone() {
        Request cpy = new Request();
        cpy.setRequestID(this.requestID);
        cpy.setDemand(this.demand);
        cpy.setPickupPoint(this.pickupPoint);
        cpy.setDeliveryPoint(this.deliveryPoint);
        cpy.setPickupDateTime(this.pickupDateTime);
        cpy.setDeliveryDateTime(this.deliveryDateTime);

        return cpy;
    }

}
