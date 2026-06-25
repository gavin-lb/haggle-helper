package com.hagglehelper;

public class TrackedItem {
    private final int itemId;
    private final String name;
    private final int cost;
    private final int value;

    public TrackedItem(int itemId, String name, int cost, int value) 
    {
        this.itemId = itemId;
        this.name = name;
        this.cost = cost;
        this.value = value;
    }

    public int getItemId() 
    {
        return itemId;
    }

    public String getName() 
    {
        return name;
    }

    public int getCost() 
    {
        return cost;
    }
    
    public int getValue() 
    {
        return value;
    }
}