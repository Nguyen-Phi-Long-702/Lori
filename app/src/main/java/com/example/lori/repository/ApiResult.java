package com.example.lori.repository;

//Kết quả một lần gọi API do Repository trả về cho ViewModel. ViewModel đổi các trạng thái lỗi thành câu thông báo (R.string)
public final class ApiResult<T> {

    public enum Status {
        SUCCESS,
        //Máy không có mạng, chưa gửi request
        OFFLINE,
        //Hết thời gian chờ (Render Free có thể đang ngủ)
        TIMEOUT,
        //Lỗi mạng khác (mất kết nối giữa chừng, không phân giải được tên miền...)
        NETWORK_ERROR,
        //Backend trả mã lỗi HTTP (4xx/5xx)
        HTTP_ERROR
    }

    //Giá trị trường "message" Backend trả khi user Free gọi API Premium (kèm mã 403)
    public static final String PREMIUM_REQUIRED = "PREMIUM_REQUIRED";

    private final Status status;
    private final T data;
    private final int httpCode;
    private final String errorMessage;

    private ApiResult(Status status, T data, int httpCode, String errorMessage) {
        this.status = status;
        this.data = data;
        this.httpCode = httpCode;
        this.errorMessage = errorMessage;
    }

    public static <T> ApiResult<T> success(T data, int httpCode) {
        return new ApiResult<>(Status.SUCCESS, data, httpCode, null);
    }

    public static <T> ApiResult<T> offline() {
        return new ApiResult<>(Status.OFFLINE, null, 0, null);
    }

    public static <T> ApiResult<T> timeout() {
        return new ApiResult<>(Status.TIMEOUT, null, 0, null);
    }

    public static <T> ApiResult<T> networkError() {
        return new ApiResult<>(Status.NETWORK_ERROR, null, 0, null);
    }

    public static <T> ApiResult<T> httpError(int httpCode, String errorMessage) {
        return new ApiResult<>(Status.HTTP_ERROR, null, httpCode, errorMessage);
    }

    public Status getStatus() { return status; }

    public boolean isSuccess() { return status == Status.SUCCESS; }

    //Dữ liệu trả về khi thành công; null khi lỗi (và cả khi API không có body, vd 204)
    public T getData() { return data; }

    //Mã HTTP khi SUCCESS hoặc HTTP_ERROR; 0 với các trạng thái còn lại
    public int getHttpCode() { return httpCode; }

    //Trường "message" trong body lỗi của Backend; null nếu không đọc được
    public String getErrorMessage() { return errorMessage; }

    //User Free gọi API Premium (vd GET /api/exams) → ViewModel mở màn nâng cấp
    public boolean isPremiumRequired() {
        return status == Status.HTTP_ERROR && httpCode == 403 && PREMIUM_REQUIRED.equals(errorMessage);
    }
}