package com.microwebservice.model;

import java.util.HashMap;
import java.util.List;

public class MyResponse {
    private String code;
    private String message;
    private List<HashMap<String, String>> data;

    public MyResponse(String code, String message, List<HashMap<String, String>> data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    //To cater for exception and error message
    public MyResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
