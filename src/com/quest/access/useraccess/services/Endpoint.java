
package com.quest.access.useraccess.services;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author CONSTANT
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Endpoint {
    String name();  // the message name
    public String[] shareMethodWith() default {};// the services that can share this method without privileges
    public String[] cacheModifiers() default {}; //if anything is specified here it means this is a cacheable method
    //and the cache modifiers are methods that affect the cached values on the client
}
