package com.checkingcontainer.core.model

enum class InspStatus(val label: String) {
    INSP("INSP"),
    OP("OP"),
    NEST("NEST"),
    EST("EST"),
    // Equipos no-reefer (contratos de mantenimiento): preventivo y correctivo
    // quedan como visita en el historial; REPARACION dispara el estimado.
    MANT_PREVENTIVO("PREVENTIVO"),
    MANT_CORRECTIVO("CORRECTIVO"),
    REPARACION("REPARACIÓN"),
}
