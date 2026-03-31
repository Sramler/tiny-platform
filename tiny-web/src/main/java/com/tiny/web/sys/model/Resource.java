package com.tiny.web.sys.model;

import jakarta.persistence.*;

@Entity
@Table(name = "resource")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // 资源名，如“用户新增权限”

    @Column(length = 200)
    private String path; // 请求路径，如 /api/user/add

    @Column(length = 10)
    private String method; // GET, POST, PUT, DELETE

    @Column(length = 20)
    private String type; // MENU, BUTTON, API

    @Column(name = "parent_id")
    private Long parentId; // 菜单结构层级

    // getter/setter


    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}