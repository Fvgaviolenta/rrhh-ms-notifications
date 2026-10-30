package com.rrhh.notifications.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RolesTest {

    @Test
    void sinRolOVacioEsSinRol() {
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim(null));
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim(""));
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim("  "));
        assertEquals(Roles.ADMIN_PLATAFORMA, Roles.authorityFromClaim("AdminPlataforma"));
        assertEquals(Roles.TRABAJADOR, Roles.authorityFromClaim("Trabajador"));
    }

    @Test
    void rolDesconocidoEsSinRol() {
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim("Gerente"));
    }

    @Test
    void claimDeAuthorityDePlataformaYSinRol() {
        assertEquals("AdminPlataforma", Roles.claimFromAuthority(Roles.ADMIN_PLATAFORMA));
        assertEquals("Trabajador", Roles.claimFromAuthority(Roles.TRABAJADOR));
        assertNull(Roles.claimFromAuthority(Roles.SIN_ROL));
    }
}
