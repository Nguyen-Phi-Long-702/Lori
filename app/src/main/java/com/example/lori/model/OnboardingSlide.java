package com.example.lori.model;

public class OnboardingSlide {
    private final int iconRes;
    private final int titleRes;
    private final int descRes;

    public OnboardingSlide(int iconRes, int titleRes, int descRes) {
        this.iconRes = iconRes;
        this.titleRes = titleRes;
        this.descRes = descRes;
    }

    public int getIconRes() { return iconRes; }
    public int getTitleRes() { return titleRes; }
    public int getDescRes() { return descRes; }
}