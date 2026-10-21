package org.star5025.backend.context;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


//这个类的作用是帮助你在前后端传输的时候带上userID，将userID传入线程中，到时候便于使用
public class BaseContext {

    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static Long requireCurrentId() {
        Long id = getCurrentId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return id;
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

}
