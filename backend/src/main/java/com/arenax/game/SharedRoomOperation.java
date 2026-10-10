package com.arenax.game;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SharedRoomOperation {
    int codeArgument() default 0;
    boolean global() default false;
}
