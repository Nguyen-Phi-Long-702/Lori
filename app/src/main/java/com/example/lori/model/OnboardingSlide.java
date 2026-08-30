package com.example.lori.model;

//Đại diện cho một slide trong màn hình onboarding
public class OnboardingSlide {
    //Resource id của icon hiển thị trên slide
    private final int iconRes;

    //Resource id của tiêu đề slide
    private final int titleRes;

    //Resource id của nội dung mô tả slide
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