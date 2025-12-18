package com.matchmaking.config;

public class MatchmakingConfig {
    private int baseWindow = 100;
    private int maxWindow = 500;
    private int windowExpansionPerMin = 50;
    private int secondaryPenalty = 50;
    private int autofillPenalty = 100;
    private int maxMmrDiff = 100;
    private int maxAutofillPerTeam = 1;
    private int maxDuoMmrGap = 500;
    private int playersPerTeam = 5;
    private int playersPerMatch = 10;

    public int getBaseWindow() {
        return baseWindow;
    }

    public void setBaseWindow(int baseWindow) {
        this.baseWindow = baseWindow;
    }

    public int getMaxWindow() {
        return maxWindow;
    }

    public void setMaxWindow(int maxWindow) {
        this.maxWindow = maxWindow;
    }

    public int getWindowExpansionPerMin() {
        return windowExpansionPerMin;
    }

    public void setWindowExpansionPerMin(int windowExpansionPerMin) {
        this.windowExpansionPerMin = windowExpansionPerMin;
    }

    public int getSecondaryPenalty() {
        return secondaryPenalty;
    }

    public void setSecondaryPenalty(int secondaryPenalty) {
        this.secondaryPenalty = secondaryPenalty;
    }

    public int getAutofillPenalty() {
        return autofillPenalty;
    }

    public void setAutofillPenalty(int autofillPenalty) {
        this.autofillPenalty = autofillPenalty;
    }

    public int getMaxMmrDiff() {
        return maxMmrDiff;
    }

    public void setMaxMmrDiff(int maxMmrDiff) {
        this.maxMmrDiff = maxMmrDiff;
    }

    public int getMaxAutofillPerTeam() {
        return maxAutofillPerTeam;
    }

    public void setMaxAutofillPerTeam(int maxAutofillPerTeam) {
        this.maxAutofillPerTeam = maxAutofillPerTeam;
    }

    public int getMaxDuoMmrGap() {
        return maxDuoMmrGap;
    }

    public void setMaxDuoMmrGap(int maxDuoMmrGap) {
        this.maxDuoMmrGap = maxDuoMmrGap;
    }

    public int getPlayersPerTeam() {
        return playersPerTeam;
    }

    public void setPlayersPerTeam(int playersPerTeam) {
        this.playersPerTeam = playersPerTeam;
    }

    public int getPlayersPerMatch() {
        return playersPerMatch;
    }

    public void setPlayersPerMatch(int playersPerMatch) {
        this.playersPerMatch = playersPerMatch;
    }
}
