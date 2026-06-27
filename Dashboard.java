package com.mindmate.model;

import java.util.List;
import java.util.Map;

public class Dashboard {
    public int entriesCount;
    public List<String> patterns;
    public List<String> recommendedPlan;
    public double averageMood;
    public double averageStress;
    public double averageSleep;
    public List<String> topTriggers;
    public List<String> recurringEmotions;
    public List<Map<String, Object>> trend;
}
