package com.example.lori.data.remote.dto;

import java.util.List;

//Phản hồi của POST /api/ai/identify-image (type = "image_identification"). objects tối đa 5 vật, độ tin cậy giảm dần; rỗng khi không nhận ra vật nào
public class AiImageResponse {
    public String type;
    public List<ImageObject> objects;

    public static class ImageObject {
        //Tên tiếng Anh của vật
        public String word;
        //IPA lấy từ từ điển Lori; null nếu từ điển không có
        public String ipa;
        //Nghĩa tiếng Việt của đồ vật
        public String meaning;
        //Độ tin cậy 0..1
        public double confidence;
        public Box box;
        //Câu ví dụ do AI tạo và bản dịch; null khi AI không tạo được
        public String example;
        public String exampleVi;
    }

    //Toạ độ chuẩn hoá 0..1 theo ảnh đã gửi: góc trên-trái (x, y) và kích thước (width, height)
    public static class Box {
        public double x;
        public double y;
        public double width;
        public double height;
    }
}