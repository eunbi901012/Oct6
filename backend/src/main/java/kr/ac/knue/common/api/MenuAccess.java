package kr.ac.knue.common.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares required role and persisted screen permission; the system role is not an authorization bypass. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MenuAccess {
    /** Identifies the screen whose persisted menu decision must permit the operation. */
    String value();

    /** Lists permitted management roles in addition to, not instead of, the menu decision. */
    String[] roles() default {"R09"};
}
