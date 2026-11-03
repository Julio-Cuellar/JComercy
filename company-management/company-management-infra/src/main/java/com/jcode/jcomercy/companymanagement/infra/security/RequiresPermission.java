package com.jcode.jcomercy.companymanagement.infra.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El permiso que exige una ruta REST. {@link RestPermissionInterceptor} lo comprueba contra los permisos que la
 * persona tiene en la empresa de la ruta, y una prueba de contrato exige que coincida con el `x-required-permission`
 * del OpenAPI.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** El permiso del catálogo, por ejemplo "branches.create". */
    String value();

    /** Lo que la persona intentó hacer, en infinitivo, para el mensaje de rechazo: "crear sucursales". */
    String action();
}
