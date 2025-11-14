package com.seveninterprise.clusterforge.dto;

import java.util.Map;

/**
 * DTO padronizado para respostas de erro da API
 * Garante que todas as respostas de erro sejam JSON válido
 */
public class ErrorResponse {
    private String message;
    private String error;
    private Integer status;
    private Map<String, String[]> errors;
    
    public ErrorResponse() {}
    
    public ErrorResponse(String message) {
        this.message = message;
        this.error = message;
    }
    
    public ErrorResponse(String message, Integer status) {
        this.message = message;
        this.error = message;
        this.status = status;
    }
    
    public ErrorResponse(String message, Map<String, String[]> errors) {
        this.message = message;
        this.error = message;
        this.errors = errors;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public Map<String, String[]> getErrors() {
        return errors;
    }
    
    public void setErrors(Map<String, String[]> errors) {
        this.errors = errors;
    }
}

