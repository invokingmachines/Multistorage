package com.invokingmachines.multistorage.sample.multitenancy;

public final class TenantContext {

    private static final ThreadLocal<String> TENANT_CODE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantCode(String code) {
        TENANT_CODE.set(code);
    }

    public static String getTenantCode() {
        return TENANT_CODE.get();
    }

    public static void clear() {
        TENANT_CODE.remove();
    }
}
