package org.com.SplitPickupAndDelivery.models;

public class Hub {
    private String hubID;
    private String hubName;
    private String hub_lat;
    private String hub_long;

    public String getHubID() {
        return hubID;
    }

    public String getHubName() {
        return hubName;
    }

    public String getHub_lat() {
        return hub_lat;
    }

    public String getHub_long() {
        return hub_long;
    }

    public void setHubID(String hubID) {
        this.hubID = hubID;
    }

    public void setHubName(String hubName) {
        this.hubName = hubName;
    }

    public void setHub_lat(String hub_lat) {
        this.hub_lat = hub_lat;
    }

    public void setHub_long(String hub_long) {
        this.hub_long = hub_long;
    }
}
