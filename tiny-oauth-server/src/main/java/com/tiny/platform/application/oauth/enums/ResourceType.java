package com.tiny.platform.application.oauth.enums;

public enum ResourceType {
    DIRECTORY(0, "目录"),
    MENU(1, "菜单"),
    BUTTON(2, "按钮"),
    API(3, "接口");

    private final int code;
    private final String description;

    ResourceType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ResourceType fromCode(Integer code) {
        if (code == null) return DIRECTORY;
        for (ResourceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的资源类型 code: " + code);
    }

    @Override
    public String toString() {
        return description;
    }
}