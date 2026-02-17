package com.czcode.authservice.common.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

  private boolean enabled = true;
  private int maxSubjects = 20000;
  private List<Rule> rules = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxSubjects() {
    return maxSubjects;
  }

  public void setMaxSubjects(int maxSubjects) {
    this.maxSubjects = maxSubjects;
  }

  public List<Rule> getRules() {
    return rules;
  }

  public void setRules(List<Rule> rules) {
    this.rules = rules;
  }

  public static class Rule {
    private String id;
    private String path;
    private String method = "*";
    private long capacity = 60;
    private long refillTokens = 60;
    private long refillDurationSeconds = 60;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public long getCapacity() {
      return capacity;
    }

    public void setCapacity(long capacity) {
      this.capacity = capacity;
    }

    public long getRefillTokens() {
      return refillTokens;
    }

    public void setRefillTokens(long refillTokens) {
      this.refillTokens = refillTokens;
    }

    public long getRefillDurationSeconds() {
      return refillDurationSeconds;
    }

    public void setRefillDurationSeconds(long refillDurationSeconds) {
      this.refillDurationSeconds = refillDurationSeconds;
    }
  }
}
