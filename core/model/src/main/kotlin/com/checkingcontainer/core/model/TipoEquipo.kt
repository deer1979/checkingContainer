package com.checkingcontainer.core.model

/** Tipo de equipo inspeccionable. REEFER usa nº de contenedor ISO 6346;
 *  el resto usa un código de equipo libre (sugerido desde el serial). */
enum class TipoEquipo(val etiqueta: String) {
    REEFER("Contenedor reefer"),
    AIRE_ACONDICIONADO("Aire acondicionado"),
    CAMARA_FRIA("Cámara / cuarto frío"),
    CHILLER("Chiller"),
    OTRO("Otro equipo"),
}
