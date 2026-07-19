package com.powerpredict.common.api;

public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    T data
) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, "00000", "OK", data);
  }

  public static <T> ApiResponse<T> fail(String code, String message) {
    return new ApiResponse<>(false, code, message, null);
  }
}