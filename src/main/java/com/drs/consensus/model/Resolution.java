package com.drs.consensus.model;

import java.util.List;
import java.util.Map;

/**
 * One resolution produced by the reconciliation engine.
 * <p>
 * Direct port of legacy {@code ReconciliationEngine.Resolution}, turned into
 * a proper class with getters so Jackson can serialise it.
 */
public class Resolution {

    private String resourceKey;
    private String resourceType;
    private String finalValue;
    private String method;
    private boolean needsHumanReview;
    private List<Map<String, Object>> contributingUpdates;
    private String explanation;

    public Resolution() {}

    public Resolution(String resourceKey, String resourceType, String finalValue,
                      String method, boolean needsHumanReview,
                      List<Map<String, Object>> contributingUpdates, String explanation) {
        this.resourceKey = resourceKey;
        this.resourceType = resourceType;
        this.finalValue = finalValue;
        this.method = method;
        this.needsHumanReview = needsHumanReview;
        this.contributingUpdates = contributingUpdates;
        this.explanation = explanation;
    }

    public String getResourceKey()                      { return resourceKey; }
    public void setResourceKey(String resourceKey)      { this.resourceKey = resourceKey; }
    public String getResourceType()                     { return resourceType; }
    public void setResourceType(String resourceType)    { this.resourceType = resourceType; }
    public String getFinalValue()                       { return finalValue; }
    public void setFinalValue(String finalValue)        { this.finalValue = finalValue; }
    public String getMethod()                           { return method; }
    public void setMethod(String method)                { this.method = method; }
    public boolean isNeedsHumanReview()                 { return needsHumanReview; }
    public void setNeedsHumanReview(boolean needsHumanReview) { this.needsHumanReview = needsHumanReview; }
    public List<Map<String, Object>> getContributingUpdates()           { return contributingUpdates; }
    public void setContributingUpdates(List<Map<String, Object>> list)  { this.contributingUpdates = list; }
    public String getExplanation()                      { return explanation; }
    public void setExplanation(String explanation)      { this.explanation = explanation; }
}
